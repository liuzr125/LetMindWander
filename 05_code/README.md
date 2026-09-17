# 知行日课代码

采用前后端分离：Vue 管理端和原生微信小程序统一调用 Spring Boot 后端。当前实现以 V3.1 需求为基线。

| 目录 | 技术与用途 |
| --- | --- |
| admin-web | Vue 管理端：页面、组件、路由、接口请求与静态资源 |
| backend | Java 后端：管理端接口、小程序接口、业务逻辑与数据库访问 |
| miniprogram | 原生微信小程序：首页（今日任务、成长摘要、问一问）、学习、知识库、我的及英语记忆训练 |
| docs | 接口、联调与部署文档 |
| scripts | 素材与数据生成辅助脚本 |

英语短文按现有“入门/进阶”两档各提供 101 篇；详情页支持阿里云 TTS 整篇朗读、播放变速、跟读录音回放，以及点词查看音标、释义和发音。TTS 密钥与音色只能在 Web 管理端维护。

后端回归测试：

```bash
cd backend
mvn test
```

正式需求在 `../01_Requirements`，数据库基线在 `../02_database`。代码内 `backend/src/main/resources/schema.sql` 仅用于隔离测试；MySQL 升级必须按 `backend/sql` 中的经评审增量脚本执行。
