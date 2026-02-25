package dev.voxelcraft.client.platform;
/**
 * 中文说明：平台适配组件：负责 InputState 的窗口、输入或系统集成逻辑。
 */


// 中文标注（类）：`InputState`，职责：封装输入、状态相关逻辑。
public final class InputState {
    // 中文标注（字段）：`KEY_CAPACITY`，含义：用于表示键、capacity。
    private static final int KEY_CAPACITY = 1024;
    // 中文标注（字段）：`BUTTON_CAPACITY`，含义：用于表示button、capacity。
    private static final int BUTTON_CAPACITY = 8;

    // 中文标注（字段）：`keyDown`，含义：用于表示键、down。
    private final boolean[] keyDown = new boolean[KEY_CAPACITY];
    // 中文标注（字段）：`keyPressed`，含义：用于表示键、pressed。
    private final boolean[] keyPressed = new boolean[KEY_CAPACITY];
    // 中文标注（字段）：`keyReleased`，含义：用于表示键、released。
    private final boolean[] keyReleased = new boolean[KEY_CAPACITY];

    // 中文标注（字段）：`mouseDown`，含义：用于表示鼠标、down。
    private final boolean[] mouseDown = new boolean[BUTTON_CAPACITY];
    // 中文标注（字段）：`mousePressed`，含义：用于表示鼠标、pressed。
    private final boolean[] mousePressed = new boolean[BUTTON_CAPACITY];
    // 中文标注（字段）：`mouseReleased`，含义：用于表示鼠标、released。
    private final boolean[] mouseReleased = new boolean[BUTTON_CAPACITY];

    // 中文标注（字段）：`mouseX`，含义：用于表示鼠标、X坐标。
    private int mouseX;
    // 中文标注（字段）：`mouseY`，含义：用于表示鼠标、Y坐标。
    private int mouseY;
    // 中文标注（字段）：`mouseDeltaX`，含义：用于表示鼠标、增量、X坐标。
    private int mouseDeltaX;
    // 中文标注（字段）：`mouseDeltaY`，含义：用于表示鼠标、增量、Y坐标。
    private int mouseDeltaY;

    // 中文标注（方法）：`onKeyPressed`，参数：keyCode；用途：执行on、键、pressed相关逻辑。
    // 中文标注（参数）：`keyCode`，含义：用于表示键、code。
    public synchronized void onKeyPressed(int keyCode) {
        if (!isValidKey(keyCode)) {
            return;
        }
        if (!keyDown[keyCode]) {
            keyPressed[keyCode] = true;
        }
        keyDown[keyCode] = true;
    }

    // 中文标注（方法）：`onKeyReleased`，参数：keyCode；用途：执行on、键、released相关逻辑。
    // 中文标注（参数）：`keyCode`，含义：用于表示键、code。
    public synchronized void onKeyReleased(int keyCode) {
        if (!isValidKey(keyCode)) {
            return;
        }
        if (keyDown[keyCode]) {
            keyReleased[keyCode] = true;
        }
        keyDown[keyCode] = false;
    }

    // 中文标注（方法）：`onMousePressed`，参数：button；用途：执行on、鼠标、pressed相关逻辑。
    // 中文标注（参数）：`button`，含义：用于表示button。
    public synchronized void onMousePressed(int button) {
        if (!isValidButton(button)) {
            return;
        }
        if (!mouseDown[button]) {
            mousePressed[button] = true;
        }
        mouseDown[button] = true;
    }

    // 中文标注（方法）：`onMouseReleased`，参数：button；用途：执行on、鼠标、released相关逻辑。
    // 中文标注（参数）：`button`，含义：用于表示button。
    public synchronized void onMouseReleased(int button) {
        if (!isValidButton(button)) {
            return;
        }
        if (mouseDown[button]) {
            mouseReleased[button] = true;
        }
        mouseDown[button] = false;
    }

    // 中文标注（方法）：`onMouseMoved`，参数：x、y；用途：执行on、鼠标、moved相关逻辑。
    // 中文标注（参数）：`x`，含义：用于表示X坐标。
    // 中文标注（参数）：`y`，含义：用于表示Y坐标。
    public synchronized void onMouseMoved(int x, int y) {
        mouseDeltaX += x - mouseX;
        mouseDeltaY += y - mouseY;
        mouseX = x;
        mouseY = y;
    }

    // 中文标注（方法）：`onMouseDelta`，参数：deltaX、deltaY；用途：执行on、鼠标、增量相关逻辑。
    // 中文标注（参数）：`deltaX`，含义：用于表示增量、X坐标。
    // 中文标注（参数）：`deltaY`，含义：用于表示增量、Y坐标。
    public synchronized void onMouseDelta(int deltaX, int deltaY) {
        mouseDeltaX += deltaX;
        mouseDeltaY += deltaY;
    }

