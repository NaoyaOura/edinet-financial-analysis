# タスクリスト：業種分類の修正（industryCategory UNKNOWN 問題）

## 進捗凡例
- [ ] 未着手
- [x] 完了

---

## タスク

### 1. `EdinetApiClient.java` に企業情報取得メソッドを追加
- [x] `fetchCompanyList()` メソッドを追加する（`/api/v2/companies.json?type=3` を呼び出す）

### 2. `EdinetApiClientTest.java` にテストを追加
- [x] 企業情報APIレスポンスの `results` 配列が正しくパースできることを検証するテストを追加する
- [x] `mvn test` が通ることを確認する

### 3. `DocumentListFetcher.java` を修正
- [x] `fetch()` メソッドの先頭で `fetchCompanyList()` を呼び出し、`Map<String, String>` を構築する
- [x] 書類ループ内で `doc.path("industryCode")` の代わりにマップを参照するよう変更する

### 4. `docs/architecture.md` を更新
- [x] §8「EDINET API 連携仕様」の主要エンドポイント表に企業情報 API を追記する

### 5. 動作確認
- [x] `mvn compile` が通ることを確認する
- [x] `mvn test` が全件 PASS することを確認する（76テスト）
- [ ] `fetch-list` 実行後に `select * from companies where industryCategory <> 'UNKNOWN'` の結果が0件でないことを確認する
