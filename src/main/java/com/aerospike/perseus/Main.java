package com.aerospike.perseus;

import com.aerospike.perseus.presentation.OutputWindow;
import com.aerospike.perseus.configurations.*;

public class Main {
    public static void main(String[] args) throws InterruptedException {

        // Log any uncaught exceptions from worker threads
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            System.err.println("[UNCAUGHT] Thread " + t.getName() + " died: " + e);
            e.printStackTrace(System.err);
            System.err.flush();
        });

        var config = new ConfigurationProvider().getConfiguration();

        var setup = new TestSetup(config.aerospikeConfiguration, config.testConfiguration);
        new OutputWindow(config.outputWindowConfiguration, setup.getLoggableTestList(), setup.getTotalTps());
        setup.startTest();

        // Keep main thread alive so JVM never exits
        Thread.currentThread().join();
    }
}