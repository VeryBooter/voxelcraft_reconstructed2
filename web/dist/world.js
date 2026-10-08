export const SIZE = 16, MIN_Y = -12, MAX_Y = 48;
export const BLOCKS = [
  { name: '空气' },
  { name: '草方块', color: '#78994b', tile: 0 },
  { name: '泥土', color: '#926342', tile: 1 },
  { name: '石头', color: '#939890', tile: 2 },
  { name: '橡木', color: '#9b7950', tile: 3 },
  { name: '树叶', color: '#5a813d', tile: 4 },
  { name: '沙子', color: '#d8c491', tile: 5 },
  { name: '砖块', color: '#b16e51', tile: 6 },
];
export const chunkKey = (x, z) => `${x},${z}`;
const blockKey = (x, y, z) => `${x},${y},${z}`;
const index = (x, y, z) => (y - MIN_Y) * SIZE * SIZE + z * SIZE + x;
const mod = (n, d) => ((n % d) + d) % d;
export function hash(x, z, seed) {
  let n = Math.imul(x, 374761393) ^ Math.imul(z, 668265263) ^ seed;
  n = Math.imul(n ^ (n >>> 13), 1274126177);
  return ((n ^ (n >>> 16)) >>> 0) / 4294967296;
}
function noise(x, z, seed) {
  const ix = Math.floor(x), iz = Math.floor(z);
  let tx = x - ix, tz = z - iz;
  tx *= tx * (3 - 2 * tx); tz *= tz * (3 - 2 * tz);
  const a = hash(ix, iz, seed), b = hash(ix + 1, iz, seed);
  const c = hash(ix, iz + 1, seed), d = hash(ix + 1, iz + 1, seed);
  return (a + (b - a) * tx) * (1 - tz) + (c + (d - c) * tx) * tz;
}

