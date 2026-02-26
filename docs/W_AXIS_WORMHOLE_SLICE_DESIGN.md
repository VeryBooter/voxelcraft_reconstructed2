# W-Axis Slice + Wormhole River (Minimal Playable Skeleton)

## Core Rules
- Rendering remains normal 3D only (no 4D visualization).
- Normal world slices are discrete: `w ∈ Z` only.
- Non-integer `w` exists only as `wPhase` while the player is inside the wormhole slice.
- Player never directly controls `w`; corridor flow controls `dw/dt`.

## WorldStack / Slice Architecture
- `WorldStack` manages cached `World` instances keyed by integer `w`.
- `Game` owns:
  - `WorldStack worldStack`
  - `int activeW`
- `Game.world()` returns the active slice so existing rendering/world-view APIs stay mostly unchanged.
- `Game.switchW(int)` only changes the active slice (no teleport logic inside `Game`).

## Slice Seed Mixing
- Ordinary slices use `mixSeed(baseSeed, w)` + `FlatWorldGenerator`.
- Deterministic and order-independent:
  - same `(baseSeed, w)` => same terrain
  - different `w` => different terrain
  - no cross-chunk seam or generation-order coupling
- Wormhole uses a dedicated integer slice id:
  - `WorldStack.W_WORMHOLE = -1_000_000_000`
  - separate seed salt + `WormholeWorldGenerator`

## Wormhole Slice (3D Interior, Not 4D Render)
- A dedicated wormhole slice world is generated near origin at `Y≈64`.
- Geometry is a fixed stone shell:
  - center room `|x|<=3 && |z|<=3`
  - four corridors extending to ~32 blocks
  - stone floor/walls/ceiling, hollow air interior
- Player can move only in normal `x/y/z` inside this slice.

## Feature Flag / Controls
- Feature flag: `vc.wormhole.enabled` (legacy alias supported: `voxelcraft.wormhole.enabled`)
- Default: `false` (gameplay unchanged)
- Toggle key: `P`
- Singleplayer-only for now: slice switching is blocked while network-connected

## Wormhole Runtime State (Client)
- `entryW` (integer slice when entering)
- `entryAnchorPos` (`x/y/z`) for returning
- `wPhase` (`double`, continuous only inside wormhole)
- `wCandidate` (`int`, hysteresis snap target)
- `dwPerSecond` (`double`, current corridor flow)

## Corridor-Driven W Drift (Compass Branches)
- North corridor (`z < -4 && |x|<=2`): `dw/dt = +0.8`
- East corridor (`x > 4 && |z|<=2`): `dw/dt = +2.0`
- South corridor (`z > 4 && |x|<=2`): `dw/dt = -0.8`
- West corridor (`x < -4 && |z|<=2`): `dw/dt = -2.0`
- Center room / non-corridor area: `dw/dt = 0.0` (still water)

## Snap Rule (Hysteresis)
- `HYST = 0.60`
- While inside wormhole, update `wCandidate` incrementally:
  - `while (wPhase > wCandidate + HYST) wCandidate++`
  - `while (wPhase < wCandidate - HYST) wCandidate--`
- Exit uses `wOut = wCandidate`
- This avoids jitter around half-integer boundaries.

## Enter / Exit Flow
- Enter (`P`, when not already inside):
  - record `entryW` and anchor `x/y/z`
  - set `wPhase = entryW`, `wCandidate = entryW`
  - switch to `W_WORMHOLE`
  - teleport to wormhole center room
- Exit (`P`, only allowed in center room):
  - require `|x|<=3 && |z|<=3`
  - `wOut = wCandidate`
  - switch to slice `wOut`
  - teleport back to anchor with safe-standing search (upward + nearby fallback)

## Slice-Switch Cache Cleanup
- `GameClient.switchSlice(int)` rebuilds client-side world/render helpers:
  - `ClientWorldView`
  - `ChunkRenderSystem`
  - `LightEngine`
- Resets chunk request cache and forces fresh requests/generation.

## GPU Anti-Stale-Mesh Protection
- `GpuChunkRenderer` detects world changes via `worldView.world().seed()`.
- On world switch:
  - clears GPU chunk/VBO state
  - clears upload queue and releases queued buffers
  - clears `inFlightVersion` / visibility caches
  - increments `worldEpoch`
- Mesh jobs capture submit-time `worldEpoch`; mismatched epochs drop mesh results before enqueue/upload.
- Goal: no old-slice mesh uploads after switching to a new slice.

## Non-Goals (Current Scope)
- No multiplayer `w` synchronization protocol
- No 4D rendering
- No complex wormhole graph/branching topology beyond the fixed cross corridor
