# タスクリスト：export コマンド実装

## 進捗凡例
- [ ] 未着手
- [x] 完了

---

## タスク

### 1. CsvExporter の実装
- [x] `report/CsvExporter.java` を実装する（financial/keywords/merged の3種のCSV出力）
- [x] `report/CsvExporterTest.java` を実装する

### 2. ExportCommand の実装
- [x] `command/ExportCommand.java` を実装する（オプション解析・出力先制御）
- [x] `Main.java` の `export` ケースを接続する

### 3. 動作確認
- [x] `mvn compile` が通ることを確認する
- [x] `mvn test` が通ることを確認する（52テストすべてPASS）
- [ ] `mvn exec:java -Dexec.args="export"` でCSVが出力されることを確認する