    // 中文标注（方法）：`setMousePosition`，参数：x、y；用途：设置、写入或注册鼠标、位置。
    // 中文标注（参数）：`x`，含义：用于表示X坐标。
    // 中文标注（参数）：`y`，含义：用于表示Y坐标。
    public synchronized void setMousePosition(int x, int y) {
        mouseX = x;
        mouseY = y;
    }

    // 中文标注（方法）：`clearAll`，参数：无；用途：执行clear、all相关逻辑。
    public synchronized void clearAll() {
        // 中文标注（局部变量）：`i`，含义：用于表示i。
        for (int i = 0; i < KEY_CAPACITY; i++) {
            keyDown[i] = false;
            keyPressed[i] = false;
            keyReleased[i] = false;
        }
        // 中文标注（局部变量）：`i`，含义：用于表示i。
        for (int i = 0; i < BUTTON_CAPACITY; i++) {
            mouseDown[i] = false;
            mousePressed[i] = false;
            mouseReleased[i] = false;
        }
        mouseDeltaX = 0;
        mouseDeltaY = 0;
    }

    // 中文标注（方法）：`isKeyDown`，参数：keyCode；用途：判断键、down是否满足条件。
    // 中文标注（参数）：`keyCode`，含义：用于表示键、code。
    public synchronized boolean isKeyDown(int keyCode) {
        return isValidKey(keyCode) && keyDown[keyCode];
    }

    // 中文标注（方法）：`wasKeyPressed`，参数：keyCode；用途：执行was、键、pressed相关逻辑。
    // 中文标注（参数）：`keyCode`，含义：用于表示键、code。
    public synchronized boolean wasKeyPressed(int keyCode) {
        return isValidKey(keyCode) && keyPressed[keyCode];
    }

    // 中文标注（方法）：`wasKeyReleased`，参数：keyCode；用途：执行was、键、released相关逻辑。
    // 中文标注（参数）：`keyCode`，含义：用于表示键、code。
    public synchronized boolean wasKeyReleased(int keyCode) {
        return isValidKey(keyCode) && keyReleased[keyCode];
    }

    // 中文标注（方法）：`isMouseDown`，参数：button；用途：判断鼠标、down是否满足条件。
    // 中文标注（参数）：`button`，含义：用于表示button。
    public synchronized boolean isMouseDown(int button) {
        return isValidButton(button) && mouseDown[button];
    }

    // 中文标注（方法）：`wasMousePressed`，参数：button；用途：执行was、鼠标、pressed相关逻辑。
    // 中文标注（参数）：`button`，含义：用于表示button。
    public synchronized boolean wasMousePressed(int button) {
        return isValidButton(button) && mousePressed[button];
    }

    // 中文标注（方法）：`wasMouseReleased`，参数：button；用途：执行was、鼠标、released相关逻辑。
    // 中文标注（参数）：`button`，含义：用于表示button。
    public synchronized boolean wasMouseReleased(int button) {
        return isValidButton(button) && mouseReleased[button];
    }

    // 中文标注（方法）：`mouseX`，参数：无；用途：执行鼠标、X坐标相关逻辑。
    public synchronized int mouseX() {
        return mouseX;
    }

    // 中文标注（方法）：`mouseY`，参数：无；用途：执行鼠标、Y坐标相关逻辑。
    public synchronized int mouseY() {
        return mouseY;
    }

    // 中文标注（方法）：`mouseDeltaX`，参数：无；用途：执行鼠标、增量、X坐标相关逻辑。
    public synchronized int mouseDeltaX() {
        return mouseDeltaX;
    }

    // 中文标注（方法）：`mouseDeltaY`，参数：无；用途：执行鼠标、增量、Y坐标相关逻辑。
    public synchronized int mouseDeltaY() {
        return mouseDeltaY;
    }

    // 中文标注（方法）：`endFrame`，参数：无；用途：执行结束、帧相关逻辑。
    public synchronized void endFrame() {
        // 中文标注（局部变量）：`i`，含义：用于表示i。
        for (int i = 0; i < KEY_CAPACITY; i++) {
            keyPressed[i] = false;
            keyReleased[i] = false;
        }
        // 中文标注（局部变量）：`i`，含义：用于表示i。
        for (int i = 0; i < BUTTON_CAPACITY; i++) {
            mousePressed[i] = false;
            mouseReleased[i] = false;
        }
        mouseDeltaX = 0;
        mouseDeltaY = 0;
    }

    // 中文标注（方法）：`isValidKey`，参数：keyCode；用途：判断valid、键是否满足条件。
    // 中文标注（参数）：`keyCode`，含义：用于表示键、code。
    private static boolean isValidKey(int keyCode) {
        return keyCode >= 0 && keyCode < KEY_CAPACITY;
    }

    // 中文标注（方法）：`isValidButton`，参数：button；用途：判断valid、button是否满足条件。
    // 中文标注（参数）：`button`，含义：用于表示button。
    private static boolean isValidButton(int button) {
        return button >= 0 && button < BUTTON_CAPACITY;
    }

}