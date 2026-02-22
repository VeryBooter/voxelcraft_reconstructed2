package dev.voxelcraft.core.net;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class PacketIO {
    private PacketIO() {
    }

    public static void writeString(DataOutputStream out, String value) throws IOException {
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(value, "value");

        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 65_535) {
            throw new IOException("String payload exceeds 65535 bytes");
        }

        out.writeShort(bytes.length);
        out.write(bytes);
    }

    public static String readString(DataInputStream in) throws IOException {
        Objects.requireNonNull(in, "in");
        int length = in.readUnsignedShort();
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
