# Java Spring Boot 后端

F01 已实现微信身份判断、受邀注册、会话恢复与退出、同意记录，以及管理端邀请码和名额接口。数据库访问使用 JDBC，注册事务会锁定名额和邀请码，并在同一事务内创建账号、默认计划、同意记录与会话。

## 本地启动

本地默认使用文件型 H2 和微信模拟模式，不需要 AppSecret：

```bash
mvn spring-boot:run
```

默认地址为 `http://localhost:8025/api`，H2 控制台为 `http://localhost:8025/h2-console`。管理接口开发令牌为 `dev-admin-token`，只能用于本地。

## 连接现有 MySQL 5.7

生产环境不自动建表。空库直接使用 `../../02_database/SQL/知行日课_数据库初始化_V3.14_MySQL5.7.25.sql`；已有数据库按实际版本依次执行 `sql` 目录中尚未执行的幂等增量脚本。V3.12 新增 AI 模型运行配置；V3.13 新增两档各 101 篇英语短文、阿里云 TTS 配置、音频缓存和脱敏调用日志；V3.14 新增短文点词补充词表，覆盖 V3.13 预置短文的 104 个词形。已有数据库必须先备份并在副本演练。

```bash
SPRING_PROFILES_ACTIVE=prod \
DB_URL='jdbc:mysql://127.0.0.1:3306/zhixing?useUnicode=true&characterEncoding=utf8&useSSL=true&serverTimezone=UTC' \
DB_USERNAME='应用账号' \
DB_PASSWORD='数据库密码' \
REDIS_HOST='127.0.0.1' \
REDIS_PASSWORD='Redis密码' \
WECHAT_APP_ID='小程序AppID' \
WECHAT_APP_SECRET='小程序AppSecret' \
ADMIN_TOKEN='高强度管理端令牌' \
AI_CREDENTIAL_ENCRYPTION_KEY='至少 16 位的独立加密密钥' \
OSS_ENDPOINT='OSS Endpoint' \
OSS_BUCKET='私有 Bucket 名称' \
OSS_ACCESS_KEY_ID='OSS AccessKey ID' \
OSS_ACCESS_KEY_SECRET='OSS AccessKey Secret' \
MEDIA_BASE_URL='https://api.example.com/api/media' \
mvn spring-boot:run
```

真实密钥不得写入 YAML 或 Git。生产配置使用 Redis 保存十分钟的待注册状态；数据库仅保存邀请码和会话令牌的 SHA-256 摘要。

DeepSeek API Key 可以由服务端环境变量 `AI_DEEPSEEK_API_KEY` 注入，也可在 Web 管理端录入并使用 `AI_CREDENTIAL_ENCRYPTION_KEY` 加密入库；接口不回传密钥明文。

## 英语短文与阿里云 TTS

- `POST /api/learning/contents/{id}/speech`：按需合成短文或单词发音；首次调用生成并存入 OSS，后续复用缓存。短文点词先查正式词库，再查 V3.14 补充词表。
- `/api/admin/ai/tts`：仅 Web 管理员可查看状态、录入 AppKey/AccessKey、选择英语音色和查看脱敏调用日志；接口永不回传密钥。
- 阿里云 AppKey 不能单独完成鉴权，还需具备智能语音交互权限的 AccessKey ID/Secret。凭据仅保存在服务端，并使用 `AI_CREDENTIAL_ENCRYPTION_KEY` 加密。
- V3.13 使用阿里云短文本 TTS，因此单篇正文限制为 300 字符。播放速度由小程序播放器在 0.75x、1x、1.25x、1.5x 间调整，不重复计费合成。
- 音频写入现有私有 OSS；除 TTS 凭据外，仍需正确配置本节启动命令中的 OSS 参数。

## 问一问与 AI 管理

- `GET /api/ai/models`：返回可用模型及密钥、价格、预算的就绪状态。
- `POST /api/ai/ask`：需要 AI 同意、`Idempotency-Key`、日额度、并发名额与月预算。
- `GET /api/ai/ask/history`：仅返回本人近期提问。正文最多保留 24 小时，计量与费用记录继续保留。
- `/api/admin/ai/models`：模型、规格、地址、凭据状态和价格版本管理。
- `GET /api/admin/ai/pricing/status`：返回 DeepSeek 官网价格同步状态。服务端在北京时间每天 22:00 核验官网价格；MySQL 后端启动时也会补核验，避免笔记本休眠错过定时点。
- `/api/admin/ai/budget` 与 `/api/admin/ai/usage`：月预算、脱敏调用日志和结算费用。
- 短文中的“解释这段内容”复用同一接口、授权、额度、预算和日志机制，仅发送用户点击的当前段落。

DeepSeek 的缓存命中、缓存未命中和输出价格不再由管理员录入。每次请求会按北京时间实际发起时刻冻结高峰/空闲价格版本，并使用供应商返回的缓存命中 token 分项结算；官网抓取或页面解析失败时继续使用最后一个已验证版本，不以空值覆盖。可用 `AI_PRICING_SYNC_ENABLED=false` 停止同步，或用 `AI_PRICING_SYNC_CRON` 覆盖 cron 表达式。

## Web 词书管理查看

- `GET /api/admin/vocabulary-books`：返回启用词书及实时成员数、可用数和标称数差异，需要 `X-Admin-Token`。
- `GET /api/admin/vocabulary-books/{bookId}/words`：按词书分页查看单词，默认 20 条/页，`pageSize` 可设为 1—100，支持英文或中文释义搜索。
- `GET /api/admin/vocabulary-books/words/{contentId}`：返回单词的音标、词义、例句、来源、许可和现有发音资源。
- `POST /api/admin/vocabulary-books/words/{contentId}/speech`：仅管理员可在缺少音频时触发服务端 TTS；凭据不会下发浏览器。

