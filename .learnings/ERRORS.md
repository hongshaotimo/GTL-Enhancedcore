# 建置錯誤紀錄

## [ERR-20261028-001] 本機依賴不完整

**Logged**: 2026-10-28
**Priority**: medium
**Status**: in_progress
**Area**: build

### Summary
`verifyLocalDependencies` 因專案 `libs` 缺少指定版本 JAR 而失敗。

### Root Cause
本機未具備 `gradle/local-dependencies.json` 列出的完整依賴；不同版本或不同映射不能僅靠改名替代。

### Fix
依現有 JAR 更新依賴清單，並從 Forge JAR 取出內嵌的 MixinExtras 共用 JAR；再補入回歸測試固定要求的 FTB 與 JEI 原版本。依賴檢查、編譯及重映射已通過；回歸測試後續因缺少結構審核檔案而中止，見 `TODO.md`。

## [ERR-20261028-002] 山海：源碼檢索前須先確認檔名

**Logged**: 2026-10-28
**Priority**: low
**Status**: resolved
**Area**: tooling

### Summary
雲端樣板效能分析時，誤向不存在的 `IvExecution.java` 執行 `rg`，回傳系統找不到指定的文件。

### Root Cause
未先列舉實際檔案，依推測的類名指定檢索路徑。

### Fix
已用 `rg --files` 確認實際檔案為 `src/main/java/com/gtl/enhancedcore/common/recipe/iv/IvRecipeLogic.java`，並從該檔案核對相關日誌呼叫。後續先確認檔名再指定搜尋路徑。

### Follow-up（山海）
準備效能補丁時再次誤將 `PersistentSlots` 視為獨立文件；已以類別搜尋確認它是 `CloudLedger.java` 的私有巢狀類別。檢索類別前先用 `rg --files` 或 `rg -n 'class 類名'` 確認實際位置，不以類名推定文件路徑。

## [ERR-20261028-003] 山海：本次 Enhancedcore 修改範圍已確認

**Logged**: 2026-10-28
**Priority**: low
**Status**: resolved
**Area**: tooling

### Summary
使用者補充自己參與此專案開發，並明確允許修改本次雲端樣板效能修復相關源碼與必要測試。

### Fix
在本次修復範圍內沿用這項授權，不再因檔案原作者或缺少山海署名重複詢問。授權不包含無關改動、提交或部署。

## [ERR-20261028-004] 山海：Windows rg 文件篩選須使用 glob 選項

**Logged**: 2026-10-28
**Priority**: low
**Status**: resolved
**Area**: tooling

### Summary
PowerShell 不會將傳給 `rg` 的 `Cloud*Regression.java` 路徑展開，檢索回傳 Windows 錯誤 123。

### Fix
改為指定存在的目錄並傳入 `-g 'Cloud*Regression.java'`，已成功檢索。

## [ERR-20261028-005] 山海：背景索引等待回應保留瀏覽狀態

**Logged**: 2026-10-28
**Priority**: low
**Status**: resolved
**Area**: client

### Summary
獨立審查發現初版 pending 回應省略頁碼與搜尋範圍，前端會將頁碼讀成 0。

### Fix
`CloudLibraryService.pendingPage()` 保留請求頁碼、類型及最後已發布的物品／流體搜尋範圍，並補入回歸檢查。背景失敗另保留失敗版本，避免同版本反覆重試；中央庫介面沿用既有 review 診斷流程。
