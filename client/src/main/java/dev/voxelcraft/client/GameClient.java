package dev.voxelcraft.client;

import dev.voxelcraft.client.light.LightEngine;
import dev.voxelcraft.client.network.NetworkClient;
import dev.voxelcraft.client.physics.AABB;
import dev.voxelcraft.client.platform.InputState;
import dev.voxelcraft.client.player.PlayerController;
import dev.voxelcraft.client.render.ChunkRenderSystem;
import dev.voxelcraft.client.render.ChunkRenderSystem.RenderStats;
import dev.voxelcraft.client.world.BlockHitResult;
import dev.voxelcraft.client.world.ClientWorldView;
import dev.voxelcraft.core.Game;
import dev.voxelcraft.core.block.Block;
import dev.voxelcraft.core.block.Blocks;
import dev.voxelcraft.core.world.BlockPos;
import dev.voxelcraft.core.world.Section;
import dev.voxelcraft.core.world.World;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

public final class GameClient implements AutoCloseable {
    private static final double INTERACTION_REACH = 6.0;
    private static final double NETWORK_STATE_SEND_INTERVAL_SECONDS = 0.05;
    private static final int NETWORK_CHUNK_RADIUS = 3;
    private static final int LOCAL_CHUNK_RADIUS = 3;
    private static final int LOCAL_CHUNK_GENERATION_BUDGET_PER_TICK = 2;

    private final Game game = new Game();
    private final PlayerController playerController = new PlayerController();
    private final ClientWorldView worldView = new ClientWorldView(game.world());
    private final ChunkRenderSystem renderSystem = new ChunkRenderSystem();
    private final LightEngine lightEngine = new LightEngine();
    private final HardwareStatsSampler hardwareStatsSampler = new HardwareStatsSampler();

    private final Block[] hotbarBlocks = {
        Blocks.DIRT,
        Blocks.STONE,
        Blocks.GRASS,
        Blocks.SAND,
        Blocks.WOOD
    };
    private final boolean[] hotbarDownLastTick = new boolean[hotbarBlocks.length];

    private NetworkClient networkClient;

    private BlockHitResult targetedBlock;
    private Block selectedBlock = hotbarBlocks[0];
    private int selectedHotbarSlot;
    private String networkStatusLine = "singleplayer";
    private HardwareStatsSampler.Snapshot hardwareSnapshot = hardwareStatsSampler.sample();

    private double networkStateSendAccumulator;
    private double hardwareSampleAccumulator;
    private double smoothedFrameMs = 16.7;
    private double smoothedFps = 60.0;
    private int lastRequestedChunkX = Integer.MIN_VALUE;
    private int lastRequestedChunkZ = Integer.MIN_VALUE;
    private boolean breakButtonDownLastTick;
    private boolean placeButtonDownLastTick;
    private long lastEnsureLocalChunksNanos;
    private long lastChunkGenerationDrainNanos;

    public GameClient() {
        initializeSpawn();
    }

    public void attachNetwork(NetworkClient networkClient) {
        this.networkClient = networkClient;
        this.networkStatusLine = networkClient.statusLine();
        requestChunksIfNeeded(true);
    }

    public void tick(InputState input, double deltaSeconds) {
        if (networkClient != null) {
            networkClient.drainIncoming(worldView);
            networkStatusLine = networkClient.statusLine();
        }

        double frameMs = deltaSeconds * 1_000.0;
        if (frameMs > 0.0) {
            smoothedFrameMs = smoothedFrameMs * 0.88 + frameMs * 0.12;
            smoothedFps = smoothedFps * 0.88 + (1_000.0 / frameMs) * 0.12;
        }
        hardwareSampleAccumulator += deltaSeconds;
        if (hardwareSampleAccumulator >= 0.25) {
            hardwareSnapshot = hardwareStatsSampler.sample();
            hardwareSampleAccumulator = 0.0;
        }

        handleBlockSelection(input);
        playerController.tick(worldView, input, deltaSeconds);

        long ensureStarted = System.nanoTime();
        ensureLocalChunksAroundPlayer();
        lastEnsureLocalChunksNanos = System.nanoTime() - ensureStarted;
        long chunkDrainStarted = System.nanoTime();
        worldView.drainChunkGenerationBudget(LOCAL_CHUNK_GENERATION_BUDGET_PER_TICK);
        lastChunkGenerationDrainNanos = System.nanoTime() - chunkDrainStarted;
        requestChunksIfNeeded(false);

        targetedBlock = raycastFromPlayer(INTERACTION_REACH);
        handleInteractions(input);

        if (networkClient != null) {
            networkStateSendAccumulator += deltaSeconds;
            if (networkStateSendAccumulator >= NETWORK_STATE_SEND_INTERVAL_SECONDS) {
                networkClient.sendPlayerState(playerController);
                networkStateSendAccumulator = 0.0;
            }
            networkStatusLine = networkClient.statusLine();
        }

        lightEngine.tick(worldView);
        game.tick();
    }

