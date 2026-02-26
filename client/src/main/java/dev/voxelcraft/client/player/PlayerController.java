package dev.voxelcraft.client.player;

import dev.voxelcraft.client.physics.AABB;
import dev.voxelcraft.client.platform.InputState;
import dev.voxelcraft.client.world.ClientWorldView;
import dev.voxelcraft.core.world.World;
import java.awt.event.KeyEvent;
/**
 * 中文说明：玩家控制器：负责输入处理、视角更新、移动速度计算与碰撞步进。
 */

// 中文标注（类）：`PlayerController`，职责：封装玩家、控制器相关逻辑。
public final class PlayerController {
    // 中文标注（字段）：`PLAYER_WIDTH`，含义：用于表示玩家、宽度。
    private static final double PLAYER_WIDTH = 0.6; // meaning
    // 中文标注（字段）：`PLAYER_HEIGHT`，含义：用于表示玩家、高度。
    private static final double PLAYER_HEIGHT = 1.8; // meaning
    // 中文标注（字段）：`EYE_HEIGHT`，含义：用于表示eye、高度。
    private static final double EYE_HEIGHT = 1.62; // meaning

    // 中文标注（字段）：`WALK_SPEED`，含义：用于表示walk、speed。
    private static final double WALK_SPEED = 8.6; // meaning
    // 中文标注（字段）：`SPRINT_SPEED`，含义：用于表示sprint、speed。
    private static final double SPRINT_SPEED = 12.4; // meaning
    // 中文标注（字段）：`GRAVITY`，含义：用于表示gravity。
    private static final double GRAVITY = 24.0; // meaning
    // 中文标注（字段）：`JUMP_VELOCITY`，含义：用于表示jump、velocity。
    private static final double JUMP_VELOCITY = 8.7; // meaning
    // 中文标注（字段）：`COLLISION_STEP`，含义：用于表示collision、step。
    private static final double COLLISION_STEP = 0.05; // meaning

    // 中文标注（字段）：`x`，含义：用于表示X坐标。
    private double x = 0.5; // meaning
    // 中文标注（字段）：`y`，含义：用于表示Y坐标。
    private double y = 8.0; // meaning
    // 中文标注（字段）：`z`，含义：用于表示Z坐标。
    private double z = 0.5; // meaning
    // 中文标注（字段）：`spawnX`，含义：用于表示spawn、X坐标。
    private double spawnX = 0.5; // meaning
    // 中文标注（字段）：`spawnY`，含义：用于表示spawn、Y坐标。
    private double spawnY = 8.0; // meaning
    // 中文标注（字段）：`spawnZ`，含义：用于表示spawn、Z坐标。
    private double spawnZ = 0.5; // meaning

    // 中文标注（字段）：`verticalVelocity`，含义：用于表示垂直、velocity。
    private double verticalVelocity; // meaning
    // 中文标注（字段）：`pitch`，含义：用于表示pitch。
    private float pitch = -15.0f; // meaning
    // 中文标注（字段）：`yaw`，含义：用于表示yaw。
    private float yaw = 135.0f; // meaning
    // 中文标注（字段）：`onGround`，含义：用于表示on、ground。
    private boolean onGround; // meaning
    // 中文标注（字段）：`jumpKeyDownLastTick`，含义：用于表示jump、键、down、last、刻。
    private boolean jumpKeyDownLastTick; // meaning

