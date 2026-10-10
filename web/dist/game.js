// Transport only: the existing Java client owns world, physics, camera, blocks and HUD.
const screen = document.querySelector('#screen');
const connection = document.querySelector('#connection');
const capture = document.querySelector('#capture');
let uiOpen = false, closed = false, commands = [], sending = false, imageUrl, dragging;
const keyCodes = { KeyW:87,KeyA:65,KeyS:83,KeyD:68,KeyE:69,KeyO:79,KeyZ:90,KeyX:88,KeyV:86,KeyF:70,Space:32,ShiftLeft:16,ShiftRight:16,Escape:27,ArrowLeft:37,ArrowUp:38,ArrowRight:39,ArrowDown:40 };
for (let i = 1; i <= 9; i++) keyCodes[`Digit${i}`] = 48 + i;
function queue(command) { if (!closed) commands.push(command); }
async function flush() {
  if (sending || !commands.length || closed) return;
  sending = true;
  const batch = commands; commands = [];
  try {
    const response = await fetch('/input', {method:'POST',headers:{'Content-Type':'text/plain'},body:batch.join('\n')});
    if (!response.ok) { commands = ['clear']; connection.hidden = false; connection.textContent = '输入连接失败，正在重连…'; }
  } catch { commands = ['clear']; }
  finally { sending = false; }
}
setInterval(flush, 16);
setInterval(() => queue('ping'), 500);
function clear() { dragging = undefined; queue('clear'); }
window.addEventListener('blur', clear);
document.addEventListener('visibilitychange', () => { if (document.hidden) { clear(); flush(); } });
window.addEventListener('pagehide', () => { navigator.sendBeacon('/input', 'clear'); closed = true; });
window.addEventListener('keydown', event => {
  const code = keyCodes[event.code];
  if (code !== undefined) { event.preventDefault(); if (!event.repeat) queue(`kd ${code}`); }
});
window.addEventListener('keyup', event => { const code = keyCodes[event.code]; if (code !== undefined) { event.preventDefault(); queue(`ku ${code}`); } });
function position(event) {
  const scale = Math.min(innerWidth / screen.naturalWidth, innerHeight / screen.naturalHeight);
  const x = (event.clientX - (innerWidth - screen.naturalWidth * scale) / 2) / scale;
  const y = (event.clientY - (innerHeight - screen.naturalHeight * scale) / 2) / scale;
  if (Number.isFinite(x) && Number.isFinite(y)) queue(`pos ${Math.round(x)} ${Math.round(y)}`);
}
function lock() { if (!uiOpen && matchMedia('(pointer:fine)').matches) screen.requestPointerLock?.(); }
capture.addEventListener('click', lock);
document.addEventListener('pointerlockchange', () => { capture.hidden = document.pointerLockElement === screen; clear(); });
screen.addEventListener('contextmenu', event => event.preventDefault());
screen.addEventListener('pointerdown', event => {
  event.preventDefault(); screen.setPointerCapture(event.pointerId); position(event);
  if (uiOpen || document.pointerLockElement === screen) queue(`md ${event.button === 2 ? 3 : 1}`);
  else if (event.pointerType === 'mouse') lock();
  dragging = {id:event.pointerId,x:event.clientX,y:event.clientY};
});
screen.addEventListener('pointerup', event => {
  queue(`mu ${event.button === 2 ? 3 : 1}`);
  if (dragging?.id === event.pointerId) dragging = undefined;
});
screen.addEventListener('pointercancel', clear);
screen.addEventListener('pointermove', event => {
  if (uiOpen) { position(event); return; }
  if (document.pointerLockElement === screen) queue(`look ${Math.round(event.movementX)} ${Math.round(event.movementY)}`);
  else if (dragging?.id === event.pointerId) {
    queue(`look ${Math.round(event.clientX - dragging.x)} ${Math.round(event.clientY - dragging.y)}`);
    dragging.x = event.clientX; dragging.y = event.clientY;
  }
});
for (const button of document.querySelectorAll('#touch button')) {
  const down = button.dataset.key ? `kd ${button.dataset.key}` : `md ${button.dataset.mouse}`;
  const up = button.dataset.key ? `ku ${button.dataset.key}` : `mu ${button.dataset.mouse}`;
  button.addEventListener('pointerdown', event => { event.preventDefault(); button.setPointerCapture(event.pointerId); queue(down); });
  button.addEventListener('pointerup', () => queue(up));
  button.addEventListener('pointercancel', () => queue(up));
}
async function frames() {
  while (!closed) {
    const start = performance.now();
    try {
      const response = await fetch('/frame');
      if (!response.ok) throw new Error(await response.text());
      const nextUi = response.headers.get('X-UI-Open') === 'true';
      if (nextUi && !uiOpen && document.pointerLockElement) document.exitPointerLock();
      uiOpen = nextUi;
      const blob = await response.blob();
      const nextUrl = URL.createObjectURL(blob);
      const previousUrl = imageUrl;
      screen.src = nextUrl;
      try { await screen.decode(); } finally { if (previousUrl) URL.revokeObjectURL(previousUrl); imageUrl = nextUrl; }
      connection.hidden = true;
    } catch (error) {
      connection.hidden = false;
      connection.textContent = location.hostname.endsWith('github.io') ? '此页面需要本地 Java 客户端。请运行 ./gradlew :client:runBrowser，再打开 http://127.0.0.1:4173。' : `${error.message}（需要在电脑上启动原版 Java 客户端）`;
      await new Promise(resolve => setTimeout(resolve, 500));
    }
    await new Promise(resolve => setTimeout(resolve, Math.max(0, 1000 / 30 - (performance.now() - start))));
  }
}
frames();
