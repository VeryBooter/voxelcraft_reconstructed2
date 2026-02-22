package dev.voxelcraft.client.player;

import dev.voxelcraft.client.physics.AABB;
import dev.voxelcraft.client.platform.InputState;
import dev.voxelcraft.client.world.ClientWorldView;
import dev.voxelcraft.core.world.World;
import java.awt.event.KeyEvent;

public final class PlayerController {
    private static final double PLAYER_WIDTH = 0.6;
    private static final double PLAYER_HEIGHT = 1.8;
    private static final double EYE_HEIGHT = 1.62;

    private static final double WALK_SPEED = 8.6;
    private static final double SPRINT_SPEED = 12.4;
    private static final double GRAVITY = 24.0;
    private static final double JUMP_VELOCITY = 8.7;
    private static final double COLLISION_STEP = 0.05;

    private double x = 0.5;
    private double y = 8.0;
    private double z = 0.5;
    private double spawnX = 0.5;
    private double spawnY = 8.0;
    private double spawnZ = 0.5;

    private double verticalVelocity;
    private float pitch = -15.0f;
    private float yaw = 135.0f;
    private boolean onGround;
    private boolean jumpKeyDownLastTick;

    public void tick(ClientWorldView worldView, InputState input, double deltaSeconds) {
        updateView(input, deltaSeconds);

        boolean jumpKeyDown = input.isKeyDown(KeyEvent.VK_SPACE);
        if (jumpKeyDown && !jumpKeyDownLastTick && onGround) {
            verticalVelocity = JUMP_VELOCITY;
            onGround = false;
        }
        jumpKeyDownLastTick = jumpKeyDown;

        double forwardAxis = axis(input, KeyEvent.VK_W, KeyEvent.VK_S);
        double strafeAxis = axis(input, KeyEvent.VK_D, KeyEvent.VK_A);
        double speed = input.isKeyDown(KeyEvent.VK_SHIFT) ? SPRINT_SPEED : WALK_SPEED;

        double[] horizontalVelocity = computeHorizontalVelocity(forwardAxis, strafeAxis, speed);
        double moveX = horizontalVelocity[0] * deltaSeconds;
        double moveZ = horizontalVelocity[1] * deltaSeconds;

        verticalVelocity -= GRAVITY * deltaSeconds;
        if (verticalVelocity < -40.0) {
            verticalVelocity = -40.0;
        }
        double moveY = verticalVelocity * deltaSeconds;

        moveWithCollisions(worldView, moveX, moveY, moveZ);

        if (y < World.MIN_Y - 10) {
            respawn();
        }
    }

    private void updateView(InputState input, double deltaSeconds) {
        float mouseSensitivity = 0.28f;
        yaw += input.mouseDeltaX() * mouseSensitivity;
        pitch += input.mouseDeltaY() * mouseSensitivity;

        float keyLookSpeed = (float) (120.0 * deltaSeconds);
        if (input.isKeyDown(KeyEvent.VK_LEFT)) {
            yaw -= keyLookSpeed;
        }
        if (input.isKeyDown(KeyEvent.VK_RIGHT)) {
            yaw += keyLookSpeed;
        }
        if (input.isKeyDown(KeyEvent.VK_UP)) {
            pitch -= keyLookSpeed;
        }
        if (input.isKeyDown(KeyEvent.VK_DOWN)) {
            pitch += keyLookSpeed;
        }

        if (pitch < -89.0f) {
            pitch = -89.0f;
        }
        if (pitch > 89.0f) {
            pitch = 89.0f;
        }
    }

    private static double axis(InputState input, int positiveKey, int negativeKey) {
        double value = 0.0;
        if (input.isKeyDown(positiveKey)) {
            value += 1.0;
        }
        if (input.isKeyDown(negativeKey)) {
            value -= 1.0;
        }
        return value;
    }

    private double[] computeHorizontalVelocity(double forwardAxis, double strafeAxis, double speed) {
        if (forwardAxis == 0.0 && strafeAxis == 0.0) {
            return new double[] {0.0, 0.0};
        }

        double magnitude = Math.hypot(forwardAxis, strafeAxis);
        forwardAxis /= magnitude;
        strafeAxis /= magnitude;

        double yawRadians = Math.toRadians(yaw);
        double sin = Math.sin(yawRadians);
        double cos = Math.cos(yawRadians);

        double velocityX = (forwardAxis * sin + strafeAxis * cos) * speed;
        double velocityZ = (forwardAxis * cos - strafeAxis * sin) * speed;
        return new double[] {velocityX, velocityZ};
    }

