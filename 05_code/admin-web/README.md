# Vue 管理端

前后端分离，管理业务通过 Java 后端接口完成。

- src/pages：概览、内容与来源、用户状态、任务与运行四类页面。
- src/components：公共组件。
- src/router：前端路由。
- src/services：后端接口请求封装。
- src/styles：样式。
- src/assets、public：静态资源。
- package.json、vite.config.js、index.html、.env.example：工程配置空文件。

当前所有代码和配置文件均为空占位，没有实现页面或初始化依赖。
# 脑袋开小灶 Vue 管理端

Vue 3 + Vite 管理端。F01 已实现与 UI 稿一致的用户状态入口、邀请码管理、一次性明文展示、状态筛选、撤销和准入名额调整。

```bash
pnpm install
pnpm dev
```

本地开发通过 Vite 将 `/api` 代理到 `http://127.0.0.1:8080`。首次进入需要输入后端 `ADMIN_TOKEN`，本地默认值为 `dev-admin-token`。令牌只保存在当前浏览器的 localStorage。

生产构建：

```bash
VITE_API_BASE_URL=https://api.example.com/api pnpm build
```