export class VoxelWorld {
  constructor(seed = 24301) {
    this.seed = seed | 0;
    this.chunks = new Map();
    this.edits = new Map();
    this.dirty = new Set();
    this.revision = 0;
  }
  height(x, z) {
    return Math.round(5 + noise(x / 48, z / 48, this.seed) * 12
      + noise(x / 15, z / 15, this.seed + 71) * 3);
  }
  ensure(cx, cz) {
    const key = chunkKey(cx, cz);
    if (this.chunks.has(key)) return this.chunks.get(key);
    const data = new Uint8Array((MAX_Y - MIN_Y + 1) * SIZE * SIZE);
    const put = (x, y, z, id, emptyOnly = false) => {
      x -= cx * SIZE; z -= cz * SIZE;
      if (x < 0 || x >= SIZE || z < 0 || z >= SIZE || y < MIN_Y || y > MAX_Y) return;
      const i = index(x, y, z);
      if (!emptyOnly || data[i] === 0) data[i] = id;
    };
    for (let z = 0; z < SIZE; z++) for (let x = 0; x < SIZE; x++) {
      const wx = cx * SIZE + x, wz = cz * SIZE + z, h = this.height(wx, wz);
      const sand = h <= 9;
      for (let y = MIN_Y; y <= h; y++) data[index(x, y, z)] = y === h ? (sand ? 6 : 1) : y >= h - 3 ? (sand ? 6 : 2) : 3;
    }
    // Tree roots are selected in world space, including neighboring chunks, so canopies cross seams.
    for (let gz = Math.floor((cz * SIZE - 3) / 8); gz <= Math.floor((cz * SIZE + SIZE + 2) / 8); gz++) {
      for (let gx = Math.floor((cx * SIZE - 3) / 8); gx <= Math.floor((cx * SIZE + SIZE + 2) / 8); gx++) {
        if (hash(gx, gz, this.seed + 119) > 0.48) continue;
        const x = gx * 8 + 2 + Math.floor(hash(gx, gz, this.seed + 17) * 4);
        const z = gz * 8 + 2 + Math.floor(hash(gx, gz, this.seed + 29) * 4);
        const h = this.height(x, z);
        if (h <= 9 || (Math.abs(x) < 4 && Math.abs(z) < 4)) continue;
        const trunk = 4 + Math.floor(hash(gx, gz, this.seed + 41) * 2);
        for (let y = h + 1; y <= h + trunk; y++) put(x, y, z, 4);
        for (let dy = -2; dy <= 1; dy++) for (let dz = -2; dz <= 2; dz++) for (let dx = -2; dx <= 2; dx++) {
          if (Math.abs(dx) + Math.abs(dz) + Math.max(dy, 0) > 4) continue;
          put(x + dx, h + trunk + dy, z + dz, 5, true);
        }
      }
    }
    for (const [position, id] of this.edits) {
      const [x, y, z] = position.split(',').map(Number);
      if (Math.floor(x / SIZE) === cx && Math.floor(z / SIZE) === cz) put(x, y, z, id);
    }
    this.chunks.set(key, { cx, cz, data });
    for (const [dx, dz] of [[0, 0], [1, 0], [-1, 0], [0, 1], [0, -1]]) this.dirty.add(chunkKey(cx + dx, cz + dz));
    return this.chunks.get(key);
  }
  get(x, y, z) {
    if (y < MIN_Y) return 3;
    if (y > MAX_Y) return 0;
    const chunk = this.chunks.get(chunkKey(Math.floor(x / SIZE), Math.floor(z / SIZE)));
    return chunk ? chunk.data[index(mod(x, SIZE), y, mod(z, SIZE))] : 0;
  }
  set(x, y, z, id) {
    if (![x, y, z, id].every(Number.isInteger) || y <= MIN_Y || y > MAX_Y || id < 0 || id >= BLOCKS.length) return false;
    const cx = Math.floor(x / SIZE), cz = Math.floor(z / SIZE);
    const chunk = this.ensure(cx, cz);
    if (this.get(x, y, z) === id) return false;
    chunk.data[index(mod(x, SIZE), y, mod(z, SIZE))] = id;
    this.edits.set(blockKey(x, y, z), id);
    this.dirty.add(chunkKey(cx, cz));
    if (mod(x, SIZE) === 0) this.dirty.add(chunkKey(cx - 1, cz));
    if (mod(x, SIZE) === SIZE - 1) this.dirty.add(chunkKey(cx + 1, cz));
    if (mod(z, SIZE) === 0) this.dirty.add(chunkKey(cx, cz - 1));
    if (mod(z, SIZE) === SIZE - 1) this.dirty.add(chunkKey(cx, cz + 1));
    this.revision++;
    return true;
  }
  raycast(origin, direction, reach = 6) {
    const cell = origin.map(Math.floor), step = direction.map(v => v >= 0 ? 1 : -1);
    const delta = direction.map(v => v === 0 ? Infinity : Math.abs(1 / v));
    const next = direction.map((v, i) => v === 0 ? Infinity : ((step[i] > 0 ? cell[i] + 1 : cell[i]) - origin[i]) / v);
    let t = 0, normal = [0, 0, 0];
    while (t <= reach) {
      const id = this.get(...cell);
      if (id) return { position: [...cell], normal, id, distance: t };
      const axis = next[0] <= next[1] && next[0] <= next[2] ? 0 : next[1] <= next[2] ? 1 : 2;
      t = next[axis]; next[axis] += delta[axis]; cell[axis] += step[axis];
      normal = [0, 0, 0]; normal[axis] = -step[axis];
    }
    return null;
  }
  toJSON() { return { version: 1, seed: this.seed, edits: [...this.edits] }; }
  static fromJSON(value) {
    if (!value || value.version !== 1 || !Number.isInteger(value.seed) || !Array.isArray(value.edits) || value.edits.length > 100000) throw new Error('存档格式不正确');
    const world = new VoxelWorld(value.seed);
    for (const entry of value.edits) {
      if (!Array.isArray(entry) || entry.length !== 2 || typeof entry[0] !== 'string') throw new Error('存档方块格式不正确');
      const [position, id] = entry, coordinates = position.split(',').map(Number);
      if (coordinates.length !== 3 || !coordinates.every(v => Number.isInteger(v) && Math.abs(v) <= 1000000)
          || coordinates[1] <= MIN_Y || coordinates[1] > MAX_Y || !Number.isInteger(id) || id < 0 || id >= BLOCKS.length) throw new Error('存档方块超出范围');
      world.edits.set(position, id);
    }
    return world;
  }
}

