# 项目进度总览

最后更新：2026-07-09（OpenIM 集成 + post-service 修复）

---

## 总体进度：8 个服务 + ai-chat

```
user-service     ██████████████░░  75%  ✅ 核心完整，缺增强功能
post-service     ████████████░░░░  70%  ✅ 核心完整，缺增强功能
mobile-gateway   ██████████████░░  75%  ✅ REST→gRPC BFF 已验证，三服务全链路通
match-service    ████████████████░  85%  ✅ 核心+增强基本完整，缺少量集成
payment-service  ██████████████░░  75%  ✅ 金币+订阅完整，缺PayPal/提现
im-service       ████████████░░░░  65%  ✅ OpenIM 核心集成，缺回调增强
example-service  ██░░░░░░░░░░░░░░  10%  🏗️ 骨架
ai-chat          ██░░░░░░░░░░░░░░  10%  📦 骨架占位
```

**目前已投入：约 10 天（2026-06-29 ~ 2026-07-08）**
**预计剩余：约 2-3 个月（边学边做）**

---

## 各服务详情

### ✅ user-service（75%）

**状态**：HTTP + gRPC 全链路就绪，已验证通过

- ✅ 身份系统：手机号/三方/设备注册登录（`UserIdentityService` + gRPC）
- ✅ 资料 CRUD：`UserProfileService` + gRPC + HTTP Controller
- ✅ 头像上传：S3 presigned（`S3ObjectStorage`）
- ✅ 兴趣标签：`UserInterestService` + gRPC + Controller
- ✅ 封禁系统：`UserBanService` + gRPC + Controller
- ✅ Redis 缓存（cache aside）
- ✅ **用户发现服务（DH/BH 召回）**：`UserDiscoveryService` + `UserDiscoveryServiceImpl`（listDhCandidates / nearbyUsers）
- ✅ gRPC 4 个：UserProfileGrpc / UserIdentityGrpc / UserBanGrpc / UserInterestGrpc
- ✅ HTTP 5 个 Controller（identity / profile / avatar / ban / interest）
- ✅ 集成测试框架 + 2 个测试类
- ✅ Flyway 迁移（V1 init + V2 add match fields）

**待完成**：
- ❌ 管理后台封禁增强（批量/搜索/审计）
- ❌ 寒暄消息模板管理（`GreetingTemplate`）
- ❌ 头像审核/CDN 刷新
- ❌ 更完善的集成测试

---

### ✅ post-service（70%）

**状态**：CRUD + 写扩散 + Feed 排序 + 写合并全就绪

- ✅ 帖子 CRUD：`PostService` + `PostController`
- ✅ 评论系统：`PostCommentService`（Redis ZSet 200 条缓存）
- ✅ 点赞：`PostLikeService`
- ✅ 关注/取关：`UserFollowManager`
- ✅ Feed 时间线 + HN 热度排序：`FeedService` + `FeedScoreJob`
- ✅ **写扩散 RocketMQ**：`PostFanoutProducer` → `PostFanoutConsumer`
- ✅ **写合并 Coalescing**：Redis incr → 定时刷 DB（`PostStatsFlushJob`）
- ✅ gRPC：`PostGrpcService`（9 个 RPC）
- ✅ Flyway 迁移（3 个版本：V1 post tables + V2 follow + V20260707_02 user_type）
- ✅ ShedLock 分布式定时任务
- ✅ Nacos 注册 / Redis 缓存

**待完成**：
- ❌ 帖子举报/审核体系
- ❌ Feed 池分桶（按 gender/age）
- ❌ 三步合并 Feed（缓存池 + 写扩散收件箱 + 关注池冷启动）
- ❌ Bloom 过滤器去重
- ❌ H5 端 feed 适配

**新增（07-09）**：
- ✅ UserClient gRPC 客户端（getFriendUserIds / batchGetGenders）
- ✅ FeedScoreJob HN 公式修复：`(10 + 1.0*likes + 3.0*comments) / (hours+2)^1.5`
- ✅ PostFanoutConsumer gRPC 优先 + 本地降级

