# etcd 3性能測試

啟動docker

* [docker-compose.yaml](etcd/docker-compose.yaml)

# 測試寫入

[EtcdPerformanceApplicationWatchTest.java](src/test/java/com/alliance/slots/etcdperformace/EtcdPerformanceApplicationWatchTest.java)

已經有30000比的key在etcd內, 如果持續更新, 效能如何, 每一秒採樣做均值

結果:

```shell
631403.191 ns/op
Iteration   3: 5626671.281 ns/op
Iteration   4: 5567511.344 ns/op
Iteration   5: 5569969.300 ns/op
Iteration   6: 5406672.054 ns/op
Iteration   7: 5457697.071 ns/op
Iteration   8: 5700636.330 ns/op
Iteration   9: 5284504.805 ns/op
Iteration  10: 5292206.392 ns/op
Iteration  11: 5363208.599 ns/op
Iteration  12: 5104211.628 ns/op
Iteration  13: 5162555.696 ns/op
Iteration  14: 5218928.295 ns/op
Iteration  15: 5018968.585 ns/op
Iteration  16: 5350880.668 ns/op
Iteration  17: 5296090.931 ns/op
Iteration  18: 5256421.833 ns/op
Iteration  19: 5276225.642 ns/op
Iteration  20: 5091588.360 ns/op
Iteration  21: 5093398.444 ns/op
Iteration  22: 4943223.192 ns/op
Iteration  23: 5301165.439 ns/op
Iteration  24: 5369051.545 ns/op
Iteration  25: 5336058.011 ns/op
Iteration  26: 5293328.249 ns/op
Iteration  27: 5232770.630 ns/op
Iteration  28: 5039750.392 ns/op
Iteration  29: 5036824.458 ns/op
Iteration  30: 5107334.418 ns/op
Iteration  31: 5179357.299 ns/op
Iteration  32: 5177491.706 ns/op
Iteration  33: 5304523.732 ns/op
Iteration  34: 5215409.417 ns/op
Iteration  35: 5226650.406 ns/op
Iteration  36: 5166262.387 ns/op
Iteration  37: 5215954.880 ns/op
Iteration  38: 5254158.843 ns/op
Iteration  39: 5329645.830 ns/op
Iteration  40: 5326229.654 ns/op
Iteration  41: 5197908.782 ns/op
Iteration  42: 5161059.289 ns/op
Iteration  43: 5105206.929 ns/op
Iteration  44: 4985929.413 ns/op
Iteration  45: 5068379.919 ns/op
```

---

# 用來確認寫入的資料是否都有正確

[EtcdPerformaceApplicationGetTests.java](src/test/java/com/alliance/slots/etcdperformace/EtcdPerformaceApplicationGetTests.java)


行為正常
---

# 用來確認高速寫入下watch 是否能work

[EtcdPerformanceApplicationWatchTest.java](src/test/java/com/alliance/slots/etcdperformace/EtcdPerformanceApplicationWatchTest.java)

行為正常

---

# 這篇診斷etcd問題, 來自Gemini AI, 可信度高

### 問題分析：為什麼會失敗？

`etcd` 是一個分散式系統，寫入操作（如 `put`）需要叢集中大多數節點（Quorum，法定人數）的同意才能成功提交。因此，失敗的原因通常與*
*叢集健康狀態、資源限制或客戶端配置**有關。

-----

### 1. 叢集健康問題 (Cluster Health Issues)

這是最常見的原因。即使你的客戶端只連接到一個健康的節點，如果整個叢集無法達成共識，寫入請求也會被拒絕。

| 可能原因                        | 診斷方式                                                                     | 解決方案                                                                                         |
|:----------------------------|:-------------------------------------------------------------------------|:---------------------------------------------------------------------------------------------|
| **失去 Quorum (Quorum Loss)** | 叢集中超過一半的節點故障或無法互相通訊。叢集會進入唯讀模式以保護資料一致性。                                   | 檢查每個節點的 `etcd` 服務狀態，修復或替換故障的節點，直到超過半數的節點恢復正常。                                                |
| **沒有 Leader (No Leader)**   | 由於網路分區 (Network Partition) 或選舉失敗，叢集中沒有一個節點被選舉為 Leader。寫入請求必須由 Leader 處理。 | 使用 `etcdctl endpoint status -w table` 檢查每個成員的狀態。如果沒有 Leader，需要解決節點之間的網路連線問題，確保它們可以互相通訊並完成選舉。 |
| **網路延遲或不穩定**                | 節點間通訊延遲過高，導致 Leader 的心跳超時，觸發不必要的重新選舉，期間無法寫入。                             | 檢查叢集節點之間的網路 `ping` 延遲和穩定性。確保網路基礎設施可靠。                                                        |

**診斷指令：**

* **檢查叢集健康狀況**：這是首要步驟。

  ```bash
  etcdctl endpoint health --endpoints=<node1_ip>:2379,<node2_ip>:2379,... -w table
  ```

  如果任何一個 endpoint 回應 `false`，就表示該節點有問題。

* **檢查成員狀態和 Leader**：

  ```bash
  etcdctl endpoint status --endpoints=<your_endpoints> -w table
  ```

  這個指令會顯示哪個節點是 Leader、每個節點的資料庫 ID (Raft Index) 等資訊。如果 **IS LEADER** 欄位全部是 `false`，就表示沒有
  Leader。

### 2\. 資源限制 (Resource Limitations)

當叢集資源耗盡時，etcd 會主動拒絕寫入請求以防止系統崩潰。

