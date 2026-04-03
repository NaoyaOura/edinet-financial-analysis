# コマンドリファレンス

## EDINET系コマンド

### fetch-list

EDINET API から書類一覧を取得して DB に登録します。

```bash
mvn exec:java -Dexec.args="fetch-list --year <年度>"
```

| オプション | 必須 | 説明 |
|---|---|---|
| `--year <年度>` | ✅ | 対象年度（例: `2023` → 2023-04-01〜2024-03-31） |

---

### download

書類 ZIP をダウンロードして展開します。完了済みはスキップします。

```bash
mvn exec:java -Dexec.args="download [--year <年度>] [--edinet-code <コード>] [--force]"
```

| オプション | 必須 | 説明 |
|---|---|---|
| `--year <年度>` | | 対象年度に絞り込み |
| `--edinet-code <コード>` | | 特定企業のみ対象（例: `E01234`） |
| `--force` | | 完了済みも再ダウンロード |

---

### parse-xbrl

XBRL をパースして財務指標を DB に保存します。同時に業種コードを XBRL から取得して企業マスタを更新します。

```bash
mvn exec:java -Dexec.args="parse-xbrl [--year <年度>] [--edinet-code <コード>] [--force]"
```

| オプション | 必須 | 説明 |
|---|---|---|
| `--year <年度>` | | 対象年度に絞り込み |
| `--edinet-code <コード>` | | 特定企業のみ対象 |
| `--force` | | 完了済みも再パース |

---

### score-keywords

XBRL テキストからキーワードスコアを算出して DB に保存します。

```bash
mvn exec:java -Dexec.args="score-keywords [--year <年度>] [--edinet-code <コード>] [--force]"
```

| オプション | 必須 | 説明 |
|---|---|---|
| `--year <年度>` | | 対象年度に絞り込み |
| `--edinet-code <コード>` | | 特定企業のみ対象 |
| `--force` | | 完了済みも再算出 |

---

### analyze

統計分析を実行します。

```bash
mvn exec:java -Dexec.args="analyze [--type <分析種別>] [--year <年度>]"
```

| オプション | 必須 | 説明 |
|---|---|---|
| `--type <種別>` | | `group-comparison` / `lag-regression` / `did` / `panel` / `all`（デフォルト: `all`） |
| `--year <年度>` | | 特定年度のみで分析 |

```bash
mvn exec:java -Dexec.args="analyze --type group-comparison"  # グループ比較
mvn exec:java -Dexec.args="analyze --type lag-regression"    # ラグ回帰分析（メイン）
mvn exec:java -Dexec.args="analyze --type did"               # 差分の差分法
mvn exec:java -Dexec.args="analyze --type panel"             # 固定効果モデル
```

---

### export

分析結果を CSV に出力します。

```bash
mvn exec:java -Dexec.args="export [--type <出力種別>] [--year <年度>] [--output <出力先>]"
```

| オプション | 必須 | 説明 |
|---|---|---|
| `--type <種別>` | | `financial` / `keywords` / `merged` / `all`（デフォルト: `all`） |
| `--year <年度>` | | 対象年度に絞り込み |
| `--output <パス>` | | 出力先ディレクトリ（デフォルト: `./output`） |

```bash
mvn exec:java -Dexec.args="export --type merged --year 2023 --output ./results"
```

---

### status

各タスクの処理進捗を表示します。

```bash
mvn exec:java -Dexec.args="status"
```

---

## J-Quants系コマンド

### jquants-fetch-info

J-Quants API から上場銘柄情報を取得し、業種コード等を DB に保存します。
合わせて EDINET コードとのマッピングを自動生成します。

```bash
mvn exec:java -Dexec.args="jquants-fetch-info"
```

> 事前に `JQUANTS_REFRESH_TOKEN` 環境変数の設定が必要です。

---

### jquants-fetch-prices

分析対象企業の日次株価を取得して DB に保存します。

```bash
mvn exec:java -Dexec.args="jquants-fetch-prices --year <年度> [--code <銘柄コード>]"
```

| オプション | 必須 | 説明 |
|---|---|---|
| `--year <年度>` | ✅ | 対象年度（例: `2023` → 2023-04-01〜2024-03-31） |
| `--code <コード>` | | 特定銘柄のみ対象（例: `7203`） |

---

### jquants-fetch-fins

J-Quants API から通期財務情報を取得して DB に保存します。

```bash
mvn exec:java -Dexec.args="jquants-fetch-fins --year <年度> [--code <銘柄コード>]"
```

| オプション | 必須 | 説明 |
|---|---|---|
| `--year <年度>` | ✅ | 対象年度 |
| `--code <コード>` | | 特定銘柄のみ対象 |
