# user-service 开发进度

## 状态：HTTP 接口全部完成并验证 ✅

- Tomcat 8080 ✓
- gRPC 9090 ✓
- Nacos 注册 ✓
- 数据库连接 ✓
- HTTP 链路全通 ✓（Controller → Service → Manager → MyBatis → PostgreSQL）

---

## 已完成

### 数据库（Flyway V1）
- `user_info` — 用户主表（昵称/性别/生日/年龄/Bio/职业/学历/身高/偏好位置/头像JSONB/pending/监管状态）
- `user_login_phone` — 手机号登录表
- `user_third_party_registration` — 第三方登录表
- `user_device_registration` — 设备注册表
- `user_interest` — 兴趣标签表

### Entity 层（5个）
- UserInfo / UserLoginPhone / UserThirdPartyRegistration / UserDeviceRegistration / UserInterest

### Mapper 层（5个）
- 5 个 MyBatis-Plus Mapper 接口

### Manager 层（5个）
- **UserInfoManager** — 缓存读（Redis TTL 24h）+ 直接 DB 读写 + cache evict
- **UserInterestManager** — 缓存读（TTL 7d）+ 全量替换
- **UserLoginPhoneManager** — 按手机号查 + 增删
- **UserThirdPartyManager** — 第三方账号查 + 增删
- **UserDeviceManager** — 设备查 + 增删

### Service 层（5个）
- **UserIdentityService** — 3 种登录注册流（手机号/第三方/设备），libphonenumber 手机号归一化
- **UserBanService** — 封禁检查，Redis 缓存 5 分钟
- **UserProfileService** — 资料 CRUD + onboarding
- **UserAvatarService** — 头像 presign URL 直传（S3 占位）
- **UserInterestService** — 兴趣标签全量替换

### HTTP Controller（5个，REST 接口全部完成）
- `POST /api/v1/identity/phone` — 手机号登录/注册
- `POST /api/v1/identity/third-party` — 第三方登录/注册
- `POST /api/v1/identity/device` — 设备登录/注册
- `GET /api/v1/ban/{userId}` — 封禁检查
- `GET /api/v1/profiles/{userId}` — 查资料
- `POST /api/v1/profiles/batch` — 批量查资料
- `PUT /api/v1/profiles/{userId}` — 更新资料
- `POST /api/v1/profiles/{userId}/onboarding` — onboarding
- `PUT /api/v1/interests/replace` — 兴趣替换
- `POST /api/v1/avatars/presign` — 头像 presign
- `POST /api/v1/avatars/confirm` — 头像确认

### 启动验证（2026-06-30 curl 实测通过）
- `GET /api/v1/ban/9999999999999` → `{"banned":false}`
- `GET /api/v1/profiles/9999999999999` → `code: 10001`（用户不存在，符合预期）

### 基础设施
- Nacos 配置 `user-service-dev.yaml`（zzx-dating-dev 命名空间）
- Redis 缓存（cache aside 模式）
- Flyway 迁移（已禁用，表已建好）
- 虚拟线程开启
- gRPC 端口 9090，Actuator（health/info/prometheus）

## 待完成

- [ ] **gRPC handler**（UserIdentityGrpcImpl / UserProfileGrpcImpl 等，proto 需先发布）
- [ ] **集成测试**（单元/集成测试，目前 0 测试文件）
- [ ] **dating-common 模块 ObjectStorage 接入**（头像 presign S3 URL 实现）
- [ ] **运营封禁 Redis Set**（UserBanServiceImpl TODO）
- [ ] **批量查兴趣优化**（UserProfileServiceImpl TODO）

---

## post-service 开发进度

### 状态：CRUD API 全部完成并验证 ✅（2026-07-01）

- Tomcat 8082 ✓
- gRPC 9092 ✓
- Nacos 注册 ✓
- 数据库连接 ✓
- Redis 缓存 ✓
- HTTP 链路全通 ✓（curl 实测：发帖/查帖/点赞/评论/列表）

