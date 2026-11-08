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

真实密钥不得写入 YAML 或 Git。开发环境启动前也必须提供 `DB_PASSWORD`；生产环境按上面的启动示例提供。生产配置使用 Redis 保存十分钟的待注册状态；数据库仅保存邀请码和会话令牌的 SHA-256 摘要。

供应商密钥（如 `ALIYUN_NLS_APP_KEY`、`ALIYUN_AK_ID`、`ALIYUN_AK_SECRET` 和 DeepSeek API Key）可在 Web 管理端录入，作为加密的系统参数保存在 `app_parameter`。`AI_CREDENTIAL_ENCRYPTION_KEY` 是解密这些参数的主密钥，不能只保存在同一张参数表，否则服务启动时无法解密；请从环境变量或独立密钥管理服务注入，并在重启后保持不变。若主密钥已丢失，旧密文无法恢复，设置稳定的新密钥后必须重新录入所有受影响的供应商凭据。数据库连接密码同样必须在连接数据库之前通过环境变量注入，不能从数据库系统参数读取。

DeepSeek API Key 可以由服务端环境变量 `AI_DEEPSEEK_API_KEY` 注入，也可在 Web 管理端录入并使用 `AI_CREDENTIAL_ENCRYPTION_KEY` 加密入库；接口不回传密钥明文。

## 英语短文与阿里云 TTS

### 英语角菜单

管理端将 `words`（英语单词）、`imports`（词库批次）、`articles`（英语短文）、`study_records`（词书学习记录）集中到一级菜单“英语角”（`group_english`）。页面地址和 API 权限码不变；只有拥有至少一个英语页面权限的角色才显示该分组，分组本身不授予任何业务权限。技术内容、主题和公告仍在“内容与来源”，通用任务和 AI 配置仍在原分组。

已有数据库在 V3.18 分组迁移之后执行 `sql/V3.28_english_corner_menu.sql`，仅新增菜单行、调整 `parent_id`，并给已有英语页面权限的角色补授空路径的分组容器；不修改子页面授权。脚本可重复执行，开发测试种子 `data.sql` 已同步。前端兼容旧菜单数据和已有会话，即使分组迁移尚未执行，侧栏也能按“英语角”展示；角色权限配置页的数据库分组以迁移结果为准。无新增表或列。

- 短文音色在“英语短文 → 短文音色”选择并保存，默认 **Luca**（NLS 参数 `luca`）。独立使用数据库 `app_parameter` 的 `tts.aliyun.article_voice` 非密钥参数，保存即生效，不写 YAML，也不影响原有单词的 `tts.aliyun.voice`。已有 ready 短文音频始终复用并返回实际保存的音色，仅缺少音频时使用当前短文音色。
- 短文专属接口：`GET /api/admin/content/articles/tts-status` 返回 AppKey 可用状态、当前短文音色及可选音色；`PUT /api/admin/content/articles/tts-settings` 接收 `{ "voice": "luca" }`，仅 `articles` 菜单角色可用。`GET /api/admin/content/articles/options` 同时返回 `audioSettings`，不返回任何凭据。无需新增表、列或 SQL 迁移。

- `POST /api/learning/contents/{id}/speech`：按需合成短文或单词发音；首次调用生成并存入 OSS，后续复用缓存。短文点词先查正式词库，再查 V3.14 补充词表。
- `/api/admin/ai/tts`：仅 Web 管理员可查看状态、录入 AppKey/AccessKey、选择英语音色和查看脱敏调用日志；接口永不回传密钥。
- 阿里云 AppKey 不能单独完成鉴权，还需具备智能语音交互权限的 AccessKey ID/Secret。凭据仅保存在服务端，并使用 `AI_CREDENTIAL_ENCRYPTION_KEY` 加密。
- 阿里云短文本 TTS 单次最多 300 字符；短文正文会优先按句自动切成不超过 280 字符的片段，逐段合成并合并为一个 MP3，最多 24 段。单词及试听仍按单次 300 字符限制。部署后端时需在 `PATH` 安装 `ffmpeg`；长短文若缺少该程序，会在产生语音合成费用前返回 `TTS_AUDIO_MERGER_UNAVAILABLE`。每段都会产生一次阿里云合成调用与相应费用，合并失败不发布音频，可修复后重试。播放速度由小程序播放器在 0.75x、1x、1.25x、1.5x 间调整，不重复计费合成。
- 音频写入现有私有 OSS；除 TTS 凭据外，仍需正确配置本节启动命令中的 OSS 参数。
- 每日分词书短文同时要求 AI 返回完整中文译文，正文及译文逐段存入既有 `content_version.article_blocks` JSON；旧短文缺译文时，由小程序用户明确点击“生成并保存译文”后使用服务端 AI 配额补齐，不在普通浏览时隐式调用模型。短文点词未命中词库时，仅允许对当前短文实际出现的英文词调用服务端系统参数中的 NLS 临时 Token，音频在 `pronunciation` 与 `media_asset` 中缓存；Token 不传给小程序。

