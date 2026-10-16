package com.volmit.bytecompanions.providers.storage.sqlcommon.util;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.UUID;

public final class UUIDConverter {

    private UUIDConverter() {
        throw new UnsupportedOperationException("This class cannot be instantiated.");
    }

    public static byte @NotNull [] toBytes(final @NotNull UUID uuid) {
        final ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    public static @NotNull UUID fromBytes(final byte @NotNull [] bytes) {
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        final long mostSignificantBits = buffer.getLong();
        final long leastSignificantBits = buffer.getLong();
        return new UUID(mostSignificantBits, leastSignificantBits);
    }

}