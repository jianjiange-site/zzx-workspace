# 项目进度

最后更新：2026-07-05

---

## 总览：7 个服务 + ai-chat

```
user-service     ██████████░░░░  65%  已完成核心，待补充
post-service     ██████████░░░░  60%  已完成核心，待补充
match-service    ██████░░░░░░░░  35%  核心逻辑完成，缺调度/缓存/支付
im-service       █░░░░░░░░░░░░░   5%  刚搭完骨架
mobile-gateway   █░░░░░░░░░░░░░   5%  刚搭完骨架
payment-service  ░░░░░░░░░░░░░░   0%  刚搭完骨架
ai-chat          ██░░░░░░░░░░░░  10%  基础结构，待实现
example-service  示范服务，不计入
```

---

## 各服务详情

### ✅ user-service（~65%）

已完成：
- 用户注册/资料 CRUD（HTTP + gRPC）
- 头像上传（MinIO S3）
- 封禁体系 + 监管状态
- 兴趣标签
- Redis 缓存（cache aside）
- 用户发现服务（DH/BH 召回）

待完成：
- 管理后台接口（admin ban 已有基础，需增强）
- 更完善的集成测试
- 寒暄消息模板管理

### ✅ post-service（~60%）

已完成：
- 动态 CRUD（HTTP + gRPC）
- 评论/点赞/关注
- Feed 时间线 + 推荐排序
- 写扩散（RocketMQ）
- Redis 缓存 + 定时刷新

待完成：
- 帖子举报/审核
- Feed 池分桶（按 gender/age）
- 性能优化（缓存命中率、冷启动）

### 🏗️ match-service（~35%）

已完成：
- DB Schema + Entity + Mapper（6 张表）
- Manager 层（5 个 Manager）
- 核心业务：划卡/匹配/回应 Like/访问记录
- gRPC server（7 个 RPC）
- HTTP Controller（7 个接口）
- Proto 定义
- 用户发现服务对接

待完成（按顺序）：
1. Redis 划卡去重/已划过缓存
2. DH 互动计划调度器（DhInteractionTask 扫表）
3. MatchOutbox 事件重试（MQ 生产端）
4. 每日配额系统（右滑/超级喜欢次数限制）
5. 支付对接（超级喜欢消耗金币）

### 📦 im-service（~5%）

现状：只有启动类和 bootstrap.yml

需要实现：
- OpenIM gRPC 客户端（用户注册、Token 签发）
- 会话管理（创建/拉取会话列表）
- 消息中继（WebSocket → gRPC 转发）
- 与其他服务的集成

### 📦 mobile-gateway（~5%）

现状：只有启动类和 bootstrap.yml

需要实现：
- 路由配置（聚合各服务 HTTP API）
- 认证拦截器（JWT 校验）
- 限流/熔断

### 📦 payment-service（~0%）

现状：只有启动类和 bootstrap.yml

需要实现：
- 支付渠道对接（Stripe / 三方支付）
- 订单管理
- 金币/虚拟货币体系
- Webhook 处理

### 📦 ai-chat（~10%）

现状：gRPC server 骨架

需要实现：
- 对话管理
- 大模型接入（OpenAI / Claude API）
- 上下文管理
- 敏感内容过滤

---

## 时间估算（以"每天 2-3 小时"计）

| 阶段 | 内容 | 估算 |
|------|------|------|
| ① | match-service 收尾（Redis + DH 调度 + 配额） | 1-2 周 |
| ② | im-service 对接 OpenIM | 2-3 周 |
| ③ | mobile-gateway 路由 + 认证 | 1 周 |
| ④ | payment-service 支付对接 | 2-3 周 |
| ⑤ | ai-chat 大模型接入 | 1-2 周 |
| ⑥ | 联调、测试、修 bug | 2-3 周 |

**总计大概还需要 2-3 个月**（作为学习项目，边学边做）。

---

## 已提交的记录

| 日期 | Commit | 内容 |
|------|--------|------|
| 07-05 | `9a7d386` | match-service 核心逻辑 + proto + gRPC/HTTP API |
| 07-03 | `c711ace` | phase2: gRPC handlers, write fanout, coalescing, feed ranking |
| 07-01 | `f3d1ac5` | post-service CRUD APIs, Flyway, Redis cache |
| 06-30 | `1f96300` | user-service HTTP API, ObjectStorage, ban, test |
