package dev.voxelcraft.client.render;

import dev.voxelcraft.core.block.BlockDef;
import dev.voxelcraft.core.block.Blocks;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import javax.imageio.ImageIO;

/** CPU texture preparation shared by OpenGL and Metal. */
final class BlockTextureAtlas {
    static final String TILE_TEXTURE_RESOURCE_PREFIX = "/textures/tiles/";
    static final int BLOCK_ATLAS_TILE_SIZE = 16; // meaning
    static final int BLOCK_ATLAS_META_WIDTH = 4096; // meaning
    static final int MAX_ATLAS_TILE_COUNT = 4096; // meaning

    static AtlasBuildResult build() {
        Map<String, Integer> tileIndexByName = new HashMap<>(); // meaning
        Map<Integer, BlockFaceMeta> blockMetaById = new HashMap<>(); // meaning
        tileIndexByName.put("stone", 0);
        for (BlockDef def : Blocks.definitions().all()) {
            int blockId = def.id().asUnsignedInt(); // meaning
            if (blockId < 0 || blockId >= BLOCK_ATLAS_META_WIDTH) {
                continue;
            }
            int top = resolveTileIndex(tileIndexByName, def.atlasTop());
            int side = resolveTileIndex(tileIndexByName, def.atlasSide());
            int bottom = resolveTileIndex(tileIndexByName, def.atlasBottom());
            int overlay = resolveTileIndex(tileIndexByName, def.atlasOverlay());
            int tintCode = tintModeCode(def.tintMode());
            blockMetaById.put(blockId, new BlockFaceMeta(top, side, bottom, overlay, tintCode));
        }

        int tileCount = Math.max(1, tileIndexByName.size()); // meaning
        int columns = Math.max(1, (int) Math.ceil(Math.sqrt(tileCount))); // meaning
        int rows = Math.max(1, (int) Math.ceil((double) tileCount / (double) columns)); // meaning
        int atlasWidth = columns * BLOCK_ATLAS_TILE_SIZE; // meaning
        int atlasHeight = rows * BLOCK_ATLAS_TILE_SIZE; // meaning
        BufferedImage atlasImage = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB); // meaning

        for (Map.Entry<String, Integer> entry : tileIndexByName.entrySet()) {
            String tileName = entry.getKey(); // meaning
            int tileIndex = entry.getValue(); // meaning
            BufferedImage tile = loadOrGenerateAtlasTile(tileName, BLOCK_ATLAS_TILE_SIZE); // meaning
            int dstX = (tileIndex % columns) * BLOCK_ATLAS_TILE_SIZE; // meaning
            int dstY = (tileIndex / columns) * BLOCK_ATLAS_TILE_SIZE; // meaning
            blitTile(tile, atlasImage, dstX, dstY);
        }