---

### ✅ mobile-gateway（75%）

**状态**：REST→gRPC BFF 完整实现，三服务全链路已验证通过（07-07）

- ✅ JWT HMAC-SHA256 签发/验签/过滤器（`JwtUtil` + `JwtAuthFilter`）
- ✅ 3 种登录：手机验证码 / 三方授权 / 设备快速登录（`AuthController`）
- ✅ gRPC 客户端：
  - `UserServiceClient`（4 stub：identity/profile/ban/interest）
  - `PostServiceClient`（1 stub, 9 RPC）
  - `MatchServiceClient`（1 stub, 7 RPC）
- ✅ REST → gRPC 协议转换（`ProtoJson`）
- ✅ UserController / PostController / MatchController 全部实现（共 22+ 个端点）
- ✅ Nacos 服务发现（`discovery:/` 单斜杠）
- ✅ 统一响应体 `R<T>` + 全局异常处理
- ✅ 端口 8081（避免跟 user-service 8080 冲突）

**已验证链路**：
| 链路 | 状态 |
|---|---|
| user-auth → user-service (gRPC) | ✅ |
| user-profile → user-service (gRPC) | ✅ |
| post-list → post-service (gRPC) | ✅ |
| match-list → match-service (gRPC) | ✅ |

**待完成**：
- ❌ JWT secret 从 application.yml 移到 Nacos / 环境变量
- ❌ Docker Compose 一键启动
- ❌ 集成测试（mock gRPC stub）
- ❌ rate limiting / 限流
- ❌ 请求日志 MDC traceId

---

### ✅ match-service（85%）

**状态**：核心划卡 + 增强功能基本完整，仅缺少量集成和联调

- ✅ DB Schema + Entity + Mapper（6 张表：LikeRecord / Match / MatchOutbox / SwipeHistory / VisitRecord / DhInteractionTask）
- ✅ Manager 层（5 个）：LikeRecord / Match / MatchOutbox / SwipeHistory / VisitRecord
- ✅ **核心划卡匹配**：`MatchServiceImpl.swipe()` — 方向校验/幂等/并发锁/配额扣减/like记录/双向匹配/DH延迟匹配
- ✅ **Super Hi**：付费硬匹配，含配额扣减+金币购买兜底
- ✅ **回复喜欢**：`replyLike()` — 双向匹配触发
- ✅ **访问记录**：recordVisit / listVisitors
- ✅ **QuotaService** — Redis HASH原子扣减（右划/卡片/Super Hi），按订阅档位定价（FREE/WEEKLY/MONTHLY/YEARLY），含配额回滚机制
- ✅ **OfflinePlanGenerator** — 每20分钟扫离线用户，生成DH互动计划
- ✅ **OnlinePlanGenerator** — 每1分钟扫在线用户，为BH生成DH like/visit任务，含冷却/去重/倾向分配
- ✅ **DhInteractionSchedulerJob** — 每30秒轮询到期DH任务，执行LIKE/VISIT动作 + 双向match检测
- ✅ **MatchOutboxRetryJob** — 每30秒轮询outbox，指数退避重试（最长1h），超5次死信，含ENSURE_CONVERSATION处理
- ✅ **D1QueueScheduler** — D1队列调度
- ✅ **CandidateRecaller** — D0/D1召回逻辑（DH池+BH池）
- ✅ **ColdStartService** — 冷启动buildAndPush（323行）
- ✅ **Recommend 体系**：PreferenceBuilder / PreferenceProfile / Ranker / D1Generator 全部实现
- ✅ **gRPC server**（7 个 RPC）+ HTTP Controller（7 个端点，含 /feed）
- ✅ **对接客户端**：
  - `UserServiceClient` — 查询用户档案 + DH候选
  - `PaymentClient` — 查询订阅档位 + 扣金币（含fallback）
  - `ImServiceClient` — 在线用户列表 + 创建会话 + 发系统消息
