package com.aerospike.perseus.data.c37118;

/**
 * IEEE C37.118 protocol constants for PDC data stream encoding.
 */
public final class C37118Constants {

    private C37118Constants() {}

    // Frame sync words (AA = C37.118 marker, lower byte = frame type + version 1)
    public static final int SYNC_DATA = 0xAA01;   // Data frame
    public static final int SYNC_CFG2 = 0xAA21;   // Configuration frame type 2

    // Header: SYNC(2) + FRAMESIZE(2) + IDCODE(2) + SOC(4) + FRACSEC(4) = 14 bytes
    public static final int HEADER_SIZE = 14;

    // CRC is 2 bytes appended after data
    public static final int CRC_SIZE = 2;

    // FORMAT word: bits [3:0] = 0xF → float phasors, float freq, float analog, rectangular coords
    public static final int FORMAT_FLOAT_RECT = 0x000F;

    // TIME_BASE: denominator for FRACSEC fractional seconds (microsecond precision)
    public static final int TIME_BASE = 1_000_000;

    // Default TCP port for C37.118 data streams
    public static final int DEFAULT_PORT = 4712;

    // STAT word (2 bytes) — all good, no errors
    public static final int STAT_OK = 0x0000;

    // Per-PMU data sizes in bytes (for float format, rectangular phasors)
    public static final int STAT_SIZE = 2;
    public static final int PHASOR_FLOAT_SIZE = 8;   // real(4) + imag(4) per phasor
    public static final int FREQ_SIZE = 4;            // FREQ as float32
    public static final int DFREQ_SIZE = 4;           // DFREQ as float32
    public static final int ANALOG_FLOAT_SIZE = 4;    // one analog channel as float32
    public static final int DIGITAL_SIZE = 2;         // one digital word = 16 bits

    // CFG-2 fixed fields
    public static final int CFG2_TIMEBASE_SIZE = 4;
    public static final int CFG2_NUM_PMU_SIZE = 2;
    public static final int CFG2_STATION_NAME_SIZE = 16;
    public static final int CFG2_IDCODE_SIZE = 2;
    public static final int CFG2_FORMAT_SIZE = 2;
    public static final int CFG2_PHNMR_SIZE = 2;      // number of phasors
    public static final int CFG2_ANNMR_SIZE = 2;      // number of analogs
    public static final int CFG2_DGNMR_SIZE = 2;      // number of digitals
    public static final int CFG2_CHANNEL_NAME_SIZE = 16;
    public static final int CFG2_PHUNIT_SIZE = 4;      // phasor conversion factor
    public static final int CFG2_ANUNIT_SIZE = 4;      // analog conversion factor
    public static final int CFG2_DIGUNIT_SIZE = 4;     // digital status word mask
    public static final int CFG2_FNOM_SIZE = 2;        // nominal frequency
    public static final int CFG2_CFGCNT_SIZE = 2;      // config change count
    public static final int CFG2_DATA_RATE_SIZE = 2;   // data rate (FPS)
}
