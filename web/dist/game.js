import * as THREE from './vendor/three.module.min.js';
import { VoxelWorld, SIZE, MIN_Y, MAX_Y, BLOCKS, chunkKey, buildMesh, hash, intersectsPlayer, playerCollides, movePlayerAxis } from './world.js';
import { touchControlsEnabled, prefersTouchStart, stickVector, LookGesture } from './input.js';

const $ = id => document.getElementById(id);
const STORAGE_KEY = 'voxelcraft.browser.v1';
const canvas = $('world'), menu = $('menu');
const coarsePointer = matchMedia('(any-pointer: coarse)');
const touchAvailable = () => navigator.maxTouchPoints > 0 || coarsePointer.matches;
let inputMode = 'auto', touchActive = touchAvailable();
document.body.classList.toggle('touch', touchActive);
let world = new VoxelWorld(), savedPlayer = null, selected = 1, distance = touchActive ? 3 : 4;
let running = false, ready = false, grounded = false, dusk = false, lastSavedRevision = -1;
let yaw = 0, pitch = 0, velocityY = 0, player = { x: 0.5, y: 20, z: 0.5 };
let renderer, scene, camera, material, outline, sun, ambient;
let toastTimer, saveTimer, target = null, chunksRendered = 0, frames = 0, fpsTime = performance.now();
const keys = new Set(), meshes = new Map(), direction = new THREE.Vector3();
const stick = { x: 0, y: 0 }, look = new LookGesture(), holdTimers = new Set();
let stickPointer = null;
const desktopHelp = $('control-help').innerHTML;
const introPosition = new THREE.Vector3(24, 31, 34), introLook = new THREE.Vector3(-8, 12, -12);

function message(text) {
  $('toast').textContent = text; $('toast').hidden = false;
  clearTimeout(toastTimer); toastTimer = setTimeout(() => { $('toast').hidden = true; }, 3500);
}
function showError(text) { $('error').textContent = text; $('error').hidden = false; }
function validPlayer(p) {
  return p && ['x', 'y', 'z', 'yaw', 'pitch'].every(k => Number.isFinite(p[k]))
    && Math.abs(p.x) < 100000 && Math.abs(p.z) < 100000 && p.y >= MIN_Y && p.y < MAX_Y + 20;
}
function save() {
  if (!ready) return;
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...world.toJSON(), player: { ...player, yaw, pitch } }));
    lastSavedRevision = world.revision;
    $('save-status').textContent = '已自动保存';
  } catch { $('save-status').textContent = '无法自动保存 · 请导出备份'; }
}
function scheduleSave() {
  if (lastSavedRevision === world.revision) return;
  $('save-status').textContent = '正在保存…';
  clearTimeout(saveTimer); saveTimer = setTimeout(save, 600);
}
function respawn() {
  player = { x: 0.5, y: world.height(0, 0) + 1.01, z: 0.5 };
  world.ensure(0, 0); velocityY = 0; grounded = false;
}
function loadInitial() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const data = JSON.parse(raw); world = VoxelWorld.fromJSON(data);
      if (validPlayer(data.player)) savedPlayer = data.player;
    }
  } catch { message('未能读取本地存档，已创建新世界。'); }
  respawn();
  if (savedPlayer) {
    player = { x: savedPlayer.x, y: savedPlayer.y, z: savedPlayer.z };
    yaw = savedPlayer.yaw; pitch = Math.max(-1.5, Math.min(1.5, savedPlayer.pitch));
    world.ensure(Math.floor(player.x / SIZE), Math.floor(player.z / SIZE));
  }
  while (playerCollides(world, player) && player.y < MAX_Y + 1) player.y += 1;
  $('seed-label').textContent = `WORLD SEED / ${world.seed}`;
}
function createAtlas() {
  const atlas = document.createElement('canvas'); atlas.width = 256; atlas.height = 16;
  const ctx = atlas.getContext('2d');
  const bases = ['#839f52', '#936c4c', '#969b94', '#9c7950', '#53783c', '#d9c792', '#ac6b51', '#936c4c', '#bc9762'];
  for (let tile = 0; tile < bases.length; tile++) {
    ctx.fillStyle = bases[tile]; ctx.fillRect(tile * 16, 0, 16, 16);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const n = hash(x + tile * 31, y, 51);
      ctx.fillStyle = n < 0.5 ? `rgba(30,35,20,${(0.5 - n) * 0.24})` : `rgba(255,250,218,${(n - 0.5) * 0.3})`;
      ctx.fillRect(tile * 16 + x, y, 1, 1);
    }
    if (tile === 3) {
      ctx.fillStyle = '#574b344f';
      for (let x = 2; x < 16; x += 4) ctx.fillRect(tile * 16 + x, 0, 1, 16);
    }
    if (tile === 6) {
      ctx.fillStyle = '#d7bc91';
      for (let y = 0; y < 16; y += 5) {
        ctx.fillRect(tile * 16, y, 16, 1);
        for (let x = (y % 2) * 4; x < 16; x += 8) ctx.fillRect(tile * 16 + x, y, 1, 5);
      }
    }
    if (tile === 7) {
      for (let x = 0; x < 16; x++) {
        ctx.fillStyle = x % 3 ? '#78954a' : '#668342';
        ctx.fillRect(tile * 16 + x, 0, 1, 3 + Math.floor(hash(x, 7, 21) * 4));
      }
    }
    if (tile === 8) {
      ctx.strokeStyle = '#866440';
      for (let inset = 2; inset < 8; inset += 3) ctx.strokeRect(tile * 16 + inset, inset, 16 - inset * 2, 16 - inset * 2);
    }
  }
  const texture = new THREE.CanvasTexture(atlas);
  texture.magFilter = THREE.NearestFilter; texture.minFilter = THREE.NearestFilter;
  texture.generateMipmaps = false; texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}
