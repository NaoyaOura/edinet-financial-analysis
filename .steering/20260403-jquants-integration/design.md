# 設計：J-Quants API 連携実装

## パッケージ構成

既存パッケージ `jp.ac.example.xbrl` 配下に `jquants/` パッケージを追加する。

```
jquants/
├── JQuantsTokenManager.java   # リフレッシュトークン → IDトークン取得
└── JQuantsApiClient.java      # APIリクエスト（listed/info, prices, fins）
```

## 認証フロー

```
JQUANTS_REFRESH_TOKEN（環境変数）
    ↓ POST https://api.jquants.com/v1/token/auth_refresh
    ↓ Authorization: Bearer {refreshToken}
IDトークン（有効期間24時間、メモリのみ保持）
    ↓ Authorization: Bearer {idToken}
各APIエンドポイント
```

## API レスポンス構造

### `/v1/listed/info`
```json
{ "info": [ { "Code": "1234", "CompanyName": "...", "Sector33Code": "...", ... } ] }
```

### `/v1/token/auth_refresh`
```json
{ "idToken": "..." }
```

### `/v1/prices/daily_quotes?code=1234&from=2023-04-01&to=2024-03-31`
```json
{ "daily_quotes": [ { "Code": "1234", "Date": "2023-04-01", "Open": 100.0, ... } ] }
```

### `/v1/fins/statements?code=1234`
```json
{ "statements": [ { "LocalCode": "1234", "DisclosedDate": "2023-06-01", "TypeOfDocument": "FY", ... } ] }
```

## AppConfig の変更

- `jquantsRefreshToken` フィールドを追加（nullable、起動時エラーにしない）
- J-Quantsコマンド側で null チェックして例外スロー

## DatabaseManager の変更

`initializeSchema()` に以下を追加：

```sql
-- 4つの新テーブル（CREATE TABLE IF NOT EXISTS）
-- companiesテーブルへのsecCodeカラム追加（ALTER TABLE、duplicate column例外を握りつぶす）
```

## DAO の設計方針

既存の CompanyDao と同じパターン：
- コンストラクタで `Connection` を受け取る
- UPSERT は `INSERT ... ON CONFLICT ... DO UPDATE`
- record 型の内部クラスでレコードを表現

## Command の設計方針

既存の DownloadCommand と同じパターン：
- コンストラクタで `AppConfig` と `DatabaseManager` を受け取る
- `execute(String[] args)` で処理
- 引数は手動パース（`--year`, `--code` 等）

## jquants-fetch-info のマッピング自動生成

1. `jquants_listed_info` を全件 upsert
2. `companies` テーブルの `secCode` を持つ企業を全件取得
3. `secCode` と `jquants_listed_info.code` を照合（先頭4桁）して `edinet_jquants_mapping` に upsert

> J-Quantsの銘柄コードは5桁（末尾0）の場合がある。先頭4桁で照合する。

## jquants-fetch-prices の取得範囲

- `--year 2023` → 2023-04-01〜2024-03-31 の日次株価を取得
- `edinet_jquants_mapping` に登録済みの銘柄のみ対象
- 銘柄ごとに1リクエスト、レート制限対策でリクエスト間に300ms待機
