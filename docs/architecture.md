# 技術仕様書

## 1. システム概要

EDINET APIから有価証券報告書（XBRL・テキスト）を取得し、キーワードスコアの算出・財務データの収集・統計分析を行うCLIアプリケーション。

各処理フェーズは独立したコマンドとして分離されており、個別実行・途中再開・部分的な再実行が可能な設計とする。

---

## 2. 技術スタック

| 分類 | 採用技術 | 用途 |
|---|---|---|
| 言語 | Java 17 | メイン実装 |
| ビルド | Maven | 依存関係管理・ビルド |
| テスト | JUnit 5 | 単体・統合テスト |
| DB | SQLite（sqlite-jdbc） | 収集データの永続化・進捗管理 |
| XMLパース | Java標準（javax.xml） | XBRLファイルのパース |
| HTTP通信 | Java標準（java.net.http.HttpClient） | EDINET API呼び出し |
| JSON処理 | Jackson | EDINET APIレスポンスのデシリアライズ |
| 統計分析 | Apache Commons Math | 回帰分析・検定 |

---

## 3. CLIコマンド設計

すべての処理は独立したサブコマンドとして分離する。各コマンドは単独で実行可能であり、依存するデータがSQLiteに存在すれば任意の順序・組み合わせで実行できる。

```
mvn exec:java -Dexec.args="<コマンド> [オプション]"
```

### コマンド一覧

| コマンド | 処理内容 | 依存データ |
|---|---|---|
| `fetch-list` | EDINET書類一覧を取得してDBに保存 | なし |
| `download` | 書類ZIPをダウンロード・展開してローカル保存 | `fetch-list` 完了済みレコード |
| `parse-xbrl` | XBRLをパースして財務指標をDBに保存 | `download` 完了済みレコード |
| `score-keywords` | テキストからキーワードスコアを算出してDBに保存 | `download` 完了済みレコード |
| `analyze` | 統計分析を実行してレポートを出力 | `parse-xbrl` + `score-keywords` 完了済みレコード |
| `export` | SQLiteのデータをCSV出力 | 任意のDBデータ |
| `status` | 各フェーズの進捗状況を表示 | なし |

### 実行例

```bash
# 書類一覧を取得（2023年度・小売業＋IT企業）
mvn exec:java -Dexec.args="fetch-list --year 2023"

# ダウンロード（未取得分のみ）
mvn exec:java -Dexec.args="download --year 2023"

# 財務データのパース（未処理分のみ）
mvn exec:java -Dexec.args="parse-xbrl --year 2023"

# キーワードスコア算出（小売業のみ再実行）
mvn exec:java -Dexec.args="score-keywords --year 2023 --industry RETAIL"

# 分析実行
mvn exec:java -Dexec.args="analyze --type lag-regression"

# 進捗確認
mvn exec:java -Dexec.args="status"
```

---

## 4. 進捗管理・再実行設計

### 4-1. 基本方針

- 各コマンドは **処理単位（書類1件）ごとに進捗をSQLiteに記録** する
- 再実行時は `status = 'DONE'` のレコードをスキップし、未処理・失敗分のみ処理する
- `--force` オプションを指定した場合は、完了済みレコードも含めて再処理する

### 4-2. 進捗管理テーブル（`task_progress`）

| カラム名 | 型 | 説明 |
|---|---|---|
| `docId` | TEXT | EDINET書類ID |
| `task` | TEXT | タスク名（`DOWNLOAD` / `PARSE_XBRL` / `SCORE_KEYWORDS`） |
| `status` | TEXT | `PENDING` / `IN_PROGRESS` / `DONE` / `ERROR` |
| `errorMessage` | TEXT | エラー時のメッセージ（NULL可） |
| `updatedAt` | TEXT | 最終更新日時（ISO 8601） |

PRIMARY KEY: `(docId, task)`

### 4-3. 各タスクの再実行ルール

| タスク | 再実行の条件 | 部分更新の単位 |
|---|---|---|
| `download` | `status != 'DONE'` の書類のみ取得 | 書類1件（docId）単位 |
| `parse-xbrl` | `status != 'DONE'` の書類のみパース | 書類1件（docId）単位 |
| `score-keywords` | `status != 'DONE'` の書類のみスコア算出 | 書類1件（docId）単位 |
| `analyze` | 常に全データで再計算（結果は上書き） | 分析種別単位 |

### 4-4. `--force` オプション

```bash
# 特定企業の財務パース結果を強制的に再処理
mvn exec:java -Dexec.args="parse-xbrl --year 2023 --edinet-code E12345 --force"

# 全企業のキーワードスコアを再算出
mvn exec:java -Dexec.args="score-keywords --year 2023 --force"
```

---

## 5. モジュール構成

