package com.alliance.slots.etcdperformace;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.nio.charset.Charset;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


@BenchmarkMode(Mode.AverageTime) // 設定測試模式為平均執行時間
@OutputTimeUnit(TimeUnit.NANOSECONDS) // 設定時間單位為奈秒
@State(Scope.Thread) // 設定狀態範圍，後續會解釋
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS) // 預熱 5 輪，每輪 1 秒
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS) // 正式測量 5 輪，每輪 1 秒
@Fork(1) // 使用 1 個子行程來執行測試
public class EtcdPerformaceApplicationGetTests {


    // 1. 定義一個 State 類別來管理你的物件
    @State(Scope.Thread) // Scope.Thread 表示每個測試執行緒都會有自己獨立的一份實例
    public static class MyState {
        // 將原來的參數變成 State 類別的成員變數
        public KV kvClient;

        public int cnt = 0;

        // 2. 使用 @Setup 方法來進行初始化
        // Level.Trial: 在每一輪完整測試(Trial)開始前執行一次
        @Setup(Level.Trial)
        public void setup() {
            // 在這裡進行你的初始化工作
            kvClient = createKvClient(); // 假設你有一個方法來建立客戶端
            System.out.println("Setup is running...");
        }

        // 可以選擇性地加入 @TearDown 方法來清理資源
        @TearDown(Level.Trial)
        public void tearDown() {
            kvClient.close();
            System.out.println("TearDown is running...");
        }

        private KV createKvClient() {
            // ... 建立並返回 KV Client 實例的邏輯 ...
            // Create a client
            Client client = Client.builder().endpoints("http://localhost:2379").build();
            return client.getKVClient();
        }
    }


    public static void main(String[] args) throws RunnerException {
        // 設定 JMH 選項
        Options opt = new OptionsBuilder()
                // 指定要執行的 Benchmark 類別
                .include(EtcdPerformaceApplicationGetTests.class.getSimpleName())

                // 為了在單元測試中快速得到結果，可以覆寫部分設定
                // 注意：這會影響結果的精準度，僅適用於開發階段的快速驗證
                .warmupIterations(1)
                .warmupTime(TimeValue.nanoseconds(5))
                .measurementIterations(5000)
                .measurementTime(TimeValue.seconds(1))
                .forks(1)
                .resultFormat(ResultFormatType.TEXT)
                // 你也可以在這裡設定其他選項，例如輸出格式
                // .resultFormat(ResultFormatType.JSON)
                // .result("target/jmh-results.json")

                .build();

        // 建立並執行 Runner
        new Runner(opt).run();
    }

    @Benchmark
    public void get(MyState myState) throws ExecutionException, InterruptedException {
        String tv = TestData.DATA;
        var c = "test/v-" + myState.cnt;

        // 從 state 物件中獲取你需要的資料
        var v = myState.kvClient.get(
                ByteSequence.from((c), Charset.defaultCharset()));
        var kvs = v.get().getKvs();
        if (kvs != null && !kvs.isEmpty()) {
            var ss = kvs.getFirst();
            var d = new String(ss.getValue().getBytes());
            System.out.println("-------------> (" + c + ")  , value " + d);
            if (d.compareTo(tv) != 0) {
                throw new IllegalStateException("not " + tv + " on " + c);
            }
        } else {
            throw new IllegalStateException(" no data: on " + c);
        }
        myState.cnt = myState.cnt + 1;
    }

}
