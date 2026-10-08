import test from 'node:test';
import assert from 'node:assert/strict';
import { VoxelWorld, SIZE, MIN_Y, MAX_Y, buildMesh, intersectsPlayer, playerCollides, movePlayerAxis } from '../dist/world.js';

test('terrain and cross-boundary trees are independent of generation order', () => {
  const a = new VoxelWorld(123), b = new VoxelWorld(123);
  for (const [x, z] of [[-1, 0], [0, 0], [1, 0]]) a.ensure(x, z);
  for (const [x, z] of [[1, 0], [0, 0], [-1, 0]]) b.ensure(x, z);
  for (const [key, chunk] of a.chunks) assert.deepEqual(chunk.data, b.chunks.get(key).data);
  assert.notDeepEqual(a.ensure(0, 0).data, new VoxelWorld(124).ensure(0, 0).data);
});

test('edits at negative chunk boundaries invalidate both meshes and survive save/load/unload', () => {
  const world = new VoxelWorld(); world.ensure(-1, 0); world.ensure(0, 0); world.dirty.clear();
  assert.equal(world.set(-1, 35, 5, 7), true);
  assert.equal(world.get(-1, 35, 5), 7);
  assert.ok(world.dirty.has('-1,0')); assert.ok(world.dirty.has('0,0'));
  const restored = VoxelWorld.fromJSON(JSON.parse(JSON.stringify(world)));
  restored.ensure(-1, 0); assert.equal(restored.get(-1, 35, 5), 7);
  restored.chunks.clear(); restored.ensure(-1, 0); assert.equal(restored.get(-1, 35, 5), 7);
  restored.set(-1, 35, 5, 0);
  const again = VoxelWorld.fromJSON(restored.toJSON()); again.ensure(-1, 0);
  assert.equal(again.get(-1, 35, 5), 0);
});

test('ray casting returns block, placement normal and respects reach on axis-aligned rays', () => {
  const world = new VoxelWorld(); world.ensure(0, 0);
  world.set(3, 35, 3, 3);
  assert.deepEqual(world.raycast([3.5, 35.5, 6.5], [0, 0, -1]), { position: [3, 35, 3], normal: [0, 0, 1], id: 3, distance: 2.5 });
  assert.equal(world.raycast([3.5, 35.5, 6.5], [0, 0, -1], 2), null);
  assert.equal(world.raycast([3.5, 35.5, 6.5], [0, 1, 0]), null);
  assert.deepEqual(world.raycast([0.5, 35.5, 3.5], [1, 0, 0]).normal, [-1, 0, 0]);
});

test('meshing removes shared internal faces and all triangles face outward', () => {
  const world = new VoxelWorld();
  const chunk = world.ensure(0, 0); chunk.data.fill(0);
  world.set(3, 30, 3, 3);
  let mesh = buildMesh(world, chunk);
  assert.equal(mesh.indices.length, 36);
  for (let i = 0; i < mesh.indices.length; i += 3) {
    const points = [...mesh.indices.slice(i, i + 3)].map(j => [...mesh.positions.slice(j * 3, j * 3 + 3)]);
    const [a, b, c] = points;
    const u = b.map((v, k) => v - a[k]), v = c.map((n, k) => n - a[k]);
    const normal = [u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]];
    const centerOffset = a.map((n, k) => (n + b[k] + c[k]) / 3 - [3.5, 30.5, 3.5][k]);
    assert.ok(normal.reduce((sum, n, k) => sum + n * centerOffset[k], 0) > 0);
  }
  world.set(4, 30, 3, 3); mesh = buildMesh(world, chunk);
  assert.equal(mesh.indices.length, 60);
  assert.ok([...mesh.uvs].every(v => v > 0 && v < 1));
});

test('adjacent chunks hide boundary faces after neighbor data arrives', () => {
  const world = new VoxelWorld(), left = world.ensure(0, 0), right = world.ensure(1, 0);
  left.data.fill(0); right.data.fill(0);
  world.set(SIZE - 1, 30, 4, 3); world.set(SIZE, 30, 4, 3);
  assert.equal(buildMesh(world, left).indices.length, 30);
  assert.equal(buildMesh(world, right).indices.length, 30);
});

test('placing a block cannot intersect the player body', () => {
  const player = { x: 0.5, y: 10, z: 0.5 };
  assert.equal(intersectsPlayer([0, 10, 0], player), true);
  assert.equal(intersectsPlayer([0, 11, 0], player), true);
  assert.equal(intersectsPlayer([0, 12, 0], player), false);
  assert.equal(intersectsPlayer([1, 10, 0], player), false);
});

test('movement stops at floors, ceilings and walls even for a large step', () => {
  const world = new VoxelWorld(), chunk = world.ensure(0, 0); chunk.data.fill(0);
  for (let x = 0; x < 8; x++) for (let z = 0; z < 8; z++) world.set(x, 25, z, 3);
  world.set(4, 26, 3, 3); world.set(4, 27, 3, 3); world.set(2, 29, 3, 3);
  const player = { x: 2.5, y: 26.01, z: 3.5 };
  assert.equal(playerCollides(world, player), false);
  assert.equal(movePlayerAxis(world, player, 'y', -8), true);
  assert.ok(player.y >= 26);
  assert.equal(movePlayerAxis(world, player, 'y', 8), true);
  assert.ok(player.y + 1.8 <= 29);
  player.y = 26.01;
  assert.equal(movePlayerAxis(world, player, 'x', 8), true);
  assert.ok(player.x + 0.3 <= 4);
  assert.equal(movePlayerAxis(world, player, 'z', 1), false);
  assert.equal(playerCollides(world, player), false);
});

test('invalid imports and out-of-range writes are rejected', () => {
  for (const value of [null, {}, { version: 2, seed: 1, edits: [] },
    { version: 1, seed: 1, edits: [['1,2,3', 99]] },
    { version: 1, seed: 1, edits: [['0,999,0', 3]] },
    { version: 1, seed: 1, edits: [['0.5,20,0', 3]] }]) assert.throws(() => VoxelWorld.fromJSON(value));
  const world = new VoxelWorld();
  assert.equal(world.set(0, MIN_Y, 0, 0), false);
  assert.equal(world.set(0, MAX_Y + 1, 0, 3), false);
  assert.equal(world.set(0.5, 20, 0, 3), false);
});
