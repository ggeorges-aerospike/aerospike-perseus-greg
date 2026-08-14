package com.aerospike.perseus.testCases.wyllo;

import com.aerospike.client.Operation;
import com.aerospike.client.Value;
import com.aerospike.client.cdt.CTX;
import com.aerospike.client.cdt.MapOperation;
import com.aerospike.client.cdt.MapOrder;
import com.aerospike.client.cdt.MapPolicy;
import com.aerospike.client.cdt.MapWriteMode;
import com.aerospike.perseus.data.generators.wyllo.FeatureGenerator;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds the upsert for one entity's features. Shared by the point-write and
 * batch-write tests so both emit byte-identical records.
 *
 * <p>The record is one bin per entity_type, holding a KEY_ORDERED map of
 * entity_id -> KEY_ORDERED map of feature_id -> value.
 */
public final class FeatureOps {

    /** KEY_ORDERED so features sort on write: enables getByKeyRange and O(log n) getByKey. */
    static final MapPolicy ORDERED = new MapPolicy(MapOrder.KEY_ORDERED, MapWriteMode.UPDATE);

    private FeatureOps() {}

    /**
     * One {@code putItems} into the entity's inner map.
     *
     * <p>{@link CTX#mapKeyCreate} rather than {@link CTX#mapKey}: Aerospike creates a
     * missing map at the top level of a bin, but NOT one nested inside another map, so
     * the first write for a previously unseen entity_id would otherwise fail.
     */
    public static Operation upsert(FeatureGenerator.Write w) {
        Map<Value, Value> features = new HashMap<>(w.features().size() * 2);
        w.features().forEach((name, value) -> features.put(Value.get(name), Value.get(value)));
        CTX entity = CTX.mapKeyCreate(Value.get(w.entityId()), MapOrder.KEY_ORDERED);
        return MapOperation.putItems(ORDERED, w.bin(), features, entity);
    }
}