    // 中文标注（方法）：`tick`，参数：worldView、input、deltaSeconds；用途：更新刻相关状态。
    // 中文标注（参数）：`worldView`，含义：用于表示世界、view。
    // 中文标注（参数）：`input`，含义：用于表示输入。
    // 中文标注（参数）：`deltaSeconds`，含义：用于表示增量、seconds。
    public void tick(ClientWorldView worldView, InputState input, double deltaSeconds) {
        updateView(input, deltaSeconds);

        // 中文标注（局部变量）：`jumpKeyDown`，含义：用于表示jump、键、down。
        boolean jumpKeyDown = input.isKeyDown(KeyEvent.VK_SPACE); // meaning
        if (jumpKeyDown && !jumpKeyDownLastTick && onGround) {
            verticalVelocity = JUMP_VELOCITY;
            onGround = false;
        }
        jumpKeyDownLastTick = jumpKeyDown;

        // 约定：W/S 控制前后轴，D/A 控制左右轴（正值=向右平移）。
        // 中文标注（局部变量）：`forwardAxis`，含义：用于表示forward、axis。
        double forwardAxis = axis(input, KeyEvent.VK_W, KeyEvent.VK_S); // meaning
        // 中文标注（局部变量）：`strafeAxis`，含义：用于表示strafe、axis。
        double strafeAxis = axis(input, KeyEvent.VK_D, KeyEvent.VK_A); // meaning
        // 中文标注（局部变量）：`speed`，含义：用于表示speed。
        double speed = input.isKeyDown(KeyEvent.VK_SHIFT) ? SPRINT_SPEED : WALK_SPEED; // meaning

        // 中文标注（局部变量）：`horizontalVelocity`，含义：用于表示水平、velocity。
        double[] horizontalVelocity = computeHorizontalVelocity(forwardAxis, strafeAxis, speed); // meaning
        // 中文标注（局部变量）：`moveX`，含义：用于表示move、X坐标。
        double moveX = horizontalVelocity[0] * deltaSeconds; // meaning
        // 中文标注（局部变量）：`moveZ`，含义：用于表示move、Z坐标。
        double moveZ = horizontalVelocity[1] * deltaSeconds; // meaning

        verticalVelocity -= GRAVITY * deltaSeconds;
        if (verticalVelocity < -40.0) {
            verticalVelocity = -40.0;
        }
        // 中文标注（局部变量）：`moveY`，含义：用于表示move、Y坐标。
        double moveY = verticalVelocity * deltaSeconds; // meaning

        moveWithCollisions(worldView, moveX, moveY, moveZ);

        if (y < World.MIN_Y - 10) {
            respawn();
        }
    }