// Right-handed outward faces; these vertices are shared by rendering and mesh validation.
const FACES = [
  { n: [1, 0, 0], c: [[1, 0, 1], [1, 0, 0], [1, 1, 0], [1, 1, 1]], shade: 0.82 },
  { n: [-1, 0, 0], c: [[0, 0, 0], [0, 0, 1], [0, 1, 1], [0, 1, 0]], shade: 0.7 },
  { n: [0, 1, 0], c: [[0, 1, 1], [1, 1, 1], [1, 1, 0], [0, 1, 0]], shade: 1 },
  { n: [0, -1, 0], c: [[0, 0, 0], [1, 0, 0], [1, 0, 1], [0, 0, 1]], shade: 0.5 },
  { n: [0, 0, 1], c: [[0, 0, 1], [1, 0, 1], [1, 1, 1], [0, 1, 1]], shade: 0.9 },
  { n: [0, 0, -1], c: [[1, 0, 0], [0, 0, 0], [0, 1, 0], [1, 1, 0]], shade: 0.76 },
];
export function buildMesh(world, chunk) {
  const positions = [], colors = [], uvs = [], indices = [];
  const uv = [[0, 0], [1, 0], [1, 1], [0, 1]];
  for (let y = MIN_Y; y <= MAX_Y; y++) for (let z = 0; z < SIZE; z++) for (let x = 0; x < SIZE; x++) {
    const id = chunk.data[index(x, y, z)];
    if (!id) continue;
    const wx = chunk.cx * SIZE + x, wz = chunk.cz * SIZE + z;
    for (const face of FACES) {
      if (world.get(wx + face.n[0], y + face.n[1], wz + face.n[2])) continue;
      let tile = BLOCKS[id].tile;
      if (id === 1) tile = face.n[1] === 1 ? 0 : face.n[1] === -1 ? 1 : 7;
      if (id === 4 && face.n[1]) tile = 8;
      const start = positions.length / 3;
      face.c.forEach((c, i) => {
        positions.push(x + c[0], y + c[1], z + c[2]);
        colors.push(face.shade, face.shade, face.shade);
        // A one-pixel inset prevents neighboring atlas tiles from bleeding.
        uvs.push((tile * 16 + 0.5 + uv[i][0] * 15) / 256, (0.5 + uv[i][1] * 15) / 16);
      });
      indices.push(start, start + 1, start + 2, start, start + 2, start + 3);
    }
  }
  return { positions: new Float32Array(positions), colors: new Float32Array(colors), uvs: new Float32Array(uvs), indices: new Uint32Array(indices) };
}

export function intersectsPlayer(position, player) {
  return position[0] < player.x + 0.3 && position[0] + 1 > player.x - 0.3
    && position[1] < player.y + 1.8 && position[1] + 1 > player.y
    && position[2] < player.z + 0.3 && position[2] + 1 > player.z - 0.3;
}

export function playerCollides(world, player) {
  for (let x = Math.floor(player.x - 0.3); x <= Math.floor(player.x + 0.3 - 1e-6); x++)
    for (let z = Math.floor(player.z - 0.3); z <= Math.floor(player.z + 0.3 - 1e-6); z++) {
      world.ensure(Math.floor(x / SIZE), Math.floor(z / SIZE));
      for (let y = Math.floor(player.y); y <= Math.floor(player.y + 1.8 - 1e-6); y++) if (world.get(x, y, z)) return true;
    }
  return false;
}

export function movePlayerAxis(world, player, axis, amount) {
  const steps = Math.max(1, Math.ceil(Math.abs(amount) / 0.15)), step = amount / steps;
  for (let i = 0; i < steps; i++) {
    const previous = player[axis]; player[axis] += step;
    if (playerCollides(world, player)) { player[axis] = previous; return true; }
  }
  return false;
}
