package com.aerospike.perseus.testCases;

import com.aerospike.perseus.data.BlueCatRecord;
import com.aerospike.perseus.data.generators.BaseGenerator;
import com.aerospike.perseus.data.generators.BlueCatRecordGenerator;

public class BlueCatWriteTest extends Test<BlueCatRecord> {

    public BlueCatWriteTest(TestCaseConstructorArguments arguments, BlueCatRecordGenerator blueCatRecordGenerator) {
        super(arguments, blueCatRecordGenerator);
    }

    @Override
    protected void execute(BlueCatRecord record) {
        client.put(
                null,                       // use client.writePolicyDefault
                getKey(record.getKey()),    // inherited helper
                record.getBins()
        );
    }

    @Override
   public String[] getHeader() {
        return "Write\n ".split("\n");
    }
}
