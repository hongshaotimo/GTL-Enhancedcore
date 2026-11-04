# 本機建置待辦

- [x] 補齊回歸測試所需的原版本 FTB Library、FTB Quests、FTB Chunks 與 JEI JAR。
- [ ] 找回 `tools/hidden_structure_bindings.json` 原始審核檔案，須符合結構 manifest 中的 SHA-256。
- [ ] 審核檔案補齊後重新執行完整 `regressionTest`，並驗證目前編譯依賴與遊戲的相容性；缺檔時 `build.bat` 已允許跳過整個回歸測試任務。

## 山海：雲端樣板效能修復

- [x] 使用者確認參與 Enhancedcore 開發，明確允許本次修改相關源碼與必要測試；本次不再受署名限制阻擋。
- [x] 已套用首批修復：共用配方候選索引、索引未完成時完整候選回退、重載失效、總成介面租約與顯示快取、單步控制器復用、中央庫單次網路解析。
- [x] 新增效能回歸 16 項；合併既有雲端回歸共 12,402 項通過。Java 21／Gradle 8.8 執行 `shanhaiCloudRegression build -x regressionTest --no-daemon --offline` 成功，正式 jar 完成 reobf。
- [x] 中央庫索引改為伺服器共用 256 步／約 2 ms 軟預算；完整同版本索引才發布，等待期間保留頁碼與搜尋範圍，失敗顯示 review 並停止同版本反覆重試。網路解析復用已完成。
- [x] 獨立程式碼審查及複核完成，頁碼回歸已修正；`git diff --check` 通過。先前補丁已套用並移除草稿文件。
- [ ] 完整 `regressionTest` 已嘗試，在 `HollowStructureRegression.java:30` 因缺少 `tools/hidden_structure_bindings.json` 中止；補齊原始審核檔後重跑，不能視為完整回歸通過。
- [ ] 存檔和日誌優化留待 profiler 證據，不改變現有交易確認順序。
- [ ] 遊戲內驗證空閒、批量派單、匯入、介面、配方重載及 AE 斷線重連，並比較 profiler。

產物：`build/libs/GTL-Enhancedcore-4.1.7.jar`；SHA-256：`EF7C44DC0D6367FEF2DBFB1FCA6FB741F4F1AB859329DBF2FF297CF8EDC63332`。未部署、未重啟遊戲、未提交。
