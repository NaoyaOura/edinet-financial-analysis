# 要求内容：export コマンド実装

## 目的

SQLiteに蓄積されたデータをCSVとして出力し、
R・Python・Excel等の外部ツールで統計分析・可視化できるようにする。

## 要求事項

1. 出力する CSV の種類を `--type` オプションで指定できる
   - `financial`  : financial_data テーブル全列
   - `keywords`   : keyword_scores テーブル全列
   - `merged`     : financial_data + keyword_scores を (edinetCode, fiscalYear) でJOINした統合CSV（分析のメインデータ）
   - `all`（デフォルト）: 上記3種をすべて出力
2. `--year <年度>` でフィルタリング可能（未指定時は全年度）
3. 出力先は `--output <ディレクトリ>` で指定可能（デフォルト: `AppConfig.getOutputDir()`）
4. 出力先ディレクトリが存在しない場合は自動作成する
5. ファイル名は `financial_data_<年度>.csv` / `keyword_scores_<年度>.csv` / `merged_<年度>.csv`、
   全年度の場合は `_<年度>` サフィックスなし
6. NULLは空文字として出力する
7. ヘッダ行を1行目に出力する

## 完了条件

- `mvn exec:java -Dexec.args="export"` で3種のCSVが出力される
- `mvn exec:java -Dexec.args="export --type merged --year 2023"` で絞り込み出力できる
- `mvn test` が通ること
