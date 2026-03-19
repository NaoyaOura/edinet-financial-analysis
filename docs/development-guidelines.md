# 開発ガイドライン

## 1. 開発フロー

新しい作業を始める前に必ず以下の順序で確認する。

1. `docs/functional-design.md` — 何を作るかの確認
2. `docs/architecture.md` — どう作るかの確認
3. 既存コードをGrepして類似実装を検索
4. `.steering/YYYYMMDD-タスク名/` を作成して作業計画を立てる
5. `tasklist.md` に従って実装・進捗を更新
6. テストを書いて動作確認

---

## 2. コーディング規約

### 2-1. 言語・スタイル

- Java 17 の機能を積極的に使用する（`var`、`record`、`switch` 式等）
- インデントはスペース4つ
- コメントは日本語で記述する
- クラス・メソッドの役割が自明でない場合はJavadocコメントを付ける

### 2-2. 命名規則

| 対象 | 規則 | 例 |
|---|---|---|
| クラス名 | UpperCamelCase | `KeywordScorer` |
| メソッド名 | lowerCamelCase | `calculateScore()` |
| 定数 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| パッケージ名 | すべて小文字 | `jp.ac.example.xbrl.text` |
| DBカラム名 | lowerCamelCase | `netSales` |

### 2-3. 例外処理

- チェック例外は呼び出し元で適切にハンドリングし、握り潰さない
- APIエラー・パースエラーは `task_progress` テーブルの `status` を `ERROR` に更新してから次の処理へ進む
- ユーザーへのエラーメッセージは日本語で出力する

---

## 3. テスト方針

### 3-1. テスト構成

- `src/test/java/` に `src/main/java/` と同じパッケージ構成でテストを配置する
- クラス名は `対象クラス名Test`（例: `KeywordScorerTest`）

### 3-2. テストの対象

| 対象 | テスト種別 | 方針 |
|---|---|---|
| `xbrl/`・`text/`・`analysis/` | 単体テスト | ロジックの正確性を検証。外部依存なし |
| `db/` DAOクラス | 統合テスト | インメモリSQLiteを使用して実DBで検証 |
| `edinet/` API通信 | 単体テスト | HTTPレスポンスをモックして検証 |
| `command/` サブコマンド | 統合テスト | 主要な正常系・異常系を網羅 |

### 3-3. テスト実行

```bash
# 全テスト
mvn test

# 特定クラスのみ
mvn test -Dtest=KeywordScorerTest

# 特定メソッドのみ
mvn test -Dtest=KeywordScorerTest#否定文脈のキーワードが除外されること
```

---

## 4. コミット・ブランチ運用

### 4-1. ブランチ戦略

| ブランチ | 用途 |
|---|---|
| `main` | リリース済みの安定版。直接コミット禁止 |
| `feature/タスク名` | 機能追加・バグ修正の作業ブランチ |

### 4-2. コミットメッセージ

以下のプレフィックスを使用する：

| プレフィックス | 用途 | 例 |
|---|---|---|
| `feat:` | 新機能の追加 | `feat: fetch-listコマンドを実装` |
| `fix:` | バグ修正 | `fix: XBRLパース時のnull参照を修正` |
| `docs:` | ドキュメントの更新 | `docs: architecture.mdにDB設計を追記` |
| `test:` | テストの追加・修正 | `test: KeywordScorerの単体テストを追加` |
| `refactor:` | 動作変更を伴わないリファクタリング | `refactor: FinancialDataExtractorを整理` |
| `chore:` | ビルド設定・依存関係の更新 | `chore: pom.xmlにCommons Mathを追加` |

### 4-3. コミット禁止事項

- `data/`・`output/`・`*.pdf` のコミット（`.gitignore` で除外済み）
- APIキーや認証情報を含むファイルのコミット
- テストが通らない状態でのコミット

---

## 5. 依存ライブラリの追加ルール

- 依存ライブラリを追加する場合は `pom.xml` の `<dependencies>` に追記する
- Javaの標準ライブラリで実現できる場合は外部ライブラリを追加しない
- 承認済みの依存ライブラリ：

| ライブラリ | バージョン | 用途 |
|---|---|---|
| `sqlite-jdbc` | 最新安定版 | SQLite接続 |
| `jackson-databind` | 最新安定版 | JSON処理 |
| `commons-math3` | 最新安定版 | 統計分析 |
| `junit-jupiter` | 最新安定版 | テスト |

---

## 6. セキュリティ

- APIキーは必ず環境変数 `EDINET_API_KEY` から取得し、ハードコード禁止
- `AppConfig.java` で起動時に環境変数の存在を検証し、未設定の場合は即時エラー終了する
- PRをマージする前にコード差分にAPIキーや認証情報が含まれていないか目視確認する
