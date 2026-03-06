package com.aerospike.perseus.data.c37118;

/**
 * CRC-CCITT (polynomial 0x1021, init 0xFFFF) as specified by IEEE C37.118.
 * Uses a 256-entry lookup table for performance.
 */
public final class C37118Crc {

    private C37118Crc() {}

    private static final int[] CRC_TABLE = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            int crc = i << 8;
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x8000) != 0) {
                    crc = (crc << 1) ^ 0x1021;
                } else {
                    crc <<= 1;
                }
            }
            CRC_TABLE[i] = crc & 0xFFFF;
        }
    }

    /**
     * Compute CRC-CCITT over the given byte range.
     *
     * @param data   byte array
     * @param offset start offset
     * @param length number of bytes to process
     * @return 16-bit CRC value
     */
    public static int compute(byte[] data, int offset, int length) {
        int crc = 0xFFFF;
        for (int i = offset; i < offset + length; i++) {
            crc = ((crc << 8) ^ CRC_TABLE[((crc >> 8) ^ (data[i] & 0xFF)) & 0xFF]) & 0xFFFF;
        }
        return crc;
    }

    /**
     * Compute CRC over the entire byte array.
     */
    public static int compute(byte[] data) {
        return compute(data, 0, data.length);
    }
}
