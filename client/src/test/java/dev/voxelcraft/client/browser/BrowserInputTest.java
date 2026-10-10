package dev.voxelcraft.client.browser;

import dev.voxelcraft.client.platform.InputState;
import java.util.ArrayDeque;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BrowserInputTest {
    @Test
    void fastTapsSurviveHttpBatchingForOriginalHeldStateConsumers() {
        InputState input = new InputState();
        var events = new ArrayDeque<>(List.of("kd 50", "ku 50", "md 1", "mu 1"));
        BrowserClientMain.drainInput(events, input);
        assertTrue(input.isKeyDown(50), "Original hotbar must see a short digit tap");
        input.endFrame();
        BrowserClientMain.drainInput(events, input);
        assertFalse(input.isKeyDown(50));
        assertTrue(input.isMouseDown(1), "Original interactions must see a short click");
        input.endFrame();
        BrowserClientMain.drainInput(events, input);
        assertFalse(input.isMouseDown(1));
        assertTrue(events.isEmpty());
    }

    @Test
    void menuCoordinatesDoNotRotateTheCameraAndClearReleasesHeldInput() {
        InputState input = new InputState();
        var events = new ArrayDeque<>(List.of("pos 300 200", "look 8 -3", "kd 87", "md 3"));
        BrowserClientMain.drainInput(events, input);
        assertEquals(300, input.mouseX());
        assertEquals(200, input.mouseY());
        assertEquals(8, input.mouseDeltaX());
        assertEquals(-3, input.mouseDeltaY());
        events.add("clear");
        BrowserClientMain.drainInput(events, input);
        assertFalse(input.isKeyDown(87));
        assertFalse(input.isMouseDown(3));
        assertEquals(0, input.mouseDeltaX());
    }
}
