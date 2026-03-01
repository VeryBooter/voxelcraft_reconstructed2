package dev.voxelcraft.client;

import dev.voxelcraft.client.audio.MusicDirector;
import dev.voxelcraft.client.light.LightEngine;
import dev.voxelcraft.client.network.NetworkClient;
import dev.voxelcraft.client.physics.AABB;
import dev.voxelcraft.client.platform.InputState;
import dev.voxelcraft.client.player.PlayerController;
import dev.voxelcraft.client.render.ChunkRenderSystem;
import dev.voxelcraft.client.render.ChunkRenderSystem.RenderStats;
import dev.voxelcraft.client.ui.BlockCatalog;
import dev.voxelcraft.client.world.BlockHitResult;
import dev.voxelcraft.client.world.ClientWorldView;
import dev.voxelcraft.core.Game;
import dev.voxelcraft.core.block.Block;
import dev.voxelcraft.core.block.BlockDef;
import dev.voxelcraft.core.block.Blocks;
import dev.voxelcraft.core.world.BlockPos;
import dev.voxelcraft.core.world.Section;
import dev.voxelcraft.core.world.World;
import dev.voxelcraft.core.world.WorldStack;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;
/**
 * 中文说明：客户端主状态对象：串联输入、玩家、世界、交互逻辑与渲染运行时。
 */

// 中文标注（类）：`GameClient`，职责：封装game、客户端相关逻辑。
public final class GameClient implements AutoCloseable {
    // 中文标注（字段）：`INTERACTION_REACH`，含义：用于表示interaction、reach。
    private static final double INTERACTION_REACH = 6.0; // meaning
    // 中文标注（字段）：`NETWORK_STATE_SEND_INTERVAL_SECONDS`，含义：用于表示网络、状态、send、interval、seconds。
    private static final double NETWORK_STATE_SEND_INTERVAL_SECONDS = 0.05; // meaning
    // 中文标注（字段）：`RENDER_DISTANCE_NEAR_RADIUS`，含义：用于表示渲染、距离、近、半径。
    private static final int RENDER_DISTANCE_NEAR_RADIUS = 16; // meaning
    // 中文标注（字段）：`RENDER_DISTANCE_MEDIUM_RADIUS`，含义：用于表示渲染、距离、中、半径。
    private static final int RENDER_DISTANCE_MEDIUM_RADIUS = 27; // meaning
    // 中文标注（字段）：`RENDER_DISTANCE_FAR_RADIUS`，含义：用于表示渲染、距离、远、半径。
    private static final int RENDER_DISTANCE_FAR_RADIUS = 50; // meaning
    private static final int LOCAL_CHUNK_IMMEDIATE_RADIUS = clampImmediateChunkRadius(
        intPropertyCompat("vc.chunkImmediateRadius", "voxelcraft.chunkImmediateRadius", 2)
    ); // meaning
    private static final long IMMEDIATE_CHUNK_SYNC_LOG_THROTTLE_NANOS = 1_000_000_000L; // meaning
    // 中文标注（字段）：`LOCAL_CHUNK_GENERATION_BUDGET_PER_TICK`，含义：用于表示局部、区块、generation、budget、per、刻。
    private static final int LOCAL_CHUNK_GENERATION_BUDGET_PER_TICK = 2; // meaning
    private static final boolean WORMHOLE_FEATURE_ENABLED = booleanPropertyCompat(
        "vc.wormhole.enabled",
        "voxelcraft.wormhole.enabled",
        false
    );
    private static final int WORMHOLE_TOGGLE_KEY = KeyEvent.VK_V; // meaning
    private static final double WORMHOLE_EXIT_ROOM_HALF_EXTENT = 3.0; // meaning
    private static final double WORMHOLE_NORTH_DRIFT = +0.8; // meaning
    private static final double WORMHOLE_EAST_DRIFT = +2.0; // meaning
    private static final double WORMHOLE_SOUTH_DRIFT = -0.8; // meaning
    private static final double WORMHOLE_WEST_DRIFT = -2.0; // meaning
    private static final double WORMHOLE_SNAP_HYSTERESIS = 0.60; // meaning
    private static final int BLOCK_PICKER_TOGGLE_KEY = KeyEvent.VK_E; // meaning
    private static final int SETTINGS_TOGGLE_KEY = KeyEvent.VK_O; // meaning
    private static final double MAX_SIMULATION_STEP_SECONDS = 0.05; // meaning
    private static final double MAX_SIMULATION_CATCHUP_SECONDS = 0.25; // meaning
    private static final int BLOCK_PICKER_MAX_COLUMNS = 12; // meaning
    private static final int BLOCK_PICKER_MAX_ROWS = 7; // meaning

    // 中文标注（字段）：`game`，含义：用于表示game。
    private final Game game = new Game(); // meaning
    // 中文标注（字段）：`playerController`，含义：用于表示玩家、控制器。
    private final PlayerController playerController = new PlayerController(); // meaning
    // 中文标注（字段）：`worldView`，含义：用于表示世界、view。
    private ClientWorldView worldView = new ClientWorldView(game.world()); // meaning
    // 中文标注（字段）：`renderSystem`，含义：用于表示渲染、system。
    private ChunkRenderSystem renderSystem = new ChunkRenderSystem(); // meaning
    // 中文标注（字段）：`lightEngine`，含义：用于表示光照、engine。
    private LightEngine lightEngine = new LightEngine(); // meaning
    private final MusicDirector musicDirector = MusicDirector.createDefault();
    // 中文标注（字段）：`hardwareStatsSampler`，含义：用于表示hardware、stats、sampler。
    private final HardwareStatsSampler hardwareStatsSampler = new HardwareStatsSampler(); // meaning

    // 中文标注（字段）：`hotbarBlocks`，含义：用于表示hotbar、方块集合。
    private final Block[] hotbarBlocks = {
        Blocks.DIRT,
        Blocks.STONE,
        Blocks.GRASS,
        Blocks.SAND,
        Blocks.WOOD
    };
    // 中文标注（字段）：`hotbarDownLastTick`，含义：用于表示hotbar、down、last、刻。
    private final boolean[] hotbarDownLastTick = new boolean[hotbarBlocks.length]; // meaning
    private final BlockCatalog blockCatalog = new BlockCatalog(); // meaning
    private final List<String> pickerCategories = blockCatalog.categoryPrefixes(); // meaning
    private List<BlockDef> pickerFilteredBlocks = List.of(); // meaning

    // 中文标注（字段）：`networkClient`，含义：用于表示网络、客户端。
    private NetworkClient networkClient; // meaning

    // 中文标注（字段）：`targetedBlock`，含义：用于表示targeted、方块。
    private BlockHitResult targetedBlock; // meaning
    // 中文标注（字段）：`selectedBlock`，含义：用于表示selected、方块。
    private Block selectedBlock = hotbarBlocks[0]; // meaning
    // 中文标注（字段）：`selectedHotbarSlot`，含义：用于表示selected、hotbar、slot。
    private int selectedHotbarSlot; // meaning
    // 中文标注（字段）：`networkStatusLine`，含义：用于表示网络、status、line。
    private String networkStatusLine = "singleplayer"; // meaning
    // 中文标注（字段）：`hardwareSnapshot`，含义：用于表示hardware、快照。
    private HardwareStatsSampler.Snapshot hardwareSnapshot = hardwareStatsSampler.sample(); // meaning

