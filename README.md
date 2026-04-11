# voxelcraft_reconstructed2

Voxelcraft 重构仓库（Java 多模块：`core` / `client` / `server`）。

## 当前状态（A→E）

- 阶段 A：工程骨架完成（含可自举 `gradlew`）
- 阶段 B：`core` 世界/区块/生成/注册表 + 测试完成
- 阶段 C：客户端外壳（窗口、输入、主循环）完成
- 阶段 D：可见世界 + 可交互完成（软件渲染 + Vulkan 渲染）
- 阶段 E：服务端 + 联机同步 + 文档完成

## 关键能力

- 单机：WASD/跳跃/视角/破坏/放置
- 联机：客户端连接服务端，支持区块流送与方块更新同步
- 渲染：
  - `software`：Java2D 软件渲染（默认可回退）
  - `gpu`：兼容别名（现已转发到 Vulkan）
  - `vulkan`：LWJGL + GLFW + Vulkan 运行时（实验中，默认使用软件帧上传显示；可用 `-Dvc.vulkan.projectedWorld=true` 或 `runVulkanProjected` 开启直接面片投影绘制）
  - `accelerated`：Vulkan 启动路径（默认关闭 vsync，用于性能测试）

## Gradle 使用

仓库内 `gradlew` 会自动下载 Gradle（无需系统预装 Gradle）。

```bash
./gradlew -v
```

## 常用命令

```bash
# core 测试
./gradlew :core:test

# 编译 client/server
./gradlew :client:classes :server:classes

# 启动服务端（最稳：默认端口 25565）
./gradlew :server:runLocal

# 启动服务端（自定义端口）
./gradlew :server:runLocal -Pport=25566

# 启动客户端（GPU 兼容别名 -> Vulkan + 联机）
./gradlew :client:runGpu -Pconnect=127.0.0.1:25565

# 启动客户端（Vulkan + 联机）
./gradlew :client:runVulkan -Pconnect=127.0.0.1:25565

# 启动客户端（Vulkan 软件帧上传路径 + 联机）
./gradlew :client:runVulkanSoftware -Pconnect=127.0.0.1:25565

# 启动客户端（Vulkan 直接面片投影 + 联机）
./gradlew :client:runVulkanProjected -Pconnect=127.0.0.1:25565

# 启动客户端（Vulkan 加速本地直连，关闭 vsync）
./gradlew :client:runAcceleratedLocal

# 启动客户端（软件渲染，最稳）
./gradlew :client:runSoftware

# 启动客户端（本地直连，最稳）
./gradlew :client:runSoftwareLocal

# 启动客户端（Vulkan 本地直连）
./gradlew :client:runVulkanLocal

# 启动客户端（Vulkan 软件帧上传路径 + 本地直连）
./gradlew :client:runVulkanSoftwareLocal

# 启动客户端（Vulkan 直接面片投影 + 本地直连）
./gradlew :client:runVulkanProjectedLocal

# 无图形环境验证
./gradlew :client:runHeadless
```

## 客户端参数

- `--render auto|software|vulkan`（兼容接受 `gpu`，会自动映射到 `vulkan`）
- `--connect host:port`

示例：

```bash
./gradlew :client:run --args='--render auto --connect 192.168.1.20:25565'
```

## 运行失败排查（按顺序）

```bash
# 1) 先确认 wrapper 能启动
./gradlew -v

# 2) 确认 core 测试通过
./gradlew :core:test

# 3) 先跑最稳路径（不走 GPU）
./gradlew :server:runLocal
# 新开终端：
./gradlew :client:runSoftware -Pconnect=127.0.0.1:25565
```

如果 `runGpu` 或 `runVulkan` 失败，先用 `runSoftware` 验证玩法与联机链路，再排查显卡/驱动/Vulkan 环境。

### macOS Vulkan 依赖（MoltenVK）

如果是 macOS，建议先安装：

```bash
brew install vulkan-loader molten-vk vulkan-tools
```

若仍出现 `vkCreateInstance failed with Vulkan error code -9`，通常表示当前运行环境无法提供 Metal（常见于无图形上下文/远程无显示环境）。

如果你希望在 `--render vulkan` 失败时立刻退出（不自动回退到 software），可加：

```bash
./gradlew -Dvc.vulkan.strict=true :client:runVulkan
```

如果 Vulkan 画面明显卡住/像冻住，可先降低软件帧上传分辨率再试：

```bash
./gradlew -Dvc.vulkan.softwareMaxWidth=960 -Dvc.vulkan.softwareMaxHeight=540 :client:runVulkan
```

macOS 下 `runVulkan` 默认会启用 AWT headless workaround（`-Djava.awt.headless=true` + `-Dvc.vulkan.allowHeadless=true`）来规避 GLFW 事件循环卡死；如需关闭可加：

```bash
./gradlew -Dvc.vulkan.headlessAwt=false :client:runVulkan
```


## 交互按键

- `WASD`：移动
- `Space`：跳跃
- `Mouse` / `Arrow Keys`：视角
- `LMB`：破坏方块
- `RMB`：放置方块
- `1/2/3/4/5/6/7`：切换手持方块
- `ESC`：退出

## 主要代码入口

- `core/src/main/java/dev/voxelcraft/core/world/World.java`
- `client/src/main/java/dev/voxelcraft/client/GameClient.java`
- `client/src/main/java/dev/voxelcraft/client/runtime/VulkanClientRuntime.java`
- `client/src/main/java/dev/voxelcraft/client/network/NetworkClient.java`
- `server/src/main/java/dev/voxelcraft/server/net/VoxelcraftServer.java`

