# J-Quants 機能設計書

## 1. 概要

J-Quants API（日本取引所グループ提供）を利用して、EDINETから取得しづらいデータ（業種コード・株価等）を補完する。
取得したデータはEDINETデータとは独立したテーブルに格納し、`edinetCode` ↔ 銘柄コードのマッピングを介して結合して利用する。

- **プラン**: Standard
- **APIベースURL**: `https://api.jquants.com/v1`
- **認証方式**: リフレッシュトークン → IDトークン（Bearer認証）

---

## 2. J-Quants Standard プランで利用可能なデータ

| エンドポイント | 取得内容 | 本プロジェクトでの用途 |
|---|---|---|
| `GET /listed/info` | 上場銘柄一覧（企業名・業種コード・市場区分） | 業種コードの信頼できる取得元として利用 |
| `GET /prices/daily_quotes` | 日次株価（始値・高値・安値・終値・出来高・調整済み終値） | 株価リターンを分析の追加変数として利用 |
| `GET /fins/statements` | 四半期・通期財務情報 | EDINETの年次財務データの補完・検証用 |
| `GET /fins/dividend` | 配当情報 | （将来拡張用） |
| `GET /markets/trading_calendar` | 取引カレンダー | 株価集計の営業日基準として利用 |

---

## 3. 取得データの詳細

### 3-1. 上場銘柄情報（`/listed/info`）

| フィールド名 | 内容 | 備考 |
|---|---|---|
| `Code` | 銘柄コード（4桁） | J-Quantsの主キー |
| `CompanyName` | 企業名（日本語） | |
| `CompanyNameEnglish` | 企業名（英語） | |
| `Sector33Code` | 東証33業種コード | `6100`=小売業、`5250`=情報・通信業 |
| `Sector33CodeName` | 東証33業種名 | |
| `Sector17Code` | 東証17業種コード | |
| `Sector17CodeName` | 東証17業種名 | |
| `MarketCode` | 市場区分コード | プライム・スタンダード・グロース等 |
| `MarketCodeName` | 市場区分名 | |
| `ScaleCategory` | 規模区分 | |

**利用目的**: EDINETのXBRLから業種コードを取得する方式は解析精度に依存するため、J-Quantsの業種コードを正規データとして採用する。

### 3-2. 日次株価（`/prices/daily_quotes`）

| フィールド名 | 内容 |
|---|---|
| `Code` | 銘柄コード |
| `Date` | 取引日（YYYY-MM-DD） |
| `Open` | 始値 |
| `High` | 高値 |
| `Low` | 安値 |
| `Close` | 終値 |
| `Volume` | 出来高 |
| `AdjustmentClose` | 調整済み終値（株式分割・併合を考慮） |
| `AdjustmentVolume` | 調整済み出来高 |

**利用目的**: 年度末・年度初の株価から年次リターンを算出し、キーワードスコアとの関係を検証する（`functional-design.md` 9章「株価データとの連携」に対応）。

### 3-3. 財務情報（`/fins/statements`）

| フィールド名 | 内容 |
|---|---|
| `LocalCode` | 銘柄コード |
| `DisclosedDate` | 開示日 |
| `TypeOfDocument` | 書類種別（通期・四半期） |
| `NetSales` | 売上高 |
| `OperatingProfit` | 営業利益 |
| `OrdinaryProfit` | 経常利益 |
| `Profit` | 当期純利益 |
| `TotalAssets` | 総資産 |
| `Equity` | 純資産 |

**利用目的**: EDINETのXBRL解析で取得できなかった財務項目の補完、またはXBRL解析値の検証用。

---

## 4. 認証仕様

J-Quantsは2段階認証を採用する。

```
1. リフレッシュトークン（環境変数から取得）
      ↓ POST /v1/token/auth_refresh
2. IDトークン（有効期間: 24時間）
      ↓ Authorization: Bearer {IDトークン}
3. 各APIエンドポイントへのリクエスト
```

### 環境変数

| 変数名 | 説明 | 取得方法 |
|---|---|---|
| `JQUANTS_REFRESH_TOKEN` | J-Quantsリフレッシュトークン | J-Quantsダッシュボードまたはメール・パスワードで初回取得 |

> **注意**: リフレッシュトークンをソースコードやコミット履歴に含めないこと。

### IDトークンの管理方針

- 毎回リフレッシュトークンからIDトークンを取得する（シンプルな実装）
- APIリクエスト前にIDトークンを都度取得し、メモリ上にのみ保持する

---

## 5. データベース設計

EDINETのテーブルとは独立したテーブルに格納し、マッピングテーブルで結合する。

### 5-1. 新規テーブル

#### `jquants_listed_info` — 上場銘柄情報

```sql
CREATE TABLE jquants_listed_info (
    code              TEXT PRIMARY KEY,  -- 銘柄コード（4桁）
    companyName       TEXT,
    companyNameEn     TEXT,
    sector33Code      TEXT,
    sector33CodeName  TEXT,
    sector17Code      TEXT,
    sector17CodeName  TEXT,
    marketCode        TEXT,
    marketCodeName    TEXT,
    scaleCategory     TEXT,
    updatedAt         TEXT               -- 最終取得日時
);
```

#### `jquants_daily_prices` — 日次株価

```sql
CREATE TABLE jquants_daily_prices (
    code              TEXT,
    date              TEXT,
    open              REAL,
    high              REAL,
    low               REAL,
    close             REAL,
    volume            REAL,
    adjustmentClose   REAL,
    adjustmentVolume  REAL,
    PRIMARY KEY (code, date)
);
```

#### `jquants_fin_statements` — 財務情報（J-Quants）

