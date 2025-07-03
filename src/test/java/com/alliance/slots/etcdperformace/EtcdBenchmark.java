package com.alliance.slots.etcdperformace;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class EtcdBenchmark {

    private Client client;
    private KV kvClient;

    @Setup
    public void setup() {
        client = Client.builder().endpoints("http://localhost:2379").build();
        kvClient = client.getKVClient();
    }

    @TearDown
    public void tearDown() {
        client.close();
    }

    @Benchmark
    public void write5000Keys() throws ExecutionException, InterruptedException {
        for (int i = 0; i < 5000; i++) {
            ByteSequence key = ByteSequence.from(String.format("key_%d", i).getBytes());
            ByteSequence value = ByteSequence.from(String.format("value_%d", i).getBytes());
            kvClient.put(key, value).get();
        }
    }
}
