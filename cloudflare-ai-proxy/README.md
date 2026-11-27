# 矿大时光课表 · 免费 AI 体验中转通道部署指南（3 分钟零成本）

通过 Cloudflare Workers（免费版每天 10 万次请求）+ 智谱 BigModel 永久免费模型（`glm-4-flash-250414` 文字 + `glm-4.6v-flash` 识图），让所有安装 App 的同学**无需申请 API Key、开箱即用 AI 课表助手**，同时你的上游 Key 永远安全保存在 Cloudflare Secrets 中，不会泄露在 APK 或 Git 仓库里。

---

## 一、获取免费上游 API Key（2 选 1）

1. **智谱开放平台 BigModel（最省心，永久免费）**
   - 注册登录：`https://open.bigmodel.cn/`
   - 控制台 → API Keys → 创建一把 Key（全平台通用，无需按模型分别申请）
   - 默认免费模型：`glm-4-flash-250414`（对话与工具调用）+ `glm-4.6v-flash`（实验表/课表识图）
2. **魔搭社区 ModelScope（识图精度高，每日数千次免费）**
   - 注册登录：`https://modelscope.cn/` → 个人中心 → 访问令牌（Access Token）
   - 环境变量改为：
     - `UPSTREAM_BASE_URL = https://api-inference.modelscope.cn/v1`
     - `TEXT_MODEL = Qwen/Qwen2.5-72B-Instruct`
     - `VISION_MODEL = Qwen/Qwen2.5-VL-72B-Instruct`

---

## 二、部署到 Cloudflare Workers（零命令行，网页 3 分钟搞定）

1. 登录 [Cloudflare Dashboard](https://dash.cloudflare.com/) → 左侧菜单 **Workers 和 Pages** → **创建应用程序** → **从 Hello World 开始**，取名例如 `shiguang-free-ai-proxy`，点击部署。
2. 点击 **编辑代码**，将本目录中 `worker.js` 的全部内容复制粘贴进去，点击右上角 **部署**。
3. 回到该 Worker 的 **设置 → 变量和机密（Variables and Secrets）**，添加：
   - **Secret（加密机密）**：`UPSTREAM_API_KEY` = 刚才申请的免费 Key（支持用英文逗号 `,` 填多把 Key 自动分摊并发）
   - **普通变量（可选，不填则用默认值）**：
     - `DAILY_LIMIT` = `20`（每人每天免费次数）
     - `APP_CLIENT_TOKEN` = `cumt-shiguang-app`（防止被外部脚本盗刷）
     - 如果用魔搭，额外添加 `UPSTREAM_BASE_URL`、`TEXT_MODEL`、`VISION_MODEL`
4. *(可选)* 若希望跨边缘节点精准计数：左侧 **存储和数据库 → KV** 新建命名空间（如 `shiguang-quota`），在 Worker **设置 → 绑定** 中添加 KV 绑定，变量名填 `QUOTA_KV`。
5. *(国内网络优化提示)* `*.workers.dev` 在部分校园网可能直连较慢；如果你有任意便宜域名，在 Worker **设置 → 域和路由** 绑定一个自定义子域名（如 `ai.你的域名.com`），国内访问会非常快且稳定。

---

## 三、接入到「矿大时光课表」App

部署完成后，你会得到一个类似 `https://shiguang-free-ai-proxy.你的子域.workers.dev` 的地址（先在浏览器打开 `你的地址/health` 看到 `"ok": true` 即表示成功）。

### 方式 A：打包成「全员开箱免 Key」版本（推荐）
打开源码文件：
`shared/src/commonMain/kotlin/com/xingheyuzhuan/shiguangschedule/cumt/CumtModels.kt`

找到 `CumtConstants.FREE_AI_PROXY_BASE_URL`，填入你的地址（末尾加上 `/v1`）：
```kotlin
const val FREE_AI_PROXY_BASE_URL = "https://shiguang-free-ai-proxy.你的子域.workers.dev/v1"
```
提交并重新打包 APK 后，所有新用户打开 App 即自动默认启用 **「免费体验」** 通道，无需任何配置！

### 方式 B：不重新打包，直接在 App 内测试
打开 App → **矿大 AI 助手 → 模型与 Key → 免费体验** → 在「中转地址」填入 `https://你的地址/v1` → 点击 **测试连接** → **保存配置** 即可使用。