| 可能原因                  | 診斷方式                                                                  | 解決方案                                                                                                                                                                |
|:----------------------|:----------------------------------------------------------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **超出資料庫空間配額 (Quota)** | etcd 預設有 2GB 的儲存配額 (`--quota-backend-bytes`)。當資料庫大小超過此限制時，會觸發告警並拒絕寫入。 | 1.  **清理舊資料**：使用 `etcdctl compact` 壓縮歷史版本。\<br\>2.  **磁碟重組**：使用 `etcdctl defrag` 釋放空間給作業系統。\<br\>3.  **增加配額**：如果確實需要更多空間，可以調整啟動參數 `--quota-backend-bytes` (例如 8GB)。 |
| **伺服器磁碟空間已滿**         | etcd 儲存資料的實體磁碟 (`--data-dir` 指定的路徑) 已經沒有剩餘空間。                         | 在 etcd 節點伺服器上執行 `df -h` 檢查磁碟使用情況。清理磁碟空間或擴充磁碟容量。                                                                                                                     |
| **請求大小超過限制**          | 你嘗試 `put` 的資料量超過了 etcd 的請求大小上限 (預設 1.5MB)。                            | etcd 不適合儲存大型檔案。請將大型資料儲存在物件儲存（如 S3）中，而在 etcd 中只儲存其路徑或元資料。錯誤訊息通常會是 `etcdserver: request is too large`。                                                                |

**診斷與解決指令：**

1. **檢查配額狀態**：`endpoint status` 的輸出會包含資料庫大小 (`DB SIZE`)。

   ```bash
   etcdctl endpoint status --endpoints=<your_endpoints> -w table
   ```

2. **壓縮與重組 (定期維護的最佳實踐)**：

   ```bash
   # 獲取當前的 revision
   REV=$(etcdctl endpoint status --endpoints=<your_endpoints> -w json | egrep -o '"revision":[0-9]*' | egrep -o '[0-9]*')

   # 壓縮到當前的 revision
   etcdctl compact $REV

   # 對所有成員進行磁碟重組 (defrag)
   etcdctl defrag --endpoints=<your_endpoints>
   ```

### 3\. 客戶端配置與權限問題 (Client & Auth Issues)

問題也可能出在發起請求的客戶端。

| 可能原因                      | 診斷方式                                               | 解決方案                                                                                         |
|:--------------------------|:---------------------------------------------------|:---------------------------------------------------------------------------------------------|
| **Endpoint 不正確或無法連線**     | `etcdctl` 或你的應用程式配置的 endpoint 位址錯誤，或指向了一個已經故障的節點。  | 確認 `--endpoints` 參數中的 IP 和埠號是否正確，並確保客戶端與該 endpoint 之間的網路是通的。                                 |
| **認證失敗 (Authentication)** | 如果 etcd 啟用了認證，你提供的用戶名、密碼或憑證是錯誤的。                   | 檢查 `etcdctl` 指令中的 `--user`、`--password` 或 `--cert`、`--key` 等參數是否正確。                          |
| **權限不足 (Authorization)**  | 你的使用者或角色沒有對指定的 key 進行寫入的權限。                        | 使用 `etcdctl role get <your_role>` 檢查角色權限。聯繫 etcd 管理員為你的使用者授予對目標前綴 (prefix) 的 `readwrite` 權限。 |
| **API 版本不匹配**             | 客戶端（`etcdctl`）使用的 API 版本與伺服器不相容。最常見的是 v2 和 v3 的混用。 | 明確指定使用 v3 API。設定環境變數 `export ETCDCTL_API=3` 是最佳實踐。                                           |

**診斷指令：**

* **啟用偵錯輸出**：在你的 `etcdctl` 指令前加上 `ETCDCTL_API=3 ETCDCTL_DEBUG=true` 可以看到詳細的請求過程，有助於排查連線和認證問題。

-----

### 系統性排查步驟 (Troubleshooting Checklist)

當 `put` 失敗時，請依照以下順序檢查：

1. **檢查錯誤訊息**：終端機顯示的錯誤訊息是什麼？`permission denied`、`etcdserver: mvcc: database space exceeded`、
   `context deadline exceeded` 這些訊息都指向了不同的方向。
2. **檢查叢集健康 (`etcdctl endpoint health`)**：這是最重要的第一步。確保所有節點都是健康的。
3. **檢查 Leader 狀態 (`etcdctl endpoint status`)**：確認有一個 Leader 存在。
4. **檢查資料庫配額 (`etcdctl endpoint status`)**：確認 `DB SIZE` 沒有超出配額。
5. **檢查伺服器磁碟空間 (`df -h`)**：登入到 etcd 伺服器上檢查。
6. **檢查客戶端配置**：
    * `ETCDCTL_API=3` 是否設定？
    * `--endpoints` 是否正確？
    * 如果啟用認證，`--user` 或憑證是否正確？
7. **查看伺服器日誌**：如果以上都正常，請登入到 etcd Leader 節點，查看 `etcd` 服務的日誌，通常會有更詳細的失敗原因。

總結來說，即使沒有併發寫入，`put` 失敗也強烈暗示著 etcd 叢集的穩定性、資源或配置出現了問題。從**叢集健康**和**資源限制**
這兩個大方向著手排查，通常都能找到問題的根源。



---

# 結論

需要注意的是:

1. 預設etcd node 存儲是2G大小, 需要確認能擴大多少?!
2. 單key最多放1.5 mb 的value
3. 使用scan做多key查詢很慢, 需要避免業務上如此使用, 上千節點時, `etcdkeeper` 無法使用 (會timeout)

# etcd-performance-testing
