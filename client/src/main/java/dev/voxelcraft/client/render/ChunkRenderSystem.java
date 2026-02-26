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
/**
 * 中文说明：软件渲染路径：负责 CPU 网格投影、排序以及 2D 画布绘制。
 */

// 中文标注（类）：`ChunkRenderSystem`，职责：封装区块、渲染、system相关逻辑。
public final class ChunkRenderSystem {
    // 中文标注（字段）：`USE_TEXTURE_ATLAS`，含义：用于表示use、纹理、atlas。
    public static final boolean USE_TEXTURE_ATLAS = false;
    // 中文标注（字段）：`DRAW_FACE_OUTLINES`，含义：用于表示绘制、面、outlines。
    private static final boolean DRAW_FACE_OUTLINES = false;
    private static final boolean APPLY_AMBIENT_TO_BLOCKS = lightingFlagCompat(
        "vc.lighting.applyAmbientToBlocks",
        "voxelcraft.lighting.applyAmbientToBlocks",
        true
    );
    // 中文标注（字段）：`PROJECTED_FACE_DEPTH_DESC`，含义：用于表示projected、面、深度、desc。
    private static final Comparator<ProjectedFace> PROJECTED_FACE_DEPTH_DESC =
        Comparator.comparingDouble(ProjectedFace::averageDepth).reversed();

    // 中文标注（字段）：`VERTICAL_FOV_DEGREES`，含义：用于表示垂直、fov、degrees。
    private static final float VERTICAL_FOV_DEGREES = 75.0f;
    // 中文标注（字段）：`NEAR_PLANE`，含义：用于表示near、plane。
    private static final double NEAR_PLANE = 0.05;
    // 中文标注（字段）：`FAR_PLANE`，含义：用于表示far、plane。
    private static final double FAR_PLANE = 256.0;

    // 中文标注（字段）：`mesher`，含义：用于表示mesher。
    private final ChunkMesher mesher = new ChunkMesher();
    // 中文标注（字段）：`frustum`，含义：用于表示视锥体。
    private final Frustum frustum = new Frustum();
    // 中文标注（字段）：`scratchCamera`，含义：用于表示临时工作区、相机。
    private final Frustum.CameraPoint scratchCamera = new Frustum.CameraPoint();
    // 中文标注（字段）：`screenPointScratch`，含义：用于表示screen、point、临时工作区。
    private final MutableScreenPoint screenPointScratch = new MutableScreenPoint();
    // 中文标注（字段）：`projectedFacesScratch`，含义：用于表示projected、面集合、临时工作区。
    private final ArrayList<ProjectedFace> projectedFacesScratch = new ArrayList<>();
    // 中文标注（字段）：`projectedFacePool`，含义：用于表示projected、面、池。
    private final ArrayList<ProjectedFace> projectedFacePool = new ArrayList<>();
    // 中文标注（字段）：`projectedFacePoolUsed`，含义：用于表示projected、面、池、used。
    private int projectedFacePoolUsed;

    // 中文标注（字段）：`viewportWidth`，含义：用于表示viewport、宽度。
    private int viewportWidth;
    // 中文标注（字段）：`viewportHeight`，含义：用于表示viewport、高度。
    private int viewportHeight;
    // 中文标注（字段）：`focalLength`，含义：用于表示focal、长度。
    private double focalLength;

    // 中文标注（方法）：`draw`，参数：graphics、width、height、worldView、player；用途：执行渲染或图形资源处理：绘制。
    // 中文标注（参数）：`graphics`，含义：用于表示graphics。
    // 中文标注（参数）：`width`，含义：用于表示宽度。
    // 中文标注（参数）：`height`，含义：用于表示高度。
    // 中文标注（参数）：`worldView`，含义：用于表示世界、view。
    // 中文标注（参数）：`player`，含义：用于表示玩家。
    public RenderStats draw(
        Graphics2D graphics,
        int width,
        int height,
        ClientWorldView worldView,
        PlayerController player,
        float ambient
    ) {
        updateCamera(player, width, height);

        // 中文标注（局部变量）：`mesh`，含义：用于表示网格。
        Mesh mesh = mesher.build(worldView, player.y());
        projectedFacePoolUsed = 0;
        projectedFacesScratch.clear();
        projectedFacesScratch.ensureCapacity(mesh.faceCount());

        // 中文标注（局部变量）：`visibleCandidates`，含义：用于表示visible、candidates。
        int visibleCandidates = 0;
        // 软件路径也先做 chunk 级视锥裁剪，再投影该 chunk 的面，避免 per-face frustum 开销。
        // 中文标注（局部变量）：`chunkBatch`，含义：用于表示区块、batch。
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
            // 中文标注（局部变量）：`face`，含义：用于表示面。
            for (Mesh.Face face : chunkBatch.faces()) {
                // 中文标注（局部变量）：`projected`，含义：用于表示projected。
                ProjectedFace projected = projectFace(face);
                if (projected == null) {
                    continue;
                }
                projectedFacesScratch.add(projected);
            }
        }

