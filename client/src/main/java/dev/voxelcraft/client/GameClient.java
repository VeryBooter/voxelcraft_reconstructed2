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
/**
 * 中文说明：客户端主状态对象：串联输入、玩家、世界、交互逻辑与渲染运行时。
 */

// 中文标注（类）：`GameClient`，职责：封装game、客户端相关逻辑。
public final class GameClient implements AutoCloseable {
    // 中文标注（字段）：`INTERACTION_REACH`，含义：用于表示interaction、reach。
    private static final double INTERACTION_REACH = 6.0;
    // 中文标注（字段）：`NETWORK_STATE_SEND_INTERVAL_SECONDS`，含义：用于表示网络、状态、send、interval、seconds。
    private static final double NETWORK_STATE_SEND_INTERVAL_SECONDS = 0.05;
    // 中文标注（字段）：`NETWORK_CHUNK_RADIUS`，含义：用于表示网络、区块、radius。
    private static final int NETWORK_CHUNK_RADIUS = 3;
    // 中文标注（字段）：`LOCAL_CHUNK_RADIUS`，含义：用于表示局部、区块、radius。
    private static final int LOCAL_CHUNK_RADIUS = 3;
    // 中文标注（字段）：`LOCAL_CHUNK_GENERATION_BUDGET_PER_TICK`，含义：用于表示局部、区块、generation、budget、per、刻。
    private static final int LOCAL_CHUNK_GENERATION_BUDGET_PER_TICK = 2;

    // 中文标注（字段）：`game`，含义：用于表示game。
    private final Game game = new Game();
    // 中文标注（字段）：`playerController`，含义：用于表示玩家、控制器。
    private final PlayerController playerController = new PlayerController();
    // 中文标注（字段）：`worldView`，含义：用于表示世界、view。
    private final ClientWorldView worldView = new ClientWorldView(game.world());
    // 中文标注（字段）：`renderSystem`，含义：用于表示渲染、system。
    private final ChunkRenderSystem renderSystem = new ChunkRenderSystem();
    // 中文标注（字段）：`lightEngine`，含义：用于表示光照、engine。
    private final LightEngine lightEngine = new LightEngine();
    // 中文标注（字段）：`hardwareStatsSampler`，含义：用于表示hardware、stats、sampler。
    private final HardwareStatsSampler hardwareStatsSampler = new HardwareStatsSampler();

    // 中文标注（字段）：`hotbarBlocks`，含义：用于表示hotbar、方块集合。
    private final Block[] hotbarBlocks = {
        Blocks.DIRT,
        Blocks.STONE,
        Blocks.GRASS,
        Blocks.SAND,
        Blocks.WOOD
    };
    // 中文标注（字段）：`hotbarDownLastTick`，含义：用于表示hotbar、down、last、刻。
    private final boolean[] hotbarDownLastTick = new boolean[hotbarBlocks.length];

    // 中文标注（字段）：`networkClient`，含义：用于表示网络、客户端。
    private NetworkClient networkClient;

    // 中文标注（字段）：`targetedBlock`，含义：用于表示targeted、方块。
    private BlockHitResult targetedBlock;
    // 中文标注（字段）：`selectedBlock`，含义：用于表示selected、方块。
    private Block selectedBlock = hotbarBlocks[0];
    // 中文标注（字段）：`selectedHotbarSlot`，含义：用于表示selected、hotbar、slot。
    private int selectedHotbarSlot;
    // 中文标注（字段）：`networkStatusLine`，含义：用于表示网络、status、line。
    private String networkStatusLine = "singleplayer";
    // 中文标注（字段）：`hardwareSnapshot`，含义：用于表示hardware、快照。
    private HardwareStatsSampler.Snapshot hardwareSnapshot = hardwareStatsSampler.sample();

