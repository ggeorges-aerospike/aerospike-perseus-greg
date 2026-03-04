package com.aerospike.perseus;

import com.aerospike.perseus.aerospike.AerospikeClientProvider;
import com.aerospike.perseus.configurations.TestConfiguration;
import com.aerospike.perseus.configurations.ThreadsProvider;
import com.aerospike.perseus.configurations.pojos.AerospikeConfiguration;
import com.aerospike.perseus.data.PmuTimestampTracker;
import com.aerospike.perseus.data.generators.PmuFrameGenerator;
import com.aerospike.perseus.data.generators.PmuSliceRequestGenerator;
import com.aerospike.perseus.presentation.TotalTpsCounter;
import com.aerospike.perseus.testCases.*;
import com.aerospike.perseus.presentation.TPSLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TestSetup {
    private final ThreadsProvider threadsProvider = new ThreadsProvider();
    private final ArrayList<Test> testList = new ArrayList<>();
    private final TotalTpsCounter totalTpsCounter;

    public TestSetup(AerospikeConfiguration aerospikeConfig, TestConfiguration testConfig) throws InterruptedException {

        var client = AerospikeClientProvider.getClient(aerospikeConfig);
        totalTpsCounter = new TotalTpsCounter();

        var arguments = new TestCaseConstructorArguments(client, aerospikeConfig.namespace, aerospikeConfig.set, totalTpsCounter);

        // PMU workload tests
        var pmuFrameGenerator = new PmuFrameGenerator(
                testConfig.pmuStreamCount, testConfig.pmuStreamPrefix,
                testConfig.pmuStreamStartIndex,
                testConfig.pmuComplexCount, testConfig.pmuRealCount, testConfig.pmuFps);
        var pmuTracker = new PmuTimestampTracker(pmuFrameGenerator.getDeviceIds());
        var pmuSliceGenerator = new PmuSliceRequestGenerator(pmuTracker);

        testList.add(new PmuWriteTest(arguments, pmuFrameGenerator, pmuTracker, testConfig.pmuTtlSeconds));
        testList.add(new PmuSliceReadTest(arguments, pmuSliceGenerator));
    }

    public void startTest() {
        // Reset TPS counters before starting
        totalTpsCounter.getTPS();

        var scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleAtFixedRate(
                this::setThread, 0, 1, TimeUnit.SECONDS);
    }

    private void setThread() {
        var threads = threadsProvider.getThreads();
        var map = testList.stream().collect(Collectors.toMap(
                testCase -> testCase.getClass().getSimpleName().replace("Test", "").toLowerCase(),
                testCase -> testCase));
        for (String thread : threads.keySet()) {
            if (map.containsKey(thread.toLowerCase()))
                map.get(thread.toLowerCase()).setThreads(threads.get(thread));
        }
    }

    public List<TPSLogger> getLoggableTestList() {
        return testList.stream().map(test -> (TPSLogger) test).toList();
    }

    public TotalTpsCounter getTotalTps() {
        return totalTpsCounter;
    }
}
