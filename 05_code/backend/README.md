# Java Spring Boot 后端

F01 已实现微信身份判断、受邀注册、会话恢复与退出、同意记录，以及管理端邀请码和名额接口。数据库访问使用 JDBC，注册事务会锁定名额和邀请码，并在同一事务内创建账号、默认计划、同意记录与会话。

## 本地启动

本地默认使用文件型 H2 和微信模拟模式，不需要 AppSecret：

```bash
mvn spring-boot:run
```

默认地址为 `http://localhost:8025/api`，H2 控制台为 `http://localhost:8025/h2-console`。管理接口开发令牌为 `dev-admin-token`，只能用于本地。

## 连接现有 MySQL 5.7

生产环境不自动建表，应先使用项目 `02_database/SQL/知行日课_V3.0_数据库初始化_最终版_MySQL5.7.25.sql` 初始化。所有敏感值通过环境变量注入：

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
OSS_ENDPOINT='OSS Endpoint' \
OSS_BUCKET='私有 Bucket 名称' \
OSS_ACCESS_KEY_ID='OSS AccessKey ID' \
OSS_ACCESS_KEY_SECRET='OSS AccessKey Secret' \
MEDIA_BASE_URL='https://api.example.com/api/media' \
mvn spring-boot:run
```

真实密钥不得写入 YAML 或 Git。生产配置使用 Redis 保存十分钟的待注册状态；数据库仅保存邀请码和会话令牌的 SHA-256 摘要。

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
