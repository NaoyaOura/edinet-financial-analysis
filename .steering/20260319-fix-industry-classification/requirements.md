# 要件定義：業種分類の修正（industryCategory UNKNOWN 問題）

## 背景・問題

`fetch-list` コマンド実行後、`companies` テーブルの `industryCategory` が全件 `UNKNOWN` になる。

### 根本原因

EDINET 書類一覧 API（`/api/v2/documents.json`）のレスポンスには `industryCode` フィールドが含まれていない。
そのため `doc.path("industryCode").asText("")` が常に空文字を返し、`IndustryClassifier.classify("")` が `UNKNOWN` を返す。

### 現状の誤った前提

`DocumentListFetcher` は書類一覧APIのレスポンスから `industryCode` が取得できると仮定して実装されているが、実際には取得できない。

---

## 要求内容

### 機能要件

1. EDINET 企業情報 API（`/api/v2/companies.json?type=3`）を使用して、全上場企業の `edinetCode` ↔ `industryCode` マッピングを取得する
2. `fetch-list` コマンド実行時に、書類一覧処理の前に企業情報を取得・参照することで `industryCategory` を正しく設定する
3. 企業情報は DB にキャッシュし、再実行時は再取得しない（`--force` オプションで強制再取得可能）
4. `RETAIL`（小売業 / 東証33業種コード `6100`）と `IT`（情報・通信業 / `5250`）のみ分類対象とし、その他は `UNKNOWN`

### 非機能要件

- 企業情報APIはリクエスト1回で全件を取得できるため、APIレート制限への影響は最小限
- 既存の `fetch-list` コマンドのインターフェース（`--year`、`--force` オプション）は変更しない

---

## 受け入れ条件

- `fetch-list` 実行後、`companies` テーブルに `industryCategory = 'RETAIL'` または `industryCategory = 'IT'` のレコードが存在すること
- `select * from companies where industryCategory <> 'UNKNOWN'` の結果が0件にならないこと
- 既存テストが引き続き全PASS すること
