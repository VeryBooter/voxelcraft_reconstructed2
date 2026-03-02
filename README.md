# voxelcraft_reconstructed2

Voxelcraft 重构仓库（Java 多模块：`core` / `client` / `server`）。

## 当前状态（A→E）

- 阶段 A：工程骨架完成（含可自举 `gradlew`）
- 阶段 B：`core` 世界/区块/生成/注册表 + 测试完成
- 阶段 C：客户端外壳（窗口、输入、主循环）完成
- 阶段 D：可见世界 + 可交互完成（软件渲染 + GPU 渲染）
- 阶段 E：服务端 + 联机同步 + 文档完成

## 关键能力

- 单机：WASD/跳跃/视角/破坏/放置
- 联机：客户端连接服务端，支持区块流送与方块更新同步
- 渲染：
  - `software`：Java2D 软件渲染（默认可回退）
  - `gpu`：LWJGL + GLFW + OpenGL 实时渲染
  - `accelerated`：GPU 优先启动路径（推荐）

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

# 启动客户端（GPU + 联机）
./gradlew :client:runGpu -Pconnect=127.0.0.1:25565

# 启动客户端（GPU 加速本地直连，推荐）
./gradlew :client:runAcceleratedLocal

# 启动客户端（软件渲染，最稳）
./gradlew :client:runSoftware

# 启动客户端（本地直连，最稳）
./gradlew :client:runSoftwareLocal

# 无图形环境验证
./gradlew :client:runHeadless
```

## 客户端参数

- `--render auto|software|gpu`
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

如果 `runGpu` 失败，先用 `runSoftware` 验证玩法与联机链路，再排查显卡/驱动/OpenGL 环境。

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
- `client/src/main/java/dev/voxelcraft/client/runtime/GpuClientRuntime.java`
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
- Stage D: visible and interactive world completed (software + GPU render)
- Stage E: server + multiplayer sync + docs completed

### Key Features

- Singleplayer: move/jump/look/break/place
- Multiplayer: client-server chunk streaming + block update sync
- Rendering:
  - `software`: Java2D fallback renderer
  - `gpu`: LWJGL + GLFW + OpenGL renderer
  - `accelerated`: GPU-first startup path

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

# client (GPU + multiplayer)
./gradlew :client:runGpu -Pconnect=127.0.0.1:25565

# client (GPU accelerated local connect)
./gradlew :client:runAcceleratedLocal

# client (software, stable)
./gradlew :client:runSoftware

# client (software + local connect)
./gradlew :client:runSoftwareLocal

# headless validation
./gradlew :client:runHeadless
```

### Client Args

- `--render auto|software|gpu`
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

If `runGpu` fails, verify gameplay/network on `runSoftware` first, then debug GPU/driver/OpenGL.

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
- `client/src/main/java/dev/voxelcraft/client/runtime/GpuClientRuntime.java`
- `client/src/main/java/dev/voxelcraft/client/network/NetworkClient.java`
- `server/src/main/java/dev/voxelcraft/server/net/VoxelcraftServer.java`