- ✅ **FeedServiceImpl** — LPOP消费模型，配额检查/已划过过滤/user-service batchGetProfile拼装CardVO
- ✅ Proto 定义（`match.proto`）+ dating-proto-zzx:1.5
- ✅ Redis 划卡去重（`match:swiped:<userId>` SET + isMember检查）
- ✅ ShedLock 分布式定时任务

**待完成（按优先级）**：
1. ❌ 支付对接联调（Super Hi 金币扣减需 payment-service 实际运行）
2. ❌ im-service 集成联调（ensureConversation / sendSystemMessage）
3. ❌ 集成测试覆盖

---

### ✅ payment-service（75%）

**状态**：金币模块+订阅模块完整实现，gRPC+HTTP双协议就绪

- ✅ Flyway 建表：`coin_account` / `coin_ledger` / `user_subscription`
- ✅ **CoinService** — 双账户（免费+付费），先扣免费再扣付费，幂等扣减（idempotency_key+唯一索引兜底）
- ✅ **SubscriptionService** — FREE/WEEKLY/MONTHLY/YEARLY 四档，只升不降+时长顺延
- ✅ PaymentController — HTTP：getCoins / consumeCoins / getSubscription / activateSubscription
- ✅ PaymentGrpcService — gRPC：getCoins / consumeCoins / getSubscription / activateSubscription
- ✅ 异常体系：BizException + ErrorCodes
- ✅ Nacos 注册

**待完成**：
- ❌ PayPal 支付全链路（下单/Webhook验签/发奖）
- ❌ 提现模块
- ❌ 商品定义对接

---

### 🏗️ im-service（65%）

**状态**：OpenIM 核心集成完成，回调/出站/扣费等为 stub

- ✅ **在线状态**：Redis Hash `im:presence:<userId>`（status + heartbeat，TTL 5分钟自动过期）
- ✅ **在线 ZSet**：`im:presence:online`（score = 上线时间戳，按时间范围查询）
- ✅ ImPresenceService（159行）— getPresence / batchGetPresence / heartbeat / listOnlineUserIds / listRecentOfflineUsers
- ✅ ImPresenceGrpcService — 8 个 RPC
- ✅ ImPresenceController — HTTP：heartbeat / getPresence
- ✅ **OpenIM 配置**：`OpenIMProperties` — apiUrl / wsUrl / adminSecret 配置类
- ✅ **OpenIM REST 客户端**：`OpenImApiClient` — 管理员 Token 缓存、用户注册、用户 Token 签发、建单聊会话、发送消息
- ✅ **OpenIM 业务服务**：`OpenImService` — 懒注册（获取 Token 失败自动注册后重试）、双向会话创建、系统消息发送
- ✅ **ensureConversation**：对接 OpenIM，匹配成功后自动创建双方会话
- ✅ **sendSystemMessage**：对接 OpenIM，发送系统通知
- ✅ **Token 签发接口**：`POST /api/v1/openim/token` — 懒注册 + 签发用户 Token
- ✅ **SDK 配置接口**：`GET /api/v1/openim/config` — 返回 apiAddr + wsAddr
- ✅ **Provider 抽象层**：`ImProviderAdaptor` — 支持多 IM 引擎切换（体现设计模式）
- ✅ **OpenIM 回调解析**：`OpenImAdaptor` — 解析 beforeSend/afterSend/online/offline
- ✅ **回调接收端点**：`POST /api/v1/openim/callback` — 接收 OpenIM Webhook
- ✅ **回调分发服务**：`CallbackService` — 遍历 adaptors 按事件类型分发
- ✅ dating-proto-zzx:1.5（im_presence.proto）
- ✅ Nacos 注册

