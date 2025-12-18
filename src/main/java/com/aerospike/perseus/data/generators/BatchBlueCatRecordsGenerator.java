package com.aerospike.perseus.data.generators;

import com.aerospike.perseus.data.BlueCatRecord;
import com.aerospike.perseus.data.Record;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BatchBlueCatRecordsGenerator extends BaseGenerator<List<BlueCatRecord>> {

    private final BlueCatRecordGenerator simpleRecordGenerator;
    private final long batchSize;

    public BatchBlueCatRecordsGenerator(BlueCatRecordGenerator simpleRecordGenerator, long batchSize) {
        this.simpleRecordGenerator = simpleRecordGenerator;
        this.batchSize = batchSize;
    }

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public List<BlueCatRecord> next() {
        return Stream
                .generate(() -> simpleRecordGenerator.next())
                .limit(batchSize)
                .collect(Collectors.toList());
    }
}