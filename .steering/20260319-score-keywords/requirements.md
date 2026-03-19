# 要求内容：score-keywords コマンド実装

## 目的

展開済み書類からテキストを抽出し、生成AI・AI全般・DXの各キーワードスコアを算出して
`keyword_scores` テーブルに保存する。

## 要求事項

1. `task_progress` で `DOWNLOAD=DONE` かつ `SCORE_KEYWORDS=PENDING/ERROR` の書類のみ処理する
2. HTMLファイルから対象3セクションのテキストを抽出する
   - 経営方針・経営環境及び対処すべき課題等
   - サステナビリティに関する考え方及び取組
   - 経営者による財政状態、経営成績及びキャッシュ・フローの状況の分析（MD&A）
3. 否定文脈（「導入していない」「検討していない」等）の前後50文字のキーワードを除外する
4. キーワード密度を算出する
   ```
   キーワード密度 = Σ(出現回数 × 重み) ÷ 総文字数 × 10,000
   ```
5. `keyword_scores` テーブルに保存する（同一企業・年度は上書き）
6. 成功時は `task_progress` の `SCORE_KEYWORDS` を `DONE` に更新する
7. `--force` で完了済みも再処理できる

## 完了条件

- `mvn exec:java -Dexec.args="score-keywords --year 2023"` が実行できる
- `keyword_scores` テーブルにスコアが保存されること
- `mvn test` が通ること
