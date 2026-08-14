package com.aerospike.perseus.data.generators.wyllo;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Feature-platform writes (MongoDB -> Aerospike). See WYLLO_DATA_MODEL.md.
 *
 * <p>Source shape — one Mongo document per (entity, feature):
 * <pre>
 * {_id: ObjectId, entity_type: "bin", entity_id: "411111",
 *  feature_id: "bin_avg_tpv_3d", value: 152.34}
 * </pre>
 *
 * <p>Aerospike shape — one record per user, keyed by the ObjectId; one bin per
 * entity_type, each a KEY_ORDERED map of entity_id -> KEY_ORDERED map of
 * feature_id -> value:
 * <pre>
 * PK "6a2c1147e6325a3e33575ab8"
 *   bin:    { "411111": { "avg_tpv_3d": 152.34, "cnt_txn_7d": 12.0 } }
 *   bin_ip: { "411111|203.0.113.7": { "avg_amount_90d": 88.10 } }
 *   email:  { "a3f9c2e1": { "avg_days_since_last_txn_1d": 3.0 } }
 * </pre>
 *
 * <p>The entity_type prefix is dropped from feature_id because the bin name already
 * carries it ({@code bin_avg_tpv_3d} -> bin/{@code avg_tpv_3d}).
 *
 * <p>Each {@link #next()} is ONE upsert of {@code featuresPerEntity} features for a single
 * entity of a single type — the incremental update the feature pipeline performs. A user
 * accumulates entity types over successive writes, so bins materialise only once that user
 * actually has features of that type.
 *
 * <p>Entity ids and feature names are derived from the user id via {@link FeatureIds}, so
 * the read tests can reconstruct ids that exist. See that class for why.
 */
public class FeatureGenerator implements Iterator<FeatureGenerator.Write> {

    /** One upsert: user record, target bin (entity type), entity instance, features. */
    public record Write(String userId, String bin, String entityId, Map<String, Double> features) {}

    /** An entity type: the Aerospike bin name, instances per user, and share of writes. */
    public record EntitySpec(String bin, int maxEntitiesPerUser, int weight) {}

    private final long offset;
    private final EntitySpec[] specs;
    private final int totalWeight;
    private final int featuresPerEntity;
    private final int avgWritesPerUser;
    private final int binUniverse;
    private final AtomicLong seq = new AtomicLong();

    public FeatureGenerator(int perseusId, EntitySpec[] specs, int featuresPerEntity,
                            int avgWritesPerUser, int binUniverse) {
        if (specs == null || specs.length == 0) throw new IllegalArgumentException("no entity specs");
        // Per-instance key range so N Perseus clients never collide on a user.
        this.offset = (perseusId + 1L) * 1_000_000_000_000L;
        this.specs = specs;
        this.featuresPerEntity = Math.min(Math.max(featuresPerEntity, 1), FeatureIds.FEATURE_UNIVERSE);
        this.avgWritesPerUser = Math.max(avgWritesPerUser, 1);
        this.binUniverse = Math.max(binUniverse, 1);
        int w = 0;
        for (EntitySpec s : specs) w += Math.max(s.weight(), 1);
        this.totalWeight = w;
    }

    @Override public boolean hasNext() { return true; }

    @Override public Write next() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        // Spread writes randomly across the users created so far rather than hammering a
        // "current" user — one hot record would hit the per-record rw-hash limit and report
        // KEY_BUSY instead of real cluster throughput.
        long users = Math.max(1, seq.getAndIncrement() / avgWritesPerUser);
        String userId = FeatureIds.oid(offset + r.nextLong(users));

        EntitySpec spec = pickSpec(r);
        int instance = r.nextInt(Math.max(spec.maxEntitiesPerUser(), 1));
        String entityId = FeatureIds.entityId(spec.bin(), userId, instance, binUniverse);

        Map<String, Double> features = new LinkedHashMap<>(featuresPerEntity * 2);
        for (int i = 0; i < featuresPerEntity; i++) {
            features.put(FeatureIds.featureName(userId, entityId, i), r.nextDouble() * 1000.0);
        }
        return new Write(userId, spec.bin(), entityId, features);
    }

    /** Weighted pick over the configured entity types. */
    public EntitySpec pickSpec(ThreadLocalRandom r) {
        int roll = r.nextInt(totalWeight);
        for (EntitySpec s : specs) {
            roll -= Math.max(s.weight(), 1);
            if (roll < 0) return s;
        }
        return specs[specs.length - 1];
    }

    public long offset() { return offset; }
    public EntitySpec[] specs() { return specs; }
    public int binUniverse() { return binUniverse; }
    public int featuresPerEntity() { return featuresPerEntity; }

    /** Distinct users the writers have made eligible so far. */
    public long usersWritten() { return Math.max(1, seq.get() / avgWritesPerUser); }

    /**
     * A user key that the writers have very likely already touched.
     *
     * <p>The user universe grows as writes accumulate, so ids near the top became eligible
     * only moments ago and many have received no write yet — reading the full range gives a
     * ~10% miss rate that has nothing to do with the cluster. Holding back the newest decile
     * keeps reads on populated records, so a miss in the results means something real.
     * Introduce misses deliberately via {@code readHitRatio} instead, if you want them.
     */
    public String randomWrittenUser(ThreadLocalRandom r) {
        long settled = Math.max(1, usersWritten() * 9 / 10);
        return FeatureIds.oid(offset + r.nextLong(settled));
    }
}