## 测试文件

- `core/src/test/java/dev/voxelcraft/core/registry/RegistryTest.java`
- `core/src/test/java/dev/voxelcraft/core/world/WorldGenerationTest.java`
- `core/src/test/java/dev/voxelcraft/core/world/WorldMutationTest.java`
- `core/src/test/java/dev/voxelcraft/core/world/palette/PaletteTest.java`

## 文档

- `reconstruction/PROJECT_RECONSTRUCTION_FILE_MAP_CN.md`
- `docs/VOXELCRAFT_FLOW_CN.md`
- `docs/VOXELCRAFT_CODE_ANNOTATIONS_CN.md`
- `docs/MUSIC_PACK_INTEGRATION_CN.md`

---

## English

Voxelcraft reconstructed repository (Java multi-module: `core` / `client` / `server`).

### Current Status (A→E)

- Stage A: project skeleton completed (including bootstrap-ready `gradlew`)
- Stage B: `core` world/chunk/generation/registry + tests completed
- Stage C: client shell completed (window/input/main loop)
- Stage D: visible and interactive world completed (software + Vulkan render)
- Stage E: server + multiplayer sync + docs completed

### Key Features

- Singleplayer: move/jump/look/break/place
- Multiplayer: client-server chunk streaming + block update sync
- Rendering:
  - `software`: Java2D fallback renderer
  - `gpu`: compatibility alias (now forwarded to Vulkan)
  - `vulkan`: LWJGL + GLFW + Vulkan runtime (experimental; defaults to software-frame-upload mode, and supports projected-world mode via `-Dvc.vulkan.projectedWorld=true` or `runVulkanProjected`)
  - `accelerated`: Vulkan path with vsync disabled for performance testing

### Gradle

The repository `gradlew` can bootstrap Gradle automatically.

```bash
./gradlew -v
```

### Common Commands

```bash
# core tests
./gradlew :core:test

# compile client/server
./gradlew :client:classes :server:classes

# server (default 25565)
./gradlew :server:runLocal

# server (custom port)
./gradlew :server:runLocal -Pport=25566

# client (GPU compatibility alias -> Vulkan + multiplayer)
./gradlew :client:runGpu -Pconnect=127.0.0.1:25565

# client (Vulkan + multiplayer)
./gradlew :client:runVulkan -Pconnect=127.0.0.1:25565

# client (Vulkan software-frame upload path + multiplayer)
./gradlew :client:runVulkanSoftware -Pconnect=127.0.0.1:25565

# client (Vulkan projected world + multiplayer)
./gradlew :client:runVulkanProjected -Pconnect=127.0.0.1:25565

# client (Vulkan accelerated local connect, vsync disabled)
./gradlew :client:runAcceleratedLocal

# client (software, stable)
./gradlew :client:runSoftware

# client (software + local connect)
./gradlew :client:runSoftwareLocal

# client (Vulkan + local connect)
./gradlew :client:runVulkanLocal

# client (Vulkan software-frame upload path + local connect)
./gradlew :client:runVulkanSoftwareLocal

# client (Vulkan projected world + local connect)
./gradlew :client:runVulkanProjectedLocal

# headless validation
./gradlew :client:runHeadless
```

### Client Args

- `--render auto|software|vulkan` (`gpu` is still accepted as a legacy alias and maps to `vulkan`)
- `--connect host:port`

Example:

```bash
./gradlew :client:run --args='--render auto --connect 192.168.1.20:25565'
```

### Troubleshooting Order

```bash
# 1) verify wrapper
./gradlew -v

# 2) verify core tests
./gradlew :core:test

# 3) run stable path first
./gradlew :server:runLocal
# new terminal:
./gradlew :client:runSoftware -Pconnect=127.0.0.1:25565
```

If `runGpu` or `runVulkan` fails, verify gameplay/network on `runSoftware` first, then debug GPU/driver/Vulkan.

### macOS Vulkan Prerequisites (MoltenVK)

On macOS, install Vulkan loader + MoltenVK first:

```bash
brew install vulkan-loader molten-vk vulkan-tools
```

If you still see `vkCreateInstance failed with Vulkan error code -9`, the current runtime likely has no usable Metal context (common in headless/remote sessions).

To force hard failure instead of software fallback when `--render vulkan` fails:

```bash
./gradlew -Dvc.vulkan.strict=true :client:runVulkan
```

If Vulkan appears frozen/stuck, try lowering software-frame upload resolution:

```bash
./gradlew -Dvc.vulkan.softwareMaxWidth=960 -Dvc.vulkan.softwareMaxHeight=540 :client:runVulkan
```

On macOS, `runVulkan` now enables a headless-AWT workaround by default (`-Djava.awt.headless=true` + `-Dvc.vulkan.allowHeadless=true`) to avoid GLFW event-loop hangs. To disable it:

```bash
./gradlew -Dvc.vulkan.headlessAwt=false :client:runVulkan
```


### Controls

- `WASD`: move
- `Space`: jump
- `Mouse` / `Arrow Keys`: look
- `LMB`: break block
- `RMB`: place block
- `1/2/3/4/5/6/7`: switch hotbar slot
- `ESC`: quit

### Main Code Entrypoints

- `core/src/main/java/dev/voxelcraft/core/world/World.java`
- `client/src/main/java/dev/voxelcraft/client/GameClient.java`
- `client/src/main/java/dev/voxelcraft/client/runtime/VulkanClientRuntime.java`
- `client/src/main/java/dev/voxelcraft/client/network/NetworkClient.java`
- `server/src/main/java/dev/voxelcraft/server/net/VoxelcraftServer.java`