```
src/main/java/jp/ac/example/xbrl/
├── Main.java                        # エントリーポイント・サブコマンドのディスパッチ
├── command/
│   ├── FetchListCommand.java        # fetch-list サブコマンド
│   ├── DownloadCommand.java         # download サブコマンド
│   ├── ParseXbrlCommand.java        # parse-xbrl サブコマンド
│   ├── ScoreKeywordsCommand.java    # score-keywords サブコマンド
│   ├── AnalyzeCommand.java          # analyze サブコマンド
│   ├── ExportCommand.java           # export サブコマンド
│   └── StatusCommand.java           # status サブコマンド
├── config/
│   └── AppConfig.java               # 環境変数・設定値の読み込み（EDINET_API_KEY等）
├── edinet/
│   ├── EdinetApiClient.java         # EDINET API HTTP通信
│   ├── DocumentListFetcher.java     # 書類一覧取得（日付・業種フィルタ）
│   └── DocumentDownloader.java     # ZIPダウンロード・展開
├── xbrl/
│   ├── XbrlParser.java              # XBRLファイルのパース
│   └── FinancialDataExtractor.java  # タクソノミ要素名から財務指標値を抽出
├── text/
│   ├── SectionExtractor.java        # 有価証券報告書から対象セクションのテキスト抽出
│   ├── KeywordScorer.java           # キーワード密度スコアの算出
│   └── NegationFilter.java          # 否定文脈の除外処理
├── db/
│   ├── DatabaseManager.java         # SQLite接続・スキーマ初期化
│   ├── CompanyDao.java              # 企業マスタのCRUD
│   ├── FinancialDataDao.java        # 財務指標データのCRUD
│   ├── KeywordScoreDao.java         # キーワードスコアデータのCRUD
│   └── TaskProgressDao.java         # 進捗管理テーブルのCRUD
├── analysis/
│   ├── GroupComparator.java         # グループ比較（t検定・ANOVA）
│   ├── LagRegressionAnalyzer.java   # ラグ回帰分析
│   ├── DifferenceInDifferences.java # 差分の差分法（DiD）
│   └── PanelDataAnalyzer.java       # パネルデータ分析（固定効果モデル）
└── report/
    ├── CsvExporter.java             # CSV出力
    └── TextReporter.java            # テキストレポート出力
```

---

## 6. データフロー

```
[EDINET API]
    │
    ▼
[fetch-list]          書類一覧をDBに保存（document_list テーブル）
    │
    ▼
[download]            未取得書類のZIPをダウンロード・展開（./data/raw/{docId}/）
    │                 → task_progress: docId × DOWNLOAD = DONE
    │
    ├──▶ [parse-xbrl]       XBRLパース → financial_data テーブルに保存
    │                       → task_progress: docId × PARSE_XBRL = DONE
    │
    └──▶ [score-keywords]   テキスト分析 → keyword_scores テーブルに保存
                            → task_progress: docId × SCORE_KEYWORDS = DONE

[SQLite]
    │
    ▼
[analyze]             financial_data + keyword_scores を読み込んで統計分析
    │
    ▼
[export]              CSV / テキストレポート出力（./output/）
```

各フェーズは独立しており、失敗時は該当フェーズのみ再実行すれば続行できる。

---

## 7. データベース設計

### 7-1. テーブル一覧

#### `companies`（企業マスタ）

| カラム名 | 型 | 説明 |
|---|---|---|
| `edinetCode` | TEXT PRIMARY KEY | EDINETコード |
| `companyName` | TEXT | 企業名 |
| `industryCode` | TEXT | 日本標準産業分類コード |
| `industryCategory` | TEXT | 業種区分（`RETAIL` / `IT`） |

#### `document_list`（書類一覧）

| カラム名 | 型 | 説明 |
|---|---|---|
| `docId` | TEXT PRIMARY KEY | EDINET書類ID |
| `edinetCode` | TEXT | EDINETコード（FK: companies） |
| `fiscalYear` | INTEGER | 決算年度 |
| `submissionDate` | TEXT | 提出日 |
| `docDescription` | TEXT | 書類概要 |

#### `financial_data`（財務指標）

| カラム名 | 型 | 用途分類 | 説明 |
|---|---|---|---|
| `id` | INTEGER PRIMARY KEY | — | 自動採番 |
| `edinetCode` | TEXT | — | EDINETコード（FK: companies） |
| `fiscalYear` | INTEGER | — | 決算年度（例: 2023） |
| `netSales` | REAL | 成長性 | 売上高 |
| `grossProfit` | REAL | 収益性 | 売上総利益 |
| `operatingIncome` | REAL | 収益性 | 営業利益 |
| `ordinaryIncome` | REAL | 収益性 | 経常利益 |
| `profitLoss` | REAL | 収益性 | 当期純利益 |
| `assets` | REAL | 安全性 | 総資産 |
| `currentAssets` | REAL | 安全性 | 流動資産 |
| `currentLiabilities` | REAL | 安全性 | 流動負債 |
| `liabilities` | REAL | 安全性 | 負債合計 |
| `equity` | REAL | 安全性 | 自己資本 |
| `cashAndDeposits` | REAL | 安全性 | 現金及び預金 |
| `inventories` | REAL | 効率性 | 棚卸資産（小売業の在庫回転率算出に使用） |
| `sgaExpenses` | REAL | 効率性 | 販管費 |
| `personnelExpenses` | REAL | 効率性 | 人件費 |
| `numberOfEmployees` | INTEGER | 効率性 | 従業員数 |
| `researchAndDevelopment` | REAL | 投資 | 研究開発費（IT投資の代理変数） |
| `software` | REAL | 投資 | ソフトウェア資産（IT投資の代理変数） |
| `intangibleAssets` | REAL | 投資 | 無形固定資産合計 |
| `capitalExpenditure` | REAL | 投資 | 設備投資額 |
| `operatingCashFlow` | REAL | キャッシュフロー | 営業活動によるキャッシュフロー |
| `investingCashFlow` | REAL | キャッシュフロー | 投資活動によるキャッシュフロー |

