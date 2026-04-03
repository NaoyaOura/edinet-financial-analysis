# 要求内容：J-Quants API 連携実装

## 目的

J-Quants API（Standard プラン）を利用して、EDINETから取得しづらいデータ（業種コード・株価・財務情報）を補完する。
取得したデータはEDINETデータとは独立したテーブルに格納し、マッピングテーブルを介して結合して利用する。

## 要求事項

1. `AppConfig` に `JQUANTS_REFRESH_TOKEN` 環境変数を追加する
2. J-Quants 認証（リフレッシュトークン → IDトークン）を実装する
3. J-Quants API クライアントを実装する
4. 以下の4テーブルを `initializeSchema()` に追加する
   - `jquants_listed_info` — 上場銘柄情報
   - `jquants_daily_prices` — 日次株価
   - `jquants_fin_statements` — 財務情報
   - `edinet_jquants_mapping` — EDINETコード↔銘柄コードのマッピング
5. `companies` テーブルに `secCode` カラムを追加する（マイグレーション対応）
6. 各テーブルに対応する DAO クラスを実装する
7. 以下のコマンドを実装する
   - `jquants-fetch-info` — 上場銘柄情報取得 + マッピング自動生成
   - `jquants-fetch-prices` — 日次株価取得
   - `jquants-fetch-fins` — 財務情報取得
8. `Main.java` のルーティングに3コマンドを追加する
9. `mvn test` が通ること

## 参照ドキュメント

- `docs/jquants-functional-design.md`
- `docs/jquants-repository-structure.md`
