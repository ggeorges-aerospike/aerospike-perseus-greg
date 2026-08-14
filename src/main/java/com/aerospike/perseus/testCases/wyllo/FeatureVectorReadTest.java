package com.aerospike.perseus.testCases.wyllo;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Value;
import com.aerospike.client.cdt.MapReturnType;
import com.aerospike.client.exp.Exp;
import com.aerospike.client.exp.ExpOperation;
import com.aerospike.client.exp.ExpReadFlags;
import com.aerospike.client.exp.MapExp;
import com.aerospike.perseus.data.generators.wyllo.FeatureGenerator;
import com.aerospike.perseus.data.generators.wyllo.FeatureIds;
import com.aerospike.perseus.testCases.Test;
import com.aerospike.perseus.testCases.TestCaseConstructorArguments;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The model-scoring read: a named subset of features for one entity, fetched in a single
 * server-side operation.
 *
 * <p>This is the most representative read in the workload — inference wants a fixed feature
 * vector, not the whole record — and the one that most rewards KEY_ORDERED, since each lookup
 * is O(log n) inside the map. Only the requested values cross the network.
 *
 * <p>Implemented as an expression read rather than {@code MapOperation.getByKeyList} with a
 * {@code CTX.mapKey}: a CTX that names a missing entity makes the server return error 26
 * ("Operation not applicable") rather than an empty result, and a user legitimately may not
 * own the entity being asked for. Feeding the inner map into the key-list lookup as an
 * expression and reading with {@link ExpReadFlags#EVAL_NO_FAIL} yields null on a missing
 * path instead, so a genuinely absent entity is a normal empty answer rather than an error.
 */
public class FeatureVectorReadTest extends Test<Long> {
    private final FeatureGenerator gen;
    private final int vectorSize;

    public FeatureVectorReadTest(TestCaseConstructorArguments args, FeatureGenerator gen, int vectorSize) {
        super(args, Ticks.INSTANCE);
        this.gen = gen;
        // Never ask for more names than the entity actually carries, or the hit rate is
        // capped by the model rather than by the cluster.
        this.vectorSize = Math.min(Math.max(vectorSize, 1), gen.featuresPerEntity());
    }

    @Override protected void execute(Long ignored) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String userId = gen.randomWrittenUser(r);
        FeatureGenerator.EntitySpec spec = gen.pickSpec(r);
        int instance = r.nextInt(Math.max(spec.maxEntitiesPerUser(), 1));
        String entityId = FeatureIds.entityId(spec.bin(), userId, instance, gen.binUniverse());

        // Names the writers actually wrote for this entity — a contiguous slice of its set.
        int from = r.nextInt(gen.featuresPerEntity() - vectorSize + 1);
        List<Value> names = new ArrayList<>(vectorSize);
        for (int i = 0; i < vectorSize; i++) {
            names.add(Value.get(FeatureIds.featureName(userId, entityId, from + i)));
        }

        // entity map = bin[entityId]; vector = entityMap[names...]
        Exp entityMap = MapExp.getByKey(MapReturnType.VALUE, Exp.Type.MAP,
                Exp.val(entityId), Exp.mapBin(spec.bin()));
        Exp vector = MapExp.getByKeyList(MapReturnType.KEY_VALUE, Exp.val(names), entityMap);

        try {
            client.operate(null, getKey(userId),
                    ExpOperation.read("v", Exp.build(vector), ExpReadFlags.EVAL_NO_FAIL));
        } catch (AerospikeException ignored2) {}
    }

    public String[] getHeader() { return String.format("feature\nvector %d", vectorSize).split("\n"); }
}
