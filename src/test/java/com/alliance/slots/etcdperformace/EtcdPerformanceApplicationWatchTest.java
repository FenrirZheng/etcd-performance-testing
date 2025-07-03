
package com.alliance.slots.etcdperformace;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.watch.WatchResponse;
import org.openjdk.jmh.annotations.*;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;


@BenchmarkMode(Mode.AverageTime) // 設定測試模式為平均執行時間
@OutputTimeUnit(TimeUnit.NANOSECONDS) // 設定時間單位為奈秒
@State(Scope.Thread) // 設定狀態範圍，後續會解釋
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS) // 預熱 5 輪，每輪 1 秒
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS) // 正式測量 5 輪，每輪 1 秒
@Fork(1) // 使用 1 個子行程來執行測試
public class EtcdPerformanceApplicationWatchTest {


    private static Watch.Watcher watch;
    private static Client client;

    private static KV createKvClient() {
        // ... 建立並返回 KV Client 實例的邏輯 ...
        // Create a client
        Client client = Client.builder().endpoints("http://localhost:2379").build();
        EtcdPerformanceApplicationWatchTest.client = client;
        // 3. 建立 WatchOption，並設定 withPrefix
        // 這是最關鍵的一步！
        return client.getKVClient();
    }

    private static Watch.Watcher doWatch(Client client) {
        String watchPrefix = TestData.PREFIX;
        WatchOption watchOption = WatchOption.builder()
                .isPrefix(true)
                .build();
        return client.getWatchClient().watch(ByteSequence.from(watchPrefix, StandardCharsets.UTF_8), watchOption, new Consumer<WatchResponse>() {

            @Override
            public void accept(WatchResponse watchResponse) {
                for (var c : watchResponse.getEvents()) {
                    System.out.println(" event " + new String(c.getKeyValue().getKey().getBytes()));
                }
            }
        });
    }


    public static KV kvClient;

    public int cnt = 0;

    // 2. 使用 @Setup 方法來進行初始化
    // Level.Trial: 在每一輪完整測試(Trial)開始前執行一次
    public static void setup() {
        // 在這裡進行你的初始化工作
        kvClient = createKvClient(); // 假設你有一個方法來建立客戶端
        System.out.println("Setup is running...");
    }


    public static void main(String[] args) throws InterruptedException {
        setup();
        CountDownLatch latch = new CountDownLatch(1);
        try (Watch.Watcher watch = doWatch(client)) {
            latch.await();
        } catch (Exception e ) {
            e.printStackTrace();
        } finally {
            latch.countDown();
        }
        TimeUnit.HOURS.sleep(1);
    }


}
