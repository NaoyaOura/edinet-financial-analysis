# 設計書: 統計分析の前提変更

## 1. ドキュメント変更設計

### 1-1. `docs/functional-design.md`

#### 変更箇所: 3-2. 分析期間

```
変更前:
  ベースライン期間: 2019〜2022年度
  生成AI普及期: 2023〜2024年度
  ※ コロナ禍ダミー変数の考慮あり

変更後:
  統一分析期間: 2022〜2025年度（4年間）
  ※ コロナ禍は分析期間外のため、コロナダミー不要
```

#### 変更箇所: 4. データソース

```
変更前:
  - 4-1: EDINET API（財務データ + テキスト）
  - 4-2: 財務データ項目（XBRLタクソノミ対応）

変更後:
  - 4-1: EDINET API（テキストデータのみ）
  - 4-2: J-Quants API（財務データ一次ソース）
  - 4-3: 財務データ項目（J-Quants /fins/statements から取得）
```

#### 変更箇所: 6. 分析手法

```
変更前:
  - 固定変数：営業利益率(t+1)、totalScore(t)、log(売上高)、IT業種ダミー

変更後:
  - 変数探索モードを追加
  - 目的変数: 5種類の候補を全検証
  - キーワード変数: 4種類の候補を検証
  - コントロール変数: 複数の組み合わせを検証
  - 結果の比較表を出力（R²・p値）
```

#### 変更箇所: 8. 制約・前提条件

- コロナ禍の項目を削除
- J-Quants 財務データを一次ソースとして明記

---

### 1-2. `docs/jquants-functional-design.md`

#### 変更箇所: 1. 概要

```
変更前: EDINETから取得しづらいデータを「補完」する
変更後: 財務データの「一次ソース」として利用する
```

#### 変更箇所: 3-3. 財務情報テーブルフィールド

追加フィールド（J-Quants `/fins/statements` API対応）:

| 追加フィールド | 内容 |
|---|---|
| `CashFlowsFromOperatingActivities` | 営業キャッシュフロー |
| `CashFlowsFromInvestingActivities` | 投資キャッシュフロー |
| `CashFlowsFromFinancingActivities` | 財務キャッシュフロー |
| `CashAndEquivalents` | 現金及び現金同等物 |

#### 変更箇所: 5-1. jquants_fin_statementsテーブル

上記4列を追加。マイグレーション方式（ALTER TABLE）で対応。

#### 変更箇所: 8. 制約・前提条件

- 「EDINETのXBRL解析結果を正とし、J-Quantsは補完用」→ 「J-Quantsを財務データの一次ソースとして利用」

---

## 2. プログラム変更設計

### 2-1. DBスキーマ変更（DatabaseManager.java）

`jquants_fin_statements` テーブルへ4列追加:

```sql
ALTER TABLE jquants_fin_statements ADD COLUMN cashFlowsFromOperating   REAL;
ALTER TABLE jquants_fin_statements ADD COLUMN cashFlowsFromInvesting   REAL;
ALTER TABLE jquants_fin_statements ADD COLUMN cashFlowsFromFinancing   REAL;
ALTER TABLE jquants_fin_statements ADD COLUMN cashAndEquivalents       REAL;
```

既存テーブルへの追加はマイグレーション方式（既存カラムがある場合のエラーを無視）。

---

### 2-2. JQuantsFinStatementsDao.java

`FinStatementRecord` に4フィールド追加。
upsert / findBy のSQL・パラメータバインドを更新。

---

### 2-3. JQuantsFetchFinsCommand.java

J-Quants API レスポンスから追加フィールドを読み取り、DAO に渡す。

---

### 2-4. MergedRecord.java — フィールド刷新

#### 変更方針

- EDINET由来のフィールド名（`operatingIncome`, `profitLoss`, `assets`）→ J-Quants名（`operatingProfit`, `profit`, `totalAssets`）に統一
- `industryCategory`（RETAIL/IT/UNKNOWN）→ `sector33Code`（例: "6100"）と `sector33CodeName` に変更
- CFフィールドを追加