## Web 账号列表

- `GET /api/admin/users`：按昵称、短 ID 或手机号分页搜索账号，默认 20 条/页，支持 `all`、`active`、`disabled` 状态筛选。
- `GET /api/admin/users/{userId}/learning`：只读返回该用户当前已保存的计划、今日任务汇总、所选词书进度、生词本汇总和生词本分页列表；`notebookPageSize` 为 1—50。
- 响应只包含管理所需的短 ID、序号、昵称、脱敏手机号、状态、AI 同意状态、最近登录与创建时间；不返回微信 OpenID、完整手机号或用户私有内容。
- 学习详情同样需要 `X-Admin-Token`，且不返回 AI 提问/回答正文、日记正文；未保存计划或未选择词书时返回 `null`，不会用默认值伪装为用户真实设置。

## Web 技术知识查看

- `GET /api/learning/contents/{contentId}`：用户详情返回当前 `published_version_id` 指向的正文和版本化来源字段，并同时返回 `originPublishedAt`（原文发布时间）与 `publishedAt`（站内发布时间）。
- `GET /api/admin/content/technical`：分页查看已发布的技术知识，默认 20 条/页，支持按主题过滤以及标题、摘要、正文关键词搜索；列表返回原文发布时间，缺失时由前端回退站内发布时间。
- `GET /api/admin/content/technical/{contentId}`：查看已发布版本的正文、难度、预计时长、主题、审核状态、来源、原文/站内发布时间和许可快照。
- `GET /api/admin/content/articles`：分页查看已发布英语短文，支持按 `intro`/`advanced` 难度筛选以及标题、摘要、正文关键词搜索；时间字段语义与技术知识一致。
- `GET /api/admin/content/articles/{contentId}`：查看英语短文正文、难度、预计时长、审核状态、来源、原文/站内发布时间和许可快照。
- 两个接口都需要 `X-Admin-Token`，当前只读，不提供修改或删除已发布内容的能力。

## F01 和 F02 接口

- `POST /api/auth/wechat`
- `POST /api/auth/sms-code`
- `POST /api/auth/register`
- `GET /api/user`
- `PUT /api/me`：更新本人资料；必须携带读取资料时得到的 `rowVersion`。
- `POST /api/media/upload`：上传头像，服务端会解码并限制为 JPEG、PNG 或 WebP，单张最大 5 MB。
- `GET /api/users/{id}/profile`：返回受当前用户与资料权限约束的只读资料，不返回手机号。
- `POST /api/auth/logout`
- `POST /api/consents`
- `GET/POST /api/admin/invites`
- `POST /api/admin/invites/{id}/revoke`
- `GET/PUT /api/admin/admission`

## 本地短信验证码

`dev` 环境默认启用控制台短信适配器。小程序调用 `POST /api/auth/sms-code` 后，验证码会以
`DEV SMS verification code` 开头写入 IDEA 控制台以及 `logs/let-mind-wander.log`，有效期 5 分钟，
60 秒内不可重复发送。接口响应不会包含验证码。

生产环境不会启用控制台短信；上线前需要接入真实短信供应商，并保持 `SMS_MOCK_ENABLED=false`。

## F02 媒体配置

`MEDIA_BASE_URL` 是媒体代理的公网 HTTPS 地址，例如 `https://api.example.com/api/media`。头像数据只保留该受控引用；代理会签发短时 OSS 地址。不要使用本机地址或把 OSS 密钥放进小程序。

## V3.1 英语记忆训练

`/api/word-memory` 实现已审核提示读取、会话创建/恢复、提示记录、幂等作答、最多两次回补、部分完成与结果统计。题目的标准答案不随 GET 响应下发；提交由服务端判定。训练结算不会自动写入“已理解”或生词本，只在用户创建会话时显式选择后才加入复习。

核心写接口要求 `Idempotency-Key`，会话变更要求 `expectedVersion`；409 `MEMORY_SESSION_VERSION_CONFLICT` 时客户端应重新读取会话而不是覆盖。

## 英语词书选择

`GET /api/vocabulary-books` 返回可用词书，`GET /api/vocabulary-books/current` 返回当前词书，`PUT /api/vocabulary-books/current` 选择或更换词书。今日新词不再从全库随机选取：未选词书时服务端返回“未选择词书”缺口，选定后只从该词书的成员关系中生成任务。更换词书仅替换未开始的当日新词，已开始和已完成记录不被删除。

`GET /api/vocabulary-books/current/progress` 返回当前词书的总词数、已学数、剩余数、完成率、每日新词数、预计剩余天数和分页单词列表；`status` 支持 `all`/`learned`/`remaining`。预计天数为 `ceil(剩余词数 / 当前计划每日新词数)`；每日新词为 0 时不返回预计天数。“已学”只以服务端 `learning_record.learning_status` 为 `understood` 或 `mastered` 的事实记录计算，不由客户端累加，也不把记忆测验自动算作已学。

## 计划设置数据字典

`GET /api/plans/settings` 只返回计划页面需要的非敏感白名单配置。每日分钟数可以直接输入，范围、加减步长、快捷值、星期、难度、数量上限、默认值和预算提示耗时均来自 `app_parameter` 的 `plan.*` 数据字典；保存时后端使用同一份字典再次校验，客户端不能绕过范围约束。