## 问一问与 AI 管理

- `GET /api/ai/models`：返回可用模型及密钥、价格、预算的就绪状态。
- `POST /api/ai/ask`：需要 AI 同意、`Idempotency-Key`、日额度、并发名额与月预算。
- `GET /api/ai/ask/history`：仅返回本人近期提问。正文最多保留 24 小时，计量与费用记录继续保留。
- `/api/admin/ai/models`：模型、规格、地址、凭据状态和价格版本管理。
- `GET /api/admin/ai/pricing/status`：返回 DeepSeek 官网价格同步状态。服务端在北京时间每天 22:00 核验官网价格；MySQL 后端启动时也会补核验，避免笔记本休眠错过定时点。
- `/api/admin/ai/budget` 与 `/api/admin/ai/usage`：月预算、脱敏调用日志和结算费用。
- `GET /api/admin/ai-audit/questions/{jobId}/detail` 仍需 `ai_audit` 菜单权限。每次成功读取详情前，事务内写入 `admin_audit`：真实管理员 ID、`ai_conversation_view`、目标类型 `ai_job`、目标记录 ID、查看请求时间与 180 天到期时间。元数据为 `{}`，不复制问题、回答、用户昵称、查询关键词或凭据。列表查询不写正文查看记录；无权限或不存在的记录不写成功查看记录。审计写入失败时不返回正文，返回 503 `ADMIN_AUDIT_UNAVAILABLE`。记录表示已授权的读取请求，不证明客户端已收到或阅读响应；网络中断不删除已写入的记录。
- AI 追溯列表和详情均返回 `Cache-Control: no-store` 与 `Pragma: no-cache`，避免浏览器或代理缓存敏感内容。复用现有审计表，无数据库结构变更、无需迁移；本轮不修改正文保留期限或历史记录，也不增加批量导出权限。
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

## 存活与就绪探针

- `GET /api/health` 保留原有返回结构，只表示进程可响应，不能当作数据库可用证明。
- `GET /api/health/live` 返回 200 与 `{"status":"UP"}`，不访问数据库或外部供应商。适合存活检查，数据库故障不应因此反复重启进程。
- `GET /api/health/ready` 在应用启动完成后执行只读 `SELECT 1`：成功为 200/UP，启动未完成、关闭中、连接或查询失败及等待超时均为 503/DOWN。响应只包含 status，不返回连接地址、数据库账号、凭据或异常原文；新探针响应禁用缓存。部署需显式将就绪探针接到负载均衡/编排系统，新增接口本身不会自动切流。
- 默认调用等待上限 2000ms、结果短缓存 1000ms；生产环境变量 `HEALTH_PROBE_TIMEOUT_MS`（1–30000）、`HEALTH_PROBE_CACHE_MS`（0–60000）。单实例仅一个探测线程，并发请求共享未完成的探测；JDBC 查询也设置超时。即使驱动连接阻塞或不响应中断，也不会为每个探针请求创建新线程；探针调用超时不意味着底层连接请求已经终止，底层仍受连接池/驱动超时控制。
- 这是数据库连通性检查，不验证数据库迁移、全部业务表权限、Redis、任务调度或 AI/OSS/TTS；不会调用付费接口。不改表、不改业务数据，无需数据库迁移。

## 管理员登录防护

