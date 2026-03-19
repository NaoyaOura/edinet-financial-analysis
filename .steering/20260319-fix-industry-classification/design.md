# 設計：業種分類の修正（industryCategory UNKNOWN 問題）

## 変更方針

書類一覧の処理前に企業情報 API から `edinetCode` ↔ `industryCode` の全件マッピングを取得し、書類ループ内でそのマッピングを参照して `industryCategory` を分類する。

---

## EDINET 企業情報 API

### エンドポイント

```
GET /api/v2/companies.json?type=3&Subscription-Key={key}
```

### レスポンス形式（抜粋）

```json
{
  "metadata": { ... },
  "results": [
    {
      "edinetCode": "E12345",
      "secCode": "1234",
      "filerName": "テスト小売株式会社",
      "industryCode": "6100",
      ...
    }
  ]
}
```

- 1リクエストで全上場企業の情報を取得できる（数万件規模）
- `industryCode` は東証33業種コード（参考: https://jpx-jquants.com/ja/spec/eq-master/sector33code）

---

## 変更対象ファイル

### 1. `EdinetApiClient.java`（追加）

`fetchCompanyList()` メソッドを追加する。

```java
public JsonNode fetchCompanyList() throws IOException, InterruptedException {
    String url = String.format("%s/companies.json?type=3&Subscription-Key=%s", BASE_URL, apiKey);
    return getWithRetry(url);
}
```

### 2. `DocumentListFetcher.java`（修正）

`fetch()` メソッドの先頭で `apiClient.fetchCompanyList()` を呼び出し、`Map<String, String> companyIndustryMap`（edinetCode → industryCode）を構築する。書類ループ内では書類一覧レスポンスの `industryCode` フィールドの代わりにこのマップを参照する。

```
fetch(fiscalYear) の流れ:
  1. 企業情報APIを呼び出して edinetCode → industryCode マップを構築
  2. 年度ループ（既存の日付ループ処理）
     - 各書類の edinetCode をキーにマップから industryCode を取得
     - IndustryClassifier.classify(industryCode) で分類
```

### 3. `EdinetApiClientTest.java`（追加）

`fetchCompanyList` のレスポンスパース処理のテストを追加する。

---

## 変更しないもの

- `IndustryClassifier.java`（ロジック変更なし）
- `CompanyDao.java`（upsert シグネチャ変更なし）
- `FetchListCommand.java`（インターフェース変更なし）
- `--year`、`--force` オプションの挙動

---

## エラーハンドリング

- 企業情報APIの取得に失敗した場合は処理を中断し、エラーメッセージを表示する（書類一覧の処理は行わない）
- `companyIndustryMap` にキーが存在しない edinetCode は `industryCode = ""` として扱い、`UNKNOWN` に分類する（既存挙動と同じ）

---

## docs への反映

`docs/architecture.md` の EDINET API 連携仕様（§8）に企業情報エンドポイントを追記する。
