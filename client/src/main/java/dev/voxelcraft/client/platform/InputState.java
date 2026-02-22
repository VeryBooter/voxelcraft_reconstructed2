package dev.voxelcraft.client.platform;


public final class InputState {
    private static final int KEY_CAPACITY = 1024;
    private static final int BUTTON_CAPACITY = 8;

    private final boolean[] keyDown = new boolean[KEY_CAPACITY];
    private final boolean[] keyPressed = new boolean[KEY_CAPACITY];
    private final boolean[] keyReleased = new boolean[KEY_CAPACITY];

    private final boolean[] mouseDown = new boolean[BUTTON_CAPACITY];
    private final boolean[] mousePressed = new boolean[BUTTON_CAPACITY];
    private final boolean[] mouseReleased = new boolean[BUTTON_CAPACITY];

    private int mouseX;
    private int mouseY;
    private int mouseDeltaX;
    private int mouseDeltaY;

    public synchronized void onKeyPressed(int keyCode) {
        if (!isValidKey(keyCode)) {
            return;
        }
        if (!keyDown[keyCode]) {
            keyPressed[keyCode] = true;
        }
        keyDown[keyCode] = true;
    }

    public synchronized void onKeyReleased(int keyCode) {
        if (!isValidKey(keyCode)) {
            return;
        }
        if (keyDown[keyCode]) {
            keyReleased[keyCode] = true;
        }
        keyDown[keyCode] = false;
    }

    public synchronized void onMousePressed(int button) {
        if (!isValidButton(button)) {
            return;
        }
        if (!mouseDown[button]) {
            mousePressed[button] = true;
        }
        mouseDown[button] = true;
    }

    public synchronized void onMouseReleased(int button) {
        if (!isValidButton(button)) {
            return;
        }
        if (mouseDown[button]) {
            mouseReleased[button] = true;
        }
        mouseDown[button] = false;
    }

    public synchronized void onMouseMoved(int x, int y) {
        mouseDeltaX += x - mouseX;
        mouseDeltaY += y - mouseY;
        mouseX = x;
        mouseY = y;
    }

    public synchronized void onMouseDelta(int deltaX, int deltaY) {
        mouseDeltaX += deltaX;
        mouseDeltaY += deltaY;
    }

    public synchronized void setMousePosition(int x, int y) {
        mouseX = x;
        mouseY = y;
    }

    public synchronized void clearAll() {
        for (int i = 0; i < KEY_CAPACITY; i++) {
            keyDown[i] = false;
            keyPressed[i] = false;
            keyReleased[i] = false;
        }
        for (int i = 0; i < BUTTON_CAPACITY; i++) {
            mouseDown[i] = false;
            mousePressed[i] = false;
            mouseReleased[i] = false;
        }
        mouseDeltaX = 0;
        mouseDeltaY = 0;
    }

    public synchronized boolean isKeyDown(int keyCode) {
        return isValidKey(keyCode) && keyDown[keyCode];
    }

    public synchronized boolean wasKeyPressed(int keyCode) {
        return isValidKey(keyCode) && keyPressed[keyCode];
    }

    public synchronized boolean wasKeyReleased(int keyCode) {
        return isValidKey(keyCode) && keyReleased[keyCode];
    }

    public synchronized boolean isMouseDown(int button) {
        return isValidButton(button) && mouseDown[button];
    }

    public synchronized boolean wasMousePressed(int button) {
        return isValidButton(button) && mousePressed[button];
    }

    public synchronized boolean wasMouseReleased(int button) {
        return isValidButton(button) && mouseReleased[button];
    }

    public synchronized int mouseX() {
        return mouseX;
    }

    public synchronized int mouseY() {
        return mouseY;
    }

    public synchronized int mouseDeltaX() {
        return mouseDeltaX;
    }

    public synchronized int mouseDeltaY() {
        return mouseDeltaY;
    }

    public synchronized void endFrame() {
        for (int i = 0; i < KEY_CAPACITY; i++) {
            keyPressed[i] = false;
            keyReleased[i] = false;
        }
        for (int i = 0; i < BUTTON_CAPACITY; i++) {
            mousePressed[i] = false;
            mouseReleased[i] = false;
        }
        mouseDeltaX = 0;
        mouseDeltaY = 0;
    }

    private static boolean isValidKey(int keyCode) {
        return keyCode >= 0 && keyCode < KEY_CAPACITY;
    }

    private static boolean isValidButton(int button) {
        return button >= 0 && button < BUTTON_CAPACITY;
    }

}
