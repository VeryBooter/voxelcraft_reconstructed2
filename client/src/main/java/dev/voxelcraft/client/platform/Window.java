package dev.voxelcraft.client.platform;

import java.awt.AWTException;
import java.awt.Canvas;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.IllegalComponentStateException;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import javax.swing.JFrame;
import javax.swing.WindowConstants;
/**
 * 中文说明：平台适配组件：负责 Window 的窗口、输入或系统集成逻辑。
 */

// 中文标注（类）：`Window`，职责：封装窗口相关逻辑。
public final class Window implements AutoCloseable {
    // 中文标注（字段）：`HIDDEN_CURSOR`，含义：用于表示hidden、cursor。
    private static final Cursor HIDDEN_CURSOR = createHiddenCursor(); // meaning

    // 中文标注（字段）：`frame`，含义：用于表示帧。
    private final JFrame frame; // meaning
    // 中文标注（字段）：`canvas`，含义：用于表示canvas。
    private final Canvas canvas; // meaning
    // 中文标注（字段）：`input`，含义：用于表示输入。
    private final InputState input = new InputState(); // meaning
    // 中文标注（字段）：`mouseRobot`，含义：用于表示鼠标、robot。
    private final Robot mouseRobot; // meaning
    // 中文标注（字段）：`keyDispatcher`，含义：用于表示键、dispatcher。
    private final KeyEventDispatcher keyDispatcher; // meaning

    // 中文标注（字段）：`open`，含义：用于表示open。
    private volatile boolean open = true; // meaning
    // 中文标注（字段）：`mouseCaptured`，含义：用于表示鼠标、captured。
    private volatile boolean mouseCaptured; // meaning
    // 中文标注（字段）：`suppressCenterEvent`，含义：用于表示suppress、center、event。
    private volatile boolean suppressCenterEvent; // meaning
    // 中文标注（字段）：`bufferStrategy`，含义：用于表示缓冲区、strategy。
    private BufferStrategy bufferStrategy; // meaning