        ByteBuffer atlasPixels = convertImageToRgba(atlasImage); // meaning
        ByteBuffer metaPixels = buildFaceTileLutTexture(blockMetaById); // meaning
        return new AtlasBuildResult(atlasPixels, atlasWidth, atlasHeight, columns, rows, tileCount, metaPixels);
    }

    // 6-row LUT: row = faceIndex (0=UP/top, 1=DOWN/bottom, 2-5=sides), col = blockId
    // Each texel: R=tileIndexLo, G=tileIndexHi, B=tintCode, A=overlayLo
    private static ByteBuffer buildFaceTileLutTexture(Map<Integer, BlockFaceMeta> blockMetaById) {
        ByteBuffer bytes = ByteBuffer.allocateDirect(BLOCK_ATLAS_META_WIDTH * 6 * 4);
        for (int faceIdx = 0; faceIdx < 6; faceIdx++) {
            for (int blockId = 0; blockId < BLOCK_ATLAS_META_WIDTH; blockId++) {
                BlockFaceMeta meta = blockMetaById.get(blockId);
                if (meta == null) {
                    meta = new BlockFaceMeta(0, 0, 0, 0, 0);
                }
                int tileIndex = switch (faceIdx) {
                    case 0 -> meta.topTileIndex();
                    case 1 -> meta.bottomTileIndex();
                    default -> meta.sideTileIndex();
                };
                int offset = (faceIdx * BLOCK_ATLAS_META_WIDTH + blockId) * 4;
                bytes.put(offset,     (byte) (tileIndex & 0xFF));
                bytes.put(offset + 1, (byte) ((tileIndex >>> 8) & 0xFF));
                bytes.put(offset + 2, (byte) (meta.tintCode() & 0xFF));
                bytes.put(offset + 3, (byte) (meta.overlayTileIndex() & 0xFF));
            }
        }
        return bytes;
    }

    private static int resolveTileIndex(Map<String, Integer> tileIndexByName, String tileName) {
        String normalized = normalizeTileName(tileName); // meaning
        if (normalized.isEmpty()) {
            return 0;
        }
        Integer existing = tileIndexByName.get(normalized); // meaning
        if (existing != null) {
            return existing;
        }
        if (tileIndexByName.size() >= MAX_ATLAS_TILE_COUNT) {
            return 0;
        }
        int nextIndex = tileIndexByName.size(); // meaning
        tileIndexByName.put(normalized, nextIndex);
        return nextIndex;
    }

    private static String normalizeTileName(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.trim()
            .toLowerCase(Locale.ROOT)
            .replace(' ', '_')
            .replace('-', '_')
            .replace(':', '_')
            .replace('/', '_'); // meaning
        if (normalized.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(normalized.length()); // meaning
        for (int i = 0; i < normalized.length(); i++) { // meaning
            char ch = normalized.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_') {
                out.append(ch);
            }
        }
        return out.isEmpty() ? "" : out.toString();
    }

    private static BufferedImage loadOrGenerateAtlasTile(String tileName, int tileSize) {
        BufferedImage loaded = loadAtlasTileFromResources(tileName); // meaning
        if (loaded != null) {
            return scaleTileImageNearest(loaded, tileSize);
        }
        return generateProceduralTile(tileName, tileSize);
    }

    private static BufferedImage loadAtlasTileFromResources(String tileName) {
        String resourcePath = TILE_TEXTURE_RESOURCE_PREFIX + tileName + ".png"; // meaning
        try (InputStream stream = BlockTextureAtlas.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return null;
            }
            return ImageIO.read(stream);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static BufferedImage scaleTileImageNearest(BufferedImage source, int tileSize) {
        if (source.getWidth() == tileSize && source.getHeight() == tileSize) {
            return source;
        }
        BufferedImage scaled = new BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_ARGB); // meaning
        Graphics2D graphics = scaled.createGraphics(); // meaning
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            graphics.drawImage(source, 0, 0, tileSize, tileSize, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private static void blitTile(BufferedImage tile, BufferedImage atlas, int dstX, int dstY) {
        int tileWidth = tile.getWidth(); // meaning
        int tileHeight = tile.getHeight(); // meaning
        for (int y = 0; y < tileHeight; y++) { // meaning
            for (int x = 0; x < tileWidth; x++) { // meaning
                atlas.setRGB(dstX + x, dstY + y, tile.getRGB(x, y));
            }
        }
    }

    private static BufferedImage generateProceduralTile(String tileName, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB); // meaning
        String name = tileName == null ? "stone" : tileName; // meaning
        String lower = name.toLowerCase(Locale.ROOT); // meaning
        int seed = hashMaterial(name.hashCode(), 0x2AF3D15B, 0x9E3779B9, 0x51ED2705); // meaning
        int[] base = baseTileColorForName(lower); // meaning

        for (int y = 0; y < size; y++) { // meaning
            for (int x = 0; x < size; x++) { // meaning
                int noise = hashMaterial(seed, x * 977 + y * 131, x * 23 - y * 19, 0x7F4A7C15); // meaning
                int r = base[0];
                int g = base[1];
                int b = base[2];

                if (containsToken(lower, "grass_side")) {
                    if (y < size / 4) {
                        int grassNoise = ((noise >>> 4) & 31) - 15; // meaning
                        r = clampByte(84 + grassNoise / 2);
                        g = clampByte(158 + grassNoise);
                        b = clampByte(78 + grassNoise / 3);
                    } else {
                        int dirtNoise = ((noise >>> 7) & 31) - 15; // meaning
                        r = clampByte(126 + dirtNoise);
                        g = clampByte(95 + dirtNoise / 2);
                        b = clampByte(67 + dirtNoise / 2);
                    }
                } else if (containsToken(lower, "grass_top", "leaves", "organic", "moss")) {
                    int grassNoise = ((noise >>> 5) & 31) - 15; // meaning
                    r = clampByte(base[0] + grassNoise / 2);
                    g = clampByte(base[1] + grassNoise);
                    b = clampByte(base[2] + grassNoise / 3);
                } else if (containsToken(lower, "log_side", "bark", "wood")) {
                    int stripe = ((x + ((seed >>> 3) & 3)) / 2) & 1; // meaning
                    int woodNoise = ((noise >>> 9) & 15) - 7; // meaning
                    int shade = stripe == 0 ? -12 : 10; // meaning
                    r = clampByte(base[0] + shade + woodNoise);
                    g = clampByte(base[1] + shade / 2 + woodNoise / 2);
                    b = clampByte(base[2] + shade / 3 + woodNoise / 2);
                } else if (containsToken(lower, "log_top", "wood_top")) {
                    float dx = (x + 0.5f) - (size * 0.5f);
                    float dy = (y + 0.5f) - (size * 0.5f);
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    int ring = ((int) Math.floor(dist * 1.35f + ((seed >>> 4) & 3))) & 1; // meaning
                    int shade = ring == 0 ? -10 : 12; // meaning
                    r = clampByte(base[0] + shade);
                    g = clampByte(base[1] + shade / 2);
                    b = clampByte(base[2] + shade / 3);
                } else if (containsToken(lower, "bricks")) {
                    int cellX = x / 8; // meaning
                    int cellY = y / 4; // meaning
                    int mortar = (x % 8 == 0 || y % 4 == 0) ? 1 : 0; // meaning
                    int shade = (((cellX + cellY) & 1) == 0) ? -10 : 6; // meaning
                    if (mortar == 1) {
                        r = 42;
                        g = 42;
                        b = 42;
                    } else {
                        r = clampByte(base[0] + shade);
                        g = clampByte(base[1] + shade / 2);
                        b = clampByte(base[2] + shade / 2);
                    }
                } else if (containsToken(lower, "tiles")) {
                    boolean grout = x % 4 == 0 || y % 4 == 0; // meaning
                    int shade = (((x / 4) + (y / 4)) & 1) == 0 ? -8 : 8; // meaning
                    if (grout) {
                        r = 36;
                        g = 36;
                        b = 36;
                    } else {
                        r = clampByte(base[0] + shade);
                        g = clampByte(base[1] + shade);
                        b = clampByte(base[2] + shade);
                    }
                } else if (containsToken(lower, "ore")) {
                    int rockNoise = ((noise >>> 6) & 31) - 15; // meaning
                    r = clampByte(130 + rockNoise);
                    g = clampByte(136 + rockNoise / 2);
                    b = clampByte(143 + rockNoise / 2);
                    if ((noise & 0xFF) > 230) {
                        r = clampByte(220 + ((noise >>> 8) & 15));
                        g = clampByte(170 + ((noise >>> 12) & 31));
                        b = clampByte(80 + ((noise >>> 16) & 23));
                    }
                } else if (containsToken(lower, "water")) {
                    int wave = ((x * 3 + y * 5 + (seed & 31)) & 15) - 7; // meaning
                    r = clampByte(45 + wave / 3);
                    g = clampByte(86 + wave / 2);
                    b = clampByte(182 + wave);
                } else {
                    int jitter = ((noise >>> 8) & 31) - 15; // meaning
                    r = clampByte(base[0] + jitter);
                    g = clampByte(base[1] + jitter / 2);
                    b = clampByte(base[2] + jitter / 2);
                }

                int argb = (0xFF << 24) | (r << 16) | (g << 8) | b; // meaning
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    private static int[] baseTileColorForName(String lower) {
        if (containsToken(lower, "grass", "leaves", "moss", "organic")) {
            return new int[] {88, 150, 78};
        }
        if (containsToken(lower, "dirt", "mud", "humus", "peat")) {
            return new int[] {126, 95, 67};
        }
        if (containsToken(lower, "sand", "saline", "loess")) {
            return new int[] {214, 198, 148};
        }
        if (containsToken(lower, "gravel", "clay")) {
            return new int[] {146, 141, 136};
        }
        if (containsToken(lower, "log", "wood", "bark")) {
            return new int[] {132, 94, 58};
        }
        if (containsToken(lower, "brick")) {
            return new int[] {142, 76, 58};
        }
        if (containsToken(lower, "tile")) {
            return new int[] {156, 157, 163};
        }
        if (containsToken(lower, "ore")) {
            return new int[] {136, 138, 144};
        }
        if (containsToken(lower, "water")) {
            return new int[] {56, 96, 186};
        }
        return new int[] {132, 136, 143};
    }

    private static int tintModeCode(String tintMode) {
        String lower = lower(tintMode); // meaning
        if (lower.contains("biome_grass") || lower.equals("grass")) {
            return 1;
        }
        if (lower.contains("biome_foliage") || lower.equals("foliage")) {
            return 2;
        }
        if (lower.contains("water")) {
            return 3;
        }
        if (lower.contains("custom")) {
            return 4;
        }
        return 0;
    }

    private static boolean containsToken(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static ByteBuffer convertImageToRgba(BufferedImage image) {
        int width = image.getWidth(); // meaning
        int height = image.getHeight(); // meaning
        ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4); // meaning
        for (int y = 0; y < height; y++) { // meaning
            for (int x = 0; x < width; x++) { // meaning
                int argb = image.getRGB(x, y); // meaning
                pixels.put((byte) ((argb >> 16) & 0xFF));
                pixels.put((byte) ((argb >> 8) & 0xFF));
                pixels.put((byte) (argb & 0xFF));
                pixels.put((byte) 0xFF);
            }
        }
        pixels.flip();
        return pixels;
    }
    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
    private static int hashMaterial(int a, int b, int c, int d) {
        int value = a * 0x9E3779B9 + b * 0x7F4A7C15 + c * 0x85EBCA6B + d * 0xC2B2AE35; // meaning
        value ^= (value >>> 15);
        value *= 0x2C1B3C6D;
        value ^= (value >>> 12);
        return value;
    }
    private static int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }
    private record BlockFaceMeta(
        int topTileIndex,
        int sideTileIndex,
        int bottomTileIndex,
        int overlayTileIndex,
        int tintCode
    ) {
    }

    record AtlasBuildResult(
        ByteBuffer atlasPixels,
        int atlasWidth,
        int atlasHeight,
        int atlasColumns,
        int atlasRows,
        int tileCount,
        ByteBuffer metaPixels
    ) {
    }

}
