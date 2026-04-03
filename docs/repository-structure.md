# リポジトリ構造定義書

## 1. ディレクトリ構成

```
edinet-financial-test-analysis/
│
├── README.md                        # プロジェクト概要・セットアップ手順
├── .gitignore                       # Git除外設定（APIキー・生データ等）
├── pom.xml                          # Maven設定・依存関係定義
│
├── docs/                            # 永続的ドキュメント（設計・仕様）
│   ├── functional-design.md             # 機能設計書
│   ├── jquants-functional-design.md     # J-Quants 機能設計書
│   ├── architecture.md                  # 技術仕様書
│   ├── repository-structure.md          # 本ファイル
│   ├── jquants-repository-structure.md  # J-Quants リポジトリ構造定義書
│   ├── development-guidelines.md        # 開発ガイドライン
│   ├── command-reference.md             # コマンドリファレンス
│   └── ideas/                           # ブレインストーミング・調査メモ
│       └── brainstorm-YYYYMMDD.md
│
├── .steering/                       # 作業単位のドキュメント（タスク管理）
│   └── YYYYMMDD-タスク名/
│       ├── requirements.md          # 今回の作業の要求内容
│       ├── design.md                # 変更内容の設計
│       └── tasklist.md              # タスクリスト・進捗
│
├── src/
│   ├── main/
│   │   └── java/jp/ac/example/xbrl/
│   │       ├── Main.java
│   │       ├── command/             # CLIサブコマンド
│   │       ├── config/              # 環境変数・設定値
│   │       ├── edinet/              # EDINET API通信
│   │       ├── xbrl/                # XBRLパース・財務データ抽出
│   │       ├── text/                # テキスト分析・キーワードスコア
│   │       ├── db/                  # SQLite DAO層
│   │       ├── analysis/            # 統計分析
│   │       └── report/              # CSV・レポート出力
│   └── test/
│       └── java/jp/ac/example/xbrl/ # JUnit テスト（src/mainと同じパッケージ構成）
│
├── data/                            # ※ .gitignore 対象（コミット禁止）
│   ├── raw/                         # EDINETからダウンロードした書類（ZIP展開後）
│   │   └── {docId}/                 # 書類ID単位で格納
│   └── xbrl.db                      # SQLiteデータベース
│
└── output/                          # ※ .gitignore 対象（コミット禁止）
    ├── *.csv                        # 分析結果CSV
    └── *.txt                        # テキストレポート
```

---

## 2. 各ディレクトリの役割

### `docs/`

プロジェクト全体の設計・仕様を定義する永続的ドキュメント。実装の「北極星」として機能し、頻繁には更新しない。

| ファイル | 更新タイミング |
|---|---|
| `functional-design.md` | 研究仮説・分析対象・手法が変わったとき |
| `architecture.md` | システム設計・DB構造・APIが変わったとき |
| `repository-structure.md` | ディレクトリ構成が変わったとき |
| `development-guidelines.md` | 開発ルール・コーディング規約が変わったとき |

### `.steering/`

特定の開発作業に特化したドキュメント。作業ごとに新規作成し、完了後も履歴として保持する。コミット対象。

命名規則: `YYYYMMDD-タスク名`（例: `20260320-implement-fetch-list`）

### `src/main/java/`

アプリケーション本体。パッケージ構成は [技術仕様書](architecture.md) のモジュール構成に従う。

### `src/test/java/`

`src/main/java/` と同じパッケージ構成でテストを配置する。

### `data/`

実行時に生成されるデータディレクトリ。**`.gitignore` により除外**。

- `data/raw/{docId}/` — EDINETからダウンロードしたZIPを展開したファイル群
- `data/xbrl.db` — SQLiteデータベース本体（財務データ・キーワードスコア・進捗管理）

### `output/`

分析結果の出力先。**`.gitignore` により除外**。

---

## 3. コミット対象・非対象の整理

| パス | コミット | 理由 |
|---|---|---|
| `src/` | ✅ | アプリケーション本体 |
| `docs/` | ✅ | 設計ドキュメント |
| `.steering/` | ✅ | 作業履歴として保持 |
| `pom.xml` | ✅ | ビルド設定 |
| `README.md` | ✅ | プロジェクト説明 |
| `.gitignore` | ✅ | Git除外設定 |
| `data/` | ❌ | EDINETデータ（再取得可能・大容量） |
| `output/` | ❌ | 分析結果（再生成可能） |
| `*.pdf` | ❌ | 仕様書等（参照専用） |
| `.env` | ❌ | APIキー等の機密情報 |

---

## 4. 環境変数・機密情報の管理

APIキーや機密情報はソースコードおよびリポジトリに**絶対に含めない**。

```bash
# 実行前に環境変数を設定する
export EDINET_API_KEY=your_api_key_here
```

ローカルでの管理が必要な場合は `.env` ファイルに記述するが、`.gitignore` により除外済みのため、コミットされることはない。
