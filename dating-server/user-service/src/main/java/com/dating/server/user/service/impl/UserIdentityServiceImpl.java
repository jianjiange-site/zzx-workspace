package com.dating.server.user.service.impl;

import com.dating.server.user.constant.CacheKeys;
import com.dating.server.user.dto.IdentityResolveResult;
import com.dating.server.user.entity.UserInfo;
import com.dating.server.user.entity.UserLoginPhone;
import com.dating.server.user.entity.UserDeviceRegistration;
import com.dating.server.user.entity.UserThirdPartyRegistration;
import com.dating.server.user.exception.BizException;
import com.dating.server.user.exception.ErrorCodes;
import com.dating.server.user.manager.UserDeviceManager;
import com.dating.server.user.manager.UserInfoManager;
import com.dating.server.user.manager.UserLoginPhoneManager;
import com.dating.server.user.manager.UserThirdPartyManager;
import com.dating.server.user.service.UserIdentityService;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 身份解析服务实现
 *
 * 这个类负责三种登录方式的「找用户或创建用户」流程。
 * 简单说：用户用手机号/第三方/设备来登录，我们去数据库查这个身份有没有绑定过用户，
 * 有就返回已有的 userId，没有就创建一个新用户（占位用户）并绑定这个身份。
 */
@Slf4j
@Service                    // 标记为 Spring 的服务层 Bean
@RequiredArgsConstructor    // Lombok：为 final 字段自动生成构造方法（Spring 依赖注入用）
public class UserIdentityServiceImpl implements UserIdentityService {

    // ===== 依赖注入（Spring 自动把 Manager 对象传进来） =====
    private final UserInfoManager userInfoManager;                 // 用户主表的操作
    private final UserLoginPhoneManager userLoginPhoneManager;     // 手机号绑定表的操作
    private final UserThirdPartyManager userThirdPartyManager;     // 第三方账号绑定表的操作
    private final UserDeviceManager userDeviceManager;             // 设备绑定表的操作

    /**
     * ===== 手机号登录 =====
     *
     * 流程（看懂这个，其他两个就懂了）：
     *
     * 第1步：校验手机号格式对不对（比如 +8613800000001 才是合法的）
     * 第2步：（TODO）加分布式锁，防止同一手机号同时注册两次
     * 第3步：去 user_login_phone 表查这个手机号有没有绑定过用户
     *   - 有绑定 → 更新用户的 last_open_at（最后登录时间），返回已有的 userId
     *   - 没绑定 → 创建占位用户 + 把手机号绑定上去，返回新的 userId（标记 pending=true）
     *
     * @param phoneE164 手机号，传进来的时候可能不是标准格式，里面会做规范化
     * @param appName   哪个 App 来的（比如 zzx-dating），同一个手机号在不同 App 可以各自注册
     * @return 身份解析结果，包含 userId 和是否是新用户（pending）
     */
    @Override
    @Transactional  // 整个方法在一个数据库事务里：要么全部成功，要么全部回滚
    public IdentityResolveResult resolveOrCreateByPhone(String phoneE164, String appName) {

        // === 第1步：校验手机号 ===
        // 用 Google 的 libphonenumber 库来校验手机号是否合法
        // 比如用户传 "13800000001" 会转成 "+8613800000001"（E.164 标准格式）
        String normalized = normalizePhone(phoneE164);

        // === 第2步：（TODO）加分布式锁 ===
        // 防止同一个手机号在极端情况下被两个人同时注册
        // 以后接 Redisson 实现，锁的 key 是 lock:user:register:phone:{手机号}:{appName}

        // === 第3步：查数据库，看这个手机号有没有绑定过用户 ===
        // user_login_phone 表存的是 (手机号, appName) → userId 的映射
        UserLoginPhone existing = userLoginPhoneManager.findByPhoneAndApp(normalized, appName);

        if (existing != null) {
            // ===== 情况A：这个手机号已经注册过了 =====
            // 更新用户的 last_open_at（记录"用户上次登录时间"）
            userInfoManager.touchLastOpenAt(existing.getUserId());
            log.info("手机号登录：已有用户, phoneE164={}, userId={}", normalized, existing.getUserId());
            // 返回已有 userId，pending=false 表示这不是新用户
            return IdentityResolveResult.existing(existing.getUserId());
        }

        // ===== 情况B：这个手机号还没注册过 =====
        // 第4步：创建一个占位用户（pending=true，nickname=User_{id}）
        UserInfo placeholder = userInfoManager.insertPlaceholder(appName);

        // 第5步：把手机号和这个新用户绑定在一起
        UserLoginPhone binding = new UserLoginPhone();
        binding.setUserId(placeholder.getId());       // 用户的 ID（雪花算法生成的分布式 ID）
        binding.setPhoneE164(normalized);              // 规范化后的手机号
        binding.setAppName(appName);                   // 哪个 App
        binding.setVerifiedAt(Instant.now());           // 手机号验证时间 = 现在
        userLoginPhoneManager.insert(binding);

        log.info("手机号登录：创建新用户, phoneE164={}, userId={}", normalized, placeholder.getId());
        // 返回新的 userId，pending=true 表示这是占位用户，需要前端引导补齐资料
        return IdentityResolveResult.pending(placeholder.getId());
    }

