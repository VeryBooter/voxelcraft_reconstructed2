package dev.voxelcraft.client.render;

import dev.voxelcraft.client.world.ClientWorldView;
import dev.voxelcraft.core.block.Block;
import dev.voxelcraft.core.block.Blocks;
import dev.voxelcraft.core.world.Chunk;
import dev.voxelcraft.core.world.Section;
import dev.voxelcraft.core.world.World;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public final class ChunkMesher {
    private static final int VERTICAL_RANGE_BELOW = 96;
    private static final int VERTICAL_RANGE_ABOVE = 192;

    private long cachedWorldVersion = Long.MIN_VALUE;
    private int cachedMinY = Integer.MIN_VALUE;
    private int cachedMaxY = Integer.MAX_VALUE;
    private Mesh cachedMesh = new Mesh(List.of());

    public Mesh build(ClientWorldView worldView, double centerY) {
        long worldVersion = worldView.blockUpdateVersion();
        int minY = Math.max(World.MIN_Y, (int) Math.floor(centerY) - VERTICAL_RANGE_BELOW);
        int maxY = Math.min(World.MAX_Y, (int) Math.floor(centerY) + VERTICAL_RANGE_ABOVE);

        if (worldVersion == cachedWorldVersion && minY == cachedMinY && maxY == cachedMaxY) {
            return cachedMesh;
        }

        List<Mesh.Face> faces = new ArrayList<>();
        for (Chunk chunk : worldView.loadedChunks()) {
            int chunkBaseX = chunk.pos().x() * Section.SIZE;
            int chunkBaseZ = chunk.pos().z() * Section.SIZE;

            chunk.forEachNonAirInRange(minY, maxY, (localX, y, localZ, block) -> {
                int worldX = chunkBaseX + localX;
                int worldZ = chunkBaseZ + localZ;
                addVisibleFaces(worldView, faces, block, worldX, y, worldZ);
            });
        }

        cachedMesh = new Mesh(faces);
        cachedWorldVersion = worldVersion;
        cachedMinY = minY;
        cachedMaxY = maxY;
        return cachedMesh;
    }

    private static void addVisibleFaces(
        ClientWorldView worldView,
        List<Mesh.Face> outFaces,
        Block block,
        int x,
        int y,
        int z
    ) {
        if (block == Blocks.AIR || !block.solid()) {
            return;
        }

        Color blockColor = colorFor(block);
        for (FaceDirection direction : FaceDirection.values()) {
            if (worldView.isSolid(x + direction.dx, y + direction.dy, z + direction.dz)) {
                continue;
            }

            float detail = detailBrightness(block, direction, x, y, z);
            Color shaded = shade(blockColor, direction.brightness * detail);
            Vec3 v0 = direction.vertex(x, y, z, 0);
            Vec3 v1 = direction.vertex(x, y, z, 1);
            Vec3 v2 = direction.vertex(x, y, z, 2);
            Vec3 v3 = direction.vertex(x, y, z, 3);
            outFaces.add(new Mesh.Face(v0, v1, v2, v3, shaded));
        }
    }

    private static Color colorFor(Block block) {
        if (block == Blocks.GRASS) {
            return new Color(96, 170, 82);
        }
        if (block == Blocks.DIRT) {
            return new Color(127, 94, 66);
        }
        if (block == Blocks.STONE) {
            return new Color(134, 138, 145);
        }
        if (block == Blocks.SAND) {
            return new Color(214, 198, 148);
        }
        if (block == Blocks.WOOD) {
            return new Color(132, 94, 57);
        }
        if (block == Blocks.LEAVES) {
            return new Color(76, 140, 72);
        }
        return new Color(215, 103, 60);
    }

    private static float detailBrightness(Block block, FaceDirection direction, int x, int y, int z) {
        int hash = hash(x, y, z);
        float randomJitter = 0.90f + ((hash & 15) / 15.0f) * 0.18f;
        float stripe = (((x + z) & 1) == 0) ? 0.96f : 1.02f;

        if (block == Blocks.GRASS && direction == FaceDirection.UP) {
            return randomJitter * stripe;
        }
        if (block == Blocks.STONE) {
            return randomJitter * 0.98f;
        }
        if (block == Blocks.DIRT || block == Blocks.SAND) {
            return randomJitter * (0.94f + ((y & 1) * 0.04f));
        }
        if (block == Blocks.LEAVES) {
            return randomJitter * 1.04f;
        }
        return randomJitter;
    }

    private static Color shade(Color color, float brightness) {
        int red = clamp((int) (color.getRed() * brightness));
        int green = clamp((int) (color.getGreen() * brightness));
        int blue = clamp((int) (color.getBlue() * brightness));
        return new Color(red, green, blue);
    }

    private static int hash(int x, int y, int z) {
        int value = x * 734_287 + y * 912_931 + z * 438_289;
        value ^= (value >>> 13);
        value *= 1_274_126_177;
        value ^= (value >>> 16);
        return value;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private enum FaceDirection {
        UP(0, 1, 0, 1.0f) {
            @Override
            Vec3 vertex(int x, int y, int z, int index) {
                return switch (index) {
                    case 0 -> new Vec3(x, y + 1, z);
                    case 1 -> new Vec3(x + 1, y + 1, z);
                    case 2 -> new Vec3(x + 1, y + 1, z + 1);
                    default -> new Vec3(x, y + 1, z + 1);
                };
            }
        },
        DOWN(0, -1, 0, 0.58f) {
            @Override
            Vec3 vertex(int x, int y, int z, int index) {
                return switch (index) {
                    case 0 -> new Vec3(x, y, z + 1);
                    case 1 -> new Vec3(x + 1, y, z + 1);
                    case 2 -> new Vec3(x + 1, y, z);
                    default -> new Vec3(x, y, z);
                };
            }
        },
        NORTH(0, 0, -1, 0.82f) {
            @Override
            Vec3 vertex(int x, int y, int z, int index) {
                return switch (index) {
                    case 0 -> new Vec3(x + 1, y, z);
                    case 1 -> new Vec3(x + 1, y + 1, z);
                    case 2 -> new Vec3(x, y + 1, z);
                    default -> new Vec3(x, y, z);
                };
            }
        },
        SOUTH(0, 0, 1, 0.82f) {
            @Override
            Vec3 vertex(int x, int y, int z, int index) {
                return switch (index) {
                    case 0 -> new Vec3(x, y, z + 1);
                    case 1 -> new Vec3(x, y + 1, z + 1);
                    case 2 -> new Vec3(x + 1, y + 1, z + 1);
                    default -> new Vec3(x + 1, y, z + 1);
                };
            }
        },
        WEST(-1, 0, 0, 0.74f) {
            @Override
            Vec3 vertex(int x, int y, int z, int index) {
                return switch (index) {
                    case 0 -> new Vec3(x, y, z);
                    case 1 -> new Vec3(x, y + 1, z);
                    case 2 -> new Vec3(x, y + 1, z + 1);
                    default -> new Vec3(x, y, z + 1);
                };
            }
        },
        EAST(1, 0, 0, 0.74f) {
            @Override
            Vec3 vertex(int x, int y, int z, int index) {
                return switch (index) {
                    case 0 -> new Vec3(x + 1, y, z + 1);
                    case 1 -> new Vec3(x + 1, y + 1, z + 1);
                    case 2 -> new Vec3(x + 1, y + 1, z);
                    default -> new Vec3(x + 1, y, z);
                };
            }
        };

        private final int dx;
        private final int dy;
        private final int dz;
        private final float brightness;

        FaceDirection(int dx, int dy, int dz, float brightness) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.brightness = brightness;
        }

        abstract Vec3 vertex(int x, int y, int z, int index);
    }
}
