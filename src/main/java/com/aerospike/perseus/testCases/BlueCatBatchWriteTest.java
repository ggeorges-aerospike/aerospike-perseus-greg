package com.aerospike.perseus.testCases;

import com.aerospike.client.*;
import com.aerospike.perseus.data.BlueCatRecord;
import com.aerospike.perseus.data.generators.BatchBlueCatRecordsGenerator;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BlueCatBatchWriteTest extends Test<List<BlueCatRecord>> {

    private final int batchSize;

    public BlueCatBatchWriteTest(TestCaseConstructorArguments arguments, BatchBlueCatRecordsGenerator batchSimpleRecordsGenerator, int batchSize) {
        super(arguments, batchSimpleRecordsGenerator);
        this.batchSize = batchSize;
    }

    @Override
    protected void execute(List<BlueCatRecord> records) {
        List<BatchRecord> batchWrites = records.stream().map(r ->
        {
            Operation[] operations = Arrays.stream(r.getBins()).map(b -> new Operation(Operation.Type.WRITE, b.name, b.value)).toArray(Operation[]::new);
            return new BatchWrite(
                    new Key(namespace, setName, r.getKey()),
                    operations);
        })
        .collect(Collectors.toList());

        client.operate(client.batchPolicyDefault, batchWrites);
    }

    public String[] getHeader(){
        return String.format("BlueCat Batch Write\nSize: %d", batchSize).split("\n");
    }
}
