# 要件定義: 統計分析の前提変更

## 変更背景

研究の分析対象・データソース・変数設計を以下の方針に変更する。

---

## 変更要件

### 1. 分析期間の変更

| 項目 | 変更前 | 変更後 |
|---|---|---|
| ベースライン期間 | 2019〜2022年度 | —（廃止） |
| 生成AI普及期 | 2023〜2024年度 | —（廃止） |
| **統一分析期間** | —  | **2022〜2025年度（4年間）** |

- 2022年度を分析の起点とすることで、コロナ禍（2020〜2021年度）の直接的影響を除外できる
- 2025年度まで含めることで、生成AI普及期を含む4年間の時系列分析が可能となる
- コロナ禍ダミー変数は不要（分析期間外）

---

### 2. 財務データソースの変更

| 項目 | 変更前 | 変更後 |
|---|---|---|
| 財務データ（一次） | EDINET XBRL解析 | **J-Quants `/fins/statements`** |
| 業種分類 | EDINET XBRL + J-Quants補完 | J-Quants `Sector33Code`（既存） |
| テキストデータ | EDINET 有価証券報告書 | EDINET 有価証券報告書（変更なし） |

- EDINET XBRL解析は精度が不安定であるため、財務データをJ-Quantsに一本化
- テキスト分析（キーワードスコア算出）はEDINETを引き続き使用
- `financial_data` テーブル（EDINET）ではなく `jquants_fin_statements` テーブルを分析の一次ソースとする

---

### 3. 変数の探索的検証

分析に使用する変数を固定せず、複数の組み合わせを検証して最適な仕様を特定する。

#### 目的変数（被説明変数）の候補

| 変数名 | 定義 | 用途分類 |
|---|---|---|
| 営業利益率 | operatingProfit / netSales × 100 | 収益性（メイン） |
| ROA | profit / totalAssets × 100 | 収益性 |
| ROE | profit / equity × 100 | 収益性 |
| 純利益率 | profit / netSales × 100 | 収益性 |
| 自己資本比率 | equity / totalAssets × 100 | 安全性 |

#### キーワード変数（主説明変数）の候補

| 変数名 | 内容 |
|---|---|
| totalScore | 全カテゴリの重み付きスコア |
| genAiScore | 生成AI（ChatGPT/LLM等）スコアのみ |
| aiScore | AI全般スコアのみ |
| dxScore | DXスコアのみ |

#### コントロール変数の候補

| 変数名 | 定義 |
|---|---|
| log(売上高) | ln(netSales) |
| log(総資産) | ln(totalAssets) |
| 自己資本比率 | equity / totalAssets（目的変数でない場合） |
| 小売業ダミー | Sector33Code = "6100" なら 1 |
| 情報通信業ダミー | Sector33Code = "5250" なら 1 |

#### 変数選択の評価基準

- R²（決定係数）・調整済みR² による説明力の比較
- 各係数のt値・p値による統計的有意性
- 多重共線性のチェック（VIFは実装外、係数の符号逆転を確認）
- 理論的整合性（係数の方向が仮説と一致するか）

---

## 変更対象ファイル

### フェーズ1: Markdownドキュメント修正

1. `docs/functional-design.md`
2. `docs/jquants-functional-design.md`

### フェーズ2: Javaプログラム修正

1. `src/main/java/.../db/DatabaseManager.java` — `jquants_fin_statements` テーブルへの列追加マイグレーション
2. `src/main/java/.../db/JQuantsFinStatementsDao.java` — 新規列のCRUD対応
3. `src/main/java/.../command/JQuantsFetchFinsCommand.java` — API取得で新規フィールドを抽出
4. `src/main/java/.../analysis/MergedRecord.java` — J-Quants列名に対応、指標メソッド追加
5. `src/main/java/.../analysis/AnalysisDataLoader.java` — J-Quants主データソースへ変更
6. `src/main/java/.../analysis/VariableSpec.java` — 変数組み合わせ仕様クラス（新規）
7. `src/main/java/.../analysis/MultiModelAnalyzer.java` — 複数モデル比較分析クラス（新規）
8. `src/main/java/.../analysis/LagRegressionAnalyzer.java` — 変数探索対応
9. `src/main/java/.../analysis/PanelDataAnalyzer.java` — 変数探索対応
10. `src/main/java/.../analysis/GroupComparator.java` — 目的変数追加対応
11. `src/main/java/.../analysis/DifferenceInDifferences.java` — 年度範囲更新
12. `src/main/java/.../command/AnalyzeCommand.java` — 変数探索モード追加
