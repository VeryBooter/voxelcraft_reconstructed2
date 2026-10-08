# voxelcraft_reconstructed2

Voxelcraft reconstructed repository (Java multi-module project: `core` / `client` / `server`).

## Current Status (A → E)

- Stage A: Project skeleton completed, including a self-bootstrapping `gradlew`
- Stage B: `core` world, chunk, generation, registry, and tests completed
- Stage C: Client shell completed, including windowing, input handling, and the main loop
- Stage D: Visible and interactive world completed, with both software and GPU rendering
- Stage E: Server, multiplayer synchronization, and documentation completed

## Key Features

- Singleplayer:
  - WASD movement
  - Jumping
  - Camera/look controls
  - Block breaking
  - Block placement

- Multiplayer:
  - Client-to-server connection
  - Chunk streaming
  - Synchronized block updates

- Rendering:
  - `software`: Java2D software renderer, also used as the default fallback
  - `gpu`: Real-time rendering using LWJGL + GLFW + OpenGL
  - `metal`: Apple unified-memory GPU rendering with CPU/GPU shared vertex and index buffers
  - `accelerated`: GPU-first startup path, recommended for normal use

## Gradle Usage

The repository includes a `gradlew` wrapper that automatically downloads Gradle, so a system-wide Gradle installation is not required.

```bash
./gradlew -v
```

## Common Commands

```bash
# Run core tests
./gradlew :core:test

# Compile client and server
./gradlew :client:classes :server:classes

# Start the server using the default port (25565)
./gradlew :server:runLocal

# Start the server using a custom port
./gradlew :server:runLocal -Pport=25566

# Start the client with GPU rendering and multiplayer
./gradlew :client:runGpu -Pconnect=127.0.0.1:25565

# Start the client with GPU acceleration and a local connection
# Recommended
./gradlew :client:runAcceleratedLocal

# Start the client with software rendering
# Most stable option
./gradlew :client:runSoftware

# Start the native Metal backend on an Apple unified-memory Mac
# Building requires Xcode Command Line Tools and JDK 21
./gradlew :client:runMetal

# Start the client with software rendering and a local connection
# Most stable local multiplayer option
./gradlew :client:runSoftwareLocal

# Run headless validation in environments without graphics support
./gradlew :client:runHeadless
```

## Client Arguments

Available client arguments:

- `--render auto|software|gpu|metal`
- `--connect host:port`

Example:

```bash
./gradlew :client:run --args='--render auto --connect 192.168.1.20:25565'
```

## Troubleshooting

If the project fails to run, troubleshoot in the following order:

```bash
# 1) Verify that the Gradle wrapper starts correctly
./gradlew -v

# 2) Verify that the core tests pass
./gradlew :core:test

# 3) Test the most stable path first, without GPU rendering
./gradlew :server:runLocal

# Open a new terminal:
./gradlew :client:runSoftware -Pconnect=127.0.0.1:25565
```

If `runGpu` fails, first use `runSoftware` to verify that the gameplay and networking pipeline are working correctly. Then troubleshoot the GPU, graphics driver, and OpenGL environment separately.

## Controls

- `WASD`: Move
- `Space`: Jump
- `Mouse` / `Arrow Keys`: Look around
- `LMB`: Break block
- `RMB`: Place block
- `1/2/3/4/5/6/7`: Switch selected block / hotbar slot
- `ESC`: Quit

## Main Code Entry Points

- `core/src/main/java/dev/voxelcraft/core/world/World.java`
- `client/src/main/java/dev/voxelcraft/client/GameClient.java`
- `client/src/main/java/dev/voxelcraft/client/runtime/GpuClientRuntime.java`
- `client/src/main/java/dev/voxelcraft/client/network/NetworkClient.java`
- `server/src/main/java/dev/voxelcraft/server/net/VoxelcraftServer.java`

## Test Files

- `core/src/test/java/dev/voxelcraft/core/registry/RegistryTest.java`
- `core/src/test/java/dev/voxelcraft/core/world/WorldGenerationTest.java`
- `core/src/test/java/dev/voxelcraft/core/world/WorldMutationTest.java`
- `core/src/test/java/dev/voxelcraft/core/world/palette/PaletteTest.java`

## Documentation

- [Metal shared memory and rendering flow (中文)](docs/METAL_SHARED_MEMORY_CN.md)
- `reconstruction/PROJECT_RECONSTRUCTION_FILE_MAP_CN.md`
- `docs/VOXELCRAFT_FLOW_CN.md`
- `docs/VOXELCRAFT_CODE_ANNOTATIONS_CN.md`
- `docs/MUSIC_PACK_INTEGRATION_CN.md`