    public void render(Graphics2D graphics, int width, int height) {
        drawBackground(graphics, width, height);

        RenderStats stats = renderSystem.draw(graphics, width, height, worldView, playerController);
        if (targetedBlock != null) {
            renderSystem.drawSelectionBox(graphics, width, height, playerController, targetedBlock.targetBlock());
        }

        drawCrosshair(graphics, width, height);
        drawHotbar(graphics, width, height);
        drawHud(graphics, stats);
    }

    public ClientWorldView worldView() {
        return worldView;
    }

    public PlayerController playerController() {
        return playerController;
    }

    public BlockHitResult targetedBlock() {
        return targetedBlock;
    }

    public float ambientLight() {
        return lightEngine.ambient();
    }

    public String networkStatusLine() {
        return networkStatusLine;
    }

    public Block selectedBlock() {
        return selectedBlock;
    }

    public long lastEnsureLocalChunksNanos() {
        return lastEnsureLocalChunksNanos;
    }

    public long lastChunkGenerationDrainNanos() {
        return lastChunkGenerationDrainNanos;
    }

    public long lastChunkGenSubmitNanos() {
        return worldView.lastChunkGenSubmitNanos();
    }

    public long lastChunkInstallNanos() {
        return worldView.lastChunkInstallNanos();
    }

    public int lastChunkGenSubmittedCount() {
        return worldView.lastChunkGenSubmittedCount();
    }

    public int lastChunkInstalledCount() {
        return worldView.lastChunkInstalledCount();
    }

    public int chunkGenerationJobsInFlight() {
        return worldView.chunkGenerationJobsInFlight();
    }

    public int readyGeneratedChunkCount() {
        return worldView.readyGeneratedChunkCount();
    }

    public int pendingChunkGenerationCount() {
        return worldView.pendingChunkGenerationCount();
    }

    @Override
    public void close() {
        if (networkClient != null) {
            networkClient.close();
            networkClient = null;
        }
        worldView.close();
    }

    private void requestChunksIfNeeded(boolean force) {
        if (networkClient == null || !networkClient.isConnected()) {
            return;
        }

        int blockX = (int) Math.floor(playerController.x());
        int blockZ = (int) Math.floor(playerController.z());
        int chunkX = Math.floorDiv(blockX, Section.SIZE);
        int chunkZ = Math.floorDiv(blockZ, Section.SIZE);

        if (!force && chunkX == lastRequestedChunkX && chunkZ == lastRequestedChunkZ) {
            return;
        }

        lastRequestedChunkX = chunkX;
        lastRequestedChunkZ = chunkZ;
        networkClient.requestChunkRadius(chunkX, chunkZ, NETWORK_CHUNK_RADIUS);
    }

    private void ensureLocalChunksAroundPlayer() {
        if (networkClient != null && networkClient.isConnected()) {
            return;
        }

        int blockX = (int) Math.floor(playerController.x());
        int blockZ = (int) Math.floor(playerController.z());
        int chunkX = Math.floorDiv(blockX, Section.SIZE);
        int chunkZ = Math.floorDiv(blockZ, Section.SIZE);
        worldView.ensureChunkRadius(chunkX, chunkZ, LOCAL_CHUNK_RADIUS);
    }

    private void handleBlockSelection(InputState input) {
        for (int slot = 0; slot < hotbarBlocks.length; slot++) {
            boolean keyDown = isHotbarKeyDown(input, slot);
            if (keyDown && !hotbarDownLastTick[slot]) {
                selectedHotbarSlot = slot;
                selectedBlock = hotbarBlocks[slot];
            }
            hotbarDownLastTick[slot] = keyDown;
        }
    }

