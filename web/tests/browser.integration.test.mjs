import test from 'node:test';
import assert from 'node:assert/strict';
const base = process.env.VOXELCRAFT_BROWSER_URL || 'http://127.0.0.1:4173';
const status = async () => {
  const response = await fetch(`${base}/status`);
  assert.equal(response.status, 200, 'Start ./gradlew :client:runBrowser before this integration check');
  return response.json();
};
const input = body => fetch(`${base}/input`, { method:'POST', headers:{'Content-Type':'text/plain'}, body });
async function until(predicate) {
  const deadline = Date.now() + 5000;
  while (Date.now() < deadline) {
    const state = await status();
    if (predicate(state)) return state;
    await new Promise(resolve => setTimeout(resolve, 50));
  }
  assert.fail('Original Java client did not apply input');
}
test('browser transport shows original Java frames and controls the original camera, hotbar and menus', async () => {
  const before = await status();
  assert.match(before.backend, /^original-java-(metal|software)$/);
  const response = await fetch(`${base}/frame`);
  assert.equal(response.headers.get('content-type'), 'image/jpeg');
  const jpeg = new Uint8Array(await response.arrayBuffer());
  assert.deepEqual([...jpeg.slice(0, 2)], [255,216]);
  assert.deepEqual([...jpeg.slice(-2)], [255,217]);
  assert.ok(jpeg.length > 1000);
  assert.equal((await input('look 10 0\nkd 50\nku 50')).status, 204);
  await until(state => Math.abs(state.yaw - before.yaw - 2.8) < 0.1 && state.hotbarSlot === 1);
  assert.equal((await input('kd 69\nku 69')).status, 204);
  await until(state => state.uiOpen);
  assert.equal((await input('kd 27\nku 27')).status, 204);
  await until(state => !state.uiOpen);
  assert.equal((await input('look 2147483648 0')).status, 400);
  assert.equal((await input('kd 87\ninvalid')).status, 400, 'Invalid batches must not partially apply');
  assert.equal((await fetch(`${base}/input`, {method:'POST',headers:{Origin:'https://unrelated.example'},body:'kd 87'})).status,403);
  await input(`clear\nlook -10 0\nkd ${49 + before.hotbarSlot}\nku ${49 + before.hotbarSlot}`);
  await until(state => Math.abs(state.yaw - before.yaw) < 0.1 && state.hotbarSlot === before.hotbarSlot);
});
