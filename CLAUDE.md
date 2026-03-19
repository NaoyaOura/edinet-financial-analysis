# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## プロジェクト概要

XBRL（eXtensible Business Reporting Language）の調査・解析を行うJavaプロジェクト。

## 技術スタック

- **言語**: Java
- **ビルドツール**: Maven
- **テスト**: JUnit
- **データベース**: SQLite

## ビルド・実行コマンド

```bash
# ビルド
mvn compile

# テスト実行（全件）
mvn test

# 単一テストクラス実行
mvn test -Dtest=ClassName

# 単一テストメソッド実行
mvn test -Dtest=ClassName#methodName

# パッケージ作成（jarファイル生成）
mvn package

# クリーンビルド
mvn clean package

# 依存関係ダウンロード
mvn dependency:resolve
```

## ディレクトリ構造

```
@XBRL調査/
├── src/
│   ├── main/
│   │   ├── java/          # メインソースコード
│   │   └── resources/     # リソースファイル（XBRL定義ファイルなど）
│   └── test/
│       ├── java/          # テストコード
│       └── resources/     # テスト用リソース
├── docs/                  # 永続的ドキュメント（設計書・仕様書）
│   └── ideas/             # 調査メモ・ブレインストーミング
├── .steering/             # 作業単位のドキュメント（要件・設計・タスクリスト）
└── pom.xml
```

## 開発フロー（スペック駆動開発）

1. `docs/` に設計書を作成・承認を得る
2. `.steering/[YYYYMMDD]-[タスク名]/` にステアリングファイルを作成
3. `tasklist.md` に従って実装
4. テストと動作確認
5. 必要に応じてドキュメント更新

**実装前の必須確認**:
1. 関連する `docs/` のドキュメントを読む
2. Grepで既存の類似実装を検索してから実装開始

## 言語設定

- 会話・コメント・ドキュメントはすべて**日本語**で記述する
