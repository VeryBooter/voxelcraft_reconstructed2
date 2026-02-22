# Voxelcraft 重构文件总表（给新仓库/新目录迁移用）

本文目标：
- 告诉你“重构整个项目”最少要带哪些文件。
- 给出按阶段重构的顺序，避免一次性搬空后难排错。

---

## 0. 强烈建议先复制的最小可运行集合（MVP）

如果你想先在新地方快速跑起来，再逐步补功能，先带这批：

### 工程骨架
- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle.properties`
- `gradlew`
- `gradlew.bat`

### core 最小运行链路
- `core/build.gradle.kts`
- `core/src/main/java/dev/voxelcraft/core/Game.java`
- `core/src/main/java/dev/voxelcraft/core/time/Stopwatch.java`
- `core/src/main/java/dev/voxelcraft/core/util/ResourceLocation.java`
- `core/src/main/java/dev/voxelcraft/core/registry/Registry.java`
- `core/src/main/java/dev/voxelcraft/core/registry/Registries.java`
- `core/src/main/java/dev/voxelcraft/core/state/*.java`
- `core/src/main/java/dev/voxelcraft/core/block/*.java`
- `core/src/main/java/dev/voxelcraft/core/world/*.java`
- `core/src/main/java/dev/voxelcraft/core/world/gen/*.java`
- `core/src/main/java/dev/voxelcraft/core/world/palette/*.java`

### client 最小运行链路
- `client/build.gradle.kts`
- `client/src/main/java/dev/voxelcraft/client/ClientMain.java`
- `client/src/main/java/dev/voxelcraft/client/VoxelcraftClientApp.java`
- `client/src/main/java/dev/voxelcraft/client/GameClient.java`
- `client/src/main/java/dev/voxelcraft/client/platform/Window.java`
- `client/src/main/java/dev/voxelcraft/client/player/PlayerController.java`
- `client/src/main/java/dev/voxelcraft/client/physics/AABB.java`
- `client/src/main/java/dev/voxelcraft/client/world/*.java`
- `client/src/main/java/dev/voxelcraft/client/light/LightEngine.java`
- `client/src/main/java/dev/voxelcraft/client/render/*.java`
- `client/src/main/resources/textures/atlas.png`（即使你先走纯 RGB，也建议带上）

### server（可最后补）
- `server/build.gradle.kts`
- `server/src/main/java/dev/voxelcraft/server/ServerMain.java`

---

## 1. 按重构阶段的推荐顺序

### 阶段 A：工程与依赖先通
1. 搬运根目录 5 个构建文件（见上）。
2. 搬 `core/build.gradle.kts`、`client/build.gradle.kts`、`server/build.gradle.kts`。
3. 先只编译 `core`（不跑 client）。

### 阶段 B：核心世界逻辑（core）
1. `state` + `registry` + `block`（确保 `Blocks` 能注册并 freeze）。
2. `world/palette` + `world`（Chunk/Section/World/ChunkManager）。
3. `world/gen`（地形生成）。
4. 跑 `core/src/test/java` 三个测试文件。

### 阶段 C：客户端外壳（窗口 + 主循环）
1. `ClientMain`、`VoxelcraftClientApp`、`Window`。
2. 先确认能开窗、ESC 能退出。

### 阶段 D：可见世界（关键）
1. `render` 全目录（尤其 `ChunkRenderSystem`、`ChunkMesher`、`ShaderProgram`、`Frustum`、`Texture2D`）。
2. `GameClient`（流送、激活、上传、draw 调度）。
3. `player` + `physics` + `world`（射线交互）。
4. `light/LightEngine`（边界修复版）。

### 阶段 E：server 和文档
1. 搬 server 占位入口。
2. 搬 `README.md` 与 docs。

---

## 2. 你之前出现蓝天屏时，重构后必须优先核对的文件

这几处是“看不见地形”的高风险点：

1. `client/src/main/java/dev/voxelcraft/client/render/ChunkRenderSystem.java`
- 是否启用了纯 RGB 兜底开关（`USE_TEXTURE_ATLAS`）。
- atlas 采样 Y 方向是否正确（`atlasY = uTilesY - 1 - ty`）。

2. `client/src/main/java/dev/voxelcraft/client/render/Frustum.java`
- 是否使用 JOML `FrustumIntersection`，避免手写裁剪误判。

3. `client/src/main/java/dev/voxelcraft/client/VoxelcraftClientApp.java`
- 是否临时关闭了背面剔除（`glDisable(GL_CULL_FACE)`）做可见性兜底。

4. `client/src/main/java/dev/voxelcraft/client/GameClient.java`
- 初始出生高度和俯角是否合理（避免开局只看天）。

5. `client/src/main/java/dev/voxelcraft/client/light/LightEngine.java`
- 是否包含 `isWithinWorldY` 边界保护，避免 Y 越界崩溃。

---

## 3. “全量重构”时建议一并复制的文档

- `README.md`
- `docs/VOXELCRAFT_FLOW_CN.md`
- `docs/VOXELCRAFT_CODE_ANNOTATIONS_CN.md`

用途：
- 让你在新仓库里快速对齐流程、模块职责、关键风险点。

---

## 4. 重构完成后的验收清单（最短）

1. `./gradlew :core:test`
2. `./gradlew :client:run`
3. 进入窗口后，确认：
- 不只是蓝天（能看到地形块面）。
- 移动/视角正常。
- LMB/RMB 可破坏/放置。

---

## 5. 一句话结论

如果你要“重构整个项目”，最安全方案就是：
- **构建文件 + core 全目录 + client 全目录 + atlas 资源 + server 占位入口 + docs** 全带走，
- 再按上面的 A→E 阶段逐步验证。