    /**
     * ===== 第三方登录（Google / Apple / 微信） =====
     *
     * 流程和手机号登录基本一样，只是查的表不同：
     * - 查 user_third_party_registration 表（第三方账号绑定表）
     * - 按 (platform, thirdPartyUserId) 来查
     *
     * @param platform         第三方平台标识（如 "google"、"apple"、"wechat"）
     * @param thirdPartyUserId 第三方平台返回的用户 ID（在该平台内唯一）
     * @param appName          应用标识
     * @param googleEmail      Google 邮箱（仅 Google 登录时有值，其他平台传 null）
     */
    @Override
    @Transactional
    public IdentityResolveResult resolveOrCreateByThirdParty(String platform, String thirdPartyUserId,
                                                             String appName, String googleEmail) {

        // === 第1步：查这个第三方账号有没有绑定过用户 ===
        UserThirdPartyRegistration existing = userThirdPartyManager.findActive(platform, thirdPartyUserId);

        if (existing != null) {
            // 已有绑定 → 更新最后登录时间 → 返回已有 userId
            userInfoManager.touchLastOpenAt(existing.getUserId());
            log.info("三方登录：已有用户, platform={}, userId={}", platform, existing.getUserId());
            return IdentityResolveResult.existing(existing.getUserId());
        }

        // === 第2步：没绑定过 → 创建占位用户 + 绑定第三方账号 ===
        UserInfo placeholder = userInfoManager.insertPlaceholder(appName);

        UserThirdPartyRegistration binding = new UserThirdPartyRegistration();
        binding.setUserId(placeholder.getId());
        binding.setPlatform(platform);
        binding.setThirdPartyUserId(thirdPartyUserId);
        binding.setAppName(appName);
        binding.setGoogleEmail(googleEmail);   // Google 邮箱，非 Google 登录时是 null
        userThirdPartyManager.insert(binding);

        log.info("三方登录：创建新用户, platform={}, userId={}", platform, placeholder.getId());
        return IdentityResolveResult.pending(placeholder.getId());
    }

    /**
     * ===== 设备快速登录 =====
     *
     * 没有短信验证码、没有第三方账号，直接用设备 ID 来登录。
     * 比如用户第一次打开 App 时，拿设备的唯一标识（iOS IDFV / Android SSAID）来注册。
     *
     * 流程：
     * - 查 user_device_registration 表（设备绑定表）
     * - 按 (deviceId, platform, appName) 查
     * - 有就返回，没有就创建 + 绑定
     *
     * @param deviceId 设备 ID（iOS IDFV 或 Android SSAID）
     * @param platform 设备平台（"ios" 或 "android"）
     * @param appName  应用标识
     */
    @Override
    @Transactional
    public IdentityResolveResult resolveOrCreateByDevice(String deviceId, String platform, String appName) {

        // === 第1步：查这个设备有没有绑定过用户 ===
        UserDeviceRegistration existing = userDeviceManager.findActive(deviceId, platform, appName);

        if (existing != null) {
            // 已有绑定 → 更新最后登录时间 → 返回已有 userId
            userInfoManager.touchLastOpenAt(existing.getUserId());
            log.info("设备登录：已有用户, deviceId={}, userId={}", deviceId, existing.getUserId());
            return IdentityResolveResult.existing(existing.getUserId());
        }

        // === 第2步：没绑定过 → 创建占位用户 + 绑定设备 ===
        UserInfo placeholder = userInfoManager.insertPlaceholder(appName);

        UserDeviceRegistration binding = new UserDeviceRegistration();
        binding.setUserId(placeholder.getId());
        binding.setDeviceId(deviceId);
        binding.setPlatform(platform);
        binding.setAppName(appName);
        userDeviceManager.insert(binding);

        log.info("设备登录：创建新用户, deviceId={}, userId={}", deviceId, placeholder.getId());
        return IdentityResolveResult.pending(placeholder.getId());
    }

    // ========================================================================
    // 私有方法：只能在这个类内部调用，外部看不见
    // ========================================================================

    /**
     * 校验手机号格式并转为标准 E.164 格式
     *
     * 用 Google 的 libphonenumber 库做校验，这个库能识别全球各国的手机号格式。
     * 不管用户传什么格式（如 13800000001、+86 138 0000 0001），都会转成 +8613800000001。
     *
     * 如果手机号不合法，会抛出 BizException（业务异常），错误码 PHONE_INVALID=10301。
     */
    private String normalizePhone(String phone) {
        // PhoneNumberUtil 是 libphonenumber 的工具类，用单例模式获取实例
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        try {
            // 解析手机号：第二个参数传 null 表示不指定国家码，由号码前缀自动判断
            Phonenumber.PhoneNumber number = util.parse(phone, null);

            // 判断这个号码是否合法（比如 13800000001 合法，12345 就不合法）
            if (!util.isValidNumber(number)) {
                throw new BizException(ErrorCodes.PHONE_INVALID, "手机号不合法: " + phone);
            }

            // 格式化为 E.164 标准格式：+国家码 + 号码，如 +8613800000001
            return util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);

        } catch (NumberParseException e) {
            // 号码格式太离谱，parse 阶段就失败了（比如全是字母）
            throw new BizException(ErrorCodes.PHONE_INVALID, "手机号解析失败: " + phone);
        }
    }
}