    // 中文标注（字段）：`networkStateSendAccumulator`，含义：用于表示网络、状态、send、accumulator。
    private double networkStateSendAccumulator; // meaning
    // 中文标注（字段）：`hardwareSampleAccumulator`，含义：用于表示hardware、sample、accumulator。
    private double hardwareSampleAccumulator; // meaning
    // 中文标注（字段）：`smoothedFrameMs`，含义：用于表示smoothed、帧、ms。
    private double smoothedFrameMs = 16.7; // meaning
    // 中文标注（字段）：`smoothedFps`，含义：用于表示smoothed、fps。
    private double smoothedFps = 60.0; // meaning
    // 中文标注（字段）：`lastRequestedChunkX`，含义：用于表示last、requested、区块、X坐标。
    private int lastRequestedChunkX = Integer.MIN_VALUE; // meaning
    // 中文标注（字段）：`lastRequestedChunkZ`，含义：用于表示last、requested、区块、Z坐标。
    private int lastRequestedChunkZ = Integer.MIN_VALUE; // meaning
    // 中文标注（字段）：`breakButtonDownLastTick`，含义：用于表示break、button、down、last、刻。
    private boolean breakButtonDownLastTick; // meaning
    // 中文标注（字段）：`placeButtonDownLastTick`，含义：用于表示place、button、down、last、刻。
    private boolean placeButtonDownLastTick; // meaning
    // 中文标注（字段）：`lastEnsureLocalChunksNanos`，含义：用于表示last、ensure、局部、区块集合、nanos。
    private long lastEnsureLocalChunksNanos; // meaning
    // 中文标注（字段）：`lastChunkGenerationDrainNanos`，含义：用于表示last、区块、generation、drain、nanos。
    private long lastChunkGenerationDrainNanos; // meaning
    private long lastImmediateChunkSyncLogNanos; // meaning
    private RenderDistancePreset renderDistancePreset = RenderDistancePreset.MEDIUM; // meaning
    private int localChunkRadius = renderDistancePreset.chunkRadius(); // meaning
    private int networkChunkRadius = renderDistancePreset.chunkRadius(); // meaning
    private boolean showFps = true; // meaning
    private boolean showLocation = true; // meaning
    private boolean showStats = true; // meaning
    private boolean settingsOpen; // meaning
    private int settingsSelectedOption; // meaning
    private boolean blockPickerOpen; // meaning
    private String blockPickerSearchQuery = ""; // meaning
    private int blockPickerSelectedCategoryIndex; // meaning
    private int blockPickerScrollRow; // meaning
    private int blockPickerHoveredIndex = -1; // meaning
    private int blockPickerSelectedIndex = -1; // meaning
    private int lastInputMouseX; // meaning
    private int lastInputMouseY; // meaning
    private int lastRenderWidth = 960; // meaning
    private int lastRenderHeight = 540; // meaning
    private PickerLayout blockPickerLayout = PickerLayout.empty(); // meaning
    private boolean inWormhole; // meaning
    private int entryW; // meaning
    private double wormholeEntryX; // meaning
    private double wormholeEntryY; // meaning
    private double wormholeEntryZ; // meaning
    private double wormholeWPhase; // meaning
    private int wormholeWCandidate; // meaning
    private double wormholeDwPerSecond; // meaning

    // 中文标注（构造方法）：`GameClient`，参数：无；用途：初始化`GameClient`实例。
    public GameClient() {
        worldView.setGenerateMissingChunksOnPeek(true);
        initializeSpawn();
    }

    // 中文标注（方法）：`attachNetwork`，参数：networkClient；用途：执行attach、网络相关逻辑。
    // 中文标注（参数）：`networkClient`，含义：用于表示网络、客户端。
    public void attachNetwork(NetworkClient networkClient) {
        this.networkClient = networkClient;
        worldView.setGenerateMissingChunksOnPeek(false);
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
        double frameMs = deltaSeconds * 1_000.0; // meaning
        if (frameMs > 0.0) {
            smoothedFrameMs = smoothedFrameMs * 0.88 + frameMs * 0.12;
            smoothedFps = smoothedFps * 0.88 + (1_000.0 / frameMs) * 0.12;
        }
        hardwareSampleAccumulator += deltaSeconds;
        if (hardwareSampleAccumulator >= 0.25) {
            hardwareSnapshot = hardwareStatsSampler.sample();
            hardwareSampleAccumulator = 0.0;
        }

        lastInputMouseX = input.mouseX();
        lastInputMouseY = input.mouseY();
        handleWormholeToggleInput(input);
        handleSettingsInput(input);
        if (!settingsOpen) {
            handleBlockPickerInput(input);
        }
        boolean uiOpen = isAnyUiOpen(); // meaning
        if (!uiOpen) {
            handleBlockSelection(input);
        }

        // 物理/碰撞按固定子步推进，避免大 dt 突刺导致单帧穿透。
        double remainingSeconds = clampSimulationCatchupSeconds(deltaSeconds); // meaning
        if (remainingSeconds <= 0.0) {
            runSimulationStep(input, uiOpen, 0.0);
        } else {
            while (remainingSeconds > 0.0) {
                double stepSeconds = Math.min(remainingSeconds, MAX_SIMULATION_STEP_SECONDS); // meaning
                runSimulationStep(input, uiOpen, stepSeconds);
                remainingSeconds -= stepSeconds;
            }
        }

        if (blockPickerOpen) {
            refreshBlockPickerView(lastRenderWidth, lastRenderHeight);
            handleBlockPickerClick(input);
            targetedBlock = null;
            breakButtonDownLastTick = input.isMouseDown(MouseEvent.BUTTON1);
            placeButtonDownLastTick = input.isMouseDown(MouseEvent.BUTTON3);
        } else if (settingsOpen) {
            targetedBlock = null;
            breakButtonDownLastTick = input.isMouseDown(MouseEvent.BUTTON1);
            placeButtonDownLastTick = input.isMouseDown(MouseEvent.BUTTON3);
        } else {
            targetedBlock = raycastFromPlayer(INTERACTION_REACH);
            handleInteractions(input);
        }

        if (networkClient != null) {
            networkStateSendAccumulator += deltaSeconds;
            if (networkStateSendAccumulator >= NETWORK_STATE_SEND_INTERVAL_SECONDS) {
                networkClient.sendPlayerState(playerController);
                networkStateSendAccumulator = 0.0;
            }
            networkStatusLine = networkClient.statusLine();
        }

        lightEngine.tick(worldView);
        musicDirector.update(deltaSeconds, inWormhole, playerController.y(), lightEngine.ambient());
        game.tick();
    }

    // 中文标注（方法）：`render`，参数：graphics、width、height；用途：执行渲染或图形资源处理：渲染。
    // 中文标注（参数）：`graphics`，含义：用于表示graphics。
    // 中文标注（参数）：`width`，含义：用于表示宽度。
    // 中文标注（参数）：`height`，含义：用于表示高度。
    public void render(Graphics2D graphics, int width, int height) {
        lastRenderWidth = width;
        lastRenderHeight = height;
        if (blockPickerOpen) {
            refreshBlockPickerView(width, height);
        }
        drawBackground(graphics, width, height);

        // 中文标注（局部变量）：`stats`，含义：用于表示stats。
        RenderStats stats = renderSystem.draw(graphics, width, height, worldView, playerController, ambientLight()); // meaning
        if (targetedBlock != null && !isAnyUiOpen()) {
            renderSystem.drawSelectionBox(graphics, width, height, playerController, targetedBlock.targetBlock());
        }

        if (!isAnyUiOpen()) {
            drawCrosshair(graphics, width, height);
        }
        drawHotbar(graphics, width, height);
        drawHud(graphics, stats);
        if (blockPickerOpen) {
            drawBlockPicker(graphics, width, height);
        }
        if (settingsOpen) {
            drawSettingsPanel(graphics, width, height);
        }
    }

    // 中文标注（方法）：`worldView`，参数：无；用途：执行世界、view相关逻辑。
    public ClientWorldView worldView() {
        return worldView;
    }

    // 中文标注（方法）：`playerController`，参数：无；用途：执行玩家、控制器相关逻辑。
    public PlayerController playerController() {
        return playerController;
    }