#### 変更後フィールド一覧

```java
public record MergedRecord(
    String edinetCode,
    int fiscalYear,
    String sector33Code,         // J-Quants Sector33Code（例: "6100"）
    String sector33CodeName,     // 業種名（例: "小売業"）
    Double netSales,
    Double operatingProfit,
    Double ordinaryProfit,
    Double profit,
    Double totalAssets,
    Double equity,
    Double cashFlowsFromOperating,
    Double cashFlowsFromInvesting,
    Double cashAndEquivalents,
    double totalScore,
    double genAiScore,
    double aiScore,
    double dxScore,
    int documentLength
)
```

#### 追加メソッド

| メソッド | 定義 |
|---|---|
| `operatingMargin()` | operatingProfit / netSales × 100 |
| `roa()` | profit / totalAssets × 100 |
| `roe()` | profit / equity × 100 |
| `netProfitMargin()` | profit / netSales × 100 |
| `equityRatio()` | equity / totalAssets × 100 |
| `logNetSales()` | ln(netSales) |
| `logTotalAssets()` | ln(totalAssets) |
| `isRetail()` | sector33Code.equals("6100") |
| `isIT()` | sector33Code.equals("5250") |
| `retailDummy()` | isRetail() ? 1 : 0 |
| `itDummy()` | isIT() ? 1 : 0 |

---

### 2-5. AnalysisDataLoader.java — SQLの刷新

#### 変更後SQL（概略）

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
ORDER BY m.edinetCode, j.fiscalYear
```

---

### 2-6. 変数探索フレームワーク（新規クラス）

#### VariableSpec.java（変数仕様）

```java
public record VariableSpec(
    String outcomeLabel,                          // 目的変数の表示名
    Function<MergedRecord, Double> outcome,       // 目的変数の計算関数
    String keywordLabel,                          // キーワード変数の表示名
    Function<MergedRecord, Double> keyword,       // キーワード変数の計算関数
    List<String> controlLabels,                   // コントロール変数の表示名リスト
    List<Function<MergedRecord, Double>> controls // コントロール変数の計算関数リスト
) {}
```

#### MultiModelAnalyzer.java（複数モデル比較）

- 複数の `VariableSpec` を受け取り、各仕様でラグ回帰を実行
- 結果を比較表として整形して出力：

```
=== 変数探索：ラグ回帰 比較 ===

# 目的変数: 営業利益率(t+1)
| キーワード変数  | コントロール変数        | n  | R²     | β(keyword) | p値     |
|----------------|------------------------|-----|--------|-----------|---------|
| totalScore     | log(売上高), 小売ダミー  | 80 | 0.1234 | 0.0023    | 0.0412** |
| genAiScore     | log(売上高), 小売ダミー  | 80 | 0.1100 | 0.0041    | 0.0823*  |
...
```

---

### 2-7. 既存分析クラスの変数探索対応

#### LagRegressionAnalyzer.java

- `analyze(List<MergedRecord>)` → `analyze(List<MergedRecord>, VariableSpec)` に変更
- 旧来の固定変数版はデフォルトSpecを使うオーバーロードで後方互換を確保

#### PanelDataAnalyzer.java

- `analyze(List<MergedRecord>, VariableSpec)` に対応
- within変換の対象を VariableSpec に応じて動的に変更

#### GroupComparator.java

- 比較する目的変数を `VariableSpec` から受け取れるように変更
- 既存の営業利益率・ROAに加え、ROE・純利益率も選択可能に

#### DifferenceInDifferences.java

- `DEFAULT_BASE_YEAR = 2022`, `DEFAULT_TREAT_YEAR = 2023` は変更なし（期間内に収まる）

---

### 2-8. AnalyzeCommand.java — 変数探索モード追加

```bash
# 既存コマンド（変更なし）
mvn exec:java -Dexec.args="analyze --type lag-regression"

# 新規: 変数探索モード
mvn exec:java -Dexec.args="analyze --type explore"
```

`explore` モードで `MultiModelAnalyzer` を呼び出し、全変数組み合わせの比較表を出力する。
