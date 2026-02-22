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

    private static final float VERTICAL_FOV_DEGREES = 75.0f;
    private static final double NEAR_PLANE = 0.05;
    private static final double FAR_PLANE = 256.0;

    private final ChunkMesher mesher = new ChunkMesher();
    private final Frustum frustum = new Frustum();

    private int viewportWidth;
    private int viewportHeight;
    private double focalLength;

    public RenderStats draw(Graphics2D graphics, int width, int height, ClientWorldView worldView, PlayerController player) {
        updateCamera(player, width, height);

        Mesh mesh = mesher.build(worldView, player.y());
        List<ProjectedFace> projectedFaces = new ArrayList<>(mesh.faceCount());

        int visibleCandidates = 0;
        for (Mesh.Face face : mesh.faces()) {
            if (!frustum.isAabbVisible(face.minX(), face.minY(), face.minZ(), face.maxX(), face.maxY(), face.maxZ())) {
                continue;
            }
            visibleCandidates++;

            ProjectedFace projected = projectFace(face);
            if (projected == null) {
                continue;
            }
            projectedFaces.add(projected);
        }

        projectedFaces.sort(Comparator.comparingDouble(ProjectedFace::averageDepth).reversed());

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        for (ProjectedFace projectedFace : projectedFaces) {
            graphics.setColor(projectedFace.color());
            graphics.fillPolygon(projectedFace.xPoints(), projectedFace.yPoints(), 4);
            if (DRAW_FACE_OUTLINES) {
                graphics.setColor(shade(projectedFace.color(), 0.72f));
                graphics.drawPolygon(projectedFace.xPoints(), projectedFace.yPoints(), 4);
            }
        }

        return new RenderStats(mesh.faceCount(), visibleCandidates, projectedFaces.size());
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
        ScreenPoint p0 = projectPoint(face.v0());
        ScreenPoint p1 = projectPoint(face.v1());
        ScreenPoint p2 = projectPoint(face.v2());
        ScreenPoint p3 = projectPoint(face.v3());
        if (p0 == null || p1 == null || p2 == null || p3 == null) {
            return null;
        }

        int[] xPoints = {p0.screenX(), p1.screenX(), p2.screenX(), p3.screenX()};
        int[] yPoints = {p0.screenY(), p1.screenY(), p2.screenY(), p3.screenY()};
        double averageDepth = (p0.depth() + p1.depth() + p2.depth() + p3.depth()) * 0.25;

        return new ProjectedFace(xPoints, yPoints, averageDepth, face.color());
    }

    private ScreenPoint projectPoint(Vec3 worldPoint) {
        Frustum.CameraPoint camera = frustum.toCameraSpace(worldPoint.x(), worldPoint.y(), worldPoint.z());
        if (camera.z() <= frustum.nearPlane()) {
            return null;
        }

        double ndcX = camera.x() / camera.z();
        double ndcY = camera.y() / camera.z();

        int screenX = (int) Math.round(viewportWidth * 0.5 + ndcX * focalLength);
        int screenY = (int) Math.round(viewportHeight * 0.5 - ndcY * focalLength);

        return new ScreenPoint(screenX, screenY, camera.z());
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

    private record ProjectedFace(int[] xPoints, int[] yPoints, double averageDepth, Color color) {
    }
}
