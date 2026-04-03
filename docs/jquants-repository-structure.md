# J-Quants リポジトリ構造定義書

## 1. 概要

J-Quants API連携に伴い追加するファイル・クラスの配置方針を定義する。
既存のEDINET関連コードとの分離を保ち、パッケージ構造を明確に区別する。

---

## 2. 追加するディレクトリ・ファイル構成

```
src/
├── main/
│   ├── java/jp/ac/example/xbrl/
│   │   ├── jquants/                          # J-Quants API連携（新規パッケージ）
│   │   │   ├── JQuantsApiClient.java         # APIクライアント（HTTPリクエスト）
│   │   │   └── JQuantsTokenManager.java      # IDトークン取得・管理
│   │   ├── command/
│   │   │   ├── JQuantsFetchInfoCommand.java  # jquants-fetch-info コマンド（新規）
│   │   │   ├── JQuantsFetchPricesCommand.java# jquants-fetch-prices コマンド（新規）
│   │   │   └── JQuantsFetchFinsCommand.java  # jquants-fetch-fins コマンド（新規）
│   │   ├── db/
│   │   │   ├── JQuantsListedInfoDao.java     # jquants_listed_info テーブル操作（新規）
│   │   │   ├── JQuantsDailyPricesDao.java    # jquants_daily_prices テーブル操作（新規）
│   │   │   ├── JQuantsFinStatementsDao.java  # jquants_fin_statements テーブル操作（新規）
│   │   │   └── EdinetJQuantsMappingDao.java  # edinet_jquants_mapping テーブル操作（新規）
│   │   ├── config/
│   │   │   └── AppConfig.java                # JQUANTS_REFRESH_TOKEN を追加（既存を修正）
│   │   └── Main.java                         # jquants-* コマンドのルーティング追加（既存を修正）
│   └── resources/
│       └── schema.sql                        # J-Quants用テーブルのDDL追加（既存を修正）
└── test/
    └── java/jp/ac/example/xbrl/
        ├── jquants/
        │   ├── JQuantsApiClientTest.java
        │   └── JQuantsTokenManagerTest.java
        └── db/
            ├── JQuantsListedInfoDaoTest.java
            ├── JQuantsDailyPricesDaoTest.java
            └── EdinetJQuantsMappingDaoTest.java
```

---

## 3. 各クラスの責務

### 3-1. `jquants/JQuantsTokenManager.java`

| 項目 | 内容 |
|---|---|
| 責務 | リフレッシュトークンを使ってIDトークンを取得する |
| 入力 | 環境変数 `JQUANTS_REFRESH_TOKEN` |
| 出力 | IDトークン文字列 |
| エンドポイント | `POST /v1/token/auth_refresh` |

```java
// 利用イメージ
String idToken = new JQuantsTokenManager(refreshToken).getIdToken();
```

### 3-2. `jquants/JQuantsApiClient.java`

| 項目 | 内容 |
|---|---|
| 責務 | J-Quants APIへのHTTPリクエスト送受信 |
| 依存 | `JQuantsTokenManager`（IDトークン取得） |
| 主なメソッド | `fetchListedInfo()`, `fetchDailyQuotes(code, from, to)`, `fetchFinStatements(code)` |

### 3-3. `command/JQuantsFetchInfoCommand.java`

| 項目 | 内容 |
|---|---|
| 責務 | `jquants-fetch-info` コマンドの実行制御 |
| 処理フロー | 1. 上場銘柄情報取得 → 2. `jquants_listed_info` 保存 → 3. `edinet_jquants_mapping` 自動生成 |

### 3-4. `command/JQuantsFetchPricesCommand.java`

| 項目 | 内容 |
|---|---|
| 責務 | `jquants-fetch-prices` コマンドの実行制御 |
| 処理フロー | 1. マッピング済み銘柄コード一覧取得 → 2. 年度範囲で株価取得 → 3. `jquants_daily_prices` 保存 |

### 3-5. `command/JQuantsFetchFinsCommand.java`

| 項目 | 内容 |
|---|---|
| 責務 | `jquants-fetch-fins` コマンドの実行制御 |
| 処理フロー | 1. マッピング済み銘柄コード一覧取得 → 2. 通期財務情報取得 → 3. `jquants_fin_statements` 保存 |

### 3-6. `db/EdinetJQuantsMappingDao.java`