    public int activeSliceW() {
        return game.w();
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

    public boolean isBlockPickerOpen() {
        return blockPickerOpen;
    }

    public boolean isSettingsOpen() {
        return settingsOpen;
    }

    public boolean isAnyUiOpen() {
        return blockPickerOpen || settingsOpen;
    }

    public int renderDistanceChunkRadius() {
        return localChunkRadius;
    }

    public boolean showFpsSetting() {
        return showFps;
    }

    public boolean showLocationSetting() {
        return showLocation;
    }

    public boolean showStatsSetting() {
        return showStats;
    }

    public String renderDistanceLabel() {
        return renderDistancePreset.label();
    }

    public String settingsSummaryText() {
        return String.format(
            "Render=%s | FPS=%s | Location=%s | Stats=%s",
            renderDistancePreset.label(),
            onOffLabel(showFps),
            onOffLabel(showLocation),
            onOffLabel(showStats)
        );
    }

    public int selectedHotbarSlot() {
        return selectedHotbarSlot;
    }

    public int hotbarSlotCount() {
        return hotbarBlocks.length;
    }

    public Block hotbarBlockAt(int slot) {
        if (slot < 0 || slot >= hotbarBlocks.length) {
            return Blocks.AIR;
        }
        return hotbarBlocks[slot];
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

    public void switchSlice(int newW) {
        if (networkClient != null && networkClient.isConnected()) {
            System.out.println("[wormhole] slice switching is disabled while connected to a multiplayer server.");
            return;
        }
        if (newW == game.w()) {
            return;
        }

        ClientWorldView oldWorldView = worldView; // meaning
        oldWorldView.close();
        game.switchW(newW);
        worldView = new ClientWorldView(game.world());
        worldView.setGenerateMissingChunksOnPeek(networkClient == null || !networkClient.isConnected());
        renderSystem = new ChunkRenderSystem();
        lightEngine = new LightEngine();
        targetedBlock = null;
        breakButtonDownLastTick = false;
        placeButtonDownLastTick = false;
        lastRequestedChunkX = Integer.MIN_VALUE;
        lastRequestedChunkZ = Integer.MIN_VALUE;
        networkStateSendAccumulator = 0.0;
        lightEngine.tick(worldView);
        requestChunksIfNeeded(true);
        System.out.printf("[wormhole] switched to slice w=%d%n", newW);
    }

    // 中文标注（方法）：`close`，参数：无；用途：执行close相关逻辑。
    @Override
    public void close() {
        if (networkClient != null) {
            networkClient.close();
            networkClient = null;
        }
        musicDirector.close();
        worldView.close();
    }

    // 中文标注（方法）：`requestChunksIfNeeded`，参数：force；用途：执行request、区块集合、if、needed相关逻辑。
    // 中文标注（参数）：`force`，含义：用于表示force。
    private void requestChunksIfNeeded(boolean force) {
        if (networkClient == null || !networkClient.isConnected()) {
            return;
        }

        // 中文标注（局部变量）：`blockX`，含义：用于表示方块、X坐标。
        int blockX = (int) Math.floor(playerController.x()); // meaning
        // 中文标注（局部变量）：`blockZ`，含义：用于表示方块、Z坐标。
        int blockZ = (int) Math.floor(playerController.z()); // meaning
        // 中文标注（局部变量）：`chunkX`，含义：用于表示区块、X坐标。
        int chunkX = Math.floorDiv(blockX, Section.SIZE); // meaning
        // 中文标注（局部变量）：`chunkZ`，含义：用于表示区块、Z坐标。
        int chunkZ = Math.floorDiv(blockZ, Section.SIZE); // meaning

        if (!force && chunkX == lastRequestedChunkX && chunkZ == lastRequestedChunkZ) {
            return;
        }

        lastRequestedChunkX = chunkX;
        lastRequestedChunkZ = chunkZ;
        networkClient.requestChunkRadius(chunkX, chunkZ, networkChunkRadius);
    }

    // 中文标注（方法）：`ensureLocalChunksAroundPlayer`，参数：无；用途：执行ensure、局部、区块集合、around、玩家相关逻辑。
    private void ensureLocalChunksAroundPlayer() {
        if (networkClient != null && networkClient.isConnected()) {
            requestChunksIfNeeded(false);
            return;
        }

        // 中文标注（局部变量）：`blockX`，含义：用于表示方块、X坐标。
        int blockX = (int) Math.floor(playerController.x()); // meaning
        // 中文标注（局部变量）：`blockZ`，含义：用于表示方块、Z坐标。
        int blockZ = (int) Math.floor(playerController.z()); // meaning
        // 中文标注（局部变量）：`chunkX`，含义：用于表示区块、X坐标。
        int chunkX = Math.floorDiv(blockX, Section.SIZE); // meaning
        // 中文标注（局部变量）：`chunkZ`，含义：用于表示区块、Z坐标。
        int chunkZ = Math.floorDiv(blockZ, Section.SIZE); // meaning
        int immediateGenerated = 0; // meaning
        for (int dz = -LOCAL_CHUNK_IMMEDIATE_RADIUS; dz <= LOCAL_CHUNK_IMMEDIATE_RADIUS; dz++) { // meaning
            for (int dx = -LOCAL_CHUNK_IMMEDIATE_RADIUS; dx <= LOCAL_CHUNK_IMMEDIATE_RADIUS; dx++) { // meaning
                int cx = chunkX + dx; // meaning
                int cz = chunkZ + dz; // meaning
                if (worldView.getChunk(cx, cz) != null) {
                    continue;
                }
                worldView.world().getOrGenerateChunk(cx, cz);
                immediateGenerated++;
            }
        }
        if (immediateGenerated > 0) {
            long now = System.nanoTime(); // meaning
            if (now - lastImmediateChunkSyncLogNanos >= IMMEDIATE_CHUNK_SYNC_LOG_THROTTLE_NANOS) {
                System.out.printf(
                    "[chunk-gen] immediate-sync center=(%d,%d) r=%d generated=%d%n",
                    chunkX,
                    chunkZ,
                    LOCAL_CHUNK_IMMEDIATE_RADIUS,
                    immediateGenerated
                );
                lastImmediateChunkSyncLogNanos = now;
            }
        }
        worldView.ensureChunkRadius(chunkX, chunkZ, localChunkRadius);
    }

    private void runSimulationStep(InputState input, boolean uiOpen, double stepSeconds) {
        long ensureStarted = System.nanoTime(); // meaning
        ensureLocalChunksAroundPlayer();
        lastEnsureLocalChunksNanos = System.nanoTime() - ensureStarted;
        if (!uiOpen && stepSeconds > 0.0) {
            playerController.tick(worldView, input, stepSeconds);
            tickWormholeIfActive(stepSeconds);
        }
        long chunkDrainStarted = System.nanoTime(); // meaning
        worldView.drainChunkGenerationBudget(LOCAL_CHUNK_GENERATION_BUDGET_PER_TICK);
        lastChunkGenerationDrainNanos = System.nanoTime() - chunkDrainStarted;
        requestChunksIfNeeded(false);
    }

    private void handleWormholeToggleInput(InputState input) {
        if (!WORMHOLE_FEATURE_ENABLED) {
            return;
        }
        if (networkClient != null && networkClient.isConnected()) {
            return;
        }
        if (!input.wasKeyPressed(WORMHOLE_TOGGLE_KEY)) {
            return;
        }

        if (inWormhole) {
            tryExitWormhole();
        } else {
            enterWormhole();
        }
    }

    private void tickWormholeIfActive(double deltaSeconds) {
        if (!WORMHOLE_FEATURE_ENABLED || !inWormhole) {
            return;
        }
        if (networkClient != null && networkClient.isConnected()) {
            return;
        }
        tickWormhole(deltaSeconds);
    }

    private void handleSettingsInput(InputState input) {
        if (input.wasKeyPressed(SETTINGS_TOGGLE_KEY)) {
            settingsOpen = !settingsOpen;
            if (settingsOpen) {
                blockPickerOpen = false;
            }
            settingsSelectedOption = Math.max(0, Math.min(3, settingsSelectedOption));
            return;
        }
        if (!settingsOpen) {
            return;
        }
        if (input.wasKeyPressed(KeyEvent.VK_ESCAPE)) {
            settingsOpen = false;
            return;
        }
        if (input.wasKeyPressed(KeyEvent.VK_UP)) {
            settingsSelectedOption = Math.max(0, settingsSelectedOption - 1);
        }
        if (input.wasKeyPressed(KeyEvent.VK_DOWN)) {
            settingsSelectedOption = Math.min(3, settingsSelectedOption + 1);
        }

        boolean change = input.wasKeyPressed(KeyEvent.VK_LEFT)
            || input.wasKeyPressed(KeyEvent.VK_RIGHT)
            || input.wasKeyPressed(KeyEvent.VK_SPACE); // meaning
        if (change) {
            applyCurrentSettingsSelection();
        }
    }

    private void applyCurrentSettingsSelection() {
        switch (settingsSelectedOption) {
            case 0 -> applyRenderDistancePreset(renderDistancePreset.next());
            case 1 -> showFps = !showFps;
            case 2 -> showLocation = !showLocation;
            case 3 -> showStats = !showStats;
            default -> {
            }
        }
    }

    private void applyRenderDistancePreset(RenderDistancePreset preset) {
        if (preset == renderDistancePreset) {
            return;
        }
        renderDistancePreset = preset;
        localChunkRadius = preset.chunkRadius();
        networkChunkRadius = preset.chunkRadius();
        lastRequestedChunkX = Integer.MIN_VALUE;
        lastRequestedChunkZ = Integer.MIN_VALUE;
        ensureLocalChunksAroundPlayer();
        requestChunksIfNeeded(true);
        System.out.printf("[settings] render-distance=%s radius=%d%n", preset.label(), preset.chunkRadius());
    }

    private void handleBlockPickerInput(InputState input) {
        if (input.wasKeyPressed(BLOCK_PICKER_TOGGLE_KEY)) {
            blockPickerOpen = !blockPickerOpen;
            if (blockPickerOpen) {
                settingsOpen = false;
                blockPickerScrollRow = 0;
                blockPickerSelectedIndex = -1;
                blockPickerHoveredIndex = -1;
                refreshBlockPickerView(lastRenderWidth, lastRenderHeight);
            }
            return;
        }

        if (!blockPickerOpen) {
            return;
        }

        boolean categoryChanged = false; // meaning
        if (input.wasKeyPressed(KeyEvent.VK_LEFT)) {
            blockPickerSelectedCategoryIndex = Math.max(0, blockPickerSelectedCategoryIndex - 1);
            categoryChanged = true;
        }
        if (input.wasKeyPressed(KeyEvent.VK_RIGHT)) {
            blockPickerSelectedCategoryIndex = Math.min(pickerCategories.size() - 1, blockPickerSelectedCategoryIndex + 1);
            categoryChanged = true;
        }
        if (categoryChanged) {
            blockPickerScrollRow = 0;
            blockPickerSelectedIndex = -1;
        }

        if (input.wasKeyPressed(KeyEvent.VK_UP)) {
            blockPickerScrollRow = Math.max(0, blockPickerScrollRow - 1);
        }
        if (input.wasKeyPressed(KeyEvent.VK_DOWN)) {
            blockPickerScrollRow++;
        }

        if (input.wasKeyPressed(KeyEvent.VK_BACK_SPACE) && !blockPickerSearchQuery.isEmpty()) {
            blockPickerSearchQuery = blockPickerSearchQuery.substring(0, blockPickerSearchQuery.length() - 1);
            blockPickerScrollRow = 0;
            blockPickerSelectedIndex = -1;
        }
        if (input.wasKeyPressed(KeyEvent.VK_SPACE)) {
            blockPickerSearchQuery = blockPickerSearchQuery + " ";
            blockPickerScrollRow = 0;
            blockPickerSelectedIndex = -1;
        }
        appendBlockPickerSearchChars(input);

        if (input.wasKeyPressed(KeyEvent.VK_ESCAPE)) {
            blockPickerOpen = false;
            return;
        }

        refreshBlockPickerView(lastRenderWidth, lastRenderHeight);
        if (input.wasKeyPressed(KeyEvent.VK_ENTER)) {
            int candidate = blockPickerSelectedIndex >= 0 ? blockPickerSelectedIndex : blockPickerHoveredIndex; // meaning
            if (candidate < 0 && !pickerFilteredBlocks.isEmpty()) {
                candidate = 0;
            }
            selectBlockFromPicker(candidate);
        }
    }

    private void appendBlockPickerSearchChars(InputState input) {
        for (int keyCode = KeyEvent.VK_A; keyCode <= KeyEvent.VK_Z; keyCode++) { // meaning
            if (!input.wasKeyPressed(keyCode)) {
                continue;
            }
            char letter = (char) ('a' + (keyCode - KeyEvent.VK_A)); // meaning
            blockPickerSearchQuery = blockPickerSearchQuery + letter;
            blockPickerScrollRow = 0;
            blockPickerSelectedIndex = -1;
        }
        for (int digit = 0; digit <= 9; digit++) { // meaning
            int keyCode = KeyEvent.VK_0 + digit; // meaning
            if (!input.wasKeyPressed(keyCode)) {
                continue;
            }
            blockPickerSearchQuery = blockPickerSearchQuery + digit;
            blockPickerScrollRow = 0;
            blockPickerSelectedIndex = -1;
        }
    }

    private void handleBlockPickerClick(InputState input) {
        if (!blockPickerOpen || !input.wasMousePressed(MouseEvent.BUTTON1)) {
            return;
        }

        int categoryHit = blockPickerLayout.categoryIndexAt(lastInputMouseX, lastInputMouseY, pickerCategories.size()); // meaning
        if (categoryHit >= 0) {
            blockPickerSelectedCategoryIndex = categoryHit;
            blockPickerScrollRow = 0;
            blockPickerSelectedIndex = -1;
            refreshBlockPickerView(lastRenderWidth, lastRenderHeight);
            return;
        }

        int index = blockPickerLayout.gridIndexAt(lastInputMouseX, lastInputMouseY, pickerFilteredBlocks.size(), blockPickerScrollRow); // meaning
        if (index >= 0) {
            blockPickerSelectedIndex = index;
            selectBlockFromPicker(index);
        }
    }

    private void selectBlockFromPicker(int index) {
        if (index < 0 || index >= pickerFilteredBlocks.size()) {
            return;
        }
        BlockDef picked = pickerFilteredBlocks.get(index); // meaning
        Block resolved = Blocks.byBlockKeyOrAir(picked.key()); // meaning
        if (resolved == Blocks.AIR) {
            resolved = Blocks.byIdOrAir(picked.key());
        }
        hotbarBlocks[selectedHotbarSlot] = resolved;
        selectedBlock = resolved;
        blockPickerSelectedIndex = index;
    }

    private void refreshBlockPickerView(int width, int height) {
        if (pickerCategories.isEmpty()) {
            pickerFilteredBlocks = List.of();
            blockPickerLayout = PickerLayout.empty();
            blockPickerHoveredIndex = -1;
            return;
        }

        blockPickerSelectedCategoryIndex = Math.max(0, Math.min(pickerCategories.size() - 1, blockPickerSelectedCategoryIndex));
        String selectedCategory = pickerCategories.get(blockPickerSelectedCategoryIndex); // meaning
        pickerFilteredBlocks = blockCatalog.filter(selectedCategory, blockPickerSearchQuery);
        blockPickerLayout = PickerLayout.forViewport(width, height);

        int maxVisibleRows = Math.max(1, blockPickerLayout.gridRows()); // meaning
        int totalRows = (pickerFilteredBlocks.size() + blockPickerLayout.gridColumns() - 1) / blockPickerLayout.gridColumns(); // meaning
        int maxScroll = Math.max(0, totalRows - maxVisibleRows); // meaning
        blockPickerScrollRow = Math.max(0, Math.min(maxScroll, blockPickerScrollRow));

        int hovered = blockPickerLayout.gridIndexAt(lastInputMouseX, lastInputMouseY, pickerFilteredBlocks.size(), blockPickerScrollRow); // meaning
        blockPickerHoveredIndex = hovered;
        if (blockPickerSelectedIndex >= pickerFilteredBlocks.size()) {
            blockPickerSelectedIndex = pickerFilteredBlocks.isEmpty() ? -1 : pickerFilteredBlocks.size() - 1;
        }
    }

    private void tickWormhole(double deltaSeconds) {
        double x = playerController.x(); // meaning
        double z = playerController.z(); // meaning
        if (z < -4.0 && Math.abs(x) <= 2.0) {
            wormholeDwPerSecond = WORMHOLE_NORTH_DRIFT;
        } else if (z > 4.0 && Math.abs(x) <= 2.0) {
            wormholeDwPerSecond = WORMHOLE_SOUTH_DRIFT;
        } else if (x > 4.0 && Math.abs(z) <= 2.0) {
            wormholeDwPerSecond = WORMHOLE_EAST_DRIFT;
        } else if (x < -4.0 && Math.abs(z) <= 2.0) {
            wormholeDwPerSecond = WORMHOLE_WEST_DRIFT;
        } else {
            wormholeDwPerSecond = 0.0;
        }

        wormholeWPhase += wormholeDwPerSecond * Math.max(0.0, deltaSeconds);
        while (wormholeWPhase > wormholeWCandidate + WORMHOLE_SNAP_HYSTERESIS) {
            wormholeWCandidate++;
        }
        while (wormholeWPhase < wormholeWCandidate - WORMHOLE_SNAP_HYSTERESIS) {
            wormholeWCandidate--;
        }
    }

    private void enterWormhole() {
        entryW = game.w();
        wormholeEntryX = playerController.x();
        wormholeEntryY = playerController.y();
        wormholeEntryZ = playerController.z();
        wormholeWPhase = entryW;
        wormholeWCandidate = entryW;
        wormholeDwPerSecond = 0.0;

        inWormhole = true;
        switchSlice(WorldStack.W_WORMHOLE);
        playerController.teleport(0.5, 66.0, 0.5);
        ensureLocalChunksAroundPlayer();
        targetedBlock = null;
        System.out.printf("[wormhole] entered mode entryW=%d phase=%.3f anchor=(%.2f,%.2f,%.2f)%n",
            entryW, wormholeWPhase, wormholeEntryX, wormholeEntryY, wormholeEntryZ);
    }

    private void tryExitWormhole() {
        double x = playerController.x(); // meaning
        double z = playerController.z(); // meaning
        if (Math.abs(x) > WORMHOLE_EXIT_ROOM_HALF_EXTENT || Math.abs(z) > WORMHOLE_EXIT_ROOM_HALF_EXTENT) {
            return;
        }
        int wOut = wormholeWCandidate; // meaning
        inWormhole = false;
        wormholeDwPerSecond = 0.0;
        switchSlice(wOut);
        teleportToSafeAnchor(wormholeEntryX, wormholeEntryY, wormholeEntryZ);
        ensureLocalChunksAroundPlayer();
        targetedBlock = null;
        System.out.printf("[wormhole] exit phase=%.3f -> w=%d%n", wormholeWPhase, wOut);
    }

    private void teleportToSafeAnchor(double anchorX, double anchorY, double anchorZ) {
        double[] safe = findSafeStandingPosition(anchorX, anchorY, anchorZ); // meaning
        playerController.teleport(safe[0], safe[1], safe[2]);
    }

    private double[] findSafeStandingPosition(double anchorX, double anchorY, double anchorZ) {
        int baseX = (int) Math.floor(anchorX); // meaning
        int baseZ = (int) Math.floor(anchorZ); // meaning
        int baseY = clampInt((int) Math.floor(anchorY), World.MIN_Y + 2, World.MAX_Y - 3); // meaning
        int[] offsets = {0, 1, -1, 2, -2, 3, -3, 4, -4}; // meaning

        for (int radius = 0; radius <= 4; radius++) { // meaning
            for (int dx : offsets) {
                if (Math.abs(dx) > radius) {
                    continue;
                }
                for (int dz : offsets) {
                    if (Math.abs(dz) > radius) {
                        continue;
                    }
                    int x = baseX + dx; // meaning
                    int z = baseZ + dz; // meaning
                    for (int dy = -4; dy <= 16; dy++) { // meaning
                        int feetY = baseY + dy; // meaning
                        if (canStandAt(x, feetY, z)) {
                            return new double[] {x + 0.5, feetY, z + 0.5};
                        }
                    }
                }
            }
        }

        int surfaceY = findSurfaceY(baseX, baseZ); // meaning
        double fallbackY = Math.max(surfaceY + 1.0, 6.0); // meaning
        return new double[] {baseX + 0.5, fallbackY, baseZ + 0.5};
    }

    private boolean canStandAt(int blockX, int feetY, int blockZ) {
        if (feetY <= World.MIN_Y || feetY + 1 > World.MAX_Y) {
            return false;
        }
        if (!worldView.isSolid(blockX, feetY - 1, blockZ)) {
            return false;
        }
        return !worldView.isSolid(blockX, feetY, blockZ)
            && !worldView.isSolid(blockX, feetY + 1, blockZ);
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // 中文标注（方法）：`handleBlockSelection`，参数：input；用途：处理handle、方块、selection逻辑。
    // 中文标注（参数）：`input`，含义：用于表示输入。
    private void handleBlockSelection(InputState input) {
        // 中文标注（局部变量）：`slot`，含义：用于表示slot。
        for (int slot = 0; slot < hotbarBlocks.length; slot++) { // meaning
            // 中文标注（局部变量）：`keyDown`，含义：用于表示键、down。
            boolean keyDown = isHotbarKeyDown(input, slot); // meaning
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
        if (inWormhole) {
            breakButtonDownLastTick = input.isMouseDown(MouseEvent.BUTTON1);
            placeButtonDownLastTick = input.isMouseDown(MouseEvent.BUTTON3);
            return;
        }
        // 中文标注（局部变量）：`hit`，含义：用于表示命中。
        BlockHitResult hit = targetedBlock; // meaning
        if (hit == null) {
            return;
        }

        // 中文标注（局部变量）：`breakButtonDown`，含义：用于表示break、button、down。
        boolean breakButtonDown = input.isMouseDown(MouseEvent.BUTTON1); // meaning
        // 中文标注（局部变量）：`placeButtonDown`，含义：用于表示place、button、down。
        boolean placeButtonDown = input.isMouseDown(MouseEvent.BUTTON3); // meaning

        if (breakButtonDown && !breakButtonDownLastTick) {
            if (worldView.setBlock(hit.targetBlock(), Blocks.AIR)) {
                sendNetworkBlockUpdate(hit.targetBlock(), Blocks.AIR);
            }
        }

        if (placeButtonDown && !placeButtonDownLastTick && hit.placementBlock() != null) {
            // 中文标注（局部变量）：`placePos`，含义：用于表示place、位置。
            BlockPos placePos = hit.placementBlock(); // meaning
            if (canPlaceBlock(placePos) && worldView.setBlock(placePos, selectedBlock)) {
                sendNetworkBlockUpdate(placePos, selectedBlock);
                musicDirector.triggerStinger("craft_success");
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
        AABB blockBounds = new AABB(pos.x(), pos.y(), pos.z(), pos.x() + 1.0, pos.y() + 1.0, pos.z() + 1.0); // meaning
        return !playerController.boundingBox().intersects(blockBounds);
    }

    // 中文标注（方法）：`raycastFromPlayer`，参数：maxDistance；用途：执行raycast、from、玩家相关逻辑。
    // 中文标注（参数）：`maxDistance`，含义：用于表示最大、distance。
    private BlockHitResult raycastFromPlayer(double maxDistance) {
        // 中文标注（局部变量）：`originX`，含义：用于表示origin、X坐标。
        double originX = playerController.eyeX(); // meaning
        // 中文标注（局部变量）：`originY`，含义：用于表示origin、Y坐标。
        double originY = playerController.eyeY(); // meaning
        // 中文标注（局部变量）：`originZ`，含义：用于表示origin、Z坐标。
        double originZ = playerController.eyeZ(); // meaning

        // 中文标注（局部变量）：`dirX`，含义：用于表示dir、X坐标。
        double dirX = playerController.lookDirX(); // meaning
        // 中文标注（局部变量）：`dirY`，含义：用于表示dir、Y坐标。
        double dirY = playerController.lookDirY(); // meaning
        // 中文标注（局部变量）：`dirZ`，含义：用于表示dir、Z坐标。
        double dirZ = playerController.lookDirZ(); // meaning

        // 中文标注（局部变量）：`lastX`，含义：用于表示last、X坐标。
        int lastX = (int) Math.floor(originX); // meaning
        // 中文标注（局部变量）：`lastY`，含义：用于表示last、Y坐标。
        int lastY = (int) Math.floor(originY); // meaning
        // 中文标注（局部变量）：`lastZ`，含义：用于表示last、Z坐标。
        int lastZ = (int) Math.floor(originZ); // meaning

        // 中文标注（局部变量）：`step`，含义：用于表示step。
        double step = 0.05; // meaning
        // 中文标注（局部变量）：`distance`，含义：用于表示distance。
        for (double distance = 0.0; distance <= maxDistance; distance += step) { // meaning
            // 中文标注（局部变量）：`sampleX`，含义：用于表示sample、X坐标。
            double sampleX = originX + dirX * distance; // meaning
            // 中文标注（局部变量）：`sampleY`，含义：用于表示sample、Y坐标。
            double sampleY = originY + dirY * distance; // meaning
            // 中文标注（局部变量）：`sampleZ`，含义：用于表示sample、Z坐标。
            double sampleZ = originZ + dirZ * distance; // meaning

            // 中文标注（局部变量）：`blockX`，含义：用于表示方块、X坐标。
            int blockX = (int) Math.floor(sampleX); // meaning
            // 中文标注（局部变量）：`blockY`，含义：用于表示方块、Y坐标。
            int blockY = (int) Math.floor(sampleY); // meaning
            // 中文标注（局部变量）：`blockZ`，含义：用于表示方块、Z坐标。
            int blockZ = (int) Math.floor(sampleZ); // meaning

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
        int spawnBlockX = 0; // meaning
        // 中文标注（局部变量）：`spawnBlockZ`，含义：用于表示spawn、方块、Z坐标。
        int spawnBlockZ = 0; // meaning
        // Budgeted local chunk generation now only queues work, so force the center chunk to exist
        // before scanning for surface height; otherwise spawn may be computed against an empty world.
        worldView.world().getOrGenerateChunk(0, 0);
        // 中文标注（局部变量）：`surfaceY`，含义：用于表示surface、Y坐标。
        int surfaceY = findSurfaceY(spawnBlockX, spawnBlockZ); // meaning
        // 中文标注（局部变量）：`spawnY`，含义：用于表示spawn、Y坐标。
        double spawnY = Math.max(surfaceY + 1.0, 6.0); // meaning
        playerController.setSpawn(spawnBlockX + 0.5, spawnY, spawnBlockZ + 0.5);
        worldView.ensureChunkRadius(0, 0, localChunkRadius);
    }

    // 中文标注（方法）：`findSurfaceY`，参数：x、z；用途：获取或读取find、surface、Y坐标。
    // 中文标注（参数）：`x`，含义：用于表示X坐标。
    // 中文标注（参数）：`z`，含义：用于表示Z坐标。
    private int findSurfaceY(int x, int z) {
        // 中文标注（局部变量）：`y`，含义：用于表示Y坐标。
        for (int y = World.MAX_Y; y >= World.MIN_Y; y--) { // meaning
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
        float ambient = lightEngine.ambient(); // meaning

        // 中文标注（局部变量）：`skyTop`，含义：用于表示天空、顶面。
        Color skyTop = shade(new Color(94, 170, 240), ambient); // meaning
        // 中文标注（局部变量）：`skyBottom`，含义：用于表示天空、底面。
        Color skyBottom = shade(new Color(178, 225, 255), ambient); // meaning

        graphics.setPaint(new GradientPaint(0, 0, skyTop, 0, height, skyBottom));
        graphics.fillRect(0, 0, width, height);

        // 中文标注（局部变量）：`horizonY`，含义：用于表示horizon、Y坐标。
        int horizonY = (int) (height * 0.72); // meaning
        graphics.setColor(shade(new Color(84, 140, 72), ambient * 0.85f));
        graphics.fillRect(0, horizonY, width, height - horizonY);
    }

    // 中文标注（方法）：`drawCrosshair`，参数：graphics、width、height；用途：执行渲染或图形资源处理：绘制、crosshair。
    // 中文标注（参数）：`graphics`，含义：用于表示graphics。
    // 中文标注（参数）：`width`，含义：用于表示宽度。
    // 中文标注（参数）：`height`，含义：用于表示高度。
    private void drawCrosshair(Graphics2D graphics, int width, int height) {
        // 中文标注（局部变量）：`centerX`，含义：用于表示center、X坐标。
        int centerX = width / 2; // meaning
        // 中文标注（局部变量）：`centerY`，含义：用于表示center、Y坐标。
        int centerY = height / 2; // meaning

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
        int slotSize = 54; // meaning
        // 中文标注（局部变量）：`gap`，含义：用于表示gap。
        int gap = 9; // meaning
        // 中文标注（局部变量）：`slots`，含义：用于表示slots。
        int slots = hotbarBlocks.length; // meaning
        // 中文标注（局部变量）：`totalWidth`，含义：用于表示total、宽度。
        int totalWidth = slots * slotSize + (slots - 1) * gap; // meaning
        // 中文标注（局部变量）：`left`，含义：用于表示left。
        int left = (width - totalWidth) / 2; // meaning
        // 中文标注（局部变量）：`top`，含义：用于表示顶面。
        int top = height - slotSize - 26; // meaning

        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        // 中文标注（局部变量）：`slot`，含义：用于表示slot。
        for (int slot = 0; slot < slots; slot++) { // meaning
            // 中文标注（局部变量）：`slotX`，含义：用于表示slot、X坐标。
            int slotX = left + slot * (slotSize + gap); // meaning
            // 中文标注（局部变量）：`selected`，含义：用于表示selected。
            boolean selected = slot == selectedHotbarSlot; // meaning
            graphics.setColor(selected ? new Color(255, 255, 255, 215) : new Color(20, 24, 28, 170));
            graphics.fillRoundRect(slotX, top, slotSize, slotSize, 8, 8);
            graphics.setColor(selected ? new Color(20, 24, 28, 220) : new Color(235, 235, 235, 180));
            graphics.drawRoundRect(slotX, top, slotSize, slotSize, 8, 8);

            // 中文标注（局部变量）：`keyText`，含义：用于表示键、text。
            String keyText = Integer.toString(slot + 1); // meaning
            graphics.setColor(selected ? new Color(10, 10, 10, 230) : new Color(255, 255, 255, 220));
            graphics.drawString(keyText, slotX + 6, top + 14);

            // 中文标注（局部变量）：`label`，含义：用于表示label。
            String label = hotbarLabel(hotbarBlocks[slot]); // meaning
            // 中文标注（局部变量）：`textWidth`，含义：用于表示text、宽度。
            int textWidth = graphics.getFontMetrics().stringWidth(label); // meaning
            // 中文标注（局部变量）：`textX`，含义：用于表示text、X坐标。
            int textX = slotX + (slotSize - textWidth) / 2; // meaning
            // 中文标注（局部变量）：`textY`，含义：用于表示text、Y坐标。
            int textY = top + 33; // meaning
            graphics.drawString(label, textX, textY);
        }
    }

    private void drawBlockPicker(Graphics2D graphics, int width, int height) {
        refreshBlockPickerView(width, height);
        PickerLayout layout = blockPickerLayout; // meaning
        if (layout.panelWidth() <= 0 || layout.panelHeight() <= 0) {
            return;
        }

        graphics.setColor(new Color(0, 0, 0, 165));
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(new Color(26, 32, 40, 235));
        graphics.fillRoundRect(layout.panelX(), layout.panelY(), layout.panelWidth(), layout.panelHeight(), 14, 14);
        graphics.setColor(new Color(210, 220, 230, 230));
        graphics.drawRoundRect(layout.panelX(), layout.panelY(), layout.panelWidth(), layout.panelHeight(), 14, 14);

        graphics.setColor(new Color(16, 22, 28, 210));
        graphics.fillRoundRect(layout.searchX(), layout.searchY(), layout.searchWidth(), layout.searchHeight(), 8, 8);
        graphics.setColor(new Color(140, 160, 175, 220));
        graphics.drawRoundRect(layout.searchX(), layout.searchY(), layout.searchWidth(), layout.searchHeight(), 8, 8);
        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        graphics.setColor(Color.WHITE);
        graphics.drawString("Search: " + blockPickerSearchQuery, layout.searchX() + 10, layout.searchY() + 20);

        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        for (int i = 0; i < pickerCategories.size(); i++) { // meaning
            int categoryY = layout.categoryY() + i * layout.categoryRowHeight(); // meaning
            if (categoryY + layout.categoryRowHeight() > layout.categoryY() + layout.categoryHeight()) {
                break;
            }
            boolean selected = i == blockPickerSelectedCategoryIndex; // meaning
            int categoryBottom = categoryY + layout.categoryRowHeight() - 2; // meaning
            graphics.setColor(selected ? new Color(82, 121, 166, 225) : new Color(36, 44, 54, 210));
            graphics.fillRoundRect(layout.categoryX(), categoryY, layout.categoryWidth(), layout.categoryRowHeight() - 2, 6, 6);
            graphics.setColor(selected ? Color.WHITE : new Color(190, 202, 212));
            String label = BlockCatalog.categoryLabel(pickerCategories.get(i)); // meaning
            graphics.drawString(label, layout.categoryX() + 8, categoryBottom - 6);
        }

        int start = blockPickerScrollRow * layout.gridColumns(); // meaning
        int capacity = layout.gridColumns() * layout.gridRows(); // meaning
        int end = Math.min(pickerFilteredBlocks.size(), start + capacity); // meaning
        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        for (int index = start; index < end; index++) { // meaning
            int visible = index - start; // meaning
            int row = visible / layout.gridColumns(); // meaning
            int col = visible % layout.gridColumns(); // meaning
            int cellX = layout.gridX() + col * (layout.cellWidth() + layout.cellGap()); // meaning
            int cellY = layout.gridY() + row * (layout.cellHeight() + layout.cellGap()); // meaning
            boolean selected = index == blockPickerSelectedIndex; // meaning
            boolean hovered = index == blockPickerHoveredIndex; // meaning

            graphics.setColor(selected ? new Color(190, 232, 255, 220) : (hovered ? new Color(118, 142, 168, 210) : new Color(43, 52, 62, 200)));
            graphics.fillRoundRect(cellX, cellY, layout.cellWidth(), layout.cellHeight(), 6, 6);
            graphics.setColor(new Color(220, 230, 238, selected ? 235 : 180));
            graphics.drawRoundRect(cellX, cellY, layout.cellWidth(), layout.cellHeight(), 6, 6);

            BlockDef def = pickerFilteredBlocks.get(index); // meaning
            Color swatchColor = pickerPreviewColor(def); // meaning
            graphics.setColor(swatchColor);
            graphics.fillRect(cellX + 6, cellY + 7, 16, layout.cellHeight() - 14);

            graphics.setColor(Color.WHITE);
            graphics.drawString(shortDisplayName(blockCatalog.displayName(def)), cellX + 28, cellY + 15);
            graphics.setColor(new Color(190, 205, 216));
            graphics.drawString(shortBlockId(def.key()), cellX + 28, cellY + layout.cellHeight() - 8);
        }

        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        graphics.setColor(new Color(225, 236, 246));
        String selectedKey = (blockPickerSelectedIndex >= 0 && blockPickerSelectedIndex < pickerFilteredBlocks.size())
            ? pickerFilteredBlocks.get(blockPickerSelectedIndex).key()
            : selectedBlock.id().toString();
        String status = String.format(
            "blocks loaded: %d / filtered: %d / selected: %s",
            blockCatalog.totalBlockCount(),
            pickerFilteredBlocks.size(),
            selectedKey
        );
        graphics.drawString(status, layout.statusX(), layout.statusY());
    }

    // 中文标注（方法）：`drawHud`，参数：graphics、stats；用途：执行渲染或图形资源处理：绘制、hud。
    // 中文标注（参数）：`graphics`，含义：用于表示graphics。
    // 中文标注（参数）：`stats`，含义：用于表示stats。
    private void drawHud(Graphics2D graphics, RenderStats stats) {
        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        int lineCount = 4; // meaning
        if (showLocation) {
            lineCount += 2;
        }
        if (showStats) {
            lineCount += 1;
        }
        if (showFps) {
            lineCount += 1;
        }
        int panelHeight = 18 + lineCount * 20; // meaning
        graphics.setColor(new Color(0, 0, 0, 130));
        graphics.fillRoundRect(12, 12, 980, panelHeight, 12, 12);

        graphics.setColor(Color.WHITE);
        int y = 34; // meaning
        if (showLocation) {
            graphics.drawString(String.format("XYZ: %.2f %.2f %.2f", playerController.x(), playerController.y(), playerController.z()), 24, y);
            y += 20;
            graphics.drawString(String.format("Yaw/Pitch: %.1f / %.1f", playerController.yaw(), playerController.pitch()), 24, y);
            y += 20;
        }
        String driftLabel = wormholeDwPerSecond >= 0.0 ? "ANA" : "KATA"; // meaning
        graphics.drawString(inWormhole
                ? String.format("W-phase: %.2f  snap=%d  drift=%.2f (%s)", wormholeWPhase, wormholeWCandidate, wormholeDwPerSecond, driftLabel)
                : "W: " + game.w(),
            24, y);
        y += 20;
        if (showStats) {
            graphics.drawString(
                String.format("Faces: total=%d, frustum=%d, drawn=%d", stats.totalFaces(), stats.frustumCandidates(), stats.drawnFaces()),
                24,
                y
            );
            y += 20;
        }
        graphics.drawString(String.format("Held Block: %s", selectedBlock.id()), 24, y);
        y += 20;
        graphics.drawString("WASD Move | Space Jump | Mouse Look | LMB Break | RMB Place | 1/2/3/4/5 Block | E Picker | O Settings", 24, y);
        y += 20;
        if (showFps) {
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
                y
            );
            y += 20;
        }
        graphics.drawString(
            WORMHOLE_FEATURE_ENABLED
                ? "Network: " + networkStatusLine + " | Wormhole: press V to enter/exit (exit only in center room)"
                : "Network: " + networkStatusLine,
            24,
            y
        );
        y += 20;
        graphics.drawString("Mouse auto-captured and cursor hidden while focused | ESC exits", 24, y);
    }

    private void drawSettingsPanel(Graphics2D graphics, int width, int height) {
        int panelWidth = 420; // meaning
        int panelHeight = 210; // meaning
        int left = (width - panelWidth) / 2; // meaning
        int top = (height - panelHeight) / 2; // meaning

        graphics.setColor(new Color(0, 0, 0, 155));
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(new Color(22, 29, 38, 236));
        graphics.fillRoundRect(left, top, panelWidth, panelHeight, 14, 14);
        graphics.setColor(new Color(220, 230, 240, 220));
        graphics.drawRoundRect(left, top, panelWidth, panelHeight, 14, 14);

        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
        graphics.setColor(Color.WHITE);
        graphics.drawString("Settings", left + 16, top + 28);

        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        String[] rows = {
            "Render distance: " + renderDistancePreset.label(),
            "Show FPS: " + onOffLabel(showFps),
            "Show location: " + onOffLabel(showLocation),
            "Show stats: " + onOffLabel(showStats)
        }; // meaning
        int rowTop = top + 54; // meaning
        int rowHeight = 32; // meaning
        for (int i = 0; i < rows.length; i++) { // meaning
            int y = rowTop + i * rowHeight; // meaning
            boolean selected = i == settingsSelectedOption; // meaning
            graphics.setColor(selected ? new Color(90, 128, 168, 220) : new Color(40, 49, 58, 210));
            graphics.fillRoundRect(left + 16, y - 18, panelWidth - 32, 24, 8, 8);
            graphics.setColor(selected ? Color.WHITE : new Color(205, 216, 228));
            graphics.drawString(rows[i], left + 26, y);
        }

        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        graphics.setColor(new Color(200, 214, 226, 230));
        graphics.drawString("O toggle | Up/Down select | Left/Right/Space change | Esc close", left + 16, top + panelHeight - 16);
    }

    private static String shortDisplayName(String rawLabel) {
        String label = rawLabel; // meaning
        if (label == null || label.isBlank()) {
            label = "block";
        }
        return label.length() <= 14 ? label : label.substring(0, 14);
    }

    private static String shortBlockId(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        int split = key.indexOf(':'); // meaning
        String normalized = split >= 0 && split + 1 < key.length() ? key.substring(split + 1) : key; // meaning
        return normalized.length() <= 18 ? normalized : normalized.substring(0, 18);
    }

    private static Color pickerPreviewColor(BlockDef def) {
        String material = def.material().toLowerCase(Locale.ROOT); // meaning
        String key = def.key().toLowerCase(Locale.ROOT); // meaning
        if (material.contains("sand") || key.contains("sand")) {
            return new Color(214, 198, 148);
        }
        if (material.contains("stone") || material.contains("rock") || key.contains("stone")) {
            return new Color(134, 138, 145);
        }
        if (material.contains("wood") || key.contains("wood") || key.contains("log")) {
            return new Color(132, 94, 57);
        }
        if (material.contains("leaf") || material.contains("moss") || key.contains("leaf")) {
            return new Color(76, 140, 72);
        }
        if (material.contains("soil") || material.contains("dirt") || key.contains("dirt")) {
            return new Color(127, 94, 66);
        }
        int seed = Math.abs((key + "|" + material + "|" + def.variant()).hashCode()); // meaning
        int red = 80 + (seed & 0x5F);
        int green = 90 + ((seed >>> 7) & 0x5F);
        int blue = 80 + ((seed >>> 13) & 0x5F);
        return new Color(clamp(red), clamp(green), clamp(blue));
    }

    // 中文标注（方法）：`hotbarLabel`，参数：block；用途：执行hotbar、label相关逻辑。
    // 中文标注（参数）：`block`，含义：用于表示方块。
    private String hotbarLabel(Block block) {
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
        BlockDef def = block.def();
        if (def != null && !def.displayName().isBlank()) {
            return shortDisplayName(blockCatalog.displayName(def));
        }
        return "Block";
    }

    // 中文标注（方法）：`shade`，参数：color、amount；用途：执行shade相关逻辑。
    // 中文标注（参数）：`color`，含义：用于表示颜色。
    // 中文标注（参数）：`amount`，含义：用于表示amount。
    private static Color shade(Color color, float amount) {
        // 中文标注（局部变量）：`red`，含义：用于表示red。
        int red = clamp((int) (color.getRed() * amount)); // meaning
        // 中文标注（局部变量）：`green`，含义：用于表示green。
        int green = clamp((int) (color.getGreen() * amount)); // meaning
        // 中文标注（局部变量）：`blue`，含义：用于表示blue。
        int blue = clamp((int) (color.getBlue() * amount)); // meaning
        return new Color(red, green, blue);
    }

    // 中文标注（方法）：`clamp`，参数：value；用途：执行clamp相关逻辑。
    // 中文标注（参数）：`value`，含义：用于表示值。
    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static String onOffLabel(boolean value) {
        return value ? "On" : "Off";
    }

    private enum RenderDistancePreset {
        NEAR("近", RENDER_DISTANCE_NEAR_RADIUS),
        MEDIUM("中", RENDER_DISTANCE_MEDIUM_RADIUS),
        FAR("远", RENDER_DISTANCE_FAR_RADIUS);

        private final String label; // meaning
        private final int chunkRadius; // meaning

        RenderDistancePreset(String label, int chunkRadius) {
            this.label = label;
            this.chunkRadius = chunkRadius;
        }

        private String label() {
            return label;
        }

        private int chunkRadius() {
            return chunkRadius;
        }

        private RenderDistancePreset next() {
            return switch (this) {
                case NEAR -> MEDIUM;
                case MEDIUM -> FAR;
                case FAR -> NEAR;
            };
        }
    }

    private record PickerLayout(
        int panelX,
        int panelY,
        int panelWidth,
        int panelHeight,
        int searchX,
        int searchY,
        int searchWidth,
        int searchHeight,
        int categoryX,
        int categoryY,
        int categoryWidth,
        int categoryHeight,
        int categoryRowHeight,
        int gridX,
        int gridY,
        int gridWidth,
        int gridHeight,
        int gridColumns,
        int gridRows,
        int cellWidth,
        int cellHeight,
        int cellGap,
        int statusX,
        int statusY
    ) {
        private static PickerLayout empty() {
            return forViewport(1, 1);
        }

        private static PickerLayout forViewport(int width, int height) {
            int margin = 24; // meaning
            int panelX = margin; // meaning
            int panelY = margin; // meaning
            int panelWidth = Math.max(0, width - margin * 2); // meaning
            int panelHeight = Math.max(0, height - margin * 2); // meaning

            int searchHeight = 30; // meaning
            int searchX = panelX + 14; // meaning
            int searchY = panelY + 12; // meaning
            int searchWidth = Math.max(120, panelWidth - 28); // meaning

            int categoryX = panelX + 14; // meaning
            int categoryY = searchY + searchHeight + 12; // meaning
            int categoryWidth = Math.max(120, Math.min(220, panelWidth / 4)); // meaning
            int categoryHeight = Math.max(0, panelHeight - (categoryY - panelY) - 40); // meaning
            int categoryRowHeight = 24; // meaning

            int gridX = categoryX + categoryWidth + 14; // meaning
            int gridY = categoryY; // meaning
            int gridWidth = Math.max(0, panelX + panelWidth - 14 - gridX); // meaning
            int gridHeight = Math.max(0, categoryHeight); // meaning

            int cellGap = 6; // meaning
            int columns = Math.max(1, Math.min(BLOCK_PICKER_MAX_COLUMNS, (gridWidth + cellGap) / (86 + cellGap))); // meaning
            int rows = Math.max(1, Math.min(BLOCK_PICKER_MAX_ROWS, (gridHeight + cellGap) / (38 + cellGap))); // meaning
            int cellWidth = Math.max(60, (gridWidth - (columns - 1) * cellGap) / columns); // meaning
            int cellHeight = Math.max(30, (gridHeight - (rows - 1) * cellGap) / rows); // meaning

            int statusX = panelX + 16; // meaning
            int statusY = panelY + panelHeight - 12; // meaning
            return new PickerLayout(
                panelX, panelY, panelWidth, panelHeight,
                searchX, searchY, searchWidth, searchHeight,
                categoryX, categoryY, categoryWidth, categoryHeight, categoryRowHeight,
                gridX, gridY, gridWidth, gridHeight,
                columns, rows, cellWidth, cellHeight, cellGap,
                statusX, statusY
            );
        }

        private int categoryIndexAt(int mouseX, int mouseY, int categoryCount) {
            if (mouseX < categoryX || mouseX >= categoryX + categoryWidth) {
                return -1;
            }
            if (mouseY < categoryY || mouseY >= categoryY + categoryHeight) {
                return -1;
            }
            int row = (mouseY - categoryY) / categoryRowHeight; // meaning
            if (row < 0 || row >= categoryCount) {
                return -1;
            }
            return row;
        }

        private int gridIndexAt(int mouseX, int mouseY, int totalItems, int scrollRow) {
            if (mouseX < gridX || mouseX >= gridX + gridWidth || mouseY < gridY || mouseY >= gridY + gridHeight) {
                return -1;
            }
            int localX = mouseX - gridX; // meaning
            int localY = mouseY - gridY; // meaning
            int slotWidth = cellWidth + cellGap; // meaning
            int slotHeight = cellHeight + cellGap; // meaning
            int col = localX / slotWidth; // meaning
            int row = localY / slotHeight; // meaning
            if (col < 0 || col >= gridColumns || row < 0 || row >= gridRows) {
                return -1;
            }
            if ((localX % slotWidth) >= cellWidth || (localY % slotHeight) >= cellHeight) {
                return -1;
            }
            int index = (scrollRow + row) * gridColumns + col; // meaning
            return index >= 0 && index < totalItems ? index : -1;
        }
    }

    private static int intPropertyCompat(String key, String legacyKey, int defaultValue) {
        String raw = System.getProperty(key); // meaning
        if (raw == null) {
            raw = System.getProperty(legacyKey);
        }
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static int clampImmediateChunkRadius(int value) {
        return Math.max(1, Math.min(2, value));
    }

    private static double clampSimulationCatchupSeconds(double deltaSeconds) {
        if (deltaSeconds <= 0.0) {
            return 0.0;
        }
        return Math.min(deltaSeconds, MAX_SIMULATION_CATCHUP_SECONDS);
    }

    private static boolean booleanPropertyCompat(String key, String legacyKey, boolean defaultValue) {
        String raw = System.getProperty(key); // meaning
        if (raw == null) {
            raw = System.getProperty(legacyKey);
        }
        if (raw == null) {
            return defaultValue;
        }
        String normalized = raw.trim().toLowerCase(); // meaning
        if (normalized.equals("1") || normalized.equals("true") || normalized.equals("yes") || normalized.equals("on")) {
            return true;
        }
        if (normalized.equals("0") || normalized.equals("false") || normalized.equals("no") || normalized.equals("off")) {
            return false;
        }
        return defaultValue;
    }
}