    // 中文标注（方法）：`updateView`，参数：input、deltaSeconds；用途：更新更新、view相关状态。
    // 中文标注（参数）：`input`，含义：用于表示输入。
    // 中文标注（参数）：`deltaSeconds`，含义：用于表示增量、seconds。
    private void updateView(InputState input, double deltaSeconds) {
        // 中文标注（局部变量）：`mouseSensitivity`，含义：用于表示鼠标、sensitivity。
        float mouseSensitivity = 0.28f; // meaning
        yaw += input.mouseDeltaX() * mouseSensitivity;
        pitch += input.mouseDeltaY() * mouseSensitivity;

        // 中文标注（局部变量）：`keyLookSpeed`，含义：用于表示键、look、speed。
        float keyLookSpeed = (float) (120.0 * deltaSeconds); // meaning
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

    // 中文标注（方法）：`axis`，参数：input、positiveKey、negativeKey；用途：执行axis相关逻辑。
    // 中文标注（参数）：`input`，含义：用于表示输入。
    // 中文标注（参数）：`positiveKey`，含义：用于表示positive、键。
    // 中文标注（参数）：`negativeKey`，含义：用于表示negative、键。
    private static double axis(InputState input, int positiveKey, int negativeKey) {
        // 中文标注（局部变量）：`value`，含义：用于表示值。
        double value = 0.0; // meaning
        if (input.isKeyDown(positiveKey)) {
            value += 1.0;
        }
        if (input.isKeyDown(negativeKey)) {
            value -= 1.0;
        }
        return value;
    }

    // 中文标注（方法）：`computeHorizontalVelocity`，参数：forwardAxis、strafeAxis、speed；用途：执行compute、水平、velocity相关逻辑。
    // 中文标注（参数）：`forwardAxis`，含义：用于表示forward、axis。
    // 中文标注（参数）：`strafeAxis`，含义：用于表示strafe、axis。
    // 中文标注（参数）：`speed`，含义：用于表示speed。
    private double[] computeHorizontalVelocity(double forwardAxis, double strafeAxis, double speed) {
        if (forwardAxis == 0.0 && strafeAxis == 0.0) {
            return new double[] {0.0, 0.0};
        }

        // 中文标注（局部变量）：`magnitude`，含义：用于表示magnitude。
        double magnitude = Math.hypot(forwardAxis, strafeAxis); // meaning
        forwardAxis /= magnitude;
        strafeAxis /= magnitude;

        // 中文标注（局部变量）：`yawRadians`，含义：用于表示yaw、radians。
        double yawRadians = Math.toRadians(yaw); // meaning
        // 中文标注（局部变量）：`sin`，含义：用于表示sin。
        double sin = Math.sin(yawRadians); // meaning
        // 中文标注（局部变量）：`cos`，含义：用于表示cos。
        double cos = Math.cos(yawRadians); // meaning

        // 与相机/Frustum 使用同一 yaw 约定，避免出现“移动方向正确但渲染/剔除方向反了”。
        // 中文标注（局部变量）：`velocityX`，含义：用于表示velocity、X坐标。
        double velocityX = (forwardAxis * sin + strafeAxis * cos) * speed; // meaning
        // 中文标注（局部变量）：`velocityZ`，含义：用于表示velocity、Z坐标。
        double velocityZ = (forwardAxis * cos - strafeAxis * sin) * speed; // meaning
        return new double[] {velocityX, velocityZ};
    }

    // 中文标注（方法）：`moveWithCollisions`，参数：worldView、moveX、moveY、moveZ；用途：执行move、with、collisions相关逻辑。
    // 中文标注（参数）：`worldView`，含义：用于表示世界、view。
    // 中文标注（参数）：`moveX`，含义：用于表示move、X坐标。
    // 中文标注（参数）：`moveY`，含义：用于表示move、Y坐标。
    // 中文标注（参数）：`moveZ`，含义：用于表示move、Z坐标。
    private void moveWithCollisions(ClientWorldView worldView, double moveX, double moveY, double moveZ) {
        moveAxis(worldView, moveX, Axis.X);
        moveAxis(worldView, moveZ, Axis.Z);

        // 中文标注（局部变量）：`movedY`，含义：用于表示moved、Y坐标。
        double movedY = moveAxis(worldView, moveY, Axis.Y); // meaning
        if (moveY < 0.0 && Math.abs(movedY - moveY) > 1e-6) {
            onGround = true;
            verticalVelocity = 0.0;
        } else if (moveY > 0.0 && Math.abs(movedY - moveY) > 1e-6) {
            verticalVelocity = 0.0;
        } else if (moveY != 0.0) {
            onGround = false;
        }
    }

    // 中文标注（方法）：`moveAxis`，参数：worldView、distance、axis；用途：执行move、axis相关逻辑。
    // 中文标注（参数）：`worldView`，含义：用于表示世界、view。
    // 中文标注（参数）：`distance`，含义：用于表示distance。
    // 中文标注（参数）：`axis`，含义：用于表示axis。
    private double moveAxis(ClientWorldView worldView, double distance, Axis axis) {
        if (distance == 0.0) {
            return 0.0;
        }

        // 中文标注（局部变量）：`stepSign`，含义：用于表示step、sign。
        double stepSign = Math.signum(distance); // meaning
        // 中文标注（局部变量）：`remaining`，含义：用于表示remaining。
        double remaining = Math.abs(distance); // meaning
        // 中文标注（局部变量）：`moved`，含义：用于表示moved。
        double moved = 0.0; // meaning

        while (remaining > 1e-6) {
            // 中文标注（局部变量）：`step`，含义：用于表示step。
            double step = Math.min(remaining, COLLISION_STEP); // meaning
            // 中文标注（局部变量）：`delta`，含义：用于表示增量。
            double delta = step * stepSign; // meaning
            // 中文标注（局部变量）：`next`，含义：用于表示next。
            AABB next = movedBoundingBox(axis, delta); // meaning

            if (collides(worldView, next)) {
                // 中文标注（局部变量）：`resolved`，含义：用于表示resolved。
                double resolved = resolveAllowedDelta(worldView, axis, delta); // meaning
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

    // 中文标注（方法）：`resolveAllowedDelta`，参数：worldView、axis、desiredDelta；用途：执行resolve、allowed、增量相关逻辑。
    // 中文标注（参数）：`worldView`，含义：用于表示世界、view。
    // 中文标注（参数）：`axis`，含义：用于表示axis。
    // 中文标注（参数）：`desiredDelta`，含义：用于表示desired、增量。
    private double resolveAllowedDelta(ClientWorldView worldView, Axis axis, double desiredDelta) {
        // 中文标注（局部变量）：`sign`，含义：用于表示sign。
        double sign = Math.signum(desiredDelta); // meaning
        // 中文标注（局部变量）：`low`，含义：用于表示low。
        double low = 0.0; // meaning
        // 中文标注（局部变量）：`high`，含义：用于表示high。
        double high = Math.abs(desiredDelta); // meaning

        // 中文标注（局部变量）：`i`，含义：用于表示i。
        for (int i = 0; i < 8; i++) { // meaning
            // 中文标注（局部变量）：`mid`，含义：用于表示mid。
            double mid = (low + high) * 0.5; // meaning
            // 中文标注（局部变量）：`candidate`，含义：用于表示candidate。
            AABB candidate = movedBoundingBox(axis, sign * mid); // meaning
            if (collides(worldView, candidate)) {
                high = mid;
            } else {
                low = mid;
            }
        }

        return sign * low;
    }

    // 中文标注（方法）：`movedBoundingBox`，参数：axis、delta；用途：执行moved、bounding、box相关逻辑。
    // 中文标注（参数）：`axis`，含义：用于表示axis。
    // 中文标注（参数）：`delta`，含义：用于表示增量。
    private AABB movedBoundingBox(Axis axis, double delta) {
        return switch (axis) {
            case X -> boundingBox().moved(delta, 0.0, 0.0);
            case Y -> boundingBox().moved(0.0, delta, 0.0);
            case Z -> boundingBox().moved(0.0, 0.0, delta);
        };
    }

    // 中文标注（方法）：`applyAxisDelta`，参数：axis、delta；用途：处理apply、axis、增量逻辑。
    // 中文标注（参数）：`axis`，含义：用于表示axis。
    // 中文标注（参数）：`delta`，含义：用于表示增量。
    private void applyAxisDelta(Axis axis, double delta) {
        switch (axis) {
            case X -> x += delta;
            case Y -> y += delta;
            case Z -> z += delta;
        }
    }

    // 中文标注（方法）：`collides`，参数：worldView、box；用途：执行collides相关逻辑。
    // 中文标注（参数）：`worldView`，含义：用于表示世界、view。
    // 中文标注（参数）：`box`，含义：用于表示box。
    private static boolean collides(ClientWorldView worldView, AABB box) {
        // 中文标注（局部变量）：`minX`，含义：用于表示最小、X坐标。
        int minX = (int) Math.floor(box.minX()); // meaning
        // 中文标注（局部变量）：`maxX`，含义：用于表示最大、X坐标。
        int maxX = (int) Math.floor(box.maxX() - 1e-6); // meaning
        // 中文标注（局部变量）：`minY`，含义：用于表示最小、Y坐标。
        int minY = (int) Math.floor(box.minY()); // meaning
        // 中文标注（局部变量）：`maxY`，含义：用于表示最大、Y坐标。
        int maxY = (int) Math.floor(box.maxY() - 1e-6); // meaning
        // 中文标注（局部变量）：`minZ`，含义：用于表示最小、Z坐标。
        int minZ = (int) Math.floor(box.minZ()); // meaning
        // 中文标注（局部变量）：`maxZ`，含义：用于表示最大、Z坐标。
        int maxZ = (int) Math.floor(box.maxZ() - 1e-6); // meaning

        // 中文标注（局部变量）：`blockY`，含义：用于表示方块、Y坐标。
        for (int blockY = minY; blockY <= maxY; blockY++) { // meaning
            if (!worldView.isWithinWorldY(blockY)) {
                return true;
            }
            // 中文标注（局部变量）：`blockX`，含义：用于表示方块、X坐标。
            for (int blockX = minX; blockX <= maxX; blockX++) { // meaning
                // 中文标注（局部变量）：`blockZ`，含义：用于表示方块、Z坐标。
                for (int blockZ = minZ; blockZ <= maxZ; blockZ++) { // meaning
                    if (worldView.isSolid(blockX, blockY, blockZ)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // 中文标注（方法）：`respawn`，参数：无；用途：执行respawn相关逻辑。
    private void respawn() {
        x = spawnX;
        y = spawnY;
        z = spawnZ;
        verticalVelocity = 0.0;
        onGround = false;
    }

    // 中文标注（方法）：`setSpawn`，参数：x、y、z；用途：设置、写入或注册spawn。
    // 中文标注（参数）：`x`，含义：用于表示X坐标。
    // 中文标注（参数）：`y`，含义：用于表示Y坐标。
    // 中文标注（参数）：`z`，含义：用于表示Z坐标。
    public void setSpawn(double x, double y, double z) {
        this.spawnX = x;
        this.spawnY = y;
        this.spawnZ = z;
        teleport(x, y, z);
    }

    public void teleport(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.verticalVelocity = 0.0;
        this.onGround = false;
        this.jumpKeyDownLastTick = false;
    }

    // 中文标注（方法）：`boundingBox`，参数：无；用途：执行bounding、box相关逻辑。
    public AABB boundingBox() {
        // 中文标注（局部变量）：`halfWidth`，含义：用于表示half、宽度。
        double halfWidth = PLAYER_WIDTH * 0.5; // meaning
        return new AABB(x - halfWidth, y, z - halfWidth, x + halfWidth, y + PLAYER_HEIGHT, z + halfWidth);
    }

    // 中文标注（方法）：`x`，参数：无；用途：执行X坐标相关逻辑。
    public double x() {
        return x;
    }

    // 中文标注（方法）：`y`，参数：无；用途：执行Y坐标相关逻辑。
    public double y() {
        return y;
    }

    // 中文标注（方法）：`z`，参数：无；用途：执行Z坐标相关逻辑。
    public double z() {
        return z;
    }

    // 中文标注（方法）：`eyeX`，参数：无；用途：执行eye、X坐标相关逻辑。
    public double eyeX() {
        return x;
    }

    // 中文标注（方法）：`eyeY`，参数：无；用途：执行eye、Y坐标相关逻辑。
    public double eyeY() {
        return y + EYE_HEIGHT;
    }

    // 中文标注（方法）：`eyeZ`，参数：无；用途：执行eye、Z坐标相关逻辑。
    public double eyeZ() {
        return z;
    }

    // 中文标注（方法）：`pitch`，参数：无；用途：执行pitch相关逻辑。
    public float pitch() {
        return pitch;
    }

    // 中文标注（方法）：`yaw`，参数：无；用途：执行yaw相关逻辑。
    public float yaw() {
        return yaw;
    }

    // 中文标注（方法）：`onGround`，参数：无；用途：执行on、ground相关逻辑。
    public boolean onGround() {
        return onGround;
    }

    // 中文标注（方法）：`lookDirX`，参数：无；用途：执行look、dir、X坐标相关逻辑。
    public double lookDirX() {
        // 中文标注（局部变量）：`yawRadians`，含义：用于表示yaw、radians。
        double yawRadians = Math.toRadians(yaw); // meaning
        // 中文标注（局部变量）：`pitchRadians`，含义：用于表示pitch、radians。
        double pitchRadians = Math.toRadians(pitch); // meaning
        return Math.sin(yawRadians) * Math.cos(pitchRadians);
    }

    // 中文标注（方法）：`lookDirY`，参数：无；用途：执行look、dir、Y坐标相关逻辑。
    public double lookDirY() {
        // 中文标注（局部变量）：`pitchRadians`，含义：用于表示pitch、radians。
        double pitchRadians = Math.toRadians(pitch); // meaning
        return -Math.sin(pitchRadians);
    }

    // 中文标注（方法）：`lookDirZ`，参数：无；用途：执行look、dir、Z坐标相关逻辑。
    public double lookDirZ() {
        // 中文标注（局部变量）：`yawRadians`，含义：用于表示yaw、radians。
        double yawRadians = Math.toRadians(yaw); // meaning
        // 中文标注（局部变量）：`pitchRadians`，含义：用于表示pitch、radians。
        double pitchRadians = Math.toRadians(pitch); // meaning
        return Math.cos(yawRadians) * Math.cos(pitchRadians);
    }

    // 中文标注（枚举）：`Axis`，职责：封装axis相关逻辑。
    private enum Axis {
        // 中文标注（字段）：`X`，含义：用于表示X坐标。
        X,
        // 中文标注（字段）：`Y`，含义：用于表示Y坐标。
        Y,
        // 中文标注（字段）：`Z`，含义：用于表示Z坐标。
        Z
    }
}
