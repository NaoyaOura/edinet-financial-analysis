# タスクリスト：score-keywords コマンド実装

## 進捗凡例
- [ ] 未着手
- [x] 完了

---

## タスク

### 1. SectionExtractor の実装
- [x] `text/SectionExtractor.java` を実装する（HTMLから3セクションのテキスト抽出）
- [x] `text/SectionExtractorTest.java` を実装する

### 2. NegationFilter の実装
- [x] `text/NegationFilter.java` を実装する（否定文脈の前後50文字のキーワードを除外）
- [x] `text/NegationFilterTest.java` を実装する

### 3. KeywordScorer の実装
- [x] `text/KeywordScorer.java` を実装する（キーワード密度の算出）
- [x] `text/KeywordScorerTest.java` を実装する

### 4. ScoreKeywordsCommand の実装
- [x] `command/ScoreKeywordsCommand.java` を実装する（オプション解析・進捗管理・実行制御）
- [x] `Main.java` の `score-keywords` ケースを接続する

### 5. 動作確認
- [x] `mvn compile` が通ることを確認する
- [x] `mvn test` が通ることを確認する（43テストすべてPASS）
- [ ] `mvn exec:java -Dexec.args="score-keywords --year 2023"` でスコアが保存されることを確認する
