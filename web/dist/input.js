// Pure input helpers keep hybrid tablets and simultaneous touch gestures testable.
export function touchControlsEnabled(mode, available) {
  return mode === 'touch' || (mode === 'auto' && available);
}

export function prefersTouchStart(mode, available, pointerType) {
  if (mode !== 'auto') return mode === 'touch';
  if (pointerType) return pointerType === 'touch' || pointerType === 'pen';
  return available;
}

export function stickVector(dx, dy, radius) {
  const length = Math.hypot(dx, dy);
  if (length <= radius * 0.12) return { x: 0, y: 0 };
  const scale = Math.min(length / radius, 1) / length;
  return { x: dx * scale, y: dy * scale };
}

export class LookGesture {
  constructor() { this.reset(); }
  reset() { this.pointerId = null; this.x = 0; this.y = 0; this.moved = 0; this.type = ''; this.button = 0; }
  begin(event) {
    if (this.pointerId !== null) return false;
    this.pointerId = event.pointerId; this.x = event.clientX; this.y = event.clientY;
    this.type = event.pointerType; this.button = event.button; this.moved = 0;
    return true;
  }
  move(event) {
    if (event.pointerId !== this.pointerId) return null;
    const dx = event.clientX - this.x, dy = event.clientY - this.y;
    this.x = event.clientX; this.y = event.clientY; this.moved += Math.abs(dx) + Math.abs(dy);
    return { dx, dy };
  }
  end(event) {
    if (event.pointerId !== this.pointerId) return null;
    const click = this.moved < 4 && this.type === 'mouse' ? { place: this.button === 2 } : null;
    this.reset();
    return click;
  }
}