### 数据库（Flyway V1）
- `posts` — 帖子主表（content/type/visibility/topic/score 等）
- `post_images` — 帖子图片表
- `post_stats` — 帖子统计表（写 coalescing 目标表）
- `post_likes` — 点赞表（UNIQUE post_id + user_id）
- `post_comments` — 评论表（parent_id 支持嵌套）
- `shedlock` — 多实例定时任务互斥锁

### 代码结构
| 层 | 文件 | 说明 |
|---|---|---|
| Entity | 5 个 | Post / PostImage / PostStats / PostLike / PostComment |
| Mapper | 5 个 | BaseMapper 继承 |
| Manager | 4 个 | 缓存读写 + 失效（Redis TTL） |
| Service | 3 个 | PostService / PostLikeService / PostCommentService |
| Controller | 2 个 | PostController + PostInteractionController |
| DTO | 4 个 | CreatePostRequest / PostVO / CreateCommentRequest / PostCommentVO |
| Exception | 2 个 | BizException / ErrorCodes |
| Config | - | Jackson、Redisson 配置类 |

### API 清单（REST 接口全部完成）
- `POST /api/v1/posts?userId=` — 发帖
- `GET /api/v1/posts/{postId}?currentUserId=` — 查帖子详情
- `DELETE /api/v1/posts/{postId}?userId=` — 删帖
- `GET /api/v1/posts/user/{userId}?currentUserId=&offset=&limit=` — 用户帖子列表
- `POST /api/v1/posts/{postId}/like?userId=` — 点赞
- `DELETE /api/v1/posts/{postId}/like?userId=` — 取消点赞
- `GET /api/v1/posts/{postId}/like/status?userId=` — 点赞状态
- `POST /api/v1/posts/{postId}/comments?userId=` — 发表评论
- `GET /api/v1/posts/{postId}/comments` — 评论列表
- `DELETE /api/v1/posts/comments/{commentId}?userId=` — 删除评论

### curl 实测通过
- 发帖 ✅ → 返回带 id 的 PostVO
- 查帖 ✅ → 含图片列表
- 点赞 ✅ → 状态 true
- 评论 ✅ → 返回 CommentVO
- 用户帖子列表 ✅ → 含点赞数/评论数

### 基础设施
- Nacos 配置 `post-service-dev.yaml`（zzx-dating-dev 命名空间）
- 远端 PG 5433 / Redis 6380 / RocketMQ 9876
- 密码用 Windows 环境变量（`DB_PASSWORD` / `REDIS_PASSWORD`）
- 启动脚本 `run-post-service.sh`（已 .gitignore，不提交密码）
- bootstrap.yml → Nacos 服务注册发现
- application-dev.yml → `${ENV}` 占位，无明文密码

### 遇到的坑
- Nacos 配置因 `spring-cloud-starter-bootstrap` 优先级低于 `application-dev.yml`，Nacos 中的密码无法覆盖本地空值 → 改走环境变量
- Flyway 校验和不匹配（之前脏数据）→ `mvn flyway:repair` 修复
- Maven 用 Java 8 运行 → 需设 `JAVA_HOME=C:\develop\Java\jdk-21`
- Nacos namespace 显示名 vs UUID 问题

## 待完成（post-service）

### 短期
- [ ] 写扩散（RocketMQ fanout）— 发帖后推送好友 feed
- [ ] Feed 流接口 — 热池 + 好友时间线 + 冷启动 3 路合并
- [ ] 写 coalescing — Redis INCR 计数 + 定时刷到 PG
- [ ] 帖子分数计算 — 定时任务更新 Hacker News 分数
- [ ] gRPC handler — 给 user-service / im-service 调用

### 长期
- [ ] Apifox 测试脚本 / 接口文档
- [ ] 单元测试 + 集成测试
- [ ] 对接 mobile-gateway
- [ ] 解决 Nacos 配置优先级问题