`POST /api/admin/auth/login` 在密码校验前预留请求容量，防止并发绕过。默认 15 分钟内同一账号失败 5 次或同一来源失败 20 次后，锁定 15 分钟；锁定期间正确密码也不能跳过保护。成功登录清空该账号失败计数，但不清空来源失败数；参数错误、配置缺失及服务内部错误不算密码失败。未知账号和错误密码使用相同响应。达到阈值后的请求返回 HTTP 429、`ADMIN_LOGIN_THROTTLED`、`Retry-After` 响应头和 `retryAfterSeconds`；页面显示具体等待秒数，不重复尝试密码校验。

配置前缀 `app.admin-login-security`，生产环境可设置 `ADMIN_LOGIN_ACCOUNT_MAX_FAILURES`、`ADMIN_LOGIN_SOURCE_MAX_FAILURES`、`ADMIN_LOGIN_FAILURE_WINDOW`、`ADMIN_LOGIN_LOCK_DURATION`、`ADMIN_LOGIN_MAX_TRACKED_KEYS`。账号按去空格、小写后的摘要归组；来源使用 Servlet `remoteAddr`，不直接信任客户端 `X-Forwarded-For`。反向代理部署应在受信任代理层正确解析客户端地址，否则多个管理员可能共用来源额度。内存仅保留摘要和计数，最多跟踪 10000 个键，过期自动清理，容量耗尽时拒绝新键而不是淘汰有效锁；日志仅记录触发范围，不记录账号、IP 或密码。

该实现是单实例进程内保护，重启清空计数，多实例之间不共享；不替代网关限流。多实例上线前需使用共享计数或受信任入口的统一限流，不能声称具备跨实例防暴力破解能力。现有账号、密码、会话、加密密钥与历史审计记录均不改写；无数据库结构变更，无需迁移。

## Web 技术知识接口

- `GET /api/learning/contents/{contentId}`：用户详情返回当前 `published_version_id` 指向的正文和版本化来源字段，并同时返回 `originPublishedAt`（原文发布时间）与 `publishedAt`（站内发布时间）。
- `GET /api/admin/content/technical`：分页查看已发布的技术知识，默认 20 条/页，支持主题、关键词及来源日期范围（`startDate`/`endDate`，按北京时间自然日）筛选。`sortOrder=asc|desc` 按来源日期全量排序后分页，默认 `desc`（最新在前）；缺失来源日期时回退站内发布时间，两者均缺失时排在末尾，同日期按标题和内容 ID 保持稳定顺序。
- `GET /api/admin/content/technical/{contentId}`：查看已发布版本的正文、难度、预计时长、主题、审核状态、来源、原文/站内发布时间和许可快照。
- `GET /api/admin/content/articles`：分页查看已发布英语短文，支持按 `intro`/`advanced` 难度筛选以及标题、摘要、正文关键词搜索；时间字段语义与技术知识一致。
- `GET /api/admin/content/articles/{contentId}`：查看英语短文正文、难度、预计时长、审核状态、来源、原文/站内发布时间和许可快照。
- 管理端接口统一由登录会话与菜单权限保护。`/api/admin/content/articles` 及子路由归属 `articles`，技术知识与通用覆盖统计归属 `content`；两者独立授权，不能互相替代。
- `GET /api/admin/content/articles/options` 仅返回短文难度统计、词书 ID/名称与短文音色设置；`GET /api/admin/content/articles/tts-status` 返回 AppKey 可用布尔状态与短文音色设置。短文页不读取其他菜单的内容覆盖、单词统计或完整 TTS 配置。补齐短文仍需 `jobs` 权限，不随短文菜单自动授予。
- 短文分类、元数据、音频生成、反馈处理、词库批次操作/同步，以及 AI 模型、预算和费用核定使用当前认证管理员 ID。缺失认证主体时拒绝操作，不静默归到配置中的固定管理员；历史审计记录不改写。系统生成仍保持系统任务语义。本轮无数据库结构变更，无需迁移。

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

## 英语短文标题译文迁移

部署读取 `content_version.title_translation` 的后端前，先对实际连接的 MySQL 执行 `sql/V3.16.12_article_title_translation.sql`；此前的 `sql/V3.16.11_article_word_on_demand.sql` 也必须已执行。`dev`/`prod` 的 `spring.sql.init.mode=never`，仅修改 `schema.sql` 不会更新现有数据库。新生成短文保存标题译文，旧短文在小程序打开时按需生成并保存；译文生成依赖模型配置，会增加一次调用费用。

## 英语短文段落解释缓存迁移

