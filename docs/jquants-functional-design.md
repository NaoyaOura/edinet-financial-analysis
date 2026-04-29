# J-Quants 機能設計書

## 1. 概要

J-Quants API（日本取引所グループ提供）を利用して、財務データ・業種コード・株価データを取得する。
本プロジェクトでは **J-Quants を財務データの一次ソース** として利用し、EDINETはテキストデータ（有価証券報告書本文）の取得にのみ使用する。

- **プラン**: Standard
- **APIベースURL**: `https://api.jquants.com/v1`
- **認証方式**: リフレッシュトークン → IDトークン（Bearer認証）

---

## 2. J-Quants Standard プランで利用可能なデータ

| エンドポイント | 取得内容 | 本プロジェクトでの用途 |
|---|---|---|
| `GET /listed/info` | 上場銘柄一覧（企業名・業種コード・市場区分） | 業種コードの正規データとして利用 |
| `GET /prices/daily_quotes` | 日次株価（始値・高値・安値・終値・出来高・調整済み終値） | 株価リターンを分析の追加変数として利用 |
| `GET /fins/statements` | 四半期・通期財務情報 | **財務データの一次ソース** |
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

**利用目的**: J-QuantsのSector33Codeを業種分類の正規データとして使用する。

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

**利用目的**: 年度末・年度初の株価から年次リターンを算出し、キーワードスコアとの関係を検証する。

### 3-3. 財務情報（`/fins/statements`）

本プロジェクトの**財務データ一次ソース**。通期（Annual）のデータを使用する。

| フィールド名 | 内容 | 用途 |
|---|---|---|
| `LocalCode` | 銘柄コード | 企業識別子 |
| `DisclosedDate` | 開示日 | データ管理 |
| `TypeOfDocument` | 書類種別（Annual / Quarterly） | Annual のみ使用 |
| `NetSales` | 売上高 | 成長性・規模コントロール変数 |
| `OperatingProfit` | 営業利益 | 収益性（目的変数候補） |
| `OrdinaryProfit` | 経常利益 | 収益性 |
| `Profit` | 当期純利益 | 収益性（目的変数候補） |
| `TotalAssets` | 総資産 | 規模コントロール・ROA分母 |
| `Equity` | 純資産 | ROE分母・自己資本比率計算 |
| `CashFlowsFromOperatingActivities` | 営業キャッシュフロー | キャッシュ創出力 |
| `CashFlowsFromInvestingActivities` | 投資キャッシュフロー | 投資活動の代理変数 |
| `CashFlowsFromFinancingActivities` | 財務キャッシュフロー | 資金調達活動 |
| `CashAndEquivalents` | 現金及び現金同等物 | 流動性 |

**分析で算出する財務指標**:

| 指標名 | 計算式 | 分類 |
|---|---|---|
| 営業利益率 (%) | OperatingProfit / NetSales × 100 | 収益性（目的変数） |
| ROA (%) | Profit / TotalAssets × 100 | 収益性（目的変数） |
| ROE (%) | Profit / Equity × 100 | 収益性（目的変数） |
| 純利益率 (%) | Profit / NetSales × 100 | 収益性（目的変数） |
| 自己資本比率 (%) | Equity / TotalAssets × 100 | 安全性（目的変数 or コントロール） |
| log(売上高) | ln(NetSales) | 規模コントロール変数 |
| log(総資産) | ln(TotalAssets) | 規模コントロール変数（代替） |

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
    localCode                      TEXT,
    disclosedDate                  TEXT,
    typeOfDocument                 TEXT,              -- 'Annual' or 'Quarterly'
    fiscalYear                     INTEGER,           -- 会計年度（例: 2023）
    netSales                       REAL,
    operatingProfit                REAL,
    ordinaryProfit                 REAL,
    profit                         REAL,
    totalAssets                    REAL,
    equity                         REAL,
    cashFlowsFromOperating         REAL,              -- 営業キャッシュフロー
    cashFlowsFromInvesting         REAL,              -- 投資キャッシュフロー
    cashFlowsFromFinancing         REAL,              -- 財務キャッシュフロー
    cashAndEquivalents             REAL,              -- 現金及び現金同等物
    PRIMARY KEY (localCode, disclosedDate, typeOfDocument)
);
```

> **注意**: 既存DBへの追加はマイグレーション方式（ALTER TABLE）で対応する。
> カラムが既に存在する場合はエラーを無視する。

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
            └── jquants_listed_info (code)           — 業種コード
            └── jquants_daily_prices (code)          — 株価
            └── jquants_fin_statements (localCode)   — 財務データ（一次ソース）

keyword_scores (edinetCode, fiscalYear)
    └── edinet_jquants_mapping
            └── jquants_fin_statements (localCode, fiscalYear)  ← 分析用JOIN

jquants_listed_info (code)  ← sector33Code を分析に使用
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
- 取得するフィールド: NetSales, OperatingProfit, OrdinaryProfit, Profit, TotalAssets, Equity, CF4項目

---

## 7. 分析用データ結合クエリ例

### キーワードスコアと財務データの統合（メイン分析クエリ）

```sql
SELECT
    m.edinetCode,
    j.fiscalYear,
    jl.sector33Code,
    jl.sector33CodeName,
    j.netSales,
    j.operatingProfit,
    j.ordinaryProfit,
    j.profit,
    j.totalAssets,
    j.equity,
    j.cashFlowsFromOperating,
    j.cashFlowsFromInvesting,
    j.cashAndEquivalents,
    k.totalScore,
    k.genAiScore,
    k.aiScore,
    k.dxScore,
    k.documentLength
FROM edinet_jquants_mapping m
INNER JOIN jquants_fin_statements j ON m.jquantsCode = j.localCode
INNER JOIN keyword_scores k
    ON m.edinetCode = k.edinetCode AND j.fiscalYear = k.fiscalYear
LEFT JOIN jquants_listed_info jl ON m.jquantsCode = jl.code
WHERE j.fiscalYear BETWEEN 2022 AND 2025
  AND j.typeOfDocument = 'Annual'
ORDER BY m.edinetCode, j.fiscalYear;
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
| 財務データ | J-Quants を財務データの一次ソースとして利用（EDINETのXBRL解析は使用しない） |
| 分析期間 | 2022〜2025年度（4年間）。`typeOfDocument = 'Annual'`（通期）のみ使用 |
| マッピング精度 | `secCode` が登録されていない企業は自動マッピングが行えないため、手動登録が必要 |
| 株価取得範囲 | 分析対象企業（小売業・情報通信業）に絞って取得（全銘柄取得は不要） |

---

## 9. 今後の拡張可能性

- `export` コマンドへのJ-Quantsデータ統合（株価リターンを含む統合CSV出力）
- `analyze` コマンドへの株価リターン変数の追加
- 配当情報を考慮した総リターン計算
- Sector33分類の全業種への分析対象拡大
