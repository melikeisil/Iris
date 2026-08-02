package com.smartwork.common.domain;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUID version 7 generation.
 *
 * <p>v7 is time-ordered, so primary keys stay local in the B-tree instead of fragmenting
 * it the way v4 does. IDs are generated in the application rather than the database
 * because an aggregate's ID must be known before insert - the outbox event written in the
 * same transaction references it.
 *
 * <p>Layout (RFC 9562): 48-bit big-endian Unix millisecond timestamp, 4-bit version,
 * 12 bits of randomness, 2-bit variant, 62 bits of randomness.
 */
public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {
    }

    public static UUID newId() {
        return newId(System.currentTimeMillis());
    }

    static UUID newId(long unixMillis) {
        byte[] bytes = new byte[10];
        RANDOM.nextBytes(bytes);

        // bits 63..16 timestamp | bits 15..12 version | bits 11..0 random
        long randA = ((bytes[0] & 0x0FL) << 8) | (bytes[1] & 0xFFL);
        long msb = ((unixMillis & 0xFFFF_FFFF_FFFFL) << 16) | (0x7L << 12) | randA;

        long lsb = 0;
        for (int i = 2; i < 10; i++) {
            lsb = (lsb << 8) | (bytes[i] & 0xFFL);
        }
        lsb &= 0x3FFF_FFFF_FFFF_FFFFL;                    // clear variant bits
        lsb |= 0x8000_0000_0000_0000L;                    // RFC 4122 variant

        return new UUID(msb, lsb);
    }

    /** Milliseconds encoded in a v7 UUID. Useful in tests to assert ordering. */
    public static long timestampOf(UUID id) {
        if (id.version() != 7) {
            throw new IllegalArgumentException("Not a UUID v7: " + id);
        }
        return id.getMostSignificantBits() >>> 16;
    }
}
