package com.aerospike.perseus;

import com.aerospike.perseus.aerospike.AerospikeClientProvider;
import com.aerospike.perseus.configurations.TestConfiguration;
import com.aerospike.perseus.configurations.ThreadsProvider;
import com.aerospike.perseus.configurations.pojos.AerospikeConfiguration;
import com.aerospike.perseus.data.generators.wyllo.FeatureGenerator;
import com.aerospike.perseus.presentation.TPSLogger;
import com.aerospike.perseus.presentation.TotalTpsCounter;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;
import com.aerospike.perseus.testCases.wyllo.FeatureBatchReadTest;
import com.aerospike.perseus.testCases.wyllo.FeatureBatchWriteTest;
import com.aerospike.perseus.testCases.wyllo.FeatureBinReadTest;
import com.aerospike.perseus.testCases.wyllo.FeatureEntityReadTest;
import com.aerospike.perseus.testCases.wyllo.FeatureReadTest;
import com.aerospike.perseus.testCases.wyllo.FeatureVectorReadTest;
import com.aerospike.perseus.testCases.wyllo.FeatureWriteTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Wyllo feature-platform workload (MongoDB -> Aerospike). One record per user,
 * keyed by the Mongo ObjectId; one bin per entity_type holding a KEY_ORDERED map
 * of entity_id -> KEY_ORDERED map of feature_id -> value. See WYLLO_DATA_MODEL.md.
 *
 * threads.yaml keys (class name minus "Test", lowercased):
 *   featurewrite        point upserts, one entity's features per op
 *   featurebatchwrite   batched upserts across distinct users (use this for 1M/sec)
 *   featureread         whole user record
 *   featurebinread      one entity type   -- get(key, bin)
 *   featureentityread   one entity's features -- map getByKey
 *   featurevectorread   named feature subset  -- nested getByKeyList (model scoring)
 *   featurebatchread    N users in one round trip
 *
 * Reads derive entity ids and feature names from the user id (see FeatureIds), so they hit
 * data the writers actually wrote. They read within the range written SO FAR by this process,
 * so run at least one writer thread alongside them or the range is a single user.
 */
public class TestSetup {
    private final ThreadsProvider threadsProvider = new ThreadsProvider();
    private final ArrayList<Test> testList = new ArrayList<>();
    private final TotalTpsCounter totalTpsCounter;

    public TestSetup(AerospikeConfiguration aero, TestConfiguration cfg) throws InterruptedException {
        var client = AerospikeClientProvider.getClient(aero);
        totalTpsCounter = new TotalTpsCounter();
        String ns = aero.namespace;

        // Entity types become bin names, so each must be <= 15 chars (Aerospike hard limit).
        // Weights set the share of writes each type receives.
        var specs = new FeatureGenerator.EntitySpec[] {
                new FeatureGenerator.EntitySpec("bin",    def(cfg.wylloMaxBinsPerUser, 2),   def(cfg.wylloBinWeight, 5)),
                new FeatureGenerator.EntitySpec("bin_ip", def(cfg.wylloMaxBinIpsPerUser, 5), def(cfg.wylloBinIpWeight, 3)),
                new FeatureGenerator.EntitySpec("email",  def(cfg.wylloMaxEmailsPerUser, 1), def(cfg.wylloEmailWeight, 2)),
        };
        for (var s : specs)
            if (s.bin().length() > 15)
                throw new IllegalArgumentException("bin name over 15 chars: " + s.bin());

        var gen = new FeatureGenerator(
                cfg.perseusId,
                specs,
                def(cfg.wylloFeaturesPerEntity, 20),
                def(cfg.wylloAvgWritesPerUser, 10),
                def(cfg.wylloBinUniverse, 100_000));

        // Cold features have no TTL today; default to never expire.
        int ttl = def(cfg.wylloTtlSeconds, 0);

        var args = new TestCaseConstructorArguments(client, ns, aero.set, totalTpsCounter);
        testList.add(new FeatureWriteTest(args, gen, ttl));
        testList.add(new FeatureBatchWriteTest(args, gen, def(cfg.writeBatchSize, 100), ttl));
        testList.add(new FeatureReadTest(args, gen));
        testList.add(new FeatureBinReadTest(args, gen));
        testList.add(new FeatureEntityReadTest(args, gen));
        testList.add(new FeatureVectorReadTest(args, gen, def(cfg.wylloVectorSize, 10)));
        testList.add(new FeatureBatchReadTest(args, gen, def(cfg.readBatchSize, 50)));
    }

    private static int def(Integer v, int d) { return v != null ? v : d; }

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