部署新版后端前，对实际连接的 MySQL 执行 `sql/V3.16.13_article_paragraph_explanation.sql`。小程序点击“解释这段内容”时，后端按已发布短文版本、段落 ID 和英文原文哈希读取缓存；首次生成成功后保存，后续请求直接读取，不再次调用模型。同一段落并发请求由数据库生成占位协调；失败可重试，过期占位可接管。段落解释属于短文系统内容，不要求用户获得“问一问”授权，但仍受系统 AI 配额、并发和预算约束；缓存命中不消耗 Token。系统调用使用独立的 `explain_article_paragraph` 任务类型，不进入用户提问历史；旧版误记为提问的短文解释也从历史列表中隐藏，审计和费用记录保留。`schema.sql` 只用于新建测试库，不会自动迁移现有 `dev`/`prod` 数据库。

## 每日英语短文校验与补齐

生成提示与服务端校验统一要求五个文本字段的类型、字符长度、英文单词数、中文译文、不同目标词覆盖及标题去重。小学／初中／高中正文 45–80 个英文单词，其他词书 75–120 个。每批最多 5 篇；合格篇保留，只对不合格槽位补生成一次，避免反复生成已通过的内容。再次点击“补齐今日短文”时查询实际缺失槽位，不依赖已有篇数。运行详情的失败摘要标明词书、槽位、字段及实际／要求长度，模型调用仍受原有配额与预算限制。本次无需新增数据库迁移。

## 管理端执行计划编辑

### 每日英语新词首次点击固定（V3.31）

V3.32 未完成词重新规划：今天未完成的 word 任务从次日起重新进入对应词书的新词候选池（含已经答过模糊、不认识的 pending 新词）；已认识完成或进入活动复习排期的词仍排除。再次选中时绑定新的每日任务，旧日期的未完成事实保留。今日一经固定不重抽，未来随机选择不保证次日必选。可通过“待巩固”提前继续；一旦重新选入今日新词，不在两个队列重复出现。

V3.32 修复首页初始化事务与半成品包恢复，统一用户锁在任务包/任务锁之前；每日学习入口由服务端队列决定当前可学词，并单独提供往日“待巩固”队列。暂不可用词条保留在固定范围中，队列明确返回 `unfinishedCount / unavailableCount / allCompleted`，不把无可用词当作完成。任务包批量附带 `meaning / available / unavailableReason` 与词书名，小程序词表一次请求即可读取完整列表。新增 `words_target_count / words_selection_note` 保存首次计划数量与不足原因；旧快照不回填推测值。部署依次执行 V3.31、`sql/V3.32_daily_word_flow_recovery.sql`，更新后端和小程序；合并版 HTML 数据字典已同步。无需创建额外的“待巩固”表，复用 word_study_state。

首页“英语新词”首次进入时调用 `POST /api/days/{date}/words/select`：按北京时间今天和当前词书随机选择合格未学词，计划难度优先，不足从同书其他难度补齐，数量受计划与剩余时间预算限制。词条范围复用 `daily_task`，`daily_package.words_selected_at / words_book_id` 保存首次确定状态与词书快照。用户及任务包行锁、事务、既有唯一索引保证多端并发与重试不重抽。已确定后换书、调整计划或退出重进均不替换当天词表；次日重新抽取。升级前已有进行中或完成事实的当天词表直接保留。`mode=daily` 限制后续学习与反馈队列为今日固定范围，全部完成后不自动追加其他词。

部署先执行幂等迁移 `sql/V3.31_daily_word_selection.sql`，再更新后端和小程序。首页预排仅用于数量/预算展示，不设置首次确定标记；没有选择词书时提示选书，不写入空快照；有效书籍但合格词不足时保存实际范围及缺口，当天不重复抽样。历史日期禁止重新选词。合并版 HTML 数据字典同步更新，不自动修改实际 MySQL。

### 每日技术资料新增目标（V3.29）

“每日官方技术资料采集”按北京时间每天合计新增 **10 条**，不是四个来源各 10 条：先扫描固定官方 RSS/Atom 的未入库条目，缺口再调用已配置 AI 生成不同的中文原创技术学习解读。候选扫描扩大到每源基准数量的五倍、最多 100 条；不复制整篇第三方正文、不假冒官方公告，新资料和 AI 内容均为 `pending`，人工审核后才发布。AI 补生成复用系统全局配额、并发和月预算，action/source 为 `generate_tech_materials` / `content_collection`，实际模型及调用次数可在运行详情查看。

