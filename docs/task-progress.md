# task_progress テーブル ステータス遷移

## 概要

`task_progress` テーブルは、各書類（`docId`）×タスク（`task`）の処理状態を管理します。
処理の途中で失敗しても、再実行時に完了済みをスキップして続きから処理できます。

## タスク一覧

| タスク名 | 登録コマンド | 処理コマンド |
|---|---|---|
| `DOWNLOAD` | `fetch-list` | `download` |
| `PARSE_XBRL` | `download`（DONE時） | `parse-xbrl` |
| `SCORE_KEYWORDS` | `download`（DONE時） | `score-keywords` |

> `PARSE_XBRL` と `SCORE_KEYWORDS` のレコードは `download` が DONE になった時点で `PENDING` として自動登録されます。

## ステータス定義

| ステータス | 意味 |
|---|---|
| `PENDING` | 未処理（処理待ち） |
| `IN_PROGRESS` | 処理中 |
| `DONE` | 正常完了 |
| `ERROR` | エラー終了（`errorMessage` に詳細が格納される） |

## ステータス遷移図

### DOWNLOAD

```
fetch-list 実行
     │
     ▼
 PENDING ──────────────────────────────────────────┐
     │                                              │
     │ download 実行                                │ fetch-list 再実行
     ▼                                              │（insertIfAbsent のため上書きしない）
IN_PROGRESS                                        │
     │                                             ◀┘
     ├─── 成功 ───▶ DONE
     │
     └─── 失敗 ───▶ ERROR ──▶ download 再実行で IN_PROGRESS に戻る
```

### PARSE_XBRL / SCORE_KEYWORDS

```
download が DONE になった時点で自動登録（insertIfAbsent）
     │
     ▼
 PENDING
     │
     │ parse-xbrl / score-keywords 実行
     ▼
IN_PROGRESS
     │
     ├─── 成功 ───▶ DONE
     │
     └─── 失敗 ───▶ ERROR ──▶ 再実行で IN_PROGRESS に戻る
```

## 再実行の挙動

| 状況 | `--force` なし | `--force` あり |
|---|---|---|
| `PENDING` の書類 | 処理する | 処理する |
| `IN_PROGRESS` の書類 | 処理する | 処理する |
| `DONE` の書類 | **スキップ** | 処理する（上書き） |
| `ERROR` の書類 | 処理する | 処理する |

## 進捗確認

```bash
mvn exec:java -Dexec.args="status"
```

出力例：

```
=== 処理進捗 ===
タスク                ステータス      件数
--------------------------------------------------
DOWNLOAD             DONE            1234
DOWNLOAD             ERROR           5

PARSE_XBRL           DONE            1200
PARSE_XBRL           ERROR           34
PARSE_XBRL           PENDING         5

SCORE_KEYWORDS       DONE            1200
SCORE_KEYWORDS       PENDING         39
```

## ERROR 時の対応

ERROR になった書類は `errorMessage` カラムに原因が格納されます。
SQLite で直接確認する場合：

```sql
SELECT docId, task, errorMessage
FROM task_progress
WHERE status = 'ERROR';
```

原因を修正後、該当コマンドを再実行すると ERROR 書類のみ再処理されます（`--force` 不要）。
