# タスクリスト：J-Quants API 連携実装

## 進捗凡例
- [ ] 未着手
- [x] 完了

---

## タスク

### 1. AppConfig に JQUANTS_REFRESH_TOKEN を追加
- [x] `jquantsRefreshToken` フィールドを追加（null 許容）
- [x] `getJquantsRefreshToken()` ゲッターを追加
- [x] `resetInstance()` で null クリア確認

### 2. JQuantsTokenManager の実装
- [x] `jquants/JQuantsTokenManager.java` を実装する
- [x] `getIdToken()` — POST /v1/token/auth_refresh でIDトークン取得
- [x] `JQuantsTokenManagerTest.java` を実装する（モックなし、レスポンスJSONを手動生成）

### 3. JQuantsApiClient の実装
- [x] `jquants/JQuantsApiClient.java` を実装する
- [x] `fetchListedInfo()` — GET /v1/listed/info
- [x] `fetchDailyQuotes(code, from, to)` — GET /v1/prices/daily_quotes
- [x] `fetchFinStatements(code)` — GET /v1/fins/statements
- [x] `JQuantsApiClientTest.java` を実装する

### 4. DatabaseManager にJ-Quantsテーブルを追加
- [x] `initializeSchema()` に4テーブルのDDLを追加
- [x] `companies` テーブルに `secCode` カラムを追加（ALTER TABLE + 例外ハンドリング）
- [x] `DatabaseManagerTest.java` に新テーブルの存在確認テストを追加

### 5. JQuantsListedInfoDao の実装
- [x] `db/JQuantsListedInfoDao.java` を実装する（upsert, findAll）
- [x] `JQuantsListedInfoDaoTest.java` を実装する

### 6. EdinetJQuantsMappingDao の実装
- [x] `db/EdinetJQuantsMappingDao.java` を実装する（upsert, findByEdinetCode, findAll）
- [x] `EdinetJQuantsMappingDaoTest.java` を実装する

### 7. JQuantsFetchInfoCommand の実装
- [x] `command/JQuantsFetchInfoCommand.java` を実装する
  - 上場銘柄情報取得 → jquants_listed_info upsert
  - secCode 照合によるマッピング自動生成 → edinet_jquants_mapping upsert
- [x] `Main.java` に `jquants-fetch-info` ルーティングを追加

### 8. JQuantsDailyPricesDao の実装
- [x] `db/JQuantsDailyPricesDao.java` を実装する（upsert, findByCodeAndDateRange）
- [x] `JQuantsDailyPricesDaoTest.java` を実装する

### 9. JQuantsFetchPricesCommand の実装
- [x] `command/JQuantsFetchPricesCommand.java` を実装する
- [x] `Main.java` に `jquants-fetch-prices` ルーティングを追加

### 10. JQuantsFinStatementsDao の実装
- [x] `db/JQuantsFinStatementsDao.java` を実装する（upsert, findByLocalCodeAndFiscalYear）
- [x] `JQuantsFinStatementsDaoTest.java` を実装する

### 11. JQuantsFetchFinsCommand の実装
- [x] `command/JQuantsFetchFinsCommand.java` を実装する
- [x] `Main.java` に `jquants-fetch-fins` ルーティングを追加

### 12. 全体動作確認
- [x] `mvn compile` が通ることを確認
- [x] `mvn test` が通ることを確認（97件 PASS）