        projectedFacesScratch.sort(PROJECTED_FACE_DEPTH_DESC);

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        float blockAmbient = APPLY_AMBIENT_TO_BLOCKS ? ambient : 1.0f;
        // 中文标注（局部变量）：`projectedFace`，含义：用于表示projected、面。
        for (ProjectedFace projectedFace : projectedFacesScratch) {
            Color fillColor = blockAmbient == 1.0f ? projectedFace.color() : shade(projectedFace.color(), blockAmbient);
            graphics.setColor(fillColor);
            graphics.fillPolygon(projectedFace.xPoints(), projectedFace.yPoints(), 4);
            if (DRAW_FACE_OUTLINES) {
                graphics.setColor(shade(fillColor, 0.72f));
                graphics.drawPolygon(projectedFace.xPoints(), projectedFace.yPoints(), 4);
            }
        }

        return new RenderStats(mesh.faceCount(), visibleCandidates, projectedFacesScratch.size());
    }

    // 中文标注（方法）：`drawSelectionBox`，参数：graphics、width、height、player、blockPos；用途：执行渲染或图形资源处理：绘制、selection、box。
    // 中文标注（参数）：`graphics`，含义：用于表示graphics。
    // 中文标注（参数）：`width`，含义：用于表示宽度。
    // 中文标注（参数）：`height`，含义：用于表示高度。
    // 中文标注（参数）：`player`，含义：用于表示玩家。
    // 中文标注（参数）：`blockPos`，含义：用于表示方块、位置。
    public void drawSelectionBox(Graphics2D graphics, int width, int height, PlayerController player, BlockPos blockPos) {
        updateCamera(player, width, height);

        // 中文标注（局部变量）：`x`，含义：用于表示X坐标。
        int x = blockPos.x();
        // 中文标注（局部变量）：`y`，含义：用于表示Y坐标。
        int y = blockPos.y();
        // 中文标注（局部变量）：`z`，含义：用于表示Z坐标。
        int z = blockPos.z();

        // 中文标注（局部变量）：`corners`，含义：用于表示corners。
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

        // 中文标注（局部变量）：`points`，含义：用于表示points。
        ScreenPoint[] points = new ScreenPoint[8];
        // 中文标注（局部变量）：`i`，含义：用于表示i。
        for (int i = 0; i < corners.length; i++) {
            points[i] = projectPoint(corners[i]);
            if (points[i] == null) {
                return;
            }
        }

        // 中文标注（局部变量）：`edges`，含义：用于表示edges。
        int[][] edges = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };

        // 中文标注（局部变量）：`previousStroke`，含义：用于表示previous、stroke。
        java.awt.Stroke previousStroke = graphics.getStroke();
        graphics.setStroke(new BasicStroke(2.0f));
        graphics.setColor(new Color(255, 255, 255, 220));
        // 中文标注（局部变量）：`edge`，含义：用于表示edge。
        for (int[] edge : edges) {
            // 中文标注（局部变量）：`a`，含义：用于表示a。
            ScreenPoint a = points[edge[0]];
            // 中文标注（局部变量）：`b`，含义：用于表示b。
            ScreenPoint b = points[edge[1]];
            graphics.drawLine(a.screenX(), a.screenY(), b.screenX(), b.screenY());
        }
        graphics.setStroke(previousStroke);
    }

    // 中文标注（方法）：`updateCamera`，参数：player、width、height；用途：更新更新、相机相关状态。
    // 中文标注（参数）：`player`，含义：用于表示玩家。
    // 中文标注（参数）：`width`，含义：用于表示宽度。
    // 中文标注（参数）：`height`，含义：用于表示高度。
    private void updateCamera(PlayerController player, int width, int height) {
        viewportWidth = Math.max(1, width);
        viewportHeight = Math.max(1, height);

        // 中文标注（局部变量）：`aspect`，含义：用于表示aspect。
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

    // 中文标注（方法）：`projectFace`，参数：face；用途：执行project、面相关逻辑。
    // 中文标注（参数）：`face`，含义：用于表示面。
    private ProjectedFace projectFace(Mesh.Face face) {
        if (!projectPointInto(face.v0(), screenPointScratch)) {
            return null;
        }
        // 中文标注（局部变量）：`x0`，含义：用于表示X坐标、0。
        int x0 = screenPointScratch.screenX;
        // 中文标注（局部变量）：`y0`，含义：用于表示Y坐标、0。
        int y0 = screenPointScratch.screenY;
        // 中文标注（局部变量）：`d0`，含义：用于表示d、0。
        double d0 = screenPointScratch.depth;

        if (!projectPointInto(face.v1(), screenPointScratch)) {
            return null;
        }
        // 中文标注（局部变量）：`x1`，含义：用于表示X坐标、1。
        int x1 = screenPointScratch.screenX;
        // 中文标注（局部变量）：`y1`，含义：用于表示Y坐标、1。
        int y1 = screenPointScratch.screenY;
        // 中文标注（局部变量）：`d1`，含义：用于表示d、1。
        double d1 = screenPointScratch.depth;

        if (!projectPointInto(face.v2(), screenPointScratch)) {
            return null;
        }
        // 中文标注（局部变量）：`x2`，含义：用于表示X坐标、2。
        int x2 = screenPointScratch.screenX;
        // 中文标注（局部变量）：`y2`，含义：用于表示Y坐标、2。
        int y2 = screenPointScratch.screenY;
        // 中文标注（局部变量）：`d2`，含义：用于表示d、2。
        double d2 = screenPointScratch.depth;

        if (!projectPointInto(face.v3(), screenPointScratch)) {
            return null;
        }
        // 中文标注（局部变量）：`x3`，含义：用于表示X坐标、3。
        int x3 = screenPointScratch.screenX;
        // 中文标注（局部变量）：`y3`，含义：用于表示Y坐标、3。
        int y3 = screenPointScratch.screenY;
        // 中文标注（局部变量）：`d3`，含义：用于表示d、3。
        double d3 = screenPointScratch.depth;

        // 中文标注（局部变量）：`projectedFace`，含义：用于表示projected、面。
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

    // 中文标注（方法）：`acquireProjectedFace`，参数：无；用途：执行acquire、projected、面相关逻辑。
    private ProjectedFace acquireProjectedFace() {
        if (projectedFacePoolUsed >= projectedFacePool.size()) {
            projectedFacePool.add(new ProjectedFace());
        }
        return projectedFacePool.get(projectedFacePoolUsed++);
    }

    // 中文标注（方法）：`projectPointInto`，参数：worldPoint、out；用途：执行project、point、into相关逻辑。
    // 中文标注（参数）：`worldPoint`，含义：用于表示世界、point。
    // 中文标注（参数）：`out`，含义：用于表示out。
    private boolean projectPointInto(Vec3 worldPoint, MutableScreenPoint out) {
        frustum.toCameraSpace(worldPoint.x(), worldPoint.y(), worldPoint.z(), scratchCamera);
        if (scratchCamera.z <= frustum.nearPlane()) {
            return false;
        }

        // 中文标注（局部变量）：`ndcX`，含义：用于表示ndc、X坐标。
        double ndcX = scratchCamera.x / scratchCamera.z;
        // 中文标注（局部变量）：`ndcY`，含义：用于表示ndc、Y坐标。
        double ndcY = scratchCamera.y / scratchCamera.z;

        out.screenX = (int) Math.round(viewportWidth * 0.5 + ndcX * focalLength);
        out.screenY = (int) Math.round(viewportHeight * 0.5 - ndcY * focalLength);
        out.depth = scratchCamera.z;
        return true;
    }

    // 中文标注（方法）：`projectPoint`，参数：worldPoint；用途：执行project、point相关逻辑。
    // 中文标注（参数）：`worldPoint`，含义：用于表示世界、point。
    private ScreenPoint projectPoint(Vec3 worldPoint) {
        if (!projectPointInto(worldPoint, screenPointScratch)) {
            return null;
        }
        return new ScreenPoint(screenPointScratch.screenX, screenPointScratch.screenY, screenPointScratch.depth);
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

    private static boolean lightingFlagCompat(String key, String legacyKey, boolean defaultValue) {
        String raw = System.getProperty(key);
        if (raw == null) {
            raw = System.getProperty(legacyKey);
        }
        if (raw == null) {
            return defaultValue;
        }
        String normalized = raw.trim().toLowerCase();
        if (normalized.equals("1") || normalized.equals("true") || normalized.equals("yes") || normalized.equals("on")) {
            return true;
        }
        if (normalized.equals("0") || normalized.equals("false") || normalized.equals("no") || normalized.equals("off")) {
            return false;
        }
        return defaultValue;
    }

    // 中文标注（记录类）：`RenderStats`，职责：封装渲染、stats相关逻辑。
    // 中文标注（字段）：`totalFaces`，含义：用于表示total、面集合。
    // 中文标注（字段）：`frustumCandidates`，含义：用于表示视锥体、candidates。
    // 中文标注（字段）：`drawnFaces`，含义：用于表示drawn、面集合。
    public record RenderStats(int totalFaces, int frustumCandidates, int drawnFaces) {
    }

    // 中文标注（记录类）：`ScreenPoint`，职责：封装screen、point相关逻辑。
    // 中文标注（字段）：`screenX`，含义：用于表示screen、X坐标。
    // 中文标注（字段）：`screenY`，含义：用于表示screen、Y坐标。
    // 中文标注（字段）：`depth`，含义：用于表示深度。
    private record ScreenPoint(int screenX, int screenY, double depth) {
    }

    // 中文标注（类）：`MutableScreenPoint`，职责：封装mutable、screen、point相关逻辑。
    private static final class MutableScreenPoint {
        // 中文标注（字段）：`screenX`，含义：用于表示screen、X坐标。
        private int screenX;
        // 中文标注（字段）：`screenY`，含义：用于表示screen、Y坐标。
        private int screenY;
        // 中文标注（字段）：`depth`，含义：用于表示深度。
        private double depth;
    }

    // 中文标注（类）：`ProjectedFace`，职责：封装projected、面相关逻辑。
    private static final class ProjectedFace {
        // 中文标注（字段）：`xPoints`，含义：用于表示X坐标、points。
        private final int[] xPoints = new int[4];
        // 中文标注（字段）：`yPoints`，含义：用于表示Y坐标、points。
        private final int[] yPoints = new int[4];
        // 中文标注（字段）：`averageDepth`，含义：用于表示average、深度。
        private double averageDepth;
        // 中文标注（字段）：`color`，含义：用于表示颜色。
        private Color color;

        // 中文标注（方法）：`set`，参数：x0、y0、x1、y1、x2、y2、x3、y3、averageDepth、color；用途：设置、写入或注册集合。
        private void set(
            // 中文标注（参数）：`x0`，含义：用于表示X坐标、0。
            // 中文标注（参数）：`y0`，含义：用于表示Y坐标、0。
            int x0, int y0,
            // 中文标注（参数）：`x1`，含义：用于表示X坐标、1。
            // 中文标注（参数）：`y1`，含义：用于表示Y坐标、1。
            int x1, int y1,
            // 中文标注（参数）：`x2`，含义：用于表示X坐标、2。
            // 中文标注（参数）：`y2`，含义：用于表示Y坐标、2。
            int x2, int y2,
            // 中文标注（参数）：`x3`，含义：用于表示X坐标、3。
            // 中文标注（参数）：`y3`，含义：用于表示Y坐标、3。
            int x3, int y3,
            // 中文标注（参数）：`averageDepth`，含义：用于表示average、深度。
            double averageDepth,
            // 中文标注（参数）：`color`，含义：用于表示颜色。
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

        // 中文标注（方法）：`xPoints`，参数：无；用途：执行X坐标、points相关逻辑。
        private int[] xPoints() {
            return xPoints;
        }

        // 中文标注（方法）：`yPoints`，参数：无；用途：执行Y坐标、points相关逻辑。
        private int[] yPoints() {
            return yPoints;
        }

        // 中文标注（方法）：`averageDepth`，参数：无；用途：执行average、深度相关逻辑。
        private double averageDepth() {
            return averageDepth;
        }

        // 中文标注（方法）：`color`，参数：无；用途：执行颜色相关逻辑。
        private Color color() {
            return color;
        }
    }
}
