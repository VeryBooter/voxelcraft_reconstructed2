# W-Axis Slice + Wormhole Skeleton (Task 2/2)

## Goal
- Rendering stays 3D only.
- Normal world state uses discrete integer `w` slices only (`w ∈ Z`).
- Non-integer `w` exists only as `wPhase` while inside a wormhole interior.

## Core Model
- `WorldStack` manages `Map<Integer, World>` (slice index -> 3D world instance).
- `Game` owns:
  - `WorldStack worldStack`
  - `int activeW`
- `Game.world()` returns the active slice world to preserve existing callers.

## Slice Seed Mixing
- Each slice world seed is derived from `(baseSeed, w)` using a deterministic mixing function.
- `w=0` keeps `baseSeed` unchanged to preserve current default behavior.
- Other slices use a stable integer mix/hash, so:
  - same `baseSeed + w` => same terrain
  - different `w` => different terrain
  - no chunk generation order dependency

## Wormhole Runtime State (Client Side)
- Feature flag: `vc.wormhole.enabled` (legacy alias `voxelcraft.wormhole.enabled`)
- Default: `false` (no gameplay changes)
- Enter wormhole:
  - record `entryW` (integer active slice)
  - record `entryAnchorPos` (`x/y/z`)
  - initialize `wPhase = entryW`
  - teleport player into a small wormhole room (still rendered as normal 3D)
- Inside wormhole:
  - player moves only in normal `x/y/z`
  - no direct `w` controls
  - `wPhase` advances over time via a constant “flow/current”
- Exit wormhole:
  - compute `wOut = snap(wPhase)` with hysteresis
  - `GameClient.switchSlice(wOut)`
  - teleport player back near `entryAnchorPos` with safe landing search

## Snap Rule (Hysteresis)
- Let `lower = floor(wPhase)`, `frac = wPhase - lower`
- thresholds:
  - `frac <= 0.4` => `lower`
  - `frac >= 0.6` => `lower + 1`
  - `0.4 < frac < 0.6` => keep previous reference slice (if adjacent), otherwise round
- Purpose: avoid jitter if exiting repeatedly near the midpoint

## Slice Switch Cache Cleanup
- `GameClient.switchSlice(int)` rebuilds:
  - `ClientWorldView`
  - `ChunkRenderSystem`
  - `LightEngine`
- Forces chunk requests/regeneration for the new active slice.
- `GpuChunkRenderer` additionally tracks slice changes and clears GPU chunk/VBO caches.
- A `sliceEpoch` is attached to queued GPU mesh uploads so stale async results from the previous slice are dropped.

## Scope / Non-Goals (this task)
- No 4D rendering
- No multiplayer protocol changes for `w`
- No complex wormhole graph/branching
- Minimal wormhole interior (room/tunnel) for validating `wPhase -> snap -> slice switch`