部署前先执行幂等迁移 `sql/V3.29_daily_technical_content_target.sql`（依赖 V3.3），再重启后端。该迁移新增 `content_collection_day`（日期、执行租约、自动尝试次数、重试时间），为原 `content_collection_run` 增加可空的每日目标快照列，并为 `learning_content(source_id,created_at)` 增加索引；不修改既有内容、计划或启停状态。开发测试 `schema.sql` / `data.sql` 已同步；测试库补齐了生产已存在的 `content_version.tags_json`。

计数读取北京时间日界内指定官方/原创来源下、实际拥有有效内容版本的技术内容行；历史当天已经入库的资料也计入，不以运行日志的累计数或重复项充数。同一天手动与自动执行只补缺口，达到目标不再抓取或调用 AI。数据库日租约阻止多实例重复付费，入库在日行锁保护的短事务中再次检查目标，并原子保存内容、版本与主题，失败整条回滚。租约五分钟且每个来源/模型请求前及成功入库时续租，失去租约或跨日停止写入。

未达标时在详情明确记录实际新增数、AI 新增数和仍缺条数；每轮最多对缺口尝试两倍数量（上限 20 次），校验重复标题、正文指纹和最近原创正文的高相似度，不能靠改标题、日期或编号计为新内容。后台每分钟检查已有当天任务的缺口，延后 15 分钟再尝试，每天最多三次**自动尝试**（包含计划触发），暂停后不再重试；进程意外退出可在租约到期后恢复。预算不足、凭据/网络/模型异常或达到重试上限时不会无限消耗费用，已成功条目保留，可修复后手动补齐。因此外部服务故障时明确显示未达标，不虚报保证完成。单条错误描述仍限原表 500 字符，目标缺口优先展示；历史记录新列为空，不伪造旧目标。

任务与运行页面支持每日时间或六段 Cron 编辑，固定使用北京时间（`Asia/Shanghai`）。`PUT /api/admin/tasks/{collection|article|pricing}/schedule` 需要管理员令牌。更新顺序为：校验表达式及下次时间 → 预注册不具备执行资格的新回调 → 事务保存计划 → 切换生效并取消旧回调。调度器拒绝注册或返回空句柄时不写数据库，返回 503 `TASK_SCHEDULE_UNAVAILABLE`；数据库写入或提交失败时取消候选计划、保留旧计划（普通持久化异常返回 `TASK_SCHEDULE_SAVE_FAILED`）。只有匹配当前注册身份的回调可执行，取消旧句柄失败也不会让旧计划重新触发。关闭调度器后拒绝计划更新。成功响应包含 `scheduleApplied=true`，下次运行时间直接按已生效表达式计算，避免保存后再读取配置失败导致误报。旧的固定 `@Scheduled` 已移除，避免重复触发。修改不会主动执行任务、启用已停止任务或中断本次执行，同一任务切换计划期间也不会重叠运行。既有按日幂等规则不变：同日多次触发仍只处理当日任务，不等于多次新增内容。

采集计划复用 `content_collection_schedule.cron_expression`；短文和价格计划分别保存到非敏感系统参数 `article_generation.cron`、`ai.pricing.deepseek.cron`，重启后恢复。无新表或字段，无需数据库迁移。首次部署需要重启后端以加载动态调度器；之后从本页面保存即可即时生效。直接从系统参数页面修改 Cron 时需重启后端，推荐使用任务页面的“修改计划”。

上述切换保护属于单实例内的一致性保护，不提供跨实例计划广播或分布式事务；进程若在数据库提交与内存切换之间退出，重启会按已提交配置恢复。数据库提交结果不确定（如提交时连接中断）仍需核对持久化配置，不应把接口失败一概理解为数据库绝对未变更。

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

## 英语图文书籍与 OSS 听读（V3.30）

新增独立 `english_books` 权限、完整书籍草稿与发布快照、章节图文、异步 TTS／配图、私有 OSS 媒体访问及小程序学习记录。部署前执行 `sql/V3.30_english_reading_books.sql`；默认关闭生成任务。完整接入协议、配置和验收见 `../docs/英语图文书籍_接入与验收.md`。