    private void handleInteractions(InputState input) {
        BlockHitResult hit = targetedBlock;
        if (hit == null) {
            return;
        }

        boolean breakButtonDown = input.isMouseDown(MouseEvent.BUTTON1);
        boolean placeButtonDown = input.isMouseDown(MouseEvent.BUTTON3);

        if (breakButtonDown && !breakButtonDownLastTick) {
            if (worldView.setBlock(hit.targetBlock(), Blocks.AIR)) {
                sendNetworkBlockUpdate(hit.targetBlock(), Blocks.AIR);
            }
        }

        if (placeButtonDown && !placeButtonDownLastTick && hit.placementBlock() != null) {
            BlockPos placePos = hit.placementBlock();
            if (canPlaceBlock(placePos) && worldView.setBlock(placePos, selectedBlock)) {
                sendNetworkBlockUpdate(placePos, selectedBlock);
            }
        }

        breakButtonDownLastTick = breakButtonDown;
        placeButtonDownLastTick = placeButtonDown;
    }

    private void sendNetworkBlockUpdate(BlockPos pos, Block block) {
        if (networkClient == null || !networkClient.isConnected()) {
            return;
        }
        networkClient.sendBlockSet(pos, block);
    }

    private boolean canPlaceBlock(BlockPos pos) {
        if (!worldView.isWithinWorldY(pos.y())) {
            return false;
        }
        if (worldView.isSolid(pos.x(), pos.y(), pos.z())) {
            return false;
        }

        AABB blockBounds = new AABB(pos.x(), pos.y(), pos.z(), pos.x() + 1.0, pos.y() + 1.0, pos.z() + 1.0);
        return !playerController.boundingBox().intersects(blockBounds);
    }

    private BlockHitResult raycastFromPlayer(double maxDistance) {
        double originX = playerController.eyeX();
        double originY = playerController.eyeY();
        double originZ = playerController.eyeZ();

        double dirX = playerController.lookDirX();
        double dirY = playerController.lookDirY();
        double dirZ = playerController.lookDirZ();

        int lastX = (int) Math.floor(originX);
        int lastY = (int) Math.floor(originY);
        int lastZ = (int) Math.floor(originZ);

        double step = 0.05;
        for (double distance = 0.0; distance <= maxDistance; distance += step) {
            double sampleX = originX + dirX * distance;
            double sampleY = originY + dirY * distance;
            double sampleZ = originZ + dirZ * distance;

            int blockX = (int) Math.floor(sampleX);
            int blockY = (int) Math.floor(sampleY);
            int blockZ = (int) Math.floor(sampleZ);

            if (worldView.isWithinWorldY(blockY) && worldView.isSolid(blockX, blockY, blockZ)) {
                return new BlockHitResult(
                    new BlockPos(blockX, blockY, blockZ),
                    new BlockPos(lastX, lastY, lastZ),
                    distance
                );
            }

            lastX = blockX;
            lastY = blockY;
            lastZ = blockZ;
        }

        return null;
    }

    private static boolean isAnyKeyDown(InputState input, int primaryKey, int secondaryKey) {
        return input.isKeyDown(primaryKey) || input.isKeyDown(secondaryKey);
    }

    private static boolean isHotbarKeyDown(InputState input, int slot) {
        return switch (slot) {
            case 0 -> isAnyKeyDown(input, KeyEvent.VK_1, KeyEvent.VK_NUMPAD1);
            case 1 -> isAnyKeyDown(input, KeyEvent.VK_2, KeyEvent.VK_NUMPAD2);
            case 2 -> isAnyKeyDown(input, KeyEvent.VK_3, KeyEvent.VK_NUMPAD3);
            case 3 -> isAnyKeyDown(input, KeyEvent.VK_4, KeyEvent.VK_NUMPAD4);
            case 4 -> isAnyKeyDown(input, KeyEvent.VK_5, KeyEvent.VK_NUMPAD5);
            default -> false;
        };
    }

    private void initializeSpawn() {
        int spawnBlockX = 0;
        int spawnBlockZ = 0;
        int surfaceY = findSurfaceY(spawnBlockX, spawnBlockZ);
        double spawnY = Math.max(surfaceY + 1.0, 6.0);
        playerController.setSpawn(spawnBlockX + 0.5, spawnY, spawnBlockZ + 0.5);
        worldView.ensureChunkRadius(0, 0, LOCAL_CHUNK_RADIUS);
    }

    private int findSurfaceY(int x, int z) {
        for (int y = World.MAX_Y; y >= World.MIN_Y; y--) {
            if (worldView.isSolid(x, y, z)) {
                return y;
            }
        }
        return 3;
    }

