# Voxelcraft 运行流程（重构完全体）

## 1. 启动流程

1. `ClientMain` 解析参数：
   - `--render auto|software|vulkan`（兼容接受 `gpu` 并映射到 `vulkan`）
   - `--connect host:port`
2. `VoxelcraftClientApp` 初始化 `GameClient`
3. 若带 `--connect`，创建 `NetworkClient` 并附加到 `GameClient`
4. 按渲染模式启动：
   - 软件模式：`Window` + Java2D
   - Vulkan 模式：`VulkanClientRuntime` + GLFW + Vulkan

## 2. Tick 流程（客户端）

每帧执行：

1. 网络收包并应用到本地世界（主线程 drain）
2. 玩家输入 -> 视角/移动/碰撞
3. 根据玩家区块位置请求周围区块
4. 视线射线检测（命中方块）
5. LMB/RMB 破坏/放置，并通过网络发送方块更新
6. 光照时间因子更新
7. `game.tick()`

## 3. 渲染流程

### 软件渲染

1. `ChunkMesher` 提取可见面
2. `Frustum` 过滤
3. 投影到屏幕并深度排序
4. 绘制 HUD 和准星

### Vulkan 渲染

1. `ChunkMesher` 构建网格/快照数据
2. Vulkan 运行时建立交换链、渲染通道和图形管线
3. 默认直接面片投影绘制（含深度测试 + 透明混合）
4. UI 叠加（准星/HUD/选中框）合成到最终帧

## 4. 服务端流程

1. `ServerMain` 启动 `VoxelcraftServer`
2. 接收客户端连接（socket）
3. 客户端 `HELLO` 协议握手
4. 发送初始区块半径数据
5. 处理客户端包：
   - 玩家状态
   - 方块修改
   - 区块请求
6. 方块修改后广播 `BLOCK_UPDATE`

## 5. 协议包

定义在 `core/src/main/java/dev/voxelcraft/core/net/Protocol.java`：

- C2S: `HELLO` / `PLAYER_STATE` / `BLOCK_SET` / `REQUEST_CHUNKS`
- S2C: `WELCOME` / `CHUNK_DATA` / `BLOCK_UPDATE`
