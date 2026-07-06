# 项目进度

最后更新：2026-07-06

---

## 总览：7 个服务 + ai-chat

```
user-service     ████████████░░  70%  核心完整，缺增强
post-service     ██████████░░░░  60%  核心完整，缺增强
match-service    ██████░░░░░░░░  35%  核心逻辑完成，缺调度/缓存/支付
im-service       █░░░░░░░░░░░░░   5%  刚搭完骨架
mobile-gateway   ██████████░░░░   60%  REST→gRPC BFF(13 files)
payment-service  ░░░░░░░░░░░░░░   0%  刚搭完骨架
ai-chat          █░░░░░░░░░░░░░   5%  main.py 占位
example-service  示范服务，不计入
```

---

## 各服务详情（对照设计文档）

### ✅ user-service（~70%）— 设计文档 `user-service-design.md`

**已完成（对照文档）**：
- ✅ 身份系统：手机号/三方/设备身份注册登录（`UserIdentityService` + `UserIdentityGrpc`）— 完整
- ✅ 资料 CRUD：`UserProfileService` + `UserProfileGrpc` + `UserProfileController` — 完整
- ✅ 头像上传：S3 presigned（`S3ObjectStorage`）— 完整
- ✅ 兴趣标签：`UserInterestService` + `UserInterestGrpc` + `UserInterestController` — 完整
- ✅ 封禁系统：`UserBanService` + `UserBanGrpc` + `UserBanController` — 完整
- ✅ Redis 缓存（cache aside）— `UserInfoManager` 实现
- ✅ 用户发现服务（DH/BH 召回）— `UserDiscoveryService` + `UserDiscoveryServiceImpl`
- ✅ gRPC service（4 个：Identity / Profile / Interest / Ban）
- ✅ HTTP Controller（5 个：身份/资料/头像/兴趣/封禁）

**待完成（对照文档）**：
- ❌ 管理后台封禁增强（批量/搜索/审计）
- ❌ 寒暄消息模板管理（`GreetingTemplate`）
- ❌ 头像审核/CDN 刷新（`AvatarDetection`）
- ❌ 更完善的集成测试

**与文档差异**：核心功能完整，管理增强和周边能力未实现。

---

### ✅ post-service（~60%）— 设计文档 `post-service-design.md`

**已完成（对照文档）**：
- ✅ 动态 CRUD：`PostService` + `PostController` — 完整
- ✅ 评论系统：`PostCommentService`（Redis ZSet 200 条最新缓存）— 完整
- ✅ 点赞：`PostLikeService` — 完整（文档说明不缓存点赞数据）
- ✅ 关注/取关：`UserFollowManager` — 完整
- ✅ Feed 时间线 + HN 热度排序：`FeedService` + `FeedScoreJob`
- ✅ 写扩散（RocketMQ）：`PostFanoutProducer` → `PostFanoutConsumer` — 完整
- ✅ 写合并（Coalescing）：Redis incr → 定时刷 DB（`PostStatsFlushJob`）— 完整
- ✅ gRPC service + HTTP Controller

**待完成（对照文档）**：
- ❌ 帖子举报/审核体系（文档 §7）
- ❌ Feed 池分桶（按 gender/age 分桶，见文档 §5.2）
- ❌ 三步合并 Feed（缓存池 + 写扩散收件箱 + 关注池冷启动）
- ❌ Bloom 过滤器去重
- ❌ 缓存命中率优化/冷启动策略
- ❌ H5 端 feed 适配

**与文档差异**：写扩散和 HN 评分已完成，但分桶冷启动和举报审核待补。

---

### 🏗️ match-service（~35%）— 设计文档 `match-service-prd-tech.md`（1132 行）

**已完成（对照文档）**：
- ✅ DB Schema + Entity + Mapper（6 张表）
- ✅ Manager 层（5 个 Manager）
- ✅ 核心业务：划卡/匹配/回应 Like/访问记录（`MatchServiceImpl`）
- ✅ gRPC server（7 个 RPC）
- ✅ HTTP Controller（7 个接口）
- ✅ Proto 定义（`match.proto`）
- ✅ 用户发现服务对接
- ✅ DhInteractionTask Entity + Mapper（表结构就绪）

**待完成（对照文档，按顺序）**：
1. ❌ **FeedService**（GetTodayFeed D0/D1 池 + 冷启动，见文档 §2）
2. ❌ **QuotaService**（每日右滑/超级喜欢配额，按订阅档位，见文档 §3）
3. ❌ **SuperHi 单独 RPC**（文档中 Swipe 和 SuperHi 是分开的 API）
4. ❌ **Redis 划卡去重/已划过缓存**（文档 §4.2）
5. ❌ **DH 互动计划调度器**（OnlinePlanGenerator / OfflinePlanGenerator / LikeVisitorTaskExecutor，见文档 §6）
6. ❌ **MatchOutbox 事件重试**（MQ 生产端可靠性，文档 §7.5）
7. ❌ **支付对接**（超级喜欢消耗金币，调 payment-service ConsumeCoins）

**与文档差异**：核心划卡匹配已实现，DH 调度、配额、Feed、支付四块大头待补。文档设计很详细（1132 行），实现工作量最大。

---

### 📦 im-service（~5%）— 设计文档 `im-service-design.md`（380 行）

**现状**：只有启动类和 bootstrap.yml（1 个 Java 文件）

