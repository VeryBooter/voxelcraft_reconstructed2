package dev.voxelcraft.core.net;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
/**
 * 中文说明：网络模块组件：负责 PacketIO 的协议封装、通信或连接管理。
 */

// 中文标注（类）：`PacketIO`，职责：封装数据包、io相关逻辑。
public final class PacketIO {
    // 中文标注（构造方法）：`PacketIO`，参数：无；用途：初始化`PacketIO`实例。
    private PacketIO() {
    }

    // 中文标注（方法）：`writeString`，参数：out、value；用途：设置、写入或注册写入、string。
    // 中文标注（参数）：`out`，含义：用于表示out。
    // 中文标注（参数）：`value`，含义：用于表示值。
    public static void writeString(DataOutputStream out, String value) throws IOException {
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(value, "value");

        // 中文标注（局部变量）：`bytes`，含义：用于表示字节数据。
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); // meaning
        if (bytes.length > 65_535) {
            throw new IOException("String payload exceeds 65535 bytes");
        }

        out.writeShort(bytes.length);
        out.write(bytes);
    }

    // 中文标注（方法）：`readString`，参数：in；用途：获取或读取读取、string。
    // 中文标注（参数）：`in`，含义：用于表示in。
    public static String readString(DataInputStream in) throws IOException {
        Objects.requireNonNull(in, "in");
        // 中文标注（局部变量）：`length`，含义：用于表示长度。
        int length = in.readUnsignedShort(); // meaning
        // 中文标注（局部变量）：`bytes`，含义：用于表示字节数据。
        byte[] bytes = new byte[length]; // meaning
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
