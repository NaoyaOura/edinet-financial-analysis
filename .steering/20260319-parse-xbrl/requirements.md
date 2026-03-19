# 要求内容：parse-xbrl コマンド実装

## 目的

`data/raw/{docId}/` に展開済みのXBRLファイルをパースして財務指標を抽出し、
`financial_data` テーブルに保存する。

## 要求事項

1. `task_progress` で `DOWNLOAD=DONE` かつ `PARSE_XBRL=PENDING/ERROR` の書類のみ処理する
2. 展開済みディレクトリから `.xbrl` 拡張子のファイルを探してDOMパースする
3. 連結財務諸表（contextRef に "Consolidated" を含む）を優先し、なければ個別を使用する
4. タクソノミ要素名から財務指標値を抽出する（architecture.md の対応表に従う）
5. 単位が千円・百万円の場合は円に正規化する
6. `financial_data` テーブルに保存する（同一企業・年度は上書き）
7. 成功時は `task_progress` の `PARSE_XBRL` を `DONE` に更新する
8. 失敗時は `ERROR` に更新して次の書類へ進む
9. `--force` で完了済みも再処理できる

## 完了条件

- `mvn exec:java -Dexec.args="parse-xbrl --year 2023"` が実行できる
- `financial_data` テーブルに財務指標が保存されること
- `mvn test` が通ること
