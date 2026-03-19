# タスクリスト：parse-xbrl コマンド実装

## 進捗凡例
- [ ] 未着手
- [x] 完了

---

## タスク

### 1. XbrlParser の実装
- [x] `xbrl/XbrlParser.java` を実装する（DOMパース・連結優先・単位正規化）
- [x] `xbrl/XbrlParserTest.java` を実装する（サンプルXBRLで各指標の抽出を検証）
- [x] `mvn test` が通ることを確認する

### 2. FinancialDataExtractor の実装
- [x] `xbrl/FinancialDataExtractor.java` を実装する（タクソノミ要素名 → FinancialRecord への変換）

### 3. ParseXbrlCommand の実装
- [x] `command/ParseXbrlCommand.java` を実装する（オプション解析・進捗管理・実行制御）
- [x] `Main.java` の `parse-xbrl` ケースを `ParseXbrlCommand` に接続する

### 4. 動作確認
- [x] `mvn compile` が通ることを確認する
- [x] `mvn test` が通ること（24テスト全PASS）
- [ ] `mvn exec:java -Dexec.args="parse-xbrl --year 2023"` で財務データが保存されることを確認する
