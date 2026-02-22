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

public final class Window implements AutoCloseable {
    private static final Cursor HIDDEN_CURSOR = createHiddenCursor();

    private final JFrame frame;
    private final Canvas canvas;
    private final InputState input = new InputState();
    private final Robot mouseRobot;
    private final KeyEventDispatcher keyDispatcher;

    private volatile boolean open = true;
    private volatile boolean mouseCaptured;
    private volatile boolean suppressCenterEvent;
    private BufferStrategy bufferStrategy;

    public Window(String title, int width, int height) {
        mouseRobot = createRobot();

        frame = new JFrame(title);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                open = false;
            }
        });

        canvas = new Canvas();
        canvas.setPreferredSize(new Dimension(width, height));
        canvas.setFocusable(true);

        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                input.onKeyPressed(event.getKeyCode());
            }

            @Override
            public void keyReleased(KeyEvent event) {
                input.onKeyReleased(event.getKeyCode());
            }
        });

        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                canvas.requestFocusInWindow();
                input.onMousePressed(event.getButton());
                captureMouse();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                input.onMouseReleased(event.getButton());
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                handleMouseMotion(event);
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                handleMouseMotion(event);
            }
        };
        canvas.addMouseListener(mouseAdapter);
        canvas.addMouseMotionListener(mouseAdapter);

        canvas.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                releaseMouse();
                input.clearAll();
            }

            @Override
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

        keyDispatcher = event -> {
            if (!frame.isFocused()) {
                return false;
            }

            int eventId = event.getID();
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

    public boolean isOpen() {
        return open && frame.isDisplayable();
    }

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

    public Graphics2D beginRender() {
        BufferStrategy strategy = bufferStrategy;
        if (strategy == null) {
            canvas.createBufferStrategy(2);
            strategy = canvas.getBufferStrategy();
            bufferStrategy = strategy;
        }
        return (Graphics2D) strategy.getDrawGraphics();
    }

    public void endRender(Graphics2D graphics) {
        graphics.dispose();
        BufferStrategy strategy = bufferStrategy;
        if (strategy != null) {
            strategy.show();
        }
        Toolkit.getDefaultToolkit().sync();
    }

    public void endFrame() {
        input.endFrame();
    }

    public InputState input() {
        return input;
    }

    public int width() {
        return Math.max(1, canvas.getWidth());
    }

    public int height() {
        return Math.max(1, canvas.getHeight());
    }

    public void setTitle(String title) {
        frame.setTitle(title);
    }

    @Override
    public void close() {
        open = false;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyDispatcher);
        releaseMouse();
        frame.dispose();
    }

    private void handleMouseMotion(MouseEvent event) {
        if (!mouseCaptured || mouseRobot == null) {
            input.onMouseMoved(event.getX(), event.getY());
            return;
        }

        int centerX = canvas.getWidth() / 2;
        int centerY = canvas.getHeight() / 2;
        if (suppressCenterEvent && event.getX() == centerX && event.getY() == centerY) {
            suppressCenterEvent = false;
            return;
        }
        suppressCenterEvent = false;

        int deltaX = event.getX() - centerX;
        int deltaY = event.getY() - centerY;
        if (deltaX == 0 && deltaY == 0) {
            return;
        }

        input.onMouseDelta(deltaX, deltaY);
        recenterMousePointer();
    }

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

    private void releaseMouse() {
        if (!mouseCaptured) {
            return;
        }
        mouseCaptured = false;
        suppressCenterEvent = false;
        canvas.setCursor(Cursor.getDefaultCursor());
    }

    private void recenterMousePointer() {
        if (!mouseCaptured || mouseRobot == null || !canvas.isShowing()) {
            return;
        }

        int centerX = Math.max(0, canvas.getWidth() / 2);
        int centerY = Math.max(0, canvas.getHeight() / 2);
        input.setMousePosition(centerX, centerY);

        try {
            Point locationOnScreen = canvas.getLocationOnScreen();
            suppressCenterEvent = true;
            mouseRobot.mouseMove(locationOnScreen.x + centerX, locationOnScreen.y + centerY);
        } catch (IllegalComponentStateException ignored) {
            suppressCenterEvent = false;
        }
    }

    private static Cursor createHiddenCursor() {
        try {
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            return Toolkit.getDefaultToolkit().createCustomCursor(image, new Point(0, 0), "voxelcraft-hidden-cursor");
        } catch (RuntimeException ignored) {
            return Cursor.getDefaultCursor();
        }
    }

    private static Robot createRobot() {
        try {
            return new Robot();
        } catch (AWTException | SecurityException ignored) {
            return null;
        }
    }
}