    // 中文标注（构造方法）：`Window`，参数：title、width、height；用途：初始化`Window`实例。
    // 中文标注（参数）：`title`，含义：用于表示title。
    // 中文标注（参数）：`width`，含义：用于表示宽度。
    // 中文标注（参数）：`height`，含义：用于表示高度。
    public Window(String title, int width, int height) {
        mouseRobot = createRobot();

        frame = new JFrame(title);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            // 中文标注（方法）：`windowClosing`，参数：event；用途：执行窗口、closing相关逻辑。
            @Override
            // 中文标注（参数）：`event`，含义：用于表示event。
            public void windowClosing(java.awt.event.WindowEvent event) {
                open = false;
            }
        });

        canvas = new Canvas();
        canvas.setPreferredSize(new Dimension(width, height));
        canvas.setFocusable(true);

        canvas.addKeyListener(new KeyAdapter() {
            // 中文标注（方法）：`keyPressed`，参数：event；用途：执行键、pressed相关逻辑。
            @Override
            // 中文标注（参数）：`event`，含义：用于表示event。
            public void keyPressed(KeyEvent event) {
                input.onKeyPressed(event.getKeyCode());
            }

            // 中文标注（方法）：`keyReleased`，参数：event；用途：执行键、released相关逻辑。
            @Override
            // 中文标注（参数）：`event`，含义：用于表示event。
            public void keyReleased(KeyEvent event) {
                input.onKeyReleased(event.getKeyCode());
            }
        });

        // 中文标注（局部变量）：`mouseAdapter`，含义：用于表示鼠标、adapter。
        MouseAdapter mouseAdapter = new MouseAdapter() {
            // 中文标注（方法）：`mousePressed`，参数：event；用途：执行鼠标、pressed相关逻辑。
            @Override
            // 中文标注（参数）：`event`，含义：用于表示event。
            public void mousePressed(MouseEvent event) {
                canvas.requestFocusInWindow();
                input.onMousePressed(event.getButton());
                captureMouse();
            }

            // 中文标注（方法）：`mouseReleased`，参数：event；用途：执行鼠标、released相关逻辑。
            @Override
            // 中文标注（参数）：`event`，含义：用于表示event。
            public void mouseReleased(MouseEvent event) {
                input.onMouseReleased(event.getButton());
            }

            // 中文标注（方法）：`mouseMoved`，参数：event；用途：执行鼠标、moved相关逻辑。
            @Override
            // 中文标注（参数）：`event`，含义：用于表示event。
            public void mouseMoved(MouseEvent event) {
                handleMouseMotion(event);
            }

            // 中文标注（方法）：`mouseDragged`，参数：event；用途：执行鼠标、dragged相关逻辑。
            @Override
            // 中文标注（参数）：`event`，含义：用于表示event。
            public void mouseDragged(MouseEvent event) {
                handleMouseMotion(event);
            }
        };
        canvas.addMouseListener(mouseAdapter);
        canvas.addMouseMotionListener(mouseAdapter);

        canvas.addFocusListener(new FocusAdapter() {
            // 中文标注（方法）：`focusLost`，参数：event；用途：执行focus、lost相关逻辑。
            @Override
            // 中文标注（参数）：`event`，含义：用于表示event。
            public void focusLost(FocusEvent event) {
                releaseMouse();
                input.clearAll();
            }

            // 中文标注（方法）：`focusGained`，参数：event；用途：执行focus、gained相关逻辑。
            @Override
            // 中文标注（参数）：`event`，含义：用于表示event。
            public void focusGained(FocusEvent event) {
                captureMouse();
            }
        });

        frame.add(canvas);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setResizable(true);
        frame.setVisible(true);

        canvas.requestFocusInWindow();
        canvas.createBufferStrategy(2);
        bufferStrategy = canvas.getBufferStrategy();

        // 中文标注（Lambda参数）：`event`，含义：用于表示event。
        keyDispatcher = event -> {
            if (!frame.isFocused()) {
                return false;
            }

            // 中文标注（局部变量）：`eventId`，含义：用于表示event、标识。
            int eventId = event.getID(); // meaning
            if (eventId == KeyEvent.KEY_PRESSED) {
                input.onKeyPressed(event.getKeyCode());
            } else if (eventId == KeyEvent.KEY_RELEASED) {
                input.onKeyReleased(event.getKeyCode());
            }
            return false;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher);
        captureMouse();
    }

    // 中文标注（方法）：`isOpen`，参数：无；用途：判断open是否满足条件。
    public boolean isOpen() {
        return open && frame.isDisplayable();
    }

    // 中文标注（方法）：`pollEvents`，参数：无；用途：执行poll、events相关逻辑。
    public void pollEvents() {
        if (!frame.isVisible()) {
            open = false;
        }
        if (frame.isFocused() && !canvas.isFocusOwner()) {
            canvas.requestFocusInWindow();
        }
        if (input.wasKeyPressed(KeyEvent.VK_ESCAPE)) {
            open = false;
        }
    }

    // 中文标注（方法）：`beginRender`，参数：无；用途：执行begin、渲染相关逻辑。
    public Graphics2D beginRender() {
        // 中文标注（局部变量）：`strategy`，含义：用于表示strategy。
        BufferStrategy strategy = bufferStrategy; // meaning
        if (strategy == null) {
            canvas.createBufferStrategy(2);
            strategy = canvas.getBufferStrategy();
            bufferStrategy = strategy;
        }
        return (Graphics2D) strategy.getDrawGraphics();
    }

    // 中文标注（方法）：`endRender`，参数：graphics；用途：执行结束、渲染相关逻辑。
    // 中文标注（参数）：`graphics`，含义：用于表示graphics。
    public void endRender(Graphics2D graphics) {
        graphics.dispose();
        // 中文标注（局部变量）：`strategy`，含义：用于表示strategy。
        BufferStrategy strategy = bufferStrategy; // meaning
        if (strategy != null) {
            strategy.show();
        }
        Toolkit.getDefaultToolkit().sync();
    }

    // 中文标注（方法）：`endFrame`，参数：无；用途：执行结束、帧相关逻辑。
    public void endFrame() {
        input.endFrame();
    }

    // 中文标注（方法）：`input`，参数：无；用途：执行输入相关逻辑。
    public InputState input() {
        return input;
    }

    // 中文标注（方法）：`width`，参数：无；用途：执行宽度相关逻辑。
    public int width() {
        return Math.max(1, canvas.getWidth());
    }

    // 中文标注（方法）：`height`，参数：无；用途：执行高度相关逻辑。
    public int height() {
        return Math.max(1, canvas.getHeight());
    }

    // 中文标注（方法）：`setTitle`，参数：title；用途：设置、写入或注册title。
    // 中文标注（参数）：`title`，含义：用于表示title。
    public void setTitle(String title) {
        frame.setTitle(title);
    }

    // 中文标注（方法）：`close`，参数：无；用途：执行close相关逻辑。
    @Override
    public void close() {
        open = false;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyDispatcher);
        releaseMouse();
        frame.dispose();
    }

    // 中文标注（方法）：`handleMouseMotion`，参数：event；用途：处理handle、鼠标、motion逻辑。
    // 中文标注（参数）：`event`，含义：用于表示event。
    private void handleMouseMotion(MouseEvent event) {
        if (!mouseCaptured || mouseRobot == null) {
            input.onMouseMoved(event.getX(), event.getY());
            return;
        }

        // 中文标注（局部变量）：`centerX`，含义：用于表示center、X坐标。
        int centerX = canvas.getWidth() / 2; // meaning
        // 中文标注（局部变量）：`centerY`，含义：用于表示center、Y坐标。
        int centerY = canvas.getHeight() / 2; // meaning
        if (suppressCenterEvent && event.getX() == centerX && event.getY() == centerY) {
            suppressCenterEvent = false;
            return;
        }
        suppressCenterEvent = false;

        // 中文标注（局部变量）：`deltaX`，含义：用于表示增量、X坐标。
        int deltaX = event.getX() - centerX; // meaning
        // 中文标注（局部变量）：`deltaY`，含义：用于表示增量、Y坐标。
        int deltaY = event.getY() - centerY; // meaning
        if (deltaX == 0 && deltaY == 0) {
            return;
        }

        input.onMouseDelta(deltaX, deltaY);
        recenterMousePointer();
    }

    // 中文标注（方法）：`captureMouse`，参数：无；用途：构建或创建capture、鼠标。
    private void captureMouse() {
        if (mouseCaptured || !canvas.isShowing()) {
            return;
        }
        mouseCaptured = true;
        canvas.setCursor(HIDDEN_CURSOR);
        if (mouseRobot != null) {
            recenterMousePointer();
        }
    }

    // 中文标注（方法）：`releaseMouse`，参数：无；用途：执行release、鼠标相关逻辑。
    private void releaseMouse() {
        if (!mouseCaptured) {
            return;
        }
        mouseCaptured = false;
        suppressCenterEvent = false;
        canvas.setCursor(Cursor.getDefaultCursor());
    }

    // 中文标注（方法）：`recenterMousePointer`，参数：无；用途：执行recenter、鼠标、pointer相关逻辑。
    private void recenterMousePointer() {
        if (!mouseCaptured || mouseRobot == null || !canvas.isShowing()) {
            return;
        }

        // 中文标注（局部变量）：`centerX`，含义：用于表示center、X坐标。
        int centerX = Math.max(0, canvas.getWidth() / 2); // meaning
        // 中文标注（局部变量）：`centerY`，含义：用于表示center、Y坐标。
        int centerY = Math.max(0, canvas.getHeight() / 2); // meaning
        input.setMousePosition(centerX, centerY);

        try {
            // 中文标注（局部变量）：`locationOnScreen`，含义：用于表示location、on、screen。
            Point locationOnScreen = canvas.getLocationOnScreen(); // meaning
            suppressCenterEvent = true;
            mouseRobot.mouseMove(locationOnScreen.x + centerX, locationOnScreen.y + centerY);
        // 中文标注（异常参数）：`ignored`，含义：用于表示ignored。
        } catch (IllegalComponentStateException ignored) {
            suppressCenterEvent = false;
        }
    }

    // 中文标注（方法）：`createHiddenCursor`，参数：无；用途：构建或创建创建、hidden、cursor。
    private static Cursor createHiddenCursor() {
        try {
            // 中文标注（局部变量）：`image`，含义：用于表示image。
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB); // meaning
            return Toolkit.getDefaultToolkit().createCustomCursor(image, new Point(0, 0), "voxelcraft-hidden-cursor");
        // 中文标注（异常参数）：`ignored`，含义：用于表示ignored。
        } catch (RuntimeException ignored) {
            return Cursor.getDefaultCursor();
        }
    }

    // 中文标注（方法）：`createRobot`，参数：无；用途：构建或创建创建、robot。
    private static Robot createRobot() {
        try {
            return new Robot();
        // 中文标注（异常参数）：`ignored`，含义：用于表示ignored。
        } catch (AWTException | SecurityException ignored) {
            return null;
        }
    }
}
