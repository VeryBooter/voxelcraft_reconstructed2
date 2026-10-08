# Voxelcraft 网页客户端

网页客户端位于 `web/`，是可直接在浏览器运行的单人方块沙盒。

已发布地址：[Voxelcraft 网页版](https://voxelcraft-browser.zesty-ape-8343.chatgpt.site)，当前仅所有者可访问。
它使用 JavaScript、Three.js 0.180.0 和 WebGL 2。图形库已随网页打包，运行时不使用外部 CDN，也不需要 Java、Metal 原生库或 npm 安装。
本地预览和检查需要 Node.js 20 或更新版本。

```bash
npm --prefix web start
# 在浏览器中打开 http://127.0.0.1:4173

npm --prefix web run check
```

## 可用功能

- 按种子生成起伏地形与跨区块树木，移动时加载周围区块，卸载远处区块。
- 第一人称移动、重力、跳跃、身体碰撞和防止放置方块卡住玩家。
- 六米内选择、挖掘和放置方块；草、泥土、石头、橡木、树叶、沙子与砖块共七种材料。
- 快捷栏、滚轮选材、坐标、绘制数量与帧率显示。
- 视野距离切换、白昼与黄昏切换、暂停菜单。
- 自动保存种子、方块修改、玩家位置与视角；支持 JSON 存档导入导出。
- 手机、电脑与 iPad 的响应式布局，支持横竖屏、安全区域与可滚动的短屏菜单。
- 触屏左侧虚拟摇杆可斜向移动、按偏移量控制速度；右侧拖动画面环顾，可同时按跳跃、挖掘和放置按钮。挖放按钮支持按住连续操作。
- 操作方式支持自动识别、触屏、键盘与鼠标；iPad 外接鼠标时仍可使用触屏，并可手动切换。输入按指针 ID 跟踪，其他手指不会中断正在进行的视角拖动。

## 操作

- `W/A/S/D` 移动，`Shift` 加速，`Space` 跳跃。
- 鼠标环顾；浏览器不允许锁定鼠标时，可以按住画面拖动或使用方向键。
- 左键挖掘、右键放置，也可以使用 `E/F`。
- `1–7`、滚轮或点击快捷栏选择材料。
- `Esc` 返回菜单，窗口失焦或页面进入后台时自动暂停。
- 触屏设备左手使用摇杆移动，右手拖动画面环顾，使用右侧按钮跳跃与挖放；点击快捷栏切换材料。
- 自动识别不合适时，在菜单的“操作方式”中手动选择“触屏 · 手机 / iPad”或“键盘与鼠标”。键盘在触屏模式下仍可使用。

需要支持 WebGL 2 的现代浏览器。电脑可使用 Chrome、Edge、Firefox 或 Safari，手机与 iPad 可使用支持 WebGL 2 的浏览器。触屏设备默认采用较短视野和较低像素倍率以减轻负载。布局与输入逻辑验证不等同于所有型号的真机性能测试，老旧设备的兼容性与帧率需实机确认。

浏览器存档只属于当前浏览器、当前网站地址。清理网站数据、更换浏览器或从本地地址切换到线上地址不会自动迁移存档，可使用导出与导入转移。隐私模式或存储配额限制可能使自动保存失败，界面会提示导出备份。导入和新建世界会要求确认替换当前世界。

## 与桌面版的关系

网页使用独立的世界生成和存档格式，当前并不是 Java 程序的完整移植，也不能直接导入桌面存档。
目前未接入桌面 TCP 多人协议、完整方块注册表、W 轴切片与传送门、导弹等机制。保留这些边界可以让第一版无需安装即可完整运行单人建造循环。
浏览器不能直接运行 JNI/Metal 桥接代码，也不会把 Java 堆直接交给 GPU。本版通过 WebGL 2 提交区块网格。

## 渲染与性能

```mermaid
flowchart TD
    A[加载网页、图形库与本地存档] --> B[按种子生成玩家附近区块]
    B --> C[生成区块网格：剔除相邻实体间的内部面]
    C --> D[创建 GPU 几何缓冲区]
    D --> E[每帧读取输入并处理身体碰撞]
    E --> F[更新相机并用体素射线检测目标方块]
    F --> G{挖掘或放置?}
    G -->|是| H[记录修改并使当前及边界邻区块网格失效]
    H --> I[按帧预算重建失效网格并保存修改]
    G -->|否| J[复用网格]
    I --> K[Three.js 视锥体与 GPU 背面、深度测试]
    J --> K
    K --> L[显示画面和 HTML 界面]
    L --> E
```

每个区块使用一个合并后的网格，避免逐方块提交绘制。网格生成剔除内部面；Three.js 默认视锥体剔除跳过视野外网格，GPU 做背面剔除与深度测试。加载和网格重建每帧都有数量与时间预算，远处区块卸载时释放几何 GPU 资源。
首版网格构建在主线程执行，没有移植桌面版的工作线程与贪心面合并。大型视野或低性能设备上可选择较短视野，后续可根据测量加入 Web Worker 和面合并。

## 文件

- `web/dist/index.html` / `style.css`：启动菜单、HUD、快捷栏与响应式布局。
- `web/dist/game.js`：图形资源、主循环、区块调度、输入、存档和界面。
- `web/dist/world.js`：纯世界逻辑、网格构建、射线和身体碰撞。
- `web/dist/input.js`：混合输入选择、摇杆向量与多指视角手势。
- `web/tests/world.test.mjs`：确定性生成、跨区块修改、存档往返、射线、网格绕序、碰撞与非法输入测试。
- `web/serve.mjs`：只监听本机地址的静态预览服务。
- `web/dist/vendor/`：固定版本 Three.js 模块与 MIT 许可证。
- `web/.openai/hosting.json`：私有 Sites 部署配置，静态目录为 `dist`。

可将 `web/dist/` 部署到支持 ES 模块的静态网站服务。首次 Sites 发布默认仅所有者可访问；公开分享权限需要另行调整。

## 发布到 GitHub Pages

仓库已加入 `.github/workflows/pages.yml`。它只发布 `web/dist/`，先运行网页检查与测试，然后上传静态文件并部署，不编译 Java 或 Metal。

首次发布：

1. 将本次改动提交并推送到 GitHub 的 `main` 分支。
2. 打开仓库 [Settings → Pages](https://github.com/VeryBooter/voxelcraft_reconstructed2/settings/pages)。
3. 在 **Build and deployment → Source** 中选择 **GitHub Actions**。
4. 打开仓库 **Actions → Publish Voxelcraft Web → Run workflow**，选择 `main` 并运行。
5. 工作流成功后，在 Pages 设置或工作流部署结果中打开实际网站地址。

按当前用户名与仓库名，默认项目网址应为 `https://verybooter.github.io/voxelcraft_reconstructed2/`；这是配置成功后的预期地址，未运行 GitHub 部署前不代表已经上线。以 GitHub Pages 实际显示的地址为准。

后续推送 `main` 分支上的 `web/` 或该工作流修改，会自动检查与重新发布。无需手动复制文件到 `/docs`；Pages 的 Source 应选择 GitHub Actions。

页面与模块资源均使用相对路径，可以部署在仓库名子路径下。网页不依赖 Sites 登录服务，GitHub Pages 发布的普通网站可直接在手机、电脑和 iPad 的浏览器中打开。

GitHub Free 支持公开仓库的 Pages，私有仓库能否使用取决于账号套餐；普通 Pages 网站通常公开可访问。浏览器存档不会自动同步到其他设备或不同网站地址，需要手动导出导入。

官方文档：[配置发布源](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site)、[自定义工作流](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages)、[创建 Pages 网站](https://docs.github.com/en/pages/getting-started-with-github-pages/creating-a-github-pages-site)。
