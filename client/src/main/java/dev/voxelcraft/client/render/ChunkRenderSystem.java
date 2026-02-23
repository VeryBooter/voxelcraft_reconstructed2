package dev.voxelcraft.client.render;

import dev.voxelcraft.client.player.PlayerController;
import dev.voxelcraft.client.world.ClientWorldView;
import dev.voxelcraft.core.world.BlockPos;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ChunkRenderSystem {
    public static final boolean USE_TEXTURE_ATLAS = false;
    private static final boolean DRAW_FACE_OUTLINES = false;
    private static final Comparator<ProjectedFace> PROJECTED_FACE_DEPTH_DESC =
        Comparator.comparingDouble(ProjectedFace::averageDepth).reversed();

    private static final float VERTICAL_FOV_DEGREES = 75.0f;
    private static final double NEAR_PLANE = 0.05;
    private static final double FAR_PLANE = 256.0;

    private final ChunkMesher mesher = new ChunkMesher();
    private final Frustum frustum = new Frustum();
    private final Frustum.CameraPoint scratchCamera = new Frustum.CameraPoint();
    private final MutableScreenPoint screenPointScratch = new MutableScreenPoint();
    private final ArrayList<ProjectedFace> projectedFacesScratch = new ArrayList<>();
    private final ArrayList<ProjectedFace> projectedFacePool = new ArrayList<>();
    private int projectedFacePoolUsed;

    private int viewportWidth;
    private int viewportHeight;
    private double focalLength;

    public RenderStats draw(Graphics2D graphics, int width, int height, ClientWorldView worldView, PlayerController player) {
        updateCamera(player, width, height);

        Mesh mesh = mesher.build(worldView, player.y());
        projectedFacePoolUsed = 0;
        projectedFacesScratch.clear();
        projectedFacesScratch.ensureCapacity(mesh.faceCount());

        int visibleCandidates = 0;
        for (Mesh.ChunkBatch chunkBatch : mesh.chunks()) {
            if (!frustum.isAabbVisible(
                chunkBatch.minX(),
                chunkBatch.minY(),
                chunkBatch.minZ(),
                chunkBatch.maxX(),
                chunkBatch.maxY(),
                chunkBatch.maxZ()
            )) {
                continue;
            }

            visibleCandidates += chunkBatch.faceCount();
            for (Mesh.Face face : chunkBatch.faces()) {
                ProjectedFace projected = projectFace(face);
                if (projected == null) {
                    continue;
                }
                projectedFacesScratch.add(projected);
            }
        }

        projectedFacesScratch.sort(PROJECTED_FACE_DEPTH_DESC);

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        for (ProjectedFace projectedFace : projectedFacesScratch) {
            graphics.setColor(projectedFace.color());
            graphics.fillPolygon(projectedFace.xPoints(), projectedFace.yPoints(), 4);
            if (DRAW_FACE_OUTLINES) {
                graphics.setColor(shade(projectedFace.color(), 0.72f));
                graphics.drawPolygon(projectedFace.xPoints(), projectedFace.yPoints(), 4);
            }
        }

        return new RenderStats(mesh.faceCount(), visibleCandidates, projectedFacesScratch.size());
    }

    public void drawSelectionBox(Graphics2D graphics, int width, int height, PlayerController player, BlockPos blockPos) {
        updateCamera(player, width, height);

        int x = blockPos.x();
        int y = blockPos.y();
        int z = blockPos.z();

        Vec3[] corners = {
            new Vec3(x, y, z),
            new Vec3(x + 1, y, z),
            new Vec3(x + 1, y + 1, z),
            new Vec3(x, y + 1, z),
            new Vec3(x, y, z + 1),
            new Vec3(x + 1, y, z + 1),
            new Vec3(x + 1, y + 1, z + 1),
            new Vec3(x, y + 1, z + 1)
        };

        ScreenPoint[] points = new ScreenPoint[8];
        for (int i = 0; i < corners.length; i++) {
            points[i] = projectPoint(corners[i]);
            if (points[i] == null) {
                return;
            }
        }

        int[][] edges = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };

        java.awt.Stroke previousStroke = graphics.getStroke();
        graphics.setStroke(new BasicStroke(2.0f));
        graphics.setColor(new Color(255, 255, 255, 220));
        for (int[] edge : edges) {
            ScreenPoint a = points[edge[0]];
            ScreenPoint b = points[edge[1]];
            graphics.drawLine(a.screenX(), a.screenY(), b.screenX(), b.screenY());
        }
        graphics.setStroke(previousStroke);
    }

    private void updateCamera(PlayerController player, int width, int height) {
        viewportWidth = Math.max(1, width);
        viewportHeight = Math.max(1, height);

        double aspect = (double) viewportWidth / (double) viewportHeight;
        focalLength = (viewportHeight * 0.5) / Math.tan(Math.toRadians(VERTICAL_FOV_DEGREES) * 0.5);

        frustum.setCamera(
            player.eyeX(),
            player.eyeY(),
            player.eyeZ(),
            player.yaw(),
            player.pitch(),
            VERTICAL_FOV_DEGREES,
            aspect,
            NEAR_PLANE,
            FAR_PLANE
        );
    }

    private ProjectedFace projectFace(Mesh.Face face) {
        if (!projectPointInto(face.v0(), screenPointScratch)) {
            return null;
        }
        int x0 = screenPointScratch.screenX;
        int y0 = screenPointScratch.screenY;
        double d0 = screenPointScratch.depth;

        if (!projectPointInto(face.v1(), screenPointScratch)) {
            return null;
        }
        int x1 = screenPointScratch.screenX;
        int y1 = screenPointScratch.screenY;
        double d1 = screenPointScratch.depth;

        if (!projectPointInto(face.v2(), screenPointScratch)) {
            return null;
        }
        int x2 = screenPointScratch.screenX;
        int y2 = screenPointScratch.screenY;
        double d2 = screenPointScratch.depth;

        if (!projectPointInto(face.v3(), screenPointScratch)) {
            return null;
        }
        int x3 = screenPointScratch.screenX;
        int y3 = screenPointScratch.screenY;
        double d3 = screenPointScratch.depth;

        ProjectedFace projectedFace = acquireProjectedFace();
        projectedFace.set(
            x0, y0,
            x1, y1,
            x2, y2,
            x3, y3,
            (d0 + d1 + d2 + d3) * 0.25,
            face.color()
        );
        return projectedFace;
    }

    private ProjectedFace acquireProjectedFace() {
        if (projectedFacePoolUsed >= projectedFacePool.size()) {
            projectedFacePool.add(new ProjectedFace());
        }
        return projectedFacePool.get(projectedFacePoolUsed++);
    }

    private boolean projectPointInto(Vec3 worldPoint, MutableScreenPoint out) {
        frustum.toCameraSpace(worldPoint.x(), worldPoint.y(), worldPoint.z(), scratchCamera);
        if (scratchCamera.z <= frustum.nearPlane()) {
            return false;
        }

        double ndcX = scratchCamera.x / scratchCamera.z;
        double ndcY = scratchCamera.y / scratchCamera.z;

        out.screenX = (int) Math.round(viewportWidth * 0.5 + ndcX * focalLength);
        out.screenY = (int) Math.round(viewportHeight * 0.5 - ndcY * focalLength);
        out.depth = scratchCamera.z;
        return true;
    }

    private ScreenPoint projectPoint(Vec3 worldPoint) {
        if (!projectPointInto(worldPoint, screenPointScratch)) {
            return null;
        }
        return new ScreenPoint(screenPointScratch.screenX, screenPointScratch.screenY, screenPointScratch.depth);
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

    public record RenderStats(int totalFaces, int frustumCandidates, int drawnFaces) {
    }

    private record ScreenPoint(int screenX, int screenY, double depth) {
    }

    private static final class MutableScreenPoint {
        private int screenX;
        private int screenY;
        private double depth;
    }

    private static final class ProjectedFace {
        private final int[] xPoints = new int[4];
        private final int[] yPoints = new int[4];
        private double averageDepth;
        private Color color;

        private void set(
            int x0, int y0,
            int x1, int y1,
            int x2, int y2,
            int x3, int y3,
            double averageDepth,
            Color color
        ) {
            xPoints[0] = x0;
            yPoints[0] = y0;
            xPoints[1] = x1;
            yPoints[1] = y1;
            xPoints[2] = x2;
            yPoints[2] = y2;
            xPoints[3] = x3;
            yPoints[3] = y3;
            this.averageDepth = averageDepth;
            this.color = color;
        }

        private int[] xPoints() {
            return xPoints;
        }

        private int[] yPoints() {
            return yPoints;
        }

        private double averageDepth() {
            return averageDepth;
        }

        private Color color() {
            return color;
        }
    }
}
