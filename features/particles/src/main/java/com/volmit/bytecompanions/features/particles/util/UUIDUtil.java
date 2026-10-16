package com.volmit.bytecompanions.features.particles.util;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class UUIDUtil {

    private UUIDUtil() {
        throw new UnsupportedOperationException();
    }

    public static byte @NotNull [] uuidToBytes(final @NotNull UUID uuid) {
        final byte[] bytes = new byte[16];

        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();

        for (int i = 7; i >= 0; i--) {
            bytes[i] = (byte) msb;
            msb >>>= 8;
        }

        for (int i = 15; i >= 8; i--) {
            bytes[i] = (byte) lsb;
            lsb >>>= 8;
        }

        return bytes;
    }

    public static @NotNull UUID bytesToUuid(final byte @NotNull [] bytes) {
        if (bytes.length != 16) {
            throw new IllegalArgumentException("UUID binary representation must be 16 bytes.");
        }

        long msb = 0;
        long lsb = 0;

        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (bytes[i] & 0xFFL);
        }

        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (bytes[i] & 0xFFL);
        }

        return new UUID(msb, lsb);
    }


}