| 項目 | 内容 |
|---|---|
| 責務 | `edinet_jquants_mapping` テーブルのCRUD |
| 主なメソッド | `upsert(edinetCode, jquantsCode)`, `findByEdinetCode(edinetCode)`, `findAll()` |

---

## 4. 既存ファイルへの変更

### 4-1. `config/AppConfig.java`

`JQUANTS_REFRESH_TOKEN` 環境変数の読み取りを追加する。

```java
// 追加フィールド
private final String jquantsRefreshToken;

// 追加ゲッター
public String getJquantsRefreshToken() { return jquantsRefreshToken; }
```

### 4-2. `Main.java`

`jquants-fetch-info`、`jquants-fetch-prices`、`jquants-fetch-fins` のルーティングを追加する。

### 4-3. `resources/schema.sql`（またはDatabaseManagerの初期化処理）

以下のDDLを追加する：

```sql
CREATE TABLE IF NOT EXISTS jquants_listed_info (
    code              TEXT PRIMARY KEY,
    companyName       TEXT,
    companyNameEn     TEXT,
    sector33Code      TEXT,
    sector33CodeName  TEXT,
    sector17Code      TEXT,
    sector17CodeName  TEXT,
    marketCode        TEXT,
    marketCodeName    TEXT,
    scaleCategory     TEXT,
    updatedAt         TEXT
);

CREATE TABLE IF NOT EXISTS jquants_daily_prices (
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

CREATE TABLE IF NOT EXISTS jquants_fin_statements (
    localCode         TEXT,
    disclosedDate     TEXT,
    typeOfDocument    TEXT,
    fiscalYear        INTEGER,
    netSales          REAL,
    operatingProfit   REAL,
    ordinaryProfit    REAL,
    profit            REAL,
    totalAssets       REAL,
    equity            REAL,
    PRIMARY KEY (localCode, disclosedDate, typeOfDocument)
);

CREATE TABLE IF NOT EXISTS edinet_jquants_mapping (
    edinetCode    TEXT PRIMARY KEY,
    jquantsCode   TEXT NOT NULL
);

-- companiesテーブルへのsecCode追加（既存DBへのマイグレーション）
ALTER TABLE companies ADD COLUMN secCode TEXT;
```

> `ALTER TABLE` は既存カラムが存在する場合エラーになるため、実装時は `IF NOT EXISTS` 相当の例外処理を追加すること。

---

## 5. 環境変数一覧（J-Quants追加分）

| 変数名 | 必須 | 説明 |
|---|---|---|
| `JQUANTS_REFRESH_TOKEN` | ✅ | J-Quantsリフレッシュトークン |

既存の環境変数（`EDINET_API_KEY`、`DB_PATH` 等）は変更なし。

---

## 6. コミット対象・除外対象

### コミットしないファイル・値

| 対象 | 理由 |
|---|---|
| `JQUANTS_REFRESH_TOKEN` の値 | 認証情報のため |
| `.env` ファイル | シークレットを含む可能性 |
| `data/` ディレクトリ配下の株価・財務データ | 大容量データのため |

`.gitignore` への追記は不要（既存の `data/` 除外設定で対応済みのはずだが要確認）。

---

## 7. 実装順序（推奨）

| ステップ | 内容 |
|---|---|
| 1 | `AppConfig` に `JQUANTS_REFRESH_TOKEN` を追加 |
| 2 | `JQuantsTokenManager` の実装・テスト |
| 3 | `JQuantsApiClient` の実装・テスト（モックで `/listed/info` のみ） |
| 4 | `jquants_listed_info` テーブルDDL追加・`JQuantsListedInfoDao` 実装 |
| 5 | `edinet_jquants_mapping` テーブルDDL追加・`EdinetJQuantsMappingDao` 実装 |
| 6 | `JQuantsFetchInfoCommand` 実装（info取得 + mapping自動生成） |
| 7 | `jquants_daily_prices` テーブルDDL追加・`JQuantsDailyPricesDao` 実装 |
| 8 | `JQuantsFetchPricesCommand` 実装 |
| 9 | `jquants_fin_statements` テーブルDDL追加・`JQuantsFinStatementsDao` 実装 |
| 10 | `JQuantsFetchFinsCommand` 実装 |
| 11 | `Main.java` のルーティング追加 |