function setupGraphics() {
  renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' });
  renderer.setPixelRatio(Math.min(devicePixelRatio, touchActive ? 1.5 : 2));
  renderer.setSize(innerWidth, innerHeight);
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  scene = new THREE.Scene(); scene.background = new THREE.Color('#b8d1d0');
  scene.fog = new THREE.Fog('#b8d1d0', 40, 85);
  camera = new THREE.PerspectiveCamera(70, innerWidth / innerHeight, 0.08, 180);
  camera.rotation.order = 'YXZ';
  material = new THREE.MeshLambertMaterial({ map: createAtlas(), vertexColors: true });
  ambient = new THREE.HemisphereLight('#fff8da', '#798065', 2.4); scene.add(ambient);
  sun = new THREE.DirectionalLight('#fff1cb', 2.4); sun.position.set(-30, 65, 30); scene.add(sun);
  const outlineGeometry = new THREE.EdgesGeometry(new THREE.BoxGeometry(1.006, 1.006, 1.006));
  outline = new THREE.LineSegments(outlineGeometry, new THREE.LineBasicMaterial({ color: '#fff3b5', depthTest: true }));
  outline.visible = false; scene.add(outline);
  // A few broad voxel clouds make the horizon readable without any network assets.
  const cloudMaterial = new THREE.MeshBasicMaterial({ color: '#f1f0de', transparent: true, opacity: 0.75 });
  for (let i = 0; i < 12; i++) {
    const cloud = new THREE.Mesh(new THREE.BoxGeometry(12 + hash(i, 1, 5) * 13, 1.8, 5 + hash(i, 2, 5) * 7), cloudMaterial);
    cloud.position.set((hash(i, 3, 5) - 0.5) * 180, 41 + hash(i, 4, 5) * 8, (hash(i, 5, 5) - 0.5) * 180);
    scene.add(cloud);
  }
}
function replaceMesh(key) {
  const chunk = world.chunks.get(key);
  if (!chunk) { world.dirty.delete(key); return; }
  const data = buildMesh(world, chunk);
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.BufferAttribute(data.positions, 3));
  geometry.setAttribute('color', new THREE.BufferAttribute(data.colors, 3));
  geometry.setAttribute('uv', new THREE.BufferAttribute(data.uvs, 2));
  geometry.setIndex(new THREE.BufferAttribute(data.indices, 1));
  geometry.computeVertexNormals(); geometry.computeBoundingSphere();
  const previous = meshes.get(key);
  if (previous) { scene.remove(previous); previous.geometry.dispose(); }
  const mesh = new THREE.Mesh(geometry, material);
  mesh.position.set(chunk.cx * SIZE, 0, chunk.cz * SIZE);
  meshes.set(key, mesh); scene.add(mesh); world.dirty.delete(key);
}
function streamWorld() {
  const cx = Math.floor(player.x / SIZE), cz = Math.floor(player.z / SIZE);
  const wanted = [];
  for (let dz = -distance; dz <= distance; dz++) for (let dx = -distance; dx <= distance; dx++) {
    if (dx * dx + dz * dz > distance * distance + 1) continue;
    wanted.push([cx + dx, cz + dz, dx * dx + dz * dz]);
  }
  wanted.sort((a, b) => a[2] - b[2]);
  const start = performance.now(); let generated = 0, rebuilt = 0;
  for (const [x, z] of wanted) {
    const key = chunkKey(x, z);
    if (!world.chunks.has(key)) {
      if (generated >= 2 || performance.now() - start > 7) break;
      world.ensure(x, z); generated++;
    }
    if (world.dirty.has(key) && rebuilt < 2) { replaceMesh(key); rebuilt++; }
    if (performance.now() - start > 9) break;
  }
  for (const [key, chunk] of world.chunks) {
    if (Math.abs(chunk.cx - cx) <= distance + 2 && Math.abs(chunk.cz - cz) <= distance + 2) continue;
    const mesh = meshes.get(key);
    if (mesh) { scene.remove(mesh); mesh.geometry.dispose(); meshes.delete(key); }
    world.chunks.delete(key); world.dirty.delete(key);
    // Neighbors may need their formerly hidden boundary faces exposed after unloading.
    for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) world.dirty.add(chunkKey(chunk.cx + dx, chunk.cz + dz));
  }
  // Keys outside the active area have no data and need no work until loaded again.
  for (const key of world.dirty) if (!world.chunks.has(key)) world.dirty.delete(key);
  if (!ready && meshes.size >= 9) {
    ready = true; $('play').disabled = false; $('play').innerHTML = '进入世界 <span>↗</span>';
    $('save-status').textContent = '世界已就绪';
    save();
  }
  const visibleDistance = distance * SIZE;
  scene.fog.near = visibleDistance * 0.55; scene.fog.far = visibleDistance * 1.12;
  chunksRendered = renderer.info.render.calls;
}
function moveAxis(axis, amount) {
  if (movePlayerAxis(world, player, axis, amount) && axis === 'y') {
    if (amount < 0) grounded = true;
    velocityY = 0;
  }
}
function updatePlayer(dt) {
  if (keys.has('ArrowLeft')) yaw += dt * 1.7;
  if (keys.has('ArrowRight')) yaw -= dt * 1.7;
  if (keys.has('ArrowUp')) pitch = Math.min(1.5, pitch + dt * 1.3);
  if (keys.has('ArrowDown')) pitch = Math.max(-1.5, pitch - dt * 1.3);
  let forward = Number(keys.has('KeyW')) - Number(keys.has('KeyS')) - stick.y;
  let side = Number(keys.has('KeyD')) - Number(keys.has('KeyA')) + stick.x;
  const length = Math.hypot(forward, side);
  if (length > 1) { forward /= length; side /= length; }
  const speed = keys.has('ShiftLeft') || keys.has('ShiftRight') ? 7 : 4.3;
  moveAxis('x', (-Math.sin(yaw) * forward + Math.cos(yaw) * side) * speed * dt);
  moveAxis('z', (-Math.cos(yaw) * forward - Math.sin(yaw) * side) * speed * dt);
  if (keys.has('Space') && grounded) { velocityY = 8.2; grounded = false; }
  velocityY = Math.max(-35, velocityY - 24 * dt); grounded = false;
  moveAxis('y', velocityY * dt);
  if (player.y < MIN_Y - 4) respawn();
  camera.position.set(player.x, player.y + 1.62, player.z);
  camera.rotation.set(pitch, yaw, 0);
  camera.getWorldDirection(direction);
  target = world.raycast(camera.position.toArray(), direction.toArray(), 6);
  outline.visible = !!target;
  if (target) outline.position.set(...target.position.map(v => v + 0.5));
  $('target').hidden = !target;
  if (target) $('target').textContent = BLOCKS[target.id].name;
}
function editBlock(place) {
  if (!running || !target) return;
  const position = place ? target.position.map((v, i) => v + target.normal[i]) : target.position;
  if (place && (!target.normal.some(Boolean) || intersectsPlayer(position, player))) { message('这里会挡住你，换个位置试试。'); return; }
  if (!world.set(...position, place ? selected : 0)) { message('已到达世界的高度边界。'); return; }
  scheduleSave();
}
function selectBlock(id) {
  selected = id; $('selected-name').textContent = BLOCKS[id].name;
  document.querySelectorAll('.slot').forEach((slot, i) => {
    slot.classList.toggle('active', i + 1 === id); slot.setAttribute('aria-pressed', String(i + 1 === id));
  });
}
function makeHotbar() {
  BLOCKS.slice(1).forEach((block, i) => {
    const button = document.createElement('button'); button.className = 'slot';
    button.title = `${i + 1} · ${block.name}`; button.setAttribute('aria-label', `选择${block.name}`);
    const number = document.createElement('kbd'); number.textContent = i + 1;
    const icon = document.createElement('span'); icon.className = 'block-icon'; icon.style.setProperty('--block', block.color);
    button.append(number, icon); button.addEventListener('click', () => selectBlock(i + 1)); $('hotbar').append(button);
  });
  selectBlock(selected);
}
function setRunning(value) {
  running = value; keys.clear();
  resetTouchInput();
  menu.hidden = value; document.body.classList.toggle('playing', value);
  $('play').innerHTML = '继续探索 <span>↗</span>';
  if (!value) { outline.visible = false; $('target').hidden = true; save(); }
}
async function start(event) {
  if (!ready) return;
  $('error').hidden = true;
  if (prefersTouchStart(inputMode, touchActive, event?.pointerType)) { setRunning(true); return; }
  try {
    if (!canvas.requestPointerLock) throw new Error('鼠标锁定不可用');
    await canvas.requestPointerLock();
  } catch {
    setRunning(true); message('未能锁定鼠标；按住画面拖动环顾，方向键也可调整视角。');
  }
}
function resetTouchInput() {
  stick.x = 0; stick.y = 0; stickPointer = null; look.reset();
  $('stick-thumb').style.transform = 'translate(0px, 0px)';
  for (const timer of holdTimers) clearInterval(timer);
  holdTimers.clear();
}
function updateInputMode() {
  touchActive = touchControlsEnabled(inputMode, touchAvailable());
  document.body.classList.toggle('touch', touchActive);
  $('control-help').innerHTML = touchActive
    ? '<span>左侧摇杆移动</span><span>拖动画面环顾</span><span>右侧按钮跳跃 / 挖掘 / 放置</span><span>点击快捷栏选材</span>'
    : desktopHelp;
  resetTouchInput();
  if (renderer) renderer.setPixelRatio(Math.min(devicePixelRatio, touchActive ? 1.5 : 2));
}
function pause() {
  if (document.pointerLockElement === canvas) document.exitPointerLock();
  setRunning(false);
}
function resetWorld(next, nextPlayer = null) {
  save();
  for (const mesh of meshes.values()) { scene.remove(mesh); mesh.geometry.dispose(); }
  meshes.clear(); world = next; ready = false; target = null; outline.visible = false;
  $('play').disabled = true; $('play').textContent = '正在准备世界…';
  respawn(); yaw = 0; pitch = 0;
  if (validPlayer(nextPlayer)) {
    player = { x: nextPlayer.x, y: nextPlayer.y, z: nextPlayer.z };
    yaw = nextPlayer.yaw; pitch = Math.max(-1.5, Math.min(1.5, nextPlayer.pitch));
  }
  while (playerCollides(world, player) && player.y < MAX_Y + 1) player.y += 1;
  $('seed-label').textContent = `WORLD SEED / ${world.seed}`;
  lastSavedRevision = -1;
  introPosition.set(player.x + 24, world.height(Math.floor(player.x), Math.floor(player.z)) + 18, player.z + 34);
  introLook.set(player.x - 8, world.height(Math.floor(player.x), Math.floor(player.z)), player.z - 12);
}
function setupInput() {
  $('distance').value = String(distance);
  updateInputMode();
  $('input-mode').addEventListener('change', e => { inputMode = e.target.value; updateInputMode(); });
  coarsePointer.addEventListener('change', updateInputMode);
  document.addEventListener('pointerdown', e => {
    if (inputMode === 'auto' && !touchActive && (e.pointerType === 'touch' || e.pointerType === 'pen')) {
      touchActive = true; document.body.classList.add('touch');
      $('control-help').innerHTML = '<span>左侧摇杆移动</span><span>拖动画面环顾</span><span>右侧按钮跳跃 / 挖掘 / 放置</span>';
    }
  }, { capture: true });
  $('play').addEventListener('click', start);
  $('menu-button').addEventListener('click', () => { if (running) pause(); else $('play').focus(); });
  document.addEventListener('pointerlockchange', () => setRunning(document.pointerLockElement === canvas));
  document.addEventListener('pointerlockerror', () => { setRunning(true); message('使用拖动或方向键环顾，Esc 返回菜单。'); });
  addEventListener('keydown', e => {
    if (e.target instanceof HTMLInputElement || e.target instanceof HTMLSelectElement) return;
    if (e.code === 'Escape' && running) { pause(); return; }
    if (!running) return;
    if (['Space', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.code)) e.preventDefault();
    keys.add(e.code);
    if (/^Digit[1-7]$/.test(e.code)) selectBlock(Number(e.code.slice(-1)));
    if (e.code === 'KeyE' && !e.repeat) editBlock(false);
    if (e.code === 'KeyF' && !e.repeat) editBlock(true);
  });
  addEventListener('keyup', e => keys.delete(e.code));
  addEventListener('blur', () => { keys.clear(); if (running) pause(); });
  document.addEventListener('visibilitychange', () => { if (document.hidden && running) pause(); });
  document.addEventListener('mousemove', e => {
    if (!running || document.pointerLockElement !== canvas) return;
    yaw -= e.movementX * 0.0024; pitch = Math.max(-1.5, Math.min(1.5, pitch - e.movementY * 0.0024));
  });
  canvas.addEventListener('pointerdown', e => {
    if (!running) return;
    if (document.pointerLockElement === canvas && e.pointerType === 'mouse') {
      if (e.button === 0) editBlock(false); if (e.button === 2) editBlock(true);
    } else if (look.begin(e)) canvas.setPointerCapture(e.pointerId);
  });
  canvas.addEventListener('pointermove', e => {
    if (!running) return;
    const delta = look.move(e); if (!delta) return;
    yaw -= delta.dx * 0.004; pitch = Math.max(-1.5, Math.min(1.5, pitch - delta.dy * 0.004));
  });
  canvas.addEventListener('pointerup', e => {
    const click = look.end(e);
    if (click) editBlock(click.place);
  });
  canvas.addEventListener('pointercancel', e => { if (e.pointerId === look.pointerId) look.reset(); });
  canvas.addEventListener('lostpointercapture', e => { if (e.pointerId === look.pointerId) look.reset(); });
  canvas.addEventListener('contextmenu', e => e.preventDefault());
  canvas.addEventListener('wheel', e => {
    if (!running) return; e.preventDefault(); selectBlock(((selected - 1 + Math.sign(e.deltaY) + 7) % 7) + 1);
  }, { passive: false });
  document.querySelectorAll('[data-key]').forEach(button => {
    const release = () => keys.delete(button.dataset.key);
    button.addEventListener('pointerdown', e => { e.preventDefault(); button.setPointerCapture(e.pointerId); if (running) keys.add(button.dataset.key); });
    button.addEventListener('pointerup', release); button.addEventListener('pointercancel', release);
    button.addEventListener('lostpointercapture', release);
  });
  const joystick = $('joystick');
  const moveStick = e => {
    if (e.pointerId !== stickPointer) return;
    const bounds = joystick.getBoundingClientRect(), radius = bounds.width * 0.33;
    Object.assign(stick, stickVector(e.clientX - bounds.left - bounds.width / 2, e.clientY - bounds.top - bounds.height / 2, radius));
    $('stick-thumb').style.transform = `translate(${stick.x * radius}px, ${stick.y * radius}px)`;
  };
  joystick.addEventListener('pointerdown', e => {
    if (!running || stickPointer !== null) return;
    e.preventDefault(); stickPointer = e.pointerId; joystick.setPointerCapture(e.pointerId); moveStick(e);
  });
  joystick.addEventListener('pointermove', moveStick);
  const releaseStick = e => {
    if (e.pointerId !== stickPointer) return;
    stickPointer = null; stick.x = 0; stick.y = 0; $('stick-thumb').style.transform = 'translate(0px, 0px)';
  };
  for (const event of ['pointerup', 'pointercancel', 'lostpointercapture']) joystick.addEventListener(event, releaseStick);
  for (const [id, place] of [['touch-break', false], ['touch-place', true]]) {
    const button = $(id); let pointer = null, timer = null;
    button.addEventListener('pointerdown', e => {
      if (!running || pointer !== null) return;
      e.preventDefault(); pointer = e.pointerId; button.setPointerCapture(pointer); editBlock(place);
      timer = setInterval(() => editBlock(place), 220); holdTimers.add(timer);
    });
    const release = e => {
      if (e.pointerId !== pointer) return;
      clearInterval(timer); holdTimers.delete(timer); timer = null; pointer = null;
    };
    for (const event of ['pointerup', 'pointercancel', 'lostpointercapture']) button.addEventListener(event, release);
    button.addEventListener('click', e => { if (e.detail === 0) editBlock(place); });
  }
  $('distance').addEventListener('change', e => { distance = Number(e.target.value); message('视野距离已更新'); });
  $('time-button').addEventListener('click', () => {
    dusk = !dusk; const sky = dusk ? '#c3a58c' : '#b8d1d0';
    scene.background.set(sky); scene.fog.color.set(sky);
    sun.color.set(dusk ? '#ffb46e' : '#fff1cb'); sun.intensity = dusk ? 1.6 : 2.4;
    ambient.intensity = dusk ? 1.7 : 2.4;
    $('time-button').innerHTML = `${dusk ? '切换白昼' : '切换黄昏'} <span>◐</span>`;
  });
  $('export').addEventListener('click', () => {
    const data = { ...world.toJSON(), player: { ...player, yaw, pitch } };
    const url = URL.createObjectURL(new Blob([JSON.stringify(data)], { type: 'application/json' }));
    const a = document.createElement('a'); a.href = url; a.download = `voxelcraft-${world.seed}.json`; a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000); message('存档已导出');
  });
  $('import').addEventListener('click', () => $('import-file').click());
  $('import-file').addEventListener('change', async e => {
    const file = e.target.files[0]; if (!file) return;
    try {
      if (file.size > 8 * 1024 * 1024) throw new Error('存档文件过大');
      const data = JSON.parse(await file.text());
      const next = VoxelWorld.fromJSON(data);
      if (!confirm('导入会替换当前世界。需要保留当前世界时，请先导出。继续吗？')) return;
      resetWorld(next, data.player); message('存档已导入');
    } catch (error) { message(`无法导入：${error.message}`); }
    finally { e.target.value = ''; }
  });
  $('new-world').addEventListener('click', () => {
    if (!confirm('创建新世界会替换当前存档。需要保留时，请先导出。继续吗？')) return;
    resetWorld(new VoxelWorld(crypto.getRandomValues(new Int32Array(1))[0])); message('新的世界，新的开始。');
  });
  addEventListener('resize', () => {
    keys.clear(); resetTouchInput();
    renderer.setSize(innerWidth, innerHeight); camera.aspect = innerWidth / innerHeight; camera.updateProjectionMatrix();
  });
  addEventListener('pagehide', save);
  canvas.addEventListener('webglcontextlost', e => { e.preventDefault(); pause(); ready = false; $('play').disabled = true; showError('图形连接已中断，请刷新页面恢复。存档已保留。'); });
}
function animate(now) {
  requestAnimationFrame(animate);
  const dt = Math.min((now - previousTime) / 1000, 0.04); previousTime = now;
  streamWorld();
  if (running) updatePlayer(dt);
  else {
    camera.position.copy(introPosition); camera.lookAt(introLook);
  }
  renderer.render(scene, camera);
  frames++;
  if (now - fpsTime >= 700) {
    $('fps').textContent = `${Math.round(frames * 1000 / (now - fpsTime))} FPS`;
    $('render-stats').textContent = `${chunksRendered} DRAWS · ${Math.round(renderer.info.render.triangles / 1000)}K TRI`;
    $('coordinates').textContent = `${Math.floor(player.x)}, ${Math.floor(player.y)}, ${Math.floor(player.z)}`;
    fpsTime = now; frames = 0;
  }
}
let previousTime = performance.now();
try {
  loadInitial(); setupGraphics(); makeHotbar(); setupInput();
  if (savedPlayer) {
    introPosition.set(player.x + 24, world.height(Math.floor(player.x), Math.floor(player.z)) + 18, player.z + 34);
    introLook.set(player.x - 8, player.y, player.z - 12);
  }
  setInterval(() => { if (ready) save(); }, 10000);
  requestAnimationFrame(animate);
} catch (error) {
  showError(`无法启动 3D 世界：${error.message}。请使用支持 WebGL 2 的浏览器，并开启硬件加速。`);
  $('play').disabled = true; $('play').textContent = '暂时无法进入';
  console.error(error);
}
