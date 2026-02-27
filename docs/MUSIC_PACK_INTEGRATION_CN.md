# Voxelcraft 音乐包集成说明（基于研究报告）

本仓库已按 `voxelcraft_reconstructed2 体素沙盒游戏完整音乐包方案研究报告` 集成了“自制系统最低成本方案”的可运行骨架：

- 状态机驱动 cue 切换
- 小节边界量化切换（bar-quantized）
- stinger 一次性触发入口
- `music_manifest.properties` 统一管理 BPM/拍号/资源路径

## 已落地模块

- `client/src/main/java/dev/voxelcraft/client/audio/MusicCue.java`
- `client/src/main/java/dev/voxelcraft/client/audio/MusicManifest.java`
- `client/src/main/java/dev/voxelcraft/client/audio/MusicTrack.java`
- `client/src/main/java/dev/voxelcraft/client/audio/MusicDirector.java`
- `client/src/main/resources/music/music_manifest.properties`

## GameClient 接入点

- 每帧调用 `musicDirector.update(...)`，根据游戏上下文选择 cue：
  - `WORMHOLE`（虫洞）
  - `CAVE`（Y 轴阈值以下）
  - `EXPLORE_DAY / EXPLORE_NIGHT`（按 ambient）
- 方块放置成功时触发 `craft_success` stinger。
- `GameClient.close()` 中释放音频资源。

## 与报告对齐关系

- 报告“状态机 + 参数触发”：已实现基础 cue 状态机（后续可加 Town/Combat/Boss 参数）。
- 报告“水平重组”：每个 cue 支持多个 `paths`，运行时随机选择。
- 报告“小节量化切换”：按 `bpm + meter` 计算小节长度并在边界切换。
- 报告“stingers”：支持独立 stinger 轨道并可在事件点触发。
- 报告“48kHz 工作流”：manifest 注释已固定建议。

## 资源放置规范

在 `client/src/main/resources/music/` 下放入音频文件（推荐 WAV 48kHz），并更新：

- `cue.<state>.paths`
- `cue.<state>.bpm`
- `cue.<state>.meter`
- `stinger.<name>.paths`

示例：

```properties
cue.explore_day.paths=/music/loops/explore_day_loop.wav,/music/loops/explore_day_alt.wav
cue.explore_day.bpm=96
cue.explore_day.meter=6/8
```

## Feature Flags

- `-Dvc.music.enabled=true|false`（默认 false）
- `-Dvc.music.gain=0.0..1.0`（默认 0.72）

## 当前限制（后续可扩展）

- 当前只用到 `WORMHOLE/CAVE/EXPLORE_DAY/EXPLORE_NIGHT` 这几类 cue。
- 暂未接入 Combat/Boss/Town 的游戏参数与事件判定。
- 仅使用 JDK 内置 `javax.sound.sampled`，建议优先使用 WAV 资源。
