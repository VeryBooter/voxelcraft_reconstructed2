import test from 'node:test';
import assert from 'node:assert/strict';
import { touchControlsEnabled, prefersTouchStart, stickVector, LookGesture } from '../dist/input.js';

test('hybrid tablets can use touch controls and still enter with a real mouse', () => {
  assert.equal(touchControlsEnabled('auto', true), true);
  assert.equal(touchControlsEnabled('mouse', true), false);
  assert.equal(touchControlsEnabled('touch', false), true);
  assert.equal(prefersTouchStart('auto', true, 'touch'), true);
  assert.equal(prefersTouchStart('auto', true, 'pen'), true);
  assert.equal(prefersTouchStart('auto', true, 'mouse'), false);
  assert.equal(prefersTouchStart('touch', true, 'mouse'), true);
  assert.equal(prefersTouchStart('mouse', true, 'touch'), false);
});

test('joystick has a dead zone, proportional movement and bounded diagonal speed', () => {
  assert.deepEqual(stickVector(2, 1, 40), { x: 0, y: 0 });
  assert.deepEqual(stickVector(0, -20, 40), { x: 0, y: -0.5 });
  const diagonal = stickVector(100, 100, 40);
  assert.ok(Math.abs(Math.hypot(diagonal.x, diagonal.y) - 1) < 1e-12);
});

test('an unrelated touch cannot steal, move or end the look gesture', () => {
  const look = new LookGesture();
  const start = { pointerId: 4, pointerType: 'touch', clientX: 200, clientY: 100, button: 0 };
  assert.equal(look.begin(start), true);
  assert.equal(look.begin({ ...start, pointerId: 5 }), false);
  assert.equal(look.move({ pointerId: 5, clientX: 800, clientY: 800 }), null);
  assert.equal(look.end({ pointerId: 5 }), null);
  assert.deepEqual(look.move({ pointerId: 4, clientX: 210, clientY: 105 }), { dx: 10, dy: 5 });
  assert.equal(look.end({ pointerId: 4 }), null);
  assert.equal(look.pointerId, null);
});

test('touch taps only look; a mouse click edits, but dragging never edits on release', () => {
  const look = new LookGesture();
  const e = { pointerId: 1, pointerType: 'touch', clientX: 10, clientY: 10, button: 0 };
  look.begin(e); assert.equal(look.end(e), null);
  look.begin({ ...e, pointerType: 'mouse', button: 2 }); assert.deepEqual(look.end(e), { place: true });
  look.begin({ ...e, pointerType: 'mouse' });
  look.move({ ...e, clientX: 30 });
  assert.equal(look.end(e), null);
});
