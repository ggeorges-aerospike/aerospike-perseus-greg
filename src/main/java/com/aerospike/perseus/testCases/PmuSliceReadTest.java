package com.aerospike.perseus.testCases;

import com.aerospike.client.*;
import com.aerospike.perseus.data.PmuSliceRequest;
import com.aerospike.perseus.data.generators.PmuSliceRequestGenerator;

import java.util.List;

/**
 * PMU slice read test: THE SLICE — batch read all devices at one timestamp for one stream.
 * This is the core WAMS query pattern (islanding detection, section 3.1.1.1).
 *
 * Reads N device records (N=6 for 27-bus) in a single batch call.
 * Key format: "{streamId}:{deviceId}:{timestampMicros}"
 * Set: "pmu_frames_rt"
 */
public class PmuSliceReadTest extends Test<PmuSliceRequest> {

    private static final String PMU_SET = "pmu_frames_rt";

    public PmuSliceReadTest(TestCaseConstructorArguments arguments,
                            PmuSliceRequestGenerator generator) {
        super(arguments, generator);
    }

    @Override
    protected void execute(PmuSliceRequest request) {
        List<String> deviceIds = request.getDeviceIds();
        Key[] keys = new Key[deviceIds.size()];

        for (int i = 0; i < deviceIds.size(); i++) {
            String keyStr = request.getStreamId() + ":" + deviceIds.get(i) + ":" + request.getTsMicros();
            keys[i] = new Key(namespace, PMU_SET, keyStr);
        }

        client.get(client.batchPolicyDefault, keys);
    }

    @Override
    public String[] getHeader() {
        return "PMU Slice\nRead".split("\n");
    }
}