**需要实现（完整）**：
- OpenIM 回调收口（`ImProviderAdaptor` + `OpenImAdaptor`，文档 §3-4）
- Before-send：反导流检测 + 聊天扣费（文档 §4.2）
- After-send：消息落库 + AI 自动回复路由（文档 §4.3）
- AI 回复拟真三件套：阅读延迟 + typing 续命 + 分段打字节奏（文档 §5）
- 聊天扣金币（`CoinChargeDispatcher` + `PaymentGrpcClient`，文档 §6）
- 在线状态（Redis ZSet `im:presence:online` + PG `user_online_session`，文档 §7）
- 出站通知（`MatchSuccessNotifier` 匹配成功 fan-out，文档 §8）
- Token/注册/通话（OpenIM REST Client + LiveKit Token，文档 §9）
- 孤儿会话清扫（@Scheduled，文档 §7.2）
- 对外 RPC：`ListOnlineUserIds` / `ListRecentOfflineUsers`（文档 §7.3）

**与文档差异**：文档 380 行，设计完整但代码几乎为零。

---

### ✅ mobile-gateway（~60%）— 设计文档 `mobile-gateway-design.md`（310 行）

**现状**：REST→gRPC BFF 已完成，共 13 个 Java 文件。

**已完成**：
- ✅ JWT HMAC-SHA256 签发/验签/过滤器（`JwtUtil` + `JwtAuthFilter`）
- ✅ 三种登录：手机验证码 / 三方授权 / 设备快速登录（`AuthController`）
- ✅ gRPC 客户端封装（3 个 client 类：user / post / match）
- ✅ REST → gRPC 协议转换（`ProtoJson` 将 proto 序列化为 JSON Map）
- ✅ 用户资料读写、兴趣标签（`UserController`）
- ✅ 动态 CRUD / 点赞 / 评论 / Feed 时间线（`PostController`）
- ✅ 划卡 / 喜欢 / 匹配 / 访问记录（`MatchController`）
- ✅ Nacos 服务发现（`discovery://` + grpc-client-spring-boot-starter）
- ✅ 统一响应体 `R<T>` + 全局异常处理

**与设计文档差异**：
- JWT 从 RS256 简化为 HMAC-SHA256（面试时可展开对比非对称/对称的 tradeoff）
- 未使用 Resilience4j 限流（网关层限流单独拎出来再讲更清晰）
- REST DTO 层直接用 `Map<String, Object>`，未建大量 VO 类

---

### 📦 payment-service（~0%）— 设计文档 `payment-service-design.md`（350 行）

**现状**：只有启动类和 bootstrap.yml（1 个 Java 文件）

**需要实现（完整）**：
- PayPal 支付：下单/收款确认/Webhook 验签/发奖（文档 §4-5）
- 金币模块：双账户（免费+付费）、查/加/扣幂等（文档 §6）
- 订阅模块：FREE/WEEKLY/MONTHLY/YEARLY + 到期判定（文档 §7）
- 订单状态机：INIT → PAID → GRANTED（文档 §3.2）
- 商品定义（100~13000 金币 + 3 档订阅，文档 §4.3）
- gRPC + REST 双协议（`GrpcAdapter` 复用逻辑，文档 §2）
- ConsumeCoins 幂等（idempotency_key + 部分唯一索引，文档 §6）
- 提现模块（表就位，逻辑未实现，文档 §8）

**与文档差异**：文档设计完整但代码只有骨架。PayPal 全链路在文档中标注"已实现"，但当前代码未反映。

---

### 📦 ai-chat（~5%）

**现状**：`main.py` + `requirements.txt` + `src/__init__.py`（3 个文件）

**需要实现**：
- LangGraph Agent（对话管理 + 会话记忆）
- OpenAI / Claude API 接入
- 图片理解（VisionAgent）
- 敏感内容过滤
- gRPC server（ChatAgent + VisionAgent RPC）

---

## 各服务 Java 文件数（粗略反映实现量）

| 服务 | Java/Python 文件 | 实现度 |
|---|---|---|
| user-service | 61 | ~70% |
| post-service | 45 | ~60% |
| match-service | 29 | ~35% |
| im-service | 1 | ~5% |
| mobile-gateway | 13 | ~60% |
| payment-service | 1 | ~0% |
| ai-chat | 3 (Python) | ~5% |

---

## 剩余工作总览 & 时间估算（保守）

| 阶段 | 内容 | 估算 |
|------|------|------|
| ① | match-service 收尾：Feed/配额/DH 调度/Redis 去重/支付对接 | 2-3 周 |
| ② | im-service：OpenIM 回调/AI 回复/扣费/在线状态/出站通知 | 2-3 周 |
| ③ | mobile-gateway：JWT 鉴权/登录/REST→gRPC BFF ✅ 已完成 | 已用 ~2 天（已提前） |
| ④ | payment-service：PayPal 全链路/金币/订阅（代码实现） | 1 周 |
| ⑤ | ai-chat：LangGraph + 大模型接入 + 图片理解 | 1-2 周 |
| ⑥ | 全服务联调、测试、修 bug | 2-3 周 |

**总计大约还需要 2-3 个月**（学习项目边学边做）。

---

## 已提交的记录

| 日期 | Commit | 内容 |
|------|--------|------|
| 07-05 | `9a7d386` | match-service 核心逻辑 + proto + gRPC/HTTP API |
| 07-03 | `c711ace` | phase2: gRPC handlers, write fanout, coalescing, feed ranking |
| 07-01 | `f3d1ac5` | post-service CRUD APIs, Flyway, Redis cache |
| 06-30 | `1f96300` | user-service HTTP API, ObjectStorage, ban, test |
