package dev.voxelcraft.client.render;

import dev.voxelcraft.client.GameClient;
import dev.voxelcraft.client.player.PlayerController;
import dev.voxelcraft.client.render.ChunkRenderSystem.RenderStats;
import dev.voxelcraft.client.world.BlockHitResult;
import dev.voxelcraft.core.world.BlockPos;
import java.awt.Color;

import static org.lwjgl.opengl.GL11.GL_BACK;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_PROJECTION;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glColor3f;
import static org.lwjgl.opengl.GL11.glCullFace;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glFrustum;
import static org.lwjgl.opengl.GL11.glLineWidth;
import static org.lwjgl.opengl.GL11.glLoadIdentity;
import static org.lwjgl.opengl.GL11.glMatrixMode;
import static org.lwjgl.opengl.GL11.glOrtho;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glRotatef;
import static org.lwjgl.opengl.GL11.glTranslated;
import static org.lwjgl.opengl.GL11.glVertex2f;
import static org.lwjgl.opengl.GL11.glVertex3d;
import static org.lwjgl.opengl.GL11.glViewport;

public final class GpuChunkRenderer {
    private static final float VERTICAL_FOV_DEGREES = 75.0f;
    private static final double NEAR_PLANE = 0.05;
    private static final double FAR_PLANE = 320.0;

    private final ChunkMesher mesher = new ChunkMesher();
    private final Frustum frustum = new Frustum();

    public RenderStats render(int width, int height, GameClient gameClient) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);

        PlayerController player = gameClient.playerController();
        float ambient = gameClient.ambientLight();

        glViewport(0, 0, safeWidth, safeHeight);

        float skyR = 0.31f * ambient;
        float skyG = 0.53f * ambient;
        float skyB = 0.78f * ambient;
        glClearColor(skyR, skyG, skyB, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        configureProjection(safeWidth, safeHeight);
        configureCamera(player);

        double aspect = (double) safeWidth / (double) safeHeight;
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

        Mesh mesh = mesher.build(gameClient.worldView(), player.y());
        int totalFaces = mesh.faceCount();
        int frustumCandidates = 0;
        int drawnFaces = 0;

        glBegin(GL_QUADS);
        for (Mesh.Face face : mesh.faces()) {
            if (!frustum.isAabbVisible(face.minX(), face.minY(), face.minZ(), face.maxX(), face.maxY(), face.maxZ())) {
                continue;
            }

            frustumCandidates++;
            applyColor(face.color(), ambient);
            glVertex3d(face.v0().x(), face.v0().y(), face.v0().z());
            glVertex3d(face.v1().x(), face.v1().y(), face.v1().z());
            glVertex3d(face.v2().x(), face.v2().y(), face.v2().z());
            glVertex3d(face.v3().x(), face.v3().y(), face.v3().z());
            drawnFaces++;
        }
        glEnd();

        BlockHitResult hitResult = gameClient.targetedBlock();
        if (hitResult != null) {
            drawSelectionBox(hitResult.targetBlock());
        }

        drawCrosshair(safeWidth, safeHeight);
        return new RenderStats(totalFaces, frustumCandidates, drawnFaces);
    }

    private static void configureProjection(int width, int height) {
        double aspect = (double) width / (double) height;
        double top = NEAR_PLANE * Math.tan(Math.toRadians(VERTICAL_FOV_DEGREES * 0.5));
        double bottom = -top;
        double right = top * aspect;
        double left = -right;

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glFrustum(left, right, bottom, top, NEAR_PLANE, FAR_PLANE);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    private static void configureCamera(PlayerController player) {
        glRotatef(player.pitch(), 1.0f, 0.0f, 0.0f);
        glRotatef(180.0f - player.yaw(), 0.0f, 1.0f, 0.0f);
        glTranslated(-player.eyeX(), -player.eyeY(), -player.eyeZ());
    }

    private static void drawSelectionBox(BlockPos blockPos) {
        double epsilon = 0.002;
        double minX = blockPos.x() - epsilon;
        double minY = blockPos.y() - epsilon;
        double minZ = blockPos.z() - epsilon;
        double maxX = blockPos.x() + 1.0 + epsilon;
        double maxY = blockPos.y() + 1.0 + epsilon;
        double maxZ = blockPos.z() + 1.0 + epsilon;

        glDisable(GL_CULL_FACE);
        glLineWidth(2.0f);
        glColor3f(1.0f, 1.0f, 1.0f);

        glBegin(GL_LINES);
        drawLine(minX, minY, minZ, maxX, minY, minZ);
        drawLine(maxX, minY, minZ, maxX, maxY, minZ);
        drawLine(maxX, maxY, minZ, minX, maxY, minZ);
        drawLine(minX, maxY, minZ, minX, minY, minZ);

        drawLine(minX, minY, maxZ, maxX, minY, maxZ);
        drawLine(maxX, minY, maxZ, maxX, maxY, maxZ);
        drawLine(maxX, maxY, maxZ, minX, maxY, maxZ);
        drawLine(minX, maxY, maxZ, minX, minY, maxZ);

        drawLine(minX, minY, minZ, minX, minY, maxZ);
        drawLine(maxX, minY, minZ, maxX, minY, maxZ);
        drawLine(maxX, maxY, minZ, maxX, maxY, maxZ);
        drawLine(minX, maxY, minZ, minX, maxY, maxZ);
        glEnd();

        glEnable(GL_CULL_FACE);
    }

    private static void drawLine(double x0, double y0, double z0, double x1, double y1, double z1) {
        glVertex3d(x0, y0, z0);
        glVertex3d(x1, y1, z1);
    }

    private static void drawCrosshair(int width, int height) {
        float centerX = width * 0.5f;
        float centerY = height * 0.5f;

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0.0, width, height, 0.0, -1.0, 1.0);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glDisable(GL_DEPTH_TEST);
        glLineWidth(2.0f);
        glColor3f(1.0f, 1.0f, 1.0f);
        glBegin(GL_LINES);
        glVertex2f(centerX - 7.0f, centerY);
        glVertex2f(centerX + 7.0f, centerY);
        glVertex2f(centerX, centerY - 7.0f);
        glVertex2f(centerX, centerY + 7.0f);
        glEnd();
        glEnable(GL_DEPTH_TEST);

        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    private static void applyColor(Color color, float ambient) {
        float red = (color.getRed() / 255.0f) * ambient;
        float green = (color.getGreen() / 255.0f) * ambient;
        float blue = (color.getBlue() / 255.0f) * ambient;
        glColor3f(red, green, blue);
    }
}