    private void moveWithCollisions(ClientWorldView worldView, double moveX, double moveY, double moveZ) {
        moveAxis(worldView, moveX, Axis.X);
        moveAxis(worldView, moveZ, Axis.Z);

        double movedY = moveAxis(worldView, moveY, Axis.Y);
        if (moveY < 0.0 && Math.abs(movedY - moveY) > 1e-6) {
            onGround = true;
            verticalVelocity = 0.0;
        } else if (moveY > 0.0 && Math.abs(movedY - moveY) > 1e-6) {
            verticalVelocity = 0.0;
        } else if (moveY != 0.0) {
            onGround = false;
        }
    }

    private double moveAxis(ClientWorldView worldView, double distance, Axis axis) {
        if (distance == 0.0) {
            return 0.0;
        }

        double stepSign = Math.signum(distance);
        double remaining = Math.abs(distance);
        double moved = 0.0;

        while (remaining > 1e-6) {
            double step = Math.min(remaining, COLLISION_STEP);
            double delta = step * stepSign;
            AABB next = movedBoundingBox(axis, delta);

            if (collides(worldView, next)) {
                double resolved = resolveAllowedDelta(worldView, axis, delta);
                if (Math.abs(resolved) > 1e-6) {
                    applyAxisDelta(axis, resolved);
                    moved += resolved;
                }
                break;
            }

            applyAxisDelta(axis, delta);
            moved += delta;
            remaining -= step;
        }

        return moved;
    }

    private double resolveAllowedDelta(ClientWorldView worldView, Axis axis, double desiredDelta) {
        double sign = Math.signum(desiredDelta);
        double low = 0.0;
        double high = Math.abs(desiredDelta);

        for (int i = 0; i < 8; i++) {
            double mid = (low + high) * 0.5;
            AABB candidate = movedBoundingBox(axis, sign * mid);
            if (collides(worldView, candidate)) {
                high = mid;
            } else {
                low = mid;
            }
        }

        return sign * low;
    }

    private AABB movedBoundingBox(Axis axis, double delta) {
        return switch (axis) {
            case X -> boundingBox().moved(delta, 0.0, 0.0);
            case Y -> boundingBox().moved(0.0, delta, 0.0);
            case Z -> boundingBox().moved(0.0, 0.0, delta);
        };
    }

    private void applyAxisDelta(Axis axis, double delta) {
        switch (axis) {
            case X -> x += delta;
            case Y -> y += delta;
            case Z -> z += delta;
        }
    }

    private static boolean collides(ClientWorldView worldView, AABB box) {
        int minX = (int) Math.floor(box.minX());
        int maxX = (int) Math.floor(box.maxX() - 1e-6);
        int minY = (int) Math.floor(box.minY());
        int maxY = (int) Math.floor(box.maxY() - 1e-6);
        int minZ = (int) Math.floor(box.minZ());
        int maxZ = (int) Math.floor(box.maxZ() - 1e-6);

        for (int blockY = minY; blockY <= maxY; blockY++) {
            if (!worldView.isWithinWorldY(blockY)) {
                return true;
            }
            for (int blockX = minX; blockX <= maxX; blockX++) {
                for (int blockZ = minZ; blockZ <= maxZ; blockZ++) {
                    if (worldView.isSolid(blockX, blockY, blockZ)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void respawn() {
        x = spawnX;
        y = spawnY;
        z = spawnZ;
        verticalVelocity = 0.0;
        onGround = false;
    }

    public void setSpawn(double x, double y, double z) {
        this.spawnX = x;
        this.spawnY = y;
        this.spawnZ = z;
        this.x = x;
        this.y = y;
        this.z = z;
        this.verticalVelocity = 0.0;
        this.onGround = false;
        this.jumpKeyDownLastTick = false;
    }

    public AABB boundingBox() {
        double halfWidth = PLAYER_WIDTH * 0.5;
        return new AABB(x - halfWidth, y, z - halfWidth, x + halfWidth, y + PLAYER_HEIGHT, z + halfWidth);
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public double eyeX() {
        return x;
    }

    public double eyeY() {
        return y + EYE_HEIGHT;
    }

    public double eyeZ() {
        return z;
    }

    public float pitch() {
        return pitch;
    }

    public float yaw() {
        return yaw;
    }

    public boolean onGround() {
        return onGround;
    }

    public double lookDirX() {
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        return Math.sin(yawRadians) * Math.cos(pitchRadians);
    }

    public double lookDirY() {
        double pitchRadians = Math.toRadians(pitch);
        return -Math.sin(pitchRadians);
    }

    public double lookDirZ() {
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        return Math.cos(yawRadians) * Math.cos(pitchRadians);
    }

    private enum Axis {
        X,
        Y,
        Z
    }
}