    // 中文标注（字段）：`networkStateSendAccumulator`，含义：用于表示网络、状态、send、accumulator。
    private double networkStateSendAccumulator;
    // 中文标注（字段）：`hardwareSampleAccumulator`，含义：用于表示hardware、sample、accumulator。
    private double hardwareSampleAccumulator;
    // 中文标注（字段）：`smoothedFrameMs`，含义：用于表示smoothed、帧、ms。
    private double smoothedFrameMs = 16.7;
    // 中文标注（字段）：`smoothedFps`，含义：用于表示smoothed、fps。
    private double smoothedFps = 60.0;
    // 中文标注（字段）：`lastRequestedChunkX`，含义：用于表示last、requested、区块、X坐标。
    private int lastRequestedChunkX = Integer.MIN_VALUE;
    // 中文标注（字段）：`lastRequestedChunkZ`，含义：用于表示last、requested、区块、Z坐标。
    private int lastRequestedChunkZ = Integer.MIN_VALUE;
    // 中文标注（字段）：`breakButtonDownLastTick`，含义：用于表示break、button、down、last、刻。
    private boolean breakButtonDownLastTick;
    // 中文标注（字段）：`placeButtonDownLastTick`，含义：用于表示place、button、down、last、刻。
    private boolean placeButtonDownLastTick;
    // 中文标注（字段）：`lastEnsureLocalChunksNanos`，含义：用于表示last、ensure、局部、区块集合、nanos。
    private long lastEnsureLocalChunksNanos;
    // 中文标注（字段）：`lastChunkGenerationDrainNanos`，含义：用于表示last、区块、generation、drain、nanos。
    private long lastChunkGenerationDrainNanos;

    // 中文标注（构造方法）：`GameClient`，参数：无；用途：初始化`GameClient`实例。
    public GameClient() {
        initializeSpawn();
    }

    // 中文标注（方法）：`attachNetwork`，参数：networkClient；用途：执行attach、网络相关逻辑。
    // 中文标注（参数）：`networkClient`，含义：用于表示网络、客户端。
    public void attachNetwork(NetworkClient networkClient) {
        this.networkClient = networkClient;
        this.networkStatusLine = networkClient.statusLine();
        requestChunksIfNeeded(true);
    }

