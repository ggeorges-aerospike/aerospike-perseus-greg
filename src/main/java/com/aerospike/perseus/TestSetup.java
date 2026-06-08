package com.aerospike.perseus;

import com.aerospike.perseus.aerospike.AerospikeClientProvider;
import com.aerospike.perseus.configurations.TestConfiguration;
import com.aerospike.perseus.configurations.ThreadsProvider;
import com.aerospike.perseus.configurations.pojos.AerospikeConfiguration;
import com.aerospike.perseus.data.generators.cognitiv.*;
import com.aerospike.perseus.presentation.TPSLogger;
import com.aerospike.perseus.presentation.TotalTpsCounter;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;
import com.aerospike.perseus.testCases.cognitiv.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Cognitiv workload wiring (Scylla -> Aerospike). Registers a test case per real
 * query against four sets — kepler: pseg / url / seg, corvus: bids — all in the
 * namespace from configuration.yaml. The deployed client runs only the threads
 * its threads.yaml enables (kepler clients drive pseg/url/seg in ns=kepler,
 * corvus clients drive bids in ns=corvus). See COGNITIV_DATA_MODEL.md.
 *
 * threads.yaml keys (class name minus "Test", lowercased):
 *   psegwrite psegread psegpointread psegbatchread
 *   urlwrite urlread urlpointread
 *   segwrite segupdate segread
 *   bidwrite bidread
 */
public class TestSetup {
    private final ThreadsProvider threadsProvider = new ThreadsProvider();
    private final ArrayList<Test> testList = new ArrayList<>();
    private final TotalTpsCounter totalTpsCounter;

    public TestSetup(AerospikeConfiguration aero, TestConfiguration cfg) throws InterruptedException {
        var client = AerospikeClientProvider.getClient(aero);
        totalTpsCounter = new TotalTpsCounter();
        String ns = aero.namespace;
        int pid = cfg.perseusId;
        int ttl = def(cfg.keplerTtlSeconds, 0);

        // kepler — tbl_person_identity_segments -> set "pseg"
        var psegArgs = new TestCaseConstructorArguments(client, ns, "pseg", totalTpsCounter);
        var pseg = new PsegGenerator(pid, def(cfg.keplerAvgSegmentsPerPerson, 20), def(cfg.keplerSegmentUniverse, 500_000));
        testList.add(new PsegWriteTest(psegArgs, pseg, ttl));
        testList.add(new PsegReadTest(psegArgs, pseg));
        testList.add(new PsegPointReadTest(psegArgs, pseg));
        testList.add(new PsegBatchReadTest(psegArgs, pseg, def(cfg.readBatchSize, 50)));

        // kepler — tbl_url_segments -> set "url"
        var urlArgs = new TestCaseConstructorArguments(client, ns, "url", totalTpsCounter);
        var url = new UrlGenerator(pid, def(cfg.keplerAvgProvidersPerUrl, 2), def(cfg.keplerProviderUniverse, 8),
                def(cfg.keplerAvgSegmentsPerUrl, 5), def(cfg.keplerSegmentUniverse, 500_000));
        testList.add(new UrlWriteTest(urlArgs, url, ttl));
        testList.add(new UrlReadTest(urlArgs, url));
        testList.add(new UrlPointReadTest(urlArgs, url));

        // kepler — tbl_segments -> set "seg"
        var segArgs = new TestCaseConstructorArguments(client, ns, "seg", totalTpsCounter);
        var seg = new SegGenerator(pid, def(cfg.keplerTypeUniverse, 16), def(cfg.keplerAvgTypesPerIdentity, 3), def(cfg.keplerInnerSegmentsPerType, 10));
        testList.add(new SegWriteTest(segArgs, seg, ttl));
        testList.add(new SegUpdateTest(segArgs, seg, ttl));
        testList.add(new SegReadTest(segArgs, seg));

        // corvus — draco.bids -> set "bids"
        var bidArgs = new TestCaseConstructorArguments(client, ns, "bids", totalTpsCounter);
        var bid = new BidGenerator(pid, def(cfg.corvusSmallBlobBytes, 800), def(cfg.corvusLargeBlobBytes, 30_000), def(cfg.corvusLargeBlobRatio, 0.4));
        testList.add(new BidWriteTest(bidArgs, bid, def(cfg.corvusTtlSeconds, 172800)));
        testList.add(new BidReadTest(bidArgs, bid));
    }

    private static int def(Integer v, int d) { return v != null ? v : d; }
    private static double def(Double v, double d) { return v != null ? v : d; }

    public void startTest() {
        totalTpsCounter.getTPS();   // reset baseline before the first interval
        var scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleAtFixedRate(this::setThread, 0, 1, TimeUnit.SECONDS);
    }

    private void setThread() {
        var threads = threadsProvider.getThreads();
        var map = testList.stream().collect(Collectors.toMap(
                t -> t.getClass().getSimpleName().replace("Test", "").toLowerCase(),
                t -> t));
        for (String thread : threads.keySet())
            if (map.containsKey(thread.toLowerCase()))
                map.get(thread.toLowerCase()).setThreads(threads.get(thread));
    }

    public List<TPSLogger> getLoggableTestList() {
        return testList.stream().map(t -> (TPSLogger) t).toList();
    }

    public TotalTpsCounter getTotalTps() { return totalTpsCounter; }
}