**待完成（按优先级）**：
1. ❌ Before-send 回调增强：反导流检测 + 聊天扣费（已有 stub，需对接 payment-service）
2. ❌ After-send 回调增强：消息落库 + AI 自动回复路由（待 ai-chat 对接）
3. ❌ AI 回复拟真三件套（阅读延迟 / typing / 分段打字）
4. ❌ 聊天扣金币（CoinChargeDispatcher + PaymentGrpcClient）
5. ❌ 出站通知（MatchSuccessNotifier）
6. ❌ 孤儿会话清扫（PresenceSweepJob）

**新增（07-09）**：
- ✅ OpenIM REST 客户端 + 管理员 Token 缓存
- ✅ OpenImService（懒注册 / 双向会话 / 系统消息）
- ✅ ensureConversation 对接 OpenIM（匹配后自动建会话）
- ✅ Token 签发 HTTP 端点
- ✅ Provider 抽象层（ImProviderAdaptor）+ 回调处理
- ✅ OpenIM 配置类 + application-dev.yml

---

### 📦 example-service（10%）

**状态**：骨架，仅启动类 + bootstrap.yml，gRPC handler 待实现

---

### 📦 ai-chat（10%）

**状态**：Python 骨架，gRPC server 占位

- main.py（gRPC server skeleton，端口 50051）
- requirements.txt（grpcio / protobuf / pydantic）
- 待实现：LangGraph Agent / LLM 接入 / VisionAgent / 敏感内容过滤

---

## Proto 定义（7 个）

| Proto 文件 | 状态 |
|---|---|
| `user/user_identity.proto` | ✅ |
| `user/user_profile.proto` | ✅ |
| `match/match.proto` | ✅ |
| `post/post.proto` | ✅ |
| `im/im_presence.proto` | ✅ |
| `payment/payment.proto` | ✅ |
| `common/result.proto` | ✅ |

---

## Git 提交记录（2026-06-29 ~ 2026-07-09）

| 日期 | Commit | 内容 |
|------|--------|------|
| 07-09 | `（待提交）` | OpenIM 核心集成 + post-service gRPC 客户端 + HN 公式修复 + PROGRESS 更新 |
| 07-07 | `0154416` | fix(match-service): boolean @TableLogic for PG |
| 07-06 | `3f67819` | feat(mobile-gateway): REST→gRPC BFF + JWT |
| 07-05 | `5cd2b22` | docs: progress tracking |
| 07-05 | `9a7d386` | feat(match-service): swipe/match core logic |
| 07-04 | `c711ace` | feat(phase2): gRPC handlers, fanout, coalescing, feed |
| 07-01 | `f3d1ac5` | feat(post-service): CRUD APIs, Flyway, Redis |
| 07-01 | `1f96300` | feat(user-service): HTTP API, ObjectStorage, ban |
| 06-29 | `ab6e3c8` | feat(user-service): deps & package structure |
| 06-29 | `705b95e` | feat: init project skeleton |

---

## 待办总览（按优先级）

### 短期（1 周内）
1. Maven 编译验证 + 修复编译错误
2. Docker Compose 一键启动 + 每个服务 Dockerfile
3. Postman 演示脚本（注册→登录→发帖→刷Feed→划卡→匹配→拿IM Token）
4. 全链路跑通验证

### 中期（1-2 周）
5. im-service 回调增强（反导流检测 + 聊天扣费）
6. im-service AI 自动回复 + 拟真节奏
7. mobile-gateway 限流 + MDC traceId

### 长期（1-2 月）
8. ai-chat LangGraph + LLM + Vision 完整实现
9. 支付 PayPal 全链路
10. 前端页面（H5）

---

## 端口规划

| 服务 | gRPC 端口 | HTTP 端口 |
|------|-----------|-----------|
| user-service | 9090 | 8080 |
| post-service | 9092 | 8082 |
| example-service | 9091 | - |
| im-service | 9093 | - |
| match-service | 9094 | - |
| payment-service | 9095 | - |
| ai-chat | 50051 | - |
| mobile-gateway | - | 8081（对外） |
