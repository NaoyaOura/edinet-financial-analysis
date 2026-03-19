# edinet-financial-test-analysis

有価証券報告書における生成AI・DX関連キーワードの出現頻度と、日本企業の財務パフォーマンスとの関係を定量的に分析するCLIアプリケーション。

メインの分析対象は**小売業**（生成AIの活用が小売業の成長に寄与するかの検証）とし、**IT企業**（情報通信業）を比較対象として業種横断的な分析を行う。

> 📄 詳細ドキュメントは [GitHub Pages](https://naoyaoura.github.io/edinet-financial-test-analysis/) を参照してください。

---

## ドキュメント

| ドキュメント | 概要 |
|---|---|
| [機能設計書](docs/functional-design.md) | 研究目的・仮説・分析対象・キーワード定義・分析手法 |
| [技術仕様書](docs/architecture.md) | システム構成・CLIコマンド・DB設計・API連携仕様 |
| [リポジトリ構造定義書](docs/repository-structure.md) | ディレクトリ構成・コミット対象・機密情報の管理方針 |
| [開発ガイドライン](docs/development-guidelines.md) | コーディング規約・テスト方針・ブランチ運用・セキュリティ |

---

## 技術スタック

- **言語**: Java 17
- **ビルド**: Maven
- **テスト**: JUnit 5
- **DB**: SQLite
- **統計**: Apache Commons Math
- **データソース**: [EDINET API v2](https://disclosure2dl.edinet-fsa.go.jp/guide/static/disclosure/WZEK0110.html)

---

## セットアップ

### 前提条件

- Java 17 以上
- Maven 3.x 以上
- EDINET APIキー（[EDINET](https://disclosure.edinet-fsa.go.jp/) より取得）

### 環境変数の設定

```bash
export EDINET_API_KEY=your_api_key_here
```

> **注意**: APIキーをソースコードやコミット履歴に含めないこと。

### ビルド

```bash
mvn compile
```

---

## 使い方

各処理フェーズは独立したコマンドとして実行できます。

```bash
# 1. 書類一覧を取得（業種コードに基づき RETAIL / IT / UNKNOWN を自動分類）
mvn exec:java -Dexec.args="fetch-list --year 2023"

# 2. 書類をダウンロード（未取得分のみ）
mvn exec:java -Dexec.args="download --year 2023"

# 3. 財務データをパース
mvn exec:java -Dexec.args="parse-xbrl --year 2023"

# 4. キーワードスコアを算出
mvn exec:java -Dexec.args="score-keywords --year 2023"

# 5. 統計分析を実行（デフォルトは全分析）
mvn exec:java -Dexec.args="analyze"

# 6. CSV出力（デフォルトは全種類）
mvn exec:java -Dexec.args="export"

# 進捗確認
mvn exec:java -Dexec.args="status"
```

途中で失敗した場合は同じコマンドを再実行すると、完了済みの書類をスキップして続きから処理します。
強制的に再処理する場合は `--force` オプションを追加してください。

### analyze オプション

```bash
# 分析種別を指定（デフォルト: all）
mvn exec:java -Dexec.args="analyze --type group-comparison"  # グループ比較
mvn exec:java -Dexec.args="analyze --type lag-regression"    # ラグ回帰分析（メイン）
mvn exec:java -Dexec.args="analyze --type did"               # 差分の差分法
mvn exec:java -Dexec.args="analyze --type panel"             # 固定効果モデル

# 特定年度のみで分析
mvn exec:java -Dexec.args="analyze --type group-comparison --year 2023"
```

### export オプション

```bash
# 出力種別を指定（デフォルト: all）
mvn exec:java -Dexec.args="export --type financial"  # 財務指標CSVのみ
mvn exec:java -Dexec.args="export --type keywords"   # キーワードスコアCSVのみ
mvn exec:java -Dexec.args="export --type merged"     # 統合CSV（分析用メインデータ）

# 年度・出力先を指定
mvn exec:java -Dexec.args="export --type merged --year 2023 --output ./results"
```

---

## 業種分類

`fetch-list` 実行時に EDINET API の `industryCode`（東証33業種コード）を取得し、以下のルールで自動分類します。

| 東証33業種コード | 業種名 | 本ツールでの分類 |
|---|---|---|
| `6100` | 小売業 | `RETAIL` |
| `5250` | 情報・通信業 | `IT` |
| その他 | — | `UNKNOWN`（分析対象外） |

> 東証33業種コードの一覧: [J-Quants API ドキュメント](https://jpx-jquants.com/ja/spec/eq-master/sector33code)

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
