# edinet-financial-analysis

有価証券報告書における生成AI・DX関連キーワードの出現頻度と、日本企業の財務パフォーマンスとの関係を定量的に分析するCLIアプリケーション。

メインの分析対象は**小売業**（生成AIの活用が小売業の成長に寄与するかの検証）とし、**IT企業**（情報通信業）を比較対象として業種横断的な分析を行う。

> 📄 詳細ドキュメントは [GitHub Pages](https://naoyaoura.github.io/edinet-financial-test-analysis/) を参照してください。

---

## ドキュメント

| ドキュメント | 概要 |
|---|---|
| [機能設計書](docs/functional-design.md) | 研究目的・仮説・分析対象・キーワード定義・分析手法 |
| [J-Quants 機能設計書](docs/jquants-functional-design.md) | J-Quants API連携・取得データ・DB設計・新規コマンド仕様 |
| [技術仕様書](docs/architecture.md) | システム構成・CLIコマンド・DB設計・API連携仕様 |
| [リポジトリ構造定義書](docs/repository-structure.md) | ディレクトリ構成・コミット対象・機密情報の管理方針 |
| [J-Quants リポジトリ構造定義書](docs/jquants-repository-structure.md) | J-Quants連携で追加するクラス・テーブル・実装順序 |
| [開発ガイドライン](docs/development-guidelines.md) | コーディング規約・テスト方針・ブランチ運用・セキュリティ |
| [コマンドリファレンス](docs/command-reference.md) | 全コマンドのオプション詳細 |
| [タスク進捗管理](docs/task-progress.md) | task_progress テーブルのステータス遷移・再実行の挙動 |

---

## 技術スタック

- **言語**: Java 17
- **ビルド**: Maven
- **テスト**: JUnit 5
- **DB**: SQLite
- **統計**: Apache Commons Math
- **データソース**: [EDINET API v2](https://disclosure2dl.edinet-fsa.go.jp/guide/static/disclosure/WZEK0110.html)、[J-Quants API](https://jpx-jquants.com/)

---

## セットアップ

### 前提条件

- Java 17 以上
- Maven 3.x 以上
- EDINET APIキー（[EDINET](https://disclosure.edinet-fsa.go.jp/) より取得）
- J-Quants リフレッシュトークン（[J-Quants](https://jpx-jquants.com/) より取得、Standard プラン）

### 環境変数の設定

```bash
export EDINET_API_KEY=your_edinet_api_key
export JQUANTS_REFRESH_TOKEN=your_jquants_refresh_token
```

> **注意**: APIキー・トークンをソースコードやコミット履歴に含めないこと。

### ビルド

```bash
mvn compile
```

---

## クイックスタート

各処理フェーズは独立したコマンドとして実行できます。通常は以下の順に実行します。

```bash
# --- EDINET系 ---
# 1. 書類一覧を取得
mvn exec:java -Dexec.args="fetch-list --year 2023"

# 2. 書類をダウンロード（未取得分のみ）
mvn exec:java -Dexec.args="download --year 2023"

# 3. 財務データをパース
mvn exec:java -Dexec.args="parse-xbrl --year 2023"

# 4. キーワードスコアを算出
mvn exec:java -Dexec.args="score-keywords --year 2023"

# --- J-Quants系 ---
# 5. 上場銘柄情報・業種コードを取得（EDINETとのマッピングも自動生成）
mvn exec:java -Dexec.args="jquants-fetch-info"

# 6. 株価データを取得
mvn exec:java -Dexec.args="jquants-fetch-prices --year 2023"

# --- 分析・出力 ---
# 7. 統計分析を実行
mvn exec:java -Dexec.args="analyze"

# 8. CSV出力
mvn exec:java -Dexec.args="export"

# 進捗確認
mvn exec:java -Dexec.args="status"
```

途中で失敗した場合は同じコマンドを再実行すると、完了済みの書類をスキップして続きから処理します。

全コマンドのオプション詳細 → [コマンドリファレンス](docs/command-reference.md)

---

## 研究仮説

| 仮説 | 内容 |
|---|---|
| H1（主仮説） | 生成AI・DX関連キーワードスコアが高い企業は、翌年の売上高成長率・営業利益率が高い |
| H2 | キーワードスコアの増加率が高い企業は、業績改善が見られる |
| H3（対立仮説） | キーワードスコアと業績に有意な関係はない（過剰なバズワード） |
| H4（業種比較） | キーワードスコアと業績の関係はIT企業において小売業よりも強く現れる |

---

## ライセンス

本リポジトリは研究目的での利用を想定しています。