    // 中文标注（方法）：`tick`，参数：input、deltaSeconds；用途：更新刻相关状态。
    // 中文标注（参数）：`input`，含义：用于表示输入。
    // 中文标注（参数）：`deltaSeconds`，含义：用于表示增量、seconds。
    public void tick(InputState input, double deltaSeconds) {
        if (networkClient != null) {
            networkClient.drainIncoming(worldView);
            networkStatusLine = networkClient.statusLine();
        }

        // 中文标注（局部变量）：`frameMs`，含义：用于表示帧、ms。
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

        // 中文标注（局部变量）：`ensureStarted`，含义：用于表示ensure、started。
        long ensureStarted = System.nanoTime();
        ensureLocalChunksAroundPlayer();
        lastEnsureLocalChunksNanos = System.nanoTime() - ensureStarted;
        // 中文标注（局部变量）：`chunkDrainStarted`，含义：用于表示区块、drain、started。
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

    // 中文标注（方法）：`render`，参数：graphics、width、height；用途：执行渲染或图形资源处理：渲染。
    // 中文标注（参数）：`graphics`，含义：用于表示graphics。
    // 中文标注（参数）：`width`，含义：用于表示宽度。
    // 中文标注（参数）：`height`，含义：用于表示高度。
    public void render(Graphics2D graphics, int width, int height) {
        drawBackground(graphics, width, height);

        // 中文标注（局部变量）：`stats`，含义：用于表示stats。
        RenderStats stats = renderSystem.draw(graphics, width, height, worldView, playerController, ambientLight());
        if (targetedBlock != null) {
            renderSystem.drawSelectionBox(graphics, width, height, playerController, targetedBlock.targetBlock());
        }

        drawCrosshair(graphics, width, height);
        drawHotbar(graphics, width, height);
        drawHud(graphics, stats);
    }

    // 中文标注（方法）：`worldView`，参数：无；用途：执行世界、view相关逻辑。
    public ClientWorldView worldView() {
        return worldView;
    }

    // 中文标注（方法）：`playerController`，参数：无；用途：执行玩家、控制器相关逻辑。
    public PlayerController playerController() {
        return playerController;
    }

    // 中文标注（方法）：`targetedBlock`，参数：无；用途：执行targeted、方块相关逻辑。
    public BlockHitResult targetedBlock() {
        return targetedBlock;
    }

    // 中文标注（方法）：`ambientLight`，参数：无；用途：执行环境光、光照相关逻辑。
    public float ambientLight() {
        return lightEngine.ambient();
    }

    // 中文标注（方法）：`networkStatusLine`，参数：无；用途：执行网络、status、line相关逻辑。
    public String networkStatusLine() {
        return networkStatusLine;
    }

    // 中文标注（方法）：`selectedBlock`，参数：无；用途：执行selected、方块相关逻辑。
    public Block selectedBlock() {
        return selectedBlock;
    }

    public int selectedHotbarSlot() {
        return selectedHotbarSlot;
    }

    public int hotbarSlotCount() {
        return hotbarBlocks.length;
    }

    // 中文标注（方法）：`lastEnsureLocalChunksNanos`，参数：无；用途：执行last、ensure、局部、区块集合、nanos相关逻辑。
    public long lastEnsureLocalChunksNanos() {
        return lastEnsureLocalChunksNanos;
    }

    // 中文标注（方法）：`lastChunkGenerationDrainNanos`，参数：无；用途：执行last、区块、generation、drain、nanos相关逻辑。
    public long lastChunkGenerationDrainNanos() {
        return lastChunkGenerationDrainNanos;
    }

    // 中文标注（方法）：`lastChunkGenSubmitNanos`，参数：无；用途：执行last、区块、gen、submit、nanos相关逻辑。
    public long lastChunkGenSubmitNanos() {
        return worldView.lastChunkGenSubmitNanos();
    }

    // 中文标注（方法）：`lastChunkInstallNanos`，参数：无；用途：执行last、区块、install、nanos相关逻辑。
    public long lastChunkInstallNanos() {
        return worldView.lastChunkInstallNanos();
    }

    // 中文标注（方法）：`lastChunkGenSubmittedCount`，参数：无；用途：执行last、区块、gen、submitted、数量相关逻辑。
    public int lastChunkGenSubmittedCount() {
        return worldView.lastChunkGenSubmittedCount();
    }

    // 中文标注（方法）：`lastChunkInstalledCount`，参数：无；用途：执行last、区块、installed、数量相关逻辑。
    public int lastChunkInstalledCount() {
        return worldView.lastChunkInstalledCount();
    }

    // 中文标注（方法）：`chunkGenerationJobsInFlight`，参数：无；用途：执行区块、generation、jobs、in、flight相关逻辑。
    public int chunkGenerationJobsInFlight() {
        return worldView.chunkGenerationJobsInFlight();
    }

    // 中文标注（方法）：`readyGeneratedChunkCount`，参数：无；用途：获取或读取ready、generated、区块、数量。
    public int readyGeneratedChunkCount() {
        return worldView.readyGeneratedChunkCount();
    }

    // 中文标注（方法）：`pendingChunkGenerationCount`，参数：无；用途：执行pending、区块、generation、数量相关逻辑。
    public int pendingChunkGenerationCount() {
        return worldView.pendingChunkGenerationCount();
    }

    // 中文标注（方法）：`close`，参数：无；用途：执行close相关逻辑。
    @Override
    public void close() {
        if (networkClient != null) {
            networkClient.close();
            networkClient = null;
        }
        worldView.close();
    }

    // 中文标注（方法）：`requestChunksIfNeeded`，参数：force；用途：执行request、区块集合、if、needed相关逻辑。
    // 中文标注（参数）：`force`，含义：用于表示force。
    private void requestChunksIfNeeded(boolean force) {
        if (networkClient == null || !networkClient.isConnected()) {
            return;
        }

        // 中文标注（局部变量）：`blockX`，含义：用于表示方块、X坐标。
        int blockX = (int) Math.floor(playerController.x());
        // 中文标注（局部变量）：`blockZ`，含义：用于表示方块、Z坐标。
        int blockZ = (int) Math.floor(playerController.z());
        // 中文标注（局部变量）：`chunkX`，含义：用于表示区块、X坐标。
        int chunkX = Math.floorDiv(blockX, Section.SIZE);
        // 中文标注（局部变量）：`chunkZ`，含义：用于表示区块、Z坐标。
        int chunkZ = Math.floorDiv(blockZ, Section.SIZE);

        if (!force && chunkX == lastRequestedChunkX && chunkZ == lastRequestedChunkZ) {
            return;
        }

        lastRequestedChunkX = chunkX;
        lastRequestedChunkZ = chunkZ;
        networkClient.requestChunkRadius(chunkX, chunkZ, NETWORK_CHUNK_RADIUS);
    }

    // 中文标注（方法）：`ensureLocalChunksAroundPlayer`，参数：无；用途：执行ensure、局部、区块集合、around、玩家相关逻辑。
    private void ensureLocalChunksAroundPlayer() {
        if (networkClient != null && networkClient.isConnected()) {
            return;
        }

        // 中文标注（局部变量）：`blockX`，含义：用于表示方块、X坐标。
        int blockX = (int) Math.floor(playerController.x());
        // 中文标注（局部变量）：`blockZ`，含义：用于表示方块、Z坐标。
        int blockZ = (int) Math.floor(playerController.z());
        // 中文标注（局部变量）：`chunkX`，含义：用于表示区块、X坐标。
        int chunkX = Math.floorDiv(blockX, Section.SIZE);
        // 中文标注（局部变量）：`chunkZ`，含义：用于表示区块、Z坐标。
        int chunkZ = Math.floorDiv(blockZ, Section.SIZE);
        worldView.ensureChunkRadius(chunkX, chunkZ, LOCAL_CHUNK_RADIUS);
    }

    // 中文标注（方法）：`handleBlockSelection`，参数：input；用途：处理handle、方块、selection逻辑。
    // 中文标注（参数）：`input`，含义：用于表示输入。
    private void handleBlockSelection(InputState input) {
        // 中文标注（局部变量）：`slot`，含义：用于表示slot。
        for (int slot = 0; slot < hotbarBlocks.length; slot++) {
            // 中文标注（局部变量）：`keyDown`，含义：用于表示键、down。
            boolean keyDown = isHotbarKeyDown(input, slot);
            if (keyDown && !hotbarDownLastTick[slot]) {
                selectedHotbarSlot = slot;
                selectedBlock = hotbarBlocks[slot];
            }
            hotbarDownLastTick[slot] = keyDown;
        }
    }

    // 中文标注（方法）：`handleInteractions`，参数：input；用途：处理handle、interactions逻辑。
    // 中文标注（参数）：`input`，含义：用于表示输入。
    private void handleInteractions(InputState input) {
        // 中文标注（局部变量）：`hit`，含义：用于表示命中。
        BlockHitResult hit = targetedBlock;
        if (hit == null) {
            return;
        }

        // 中文标注（局部变量）：`breakButtonDown`，含义：用于表示break、button、down。
        boolean breakButtonDown = input.isMouseDown(MouseEvent.BUTTON1);
        // 中文标注（局部变量）：`placeButtonDown`，含义：用于表示place、button、down。
        boolean placeButtonDown = input.isMouseDown(MouseEvent.BUTTON3);

        if (breakButtonDown && !breakButtonDownLastTick) {
            if (worldView.setBlock(hit.targetBlock(), Blocks.AIR)) {
                sendNetworkBlockUpdate(hit.targetBlock(), Blocks.AIR);
            }
        }

        if (placeButtonDown && !placeButtonDownLastTick && hit.placementBlock() != null) {
            // 中文标注（局部变量）：`placePos`，含义：用于表示place、位置。
            BlockPos placePos = hit.placementBlock();
            if (canPlaceBlock(placePos) && worldView.setBlock(placePos, selectedBlock)) {
                sendNetworkBlockUpdate(placePos, selectedBlock);
            }
        }

        breakButtonDownLastTick = breakButtonDown;
        placeButtonDownLastTick = placeButtonDown;
    }

    // 中文标注（方法）：`sendNetworkBlockUpdate`，参数：pos、block；用途：执行send、网络、方块、更新相关逻辑。
    // 中文标注（参数）：`pos`，含义：用于表示位置。
    // 中文标注（参数）：`block`，含义：用于表示方块。
    private void sendNetworkBlockUpdate(BlockPos pos, Block block) {
        if (networkClient == null || !networkClient.isConnected()) {
            return;
        }
        networkClient.sendBlockSet(pos, block);
    }

    // 中文标注（方法）：`canPlaceBlock`，参数：pos；用途：判断place、方块是否满足条件。
    // 中文标注（参数）：`pos`，含义：用于表示位置。
    private boolean canPlaceBlock(BlockPos pos) {
        if (!worldView.isWithinWorldY(pos.y())) {
            return false;
        }
        if (worldView.isSolid(pos.x(), pos.y(), pos.z())) {
            return false;
        }

        // 中文标注（局部变量）：`blockBounds`，含义：用于表示方块、bounds。
        AABB blockBounds = new AABB(pos.x(), pos.y(), pos.z(), pos.x() + 1.0, pos.y() + 1.0, pos.z() + 1.0);
        return !playerController.boundingBox().intersects(blockBounds);
    }

    // 中文标注（方法）：`raycastFromPlayer`，参数：maxDistance；用途：执行raycast、from、玩家相关逻辑。
    // 中文标注（参数）：`maxDistance`，含义：用于表示最大、distance。
    private BlockHitResult raycastFromPlayer(double maxDistance) {
        // 中文标注（局部变量）：`originX`，含义：用于表示origin、X坐标。
        double originX = playerController.eyeX();
        // 中文标注（局部变量）：`originY`，含义：用于表示origin、Y坐标。
        double originY = playerController.eyeY();
        // 中文标注（局部变量）：`originZ`，含义：用于表示origin、Z坐标。
        double originZ = playerController.eyeZ();

        // 中文标注（局部变量）：`dirX`，含义：用于表示dir、X坐标。
        double dirX = playerController.lookDirX();
        // 中文标注（局部变量）：`dirY`，含义：用于表示dir、Y坐标。
        double dirY = playerController.lookDirY();
        // 中文标注（局部变量）：`dirZ`，含义：用于表示dir、Z坐标。
        double dirZ = playerController.lookDirZ();

        // 中文标注（局部变量）：`lastX`，含义：用于表示last、X坐标。
        int lastX = (int) Math.floor(originX);
        // 中文标注（局部变量）：`lastY`，含义：用于表示last、Y坐标。
        int lastY = (int) Math.floor(originY);
        // 中文标注（局部变量）：`lastZ`，含义：用于表示last、Z坐标。
        int lastZ = (int) Math.floor(originZ);

        // 中文标注（局部变量）：`step`，含义：用于表示step。
        double step = 0.05;
        // 中文标注（局部变量）：`distance`，含义：用于表示distance。
        for (double distance = 0.0; distance <= maxDistance; distance += step) {
            // 中文标注（局部变量）：`sampleX`，含义：用于表示sample、X坐标。
            double sampleX = originX + dirX * distance;
            // 中文标注（局部变量）：`sampleY`，含义：用于表示sample、Y坐标。
            double sampleY = originY + dirY * distance;
            // 中文标注（局部变量）：`sampleZ`，含义：用于表示sample、Z坐标。
            double sampleZ = originZ + dirZ * distance;

            // 中文标注（局部变量）：`blockX`，含义：用于表示方块、X坐标。
            int blockX = (int) Math.floor(sampleX);
            // 中文标注（局部变量）：`blockY`，含义：用于表示方块、Y坐标。
            int blockY = (int) Math.floor(sampleY);
            // 中文标注（局部变量）：`blockZ`，含义：用于表示方块、Z坐标。
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

    // 中文标注（方法）：`isAnyKeyDown`，参数：input、primaryKey、secondaryKey；用途：判断any、键、down是否满足条件。
    // 中文标注（参数）：`input`，含义：用于表示输入。
    // 中文标注（参数）：`primaryKey`，含义：用于表示primary、键。
    // 中文标注（参数）：`secondaryKey`，含义：用于表示secondary、键。
    private static boolean isAnyKeyDown(InputState input, int primaryKey, int secondaryKey) {
        return input.isKeyDown(primaryKey) || input.isKeyDown(secondaryKey);
    }

    // 中文标注（方法）：`isHotbarKeyDown`，参数：input、slot；用途：判断hotbar、键、down是否满足条件。
    // 中文标注（参数）：`input`，含义：用于表示输入。
    // 中文标注（参数）：`slot`，含义：用于表示slot。
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

    // 中文标注（方法）：`initializeSpawn`，参数：无；用途：执行initialize、spawn相关逻辑。
    private void initializeSpawn() {
        // 中文标注（局部变量）：`spawnBlockX`，含义：用于表示spawn、方块、X坐标。
        int spawnBlockX = 0;
        // 中文标注（局部变量）：`spawnBlockZ`，含义：用于表示spawn、方块、Z坐标。
        int spawnBlockZ = 0;
        // Budgeted local chunk generation now only queues work, so force the center chunk to exist
        // before scanning for surface height; otherwise spawn may be computed against an empty world.
        worldView.world().getOrGenerateChunk(0, 0);
        // 中文标注（局部变量）：`surfaceY`，含义：用于表示surface、Y坐标。
        int surfaceY = findSurfaceY(spawnBlockX, spawnBlockZ);
        // 中文标注（局部变量）：`spawnY`，含义：用于表示spawn、Y坐标。
        double spawnY = Math.max(surfaceY + 1.0, 6.0);
        playerController.setSpawn(spawnBlockX + 0.5, spawnY, spawnBlockZ + 0.5);
        worldView.ensureChunkRadius(0, 0, LOCAL_CHUNK_RADIUS);
    }

    // 中文标注（方法）：`findSurfaceY`，参数：x、z；用途：获取或读取find、surface、Y坐标。
    // 中文标注（参数）：`x`，含义：用于表示X坐标。
    // 中文标注（参数）：`z`，含义：用于表示Z坐标。
    private int findSurfaceY(int x, int z) {
        // 中文标注（局部变量）：`y`，含义：用于表示Y坐标。
        for (int y = World.MAX_Y; y >= World.MIN_Y; y--) {
            if (worldView.isSolid(x, y, z)) {
                return y;
            }
        }
        return 3;
    }

    // 中文标注（方法）：`drawBackground`，参数：graphics、width、height；用途：执行渲染或图形资源处理：绘制、background。
    // 中文标注（参数）：`graphics`，含义：用于表示graphics。
    // 中文标注（参数）：`width`，含义：用于表示宽度。
    // 中文标注（参数）：`height`，含义：用于表示高度。
    private void drawBackground(Graphics2D graphics, int width, int height) {
        // 中文标注（局部变量）：`ambient`，含义：用于表示环境光。
        float ambient = lightEngine.ambient();

        // 中文标注（局部变量）：`skyTop`，含义：用于表示天空、顶面。
        Color skyTop = shade(new Color(94, 170, 240), ambient);
        // 中文标注（局部变量）：`skyBottom`，含义：用于表示天空、底面。
        Color skyBottom = shade(new Color(178, 225, 255), ambient);

        graphics.setPaint(new GradientPaint(0, 0, skyTop, 0, height, skyBottom));
        graphics.fillRect(0, 0, width, height);

        // 中文标注（局部变量）：`horizonY`，含义：用于表示horizon、Y坐标。
        int horizonY = (int) (height * 0.72);
        graphics.setColor(shade(new Color(84, 140, 72), ambient * 0.85f));
        graphics.fillRect(0, horizonY, width, height - horizonY);
    }

    // 中文标注（方法）：`drawCrosshair`，参数：graphics、width、height；用途：执行渲染或图形资源处理：绘制、crosshair。
    // 中文标注（参数）：`graphics`，含义：用于表示graphics。
    // 中文标注（参数）：`width`，含义：用于表示宽度。
    // 中文标注（参数）：`height`，含义：用于表示高度。
    private void drawCrosshair(Graphics2D graphics, int width, int height) {
        // 中文标注（局部变量）：`centerX`，含义：用于表示center、X坐标。
        int centerX = width / 2;
        // 中文标注（局部变量）：`centerY`，含义：用于表示center、Y坐标。
        int centerY = height / 2;

        graphics.setColor(new Color(255, 255, 255, 230));
        graphics.drawLine(centerX - 8, centerY, centerX + 8, centerY);
        graphics.drawLine(centerX, centerY - 8, centerX, centerY + 8);
    }

    // 中文标注（方法）：`drawHotbar`，参数：graphics、width、height；用途：执行渲染或图形资源处理：绘制、hotbar。
    // 中文标注（参数）：`graphics`，含义：用于表示graphics。
    // 中文标注（参数）：`width`，含义：用于表示宽度。
    // 中文标注（参数）：`height`，含义：用于表示高度。
    private void drawHotbar(Graphics2D graphics, int width, int height) {
        // 中文标注（局部变量）：`slotSize`，含义：用于表示slot、大小。
        int slotSize = 54;
        // 中文标注（局部变量）：`gap`，含义：用于表示gap。
        int gap = 9;
        // 中文标注（局部变量）：`slots`，含义：用于表示slots。
        int slots = hotbarBlocks.length;
        // 中文标注（局部变量）：`totalWidth`，含义：用于表示total、宽度。
        int totalWidth = slots * slotSize + (slots - 1) * gap;
        // 中文标注（局部变量）：`left`，含义：用于表示left。
        int left = (width - totalWidth) / 2;
        // 中文标注（局部变量）：`top`，含义：用于表示顶面。
        int top = height - slotSize - 26;

        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        // 中文标注（局部变量）：`slot`，含义：用于表示slot。
        for (int slot = 0; slot < slots; slot++) {
            // 中文标注（局部变量）：`slotX`，含义：用于表示slot、X坐标。
            int slotX = left + slot * (slotSize + gap);
            // 中文标注（局部变量）：`selected`，含义：用于表示selected。
            boolean selected = slot == selectedHotbarSlot;
            graphics.setColor(selected ? new Color(255, 255, 255, 215) : new Color(20, 24, 28, 170));
            graphics.fillRoundRect(slotX, top, slotSize, slotSize, 8, 8);
            graphics.setColor(selected ? new Color(20, 24, 28, 220) : new Color(235, 235, 235, 180));
            graphics.drawRoundRect(slotX, top, slotSize, slotSize, 8, 8);

            // 中文标注（局部变量）：`keyText`，含义：用于表示键、text。
            String keyText = Integer.toString(slot + 1);
            graphics.setColor(selected ? new Color(10, 10, 10, 230) : new Color(255, 255, 255, 220));
            graphics.drawString(keyText, slotX + 6, top + 14);

            // 中文标注（局部变量）：`label`，含义：用于表示label。
            String label = hotbarLabel(hotbarBlocks[slot]);
            // 中文标注（局部变量）：`textWidth`，含义：用于表示text、宽度。
            int textWidth = graphics.getFontMetrics().stringWidth(label);
            // 中文标注（局部变量）：`textX`，含义：用于表示text、X坐标。
            int textX = slotX + (slotSize - textWidth) / 2;
            // 中文标注（局部变量）：`textY`，含义：用于表示text、Y坐标。
            int textY = top + 33;
            graphics.drawString(label, textX, textY);
        }
    }

    // 中文标注（方法）：`drawHud`，参数：graphics、stats；用途：执行渲染或图形资源处理：绘制、hud。
    // 中文标注（参数）：`graphics`，含义：用于表示graphics。
    // 中文标注（参数）：`stats`，含义：用于表示stats。
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

    // 中文标注（方法）：`hotbarLabel`，参数：block；用途：执行hotbar、label相关逻辑。
    // 中文标注（参数）：`block`，含义：用于表示方块。
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

    // 中文标注（方法）：`shade`，参数：color、amount；用途：执行shade相关逻辑。
    // 中文标注（参数）：`color`，含义：用于表示颜色。
    // 中文标注（参数）：`amount`，含义：用于表示amount。
    private static Color shade(Color color, float amount) {
        // 中文标注（局部变量）：`red`，含义：用于表示red。
        int red = clamp((int) (color.getRed() * amount));
        // 中文标注（局部变量）：`green`，含义：用于表示green。
        int green = clamp((int) (color.getGreen() * amount));
        // 中文标注（局部变量）：`blue`，含义：用于表示blue。
        int blue = clamp((int) (color.getBlue() * amount));
        return new Color(red, green, blue);
    }

    // 中文标注（方法）：`clamp`，参数：value；用途：执行clamp相关逻辑。
    // 中文标注（参数）：`value`，含义：用于表示值。
    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
