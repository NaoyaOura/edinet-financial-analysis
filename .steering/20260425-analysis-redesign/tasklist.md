# タスクリスト: 統計分析の前提変更

## フェーズ1: Markdownドキュメント修正

- [x] **Task 1-1**: `docs/functional-design.md` の修正
  - 分析期間を2022〜2025年度（4年間）に変更
  - データソースセクションを「EDINET（テキスト）+ J-Quants（財務）」に再編
  - 変数探索の方針を6章（分析手法）に追記
  - コロナ禍ダミー変数の記述を削除
  - 制約・前提条件を更新

- [x] **Task 1-2**: `docs/jquants-functional-design.md` の修正
  - 概要を「補完」→「一次ソース」に変更
  - `/fins/statements` のフィールド一覧にCF4列を追加
  - `jquants_fin_statements` テーブルスキーマにCF4列を追加
  - 制約・前提条件のEDINET優先の記述を削除

---

## フェーズ2: Javaプログラム修正

- [x] **Task 2-1**: `DatabaseManager.java` — テーブルマイグレーション追加
  - `jquants_fin_statements` に CF4列を ALTER TABLE で追加

- [x] **Task 2-2**: `JQuantsFinStatementsDao.java` — CF4列のCRUD対応
  - `FinStatementRecord` に CF4フィールド追加
  - upsert / findBy SQLを更新

- [x] **Task 2-3**: `JQuantsFetchFinsCommand.java` — CF4列の取得対応
  - J-Quants API レスポンスから CF4フィールドを読み取り

- [x] **Task 2-4**: `MergedRecord.java` — フィールド刷新・指標メソッド追加
  - フィールド名をJ-Quants名に統一
  - `industryCategory` → `sector33Code`, `sector33CodeName` に変更
  - CFフィールドを追加
  - `roe()`, `netProfitMargin()`, `equityRatio()`, `logTotalAssets()`, `retailDummy()`, `itDummy()` を追加

- [x] **Task 2-5**: `AnalysisDataLoader.java` — SQLをJ-Quants主ソースに変更
  - `financial_data` → `jquants_fin_statements` に切り替え
  - マッピングテーブル経由でキーワードスコアと結合
  - 年度フィルタ: 2022〜2025, typeOfDocument = 'Annual'

- [x] **Task 2-6**: `VariableSpec.java` — 変数仕様クラス（新規作成）

- [x] **Task 2-7**: `MultiModelAnalyzer.java` — 複数モデル比較クラス（新規作成）

- [x] **Task 2-8**: `LagRegressionAnalyzer.java` — VariableSpec 対応

- [x] **Task 2-9**: `PanelDataAnalyzer.java` — VariableSpec 対応

- [x] **Task 2-10**: `GroupComparator.java` — 目的変数追加対応

- [x] **Task 2-11**: `AnalyzeCommand.java` — `explore` モード追加

---

## 完了基準

- `mvn compile` がエラーなく通ること
- `mvn test` で既存テストが引き続きパスすること
- `analyze --type explore` を実行したとき、変数探索の比較表が出力されること