#### `keyword_scores`（キーワードスコア）

| カラム名 | 型 | 説明 |
|---|---|---|
| `id` | INTEGER PRIMARY KEY | 自動採番 |
| `edinetCode` | TEXT | EDINETコード（FK: companies） |
| `fiscalYear` | INTEGER | 決算年度 |
| `genAiScore` | REAL | 生成AI系キーワード密度（重み3） |
| `aiScore` | REAL | AI全般キーワード密度（重み2） |
| `dxScore` | REAL | DX系キーワード密度（重み1） |
| `totalScore` | REAL | 加重合計キーワード密度 |
| `documentLength` | INTEGER | テキスト総文字数（正規化用） |

#### `task_progress`（進捗管理）

| カラム名 | 型 | 説明 |
|---|---|---|
| `docId` | TEXT | EDINET書類ID |
| `task` | TEXT | タスク名（`DOWNLOAD` / `PARSE_XBRL` / `SCORE_KEYWORDS`） |
| `status` | TEXT | `PENDING` / `IN_PROGRESS` / `DONE` / `ERROR` |
| `errorMessage` | TEXT | エラー時のメッセージ（NULL可） |
| `updatedAt` | TEXT | 最終更新日時（ISO 8601） |

PRIMARY KEY: `(docId, task)`

---

## 8. EDINET API連携仕様

### 8-1. 認証

- APIキーは環境変数 `EDINET_API_KEY` から取得する
- `AppConfig.java` で起動時に読み込み、未設定の場合は起動エラーとする
- ソースコードおよびリポジトリへのAPIキーの記述・コミットは禁止

```java
// AppConfig.java での読み込み例
String apiKey = System.getenv("EDINET_API_KEY");
if (apiKey == null || apiKey.isBlank()) {
    throw new IllegalStateException("環境変数 EDINET_API_KEY が設定されていません");
}
```

### 8-2. 主要エンドポイント

| 用途 | エンドポイント |
|---|---|
| 書類一覧取得 | `GET /api/v2/documents.json?date={date}&type=2&Subscription-Key={key}` |
| 書類取得（ZIP） | `GET /api/v2/documents/{docID}?type=5&Subscription-Key={key}` |

### 8-3. レート制限・エラーハンドリング

- APIレート制限に配慮し、リクエスト間に適切なウェイトを設ける
- HTTPステータスコードが4xx/5xxの場合は例外をスローし、呼び出し元でリトライ（最大3回）を制御する
- リトライ失敗時は `task_progress` の `status` を `ERROR` に更新して次の書類へ進む

---

## 9. XBRLパース仕様

- ZIPから展開されたXBRLファイル（`.xbrl`）をDOMパースする
- タクソノミ要素名（例: `jppfs_cor:NetSales`）に対応する値を抽出する
- 連結財務諸表（`consolidated`）を優先し、存在しない場合は個別財務諸表を使用する
- 単位は「円」で統一し、千円・百万円表記は正規化する

---

## 10. テキスト分析仕様

### 10-1. セクション抽出

- EDINET書類のHTMLまたはテキストファイルから対象セクションを正規表現で特定する
- 対象: 経営方針・サステナビリティ・MD&Aの3セクション

### 10-2. キーワードスコア算出

```
キーワード密度 = Σ(出現回数 × 重み) ÷ 総文字数 × 10,000
```

### 10-3. 否定文脈の除外

- 「導入していない」「検討していない」「予定はない」等の否定パターンにマッチした前後50文字以内のキーワードヒットを除外する

---

## 11. 環境変数一覧

| 変数名 | 必須 | 説明 |
|---|---|---|
| `EDINET_API_KEY` | 必須 | EDINET APIキー |
| `DB_PATH` | 任意 | SQLiteファイルパス（デフォルト: `./data/xbrl.db`） |
| `RAW_DATA_DIR` | 任意 | ダウンロードZIP展開先ディレクトリ（デフォルト: `./data/raw`） |
| `OUTPUT_DIR` | 任意 | CSV・レポートの出力先ディレクトリ（デフォルト: `./output`） |
