# Voxelcraft 关键代码注释索引（完全体）

## Core

- `core/src/main/java/dev/voxelcraft/core/world/World.java`
  - 世界入口，支持按需区块生成、方块更新版本号
- `core/src/main/java/dev/voxelcraft/core/world/Chunk.java`
  - 区块存储与 `forEachNonAir` 遍历
- `core/src/main/java/dev/voxelcraft/core/block/Blocks.java`
  - 方块注册 + `byIdOrAir`（网络反序列化）
- `core/src/main/java/dev/voxelcraft/core/net/Protocol.java`
  - 网络协议号与包类型
- `core/src/main/java/dev/voxelcraft/core/net/PacketIO.java`
  - 二进制字符串编解码

## Client

- `client/src/main/java/dev/voxelcraft/client/GameClient.java`
  - 客户端主逻辑：tick、交互、网络同步、HUD
- `client/src/main/java/dev/voxelcraft/client/network/NetworkClient.java`
  - 客户端联机：收发包、主线程 mutation 队列
- `client/src/main/java/dev/voxelcraft/client/VoxelcraftClientApp.java`
  - 启动配置、软件/Vulkan 模式选择、headless 回退
- `client/src/main/java/dev/voxelcraft/client/runtime/VulkanClientRuntime.java`
  - GLFW + Vulkan 运行时主循环、交换链与绘制提交流程
- `client/src/main/java/dev/voxelcraft/client/render/ChunkRenderSystem.java`
  - 软件渲染实现（也为 Vulkan UI/投影路径提供复用逻辑）

## Server

- `server/src/main/java/dev/voxelcraft/server/net/VoxelcraftServer.java`
  - socket 服务端、握手、区块流送、方块广播
- `server/src/main/java/dev/voxelcraft/server/ServerMain.java`
  - 端口解析、生命周期管理

## 风险与后续优化

1. 当前网络层为最小协议，未做身份认证与加密。
2. Vulkan 路径仍在持续优化中（投影路径/上传路径/同步细节可继续收敛）。
3. 客户端网络同步为轻量策略（方块更新+区块请求），未做完整实体插值。