    private void drawBackground(Graphics2D graphics, int width, int height) {
        float ambient = lightEngine.ambient();

        Color skyTop = shade(new Color(94, 170, 240), ambient);
        Color skyBottom = shade(new Color(178, 225, 255), ambient);

        graphics.setPaint(new GradientPaint(0, 0, skyTop, 0, height, skyBottom));
        graphics.fillRect(0, 0, width, height);

        int horizonY = (int) (height * 0.72);
        graphics.setColor(shade(new Color(84, 140, 72), ambient * 0.85f));
        graphics.fillRect(0, horizonY, width, height - horizonY);
    }

    private void drawCrosshair(Graphics2D graphics, int width, int height) {
        int centerX = width / 2;
        int centerY = height / 2;

        graphics.setColor(new Color(255, 255, 255, 230));
        graphics.drawLine(centerX - 8, centerY, centerX + 8, centerY);
        graphics.drawLine(centerX, centerY - 8, centerX, centerY + 8);
    }

    private void drawHotbar(Graphics2D graphics, int width, int height) {
        int slotSize = 54;
        int gap = 9;
        int slots = hotbarBlocks.length;
        int totalWidth = slots * slotSize + (slots - 1) * gap;
        int left = (width - totalWidth) / 2;
        int top = height - slotSize - 26;

        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        for (int slot = 0; slot < slots; slot++) {
            int slotX = left + slot * (slotSize + gap);
            boolean selected = slot == selectedHotbarSlot;
            graphics.setColor(selected ? new Color(255, 255, 255, 215) : new Color(20, 24, 28, 170));
            graphics.fillRoundRect(slotX, top, slotSize, slotSize, 8, 8);
            graphics.setColor(selected ? new Color(20, 24, 28, 220) : new Color(235, 235, 235, 180));
            graphics.drawRoundRect(slotX, top, slotSize, slotSize, 8, 8);

            String keyText = Integer.toString(slot + 1);
            graphics.setColor(selected ? new Color(10, 10, 10, 230) : new Color(255, 255, 255, 220));
            graphics.drawString(keyText, slotX + 6, top + 14);

            String label = hotbarLabel(hotbarBlocks[slot]);
            int textWidth = graphics.getFontMetrics().stringWidth(label);
            int textX = slotX + (slotSize - textWidth) / 2;
            int textY = top + 33;
            graphics.drawString(label, textX, textY);
        }
    }

    private void drawHud(Graphics2D graphics, RenderStats stats) {
        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        graphics.setColor(new Color(0, 0, 0, 130));
        graphics.fillRoundRect(12, 12, 780, 185, 12, 12);

        graphics.setColor(Color.WHITE);
        graphics.drawString(String.format("XYZ: %.2f %.2f %.2f", playerController.x(), playerController.y(), playerController.z()), 24, 34);
        graphics.drawString(String.format("Yaw/Pitch: %.1f / %.1f", playerController.yaw(), playerController.pitch()), 24, 54);
        graphics.drawString(
            String.format("Faces: total=%d, frustum=%d, drawn=%d", stats.totalFaces(), stats.frustumCandidates(), stats.drawnFaces()),
            24,
            74
        );
        graphics.drawString(String.format("Held Block: %s", selectedBlock.id()), 24, 94);
        graphics.drawString("WASD Move | Space Jump | Mouse Look | LMB Break | RMB Place | 1/2/3/4/5 Block", 24, 114);
        graphics.drawString(
            String.format(
                "Perf: %.1f FPS | %.1f ms | CPU %s | RAM %d/%d MB | Cores %d",
                smoothedFps,
                smoothedFrameMs,
                hardwareSnapshot.cpuText(),
                hardwareSnapshot.heapUsedMb(),
                hardwareSnapshot.heapMaxMb(),
                hardwareSnapshot.logicalCores()
            ),
            24,
            134
        );
        graphics.drawString("Network: " + networkStatusLine, 24, 154);
        graphics.drawString("Mouse auto-captured and cursor hidden while focused | ESC exits", 24, 174);
    }

    private static String hotbarLabel(Block block) {
        if (block == Blocks.DIRT) {
            return "Dirt";
        }
        if (block == Blocks.STONE) {
            return "Stone";
        }
        if (block == Blocks.GRASS) {
            return "Grass";
        }
        if (block == Blocks.SAND) {
            return "Sand";
        }
        if (block == Blocks.WOOD) {
            return "Wood";
        }
        return "Block";
    }

    private static Color shade(Color color, float amount) {
        int red = clamp((int) (color.getRed() * amount));
        int green = clamp((int) (color.getGreen() * amount));
        int blue = clamp((int) (color.getBlue() * amount));
        return new Color(red, green, blue);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