```sql
CREATE TABLE jquants_fin_statements (
    localCode         TEXT,
    disclosedDate     TEXT,
    typeOfDocument    TEXT,              -- 'Annual' or 'Quarterly'
    fiscalYear        INTEGER,           -- 会計年度（例: 2023）
    netSales          REAL,
    operatingProfit   REAL,
    ordinaryProfit    REAL,
    profit            REAL,
    totalAssets       REAL,
    equity            REAL,
    PRIMARY KEY (localCode, disclosedDate, typeOfDocument)
);
```

#### `edinet_jquants_mapping` — EDINETコード ↔ 銘柄コード マッピング

```sql
CREATE TABLE edinet_jquants_mapping (
    edinetCode    TEXT PRIMARY KEY,      -- EDINET企業コード（例: E01234）
    jquantsCode   TEXT NOT NULL,         -- J-Quants銘柄コード（例: 7203）
    FOREIGN KEY (jquantsCode) REFERENCES jquants_listed_info(code)
);
```

### 5-2. 既存テーブルへの追加

#### `companies` テーブルへの `secCode` カラム追加

EDINET API の書類一覧レスポンスには証券コード（`secCode`）が含まれる。
このカラムを `companies` テーブルに追加することで、J-Quantsとのマッピング作成を自動化できる。

```sql
ALTER TABLE companies ADD COLUMN secCode TEXT;
```

> `secCode` はEDINET API v2 の書類一覧（`/api/v2/documents.json`）の各書類エントリに含まれる。

### 5-3. テーブルの結合関係

```
companies (edinetCode)
    └── edinet_jquants_mapping (edinetCode → jquantsCode)
            └── jquants_listed_info (code)
            └── jquants_daily_prices (code)
            └── jquants_fin_statements (localCode)

financial_data (edinetCode, fiscalYear)  ←─ JOIN ─→  jquants_fin_statements (jquantsCode, fiscalYear)
keyword_scores (edinetCode, fiscalYear)  ←─ JOIN ─→  jquants_daily_prices   (jquantsCode, date)
```

---

## 6. 新規コマンド設計

### `jquants-fetch-info` — 上場銘柄情報の取得

```bash
mvn exec:java -Dexec.args="jquants-fetch-info"
```

- J-Quants `/listed/info` から全上場銘柄情報を取得し、`jquants_listed_info` テーブルに保存（upsert）
- 合わせて `edinet_jquants_mapping` を自動生成（`companies.secCode` と `jquants_listed_info.code` を照合）

### `jquants-fetch-prices` — 株価データの取得

```bash
mvn exec:java -Dexec.args="jquants-fetch-prices --year <年度> [--code <銘柄コード>]"
```

| オプション | 必須 | 説明 |
|---|---|---|
| `--year <年度>` | ✅ | 対象年度（例: `2023` → 2023-04-01〜2024-03-31） |
| `--code <コード>` | | 特定銘柄のみ対象 |

- `edinet_jquants_mapping` に登録済みの銘柄コードに対して株価を取得
- `jquants_daily_prices` テーブルに保存（upsert）

### `jquants-fetch-fins` — 財務情報の取得

```bash
mvn exec:java -Dexec.args="jquants-fetch-fins --year <年度> [--code <銘柄コード>]"
```

- J-Quants `/fins/statements` から通期財務情報を取得し、`jquants_fin_statements` テーブルに保存

---

## 7. EDINETデータとの結合利用例

### 業種コードの補完

```sql
SELECT c.edinetCode, j.sector33Code, j.sector33CodeName
FROM companies c
JOIN edinet_jquants_mapping m ON c.edinetCode = m.edinetCode
JOIN jquants_listed_info j ON m.jquantsCode = j.code;
```

### キーワードスコアと株価リターンの分析用データ

```sql
SELECT
    k.edinetCode,
    k.fiscalYear,
    k.totalScore,
    -- 年度末の調整済み終値
    p_end.adjustmentClose   AS priceEnd,
    -- 前年度末の調整済み終値
    p_start.adjustmentClose AS priceStart
FROM keyword_scores k
JOIN edinet_jquants_mapping m ON k.edinetCode = m.edinetCode
JOIN jquants_daily_prices p_end
    ON m.jquantsCode = p_end.code AND p_end.date = (
        SELECT MAX(date) FROM jquants_daily_prices
        WHERE code = m.jquantsCode AND date <= (k.fiscalYear || '-03-31')
    )
JOIN jquants_daily_prices p_start
    ON m.jquantsCode = p_start.code AND p_start.date = (
        SELECT MAX(date) FROM jquants_daily_prices
        WHERE code = m.jquantsCode AND date <= ((k.fiscalYear - 1) || '-03-31')
    );
```

---

## 8. 制約・前提条件

| 項目 | 内容 |
|---|---|
| APIキー | `JQUANTS_REFRESH_TOKEN` 環境変数から取得。ソースコードへの直接記述・コミット禁止 |
| プラン | J-Quants Standard プラン（Free プランでは取得期間・件数に制限あり） |
| マッピング精度 | `secCode` が登録されていない企業は自動マッピングが行えないため、手動登録が必要 |
| 株価取得範囲 | 分析対象企業（小売業・情報通信業）に絞って取得（全銘柄取得は不要） |
| 財務データの優先度 | EDINETのXBRL解析結果を正とし、J-Quantsの財務情報は補完・検証用として扱う |

---

## 9. 今後の拡張可能性

- `export` コマンドへのJ-Quantsデータ統合（株価リターンを含む統合CSV出力）
- `analyze` コマンドへの株価リターン変数の追加
- 配当情報を考慮した総リターン計算
