package jp.ac.example.xbrl.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KeywordScorerTest {

    private final KeywordScorer scorer = new KeywordScorer(new NegationFilter());

    @Test
    void score_nullと空文字はゼロスコア() {
        KeywordScorer.ScoreResult nullResult = scorer.score(null);
        assertEquals(0.0, nullResult.totalScore());
        assertEquals(0, nullResult.documentLength());

        KeywordScorer.ScoreResult emptyResult = scorer.score("");
        assertEquals(0.0, emptyResult.totalScore());
    }

    @Test
    void score_生成AIキーワードで高いスコア() {
        // "生成AI"（4文字）は生成AI(重み3)と部分一致するAI(重み2)の両方がヒットする
        String text = "生成AI";
        KeywordScorer.ScoreResult result = scorer.score(text);
        // genAiScore = 3 * 1 / 4 * 10000 = 7500
        assertEquals(7_500.0, result.genAiScore(), 0.001);
        // totalScore = (3+2) * 1 / 4 * 10000 = 12500
        assertEquals(12_500.0, result.totalScore(), 0.001);
        assertEquals(4, result.documentLength());
    }

    @Test
    void score_AIキーワードは中スコア() {
        // "AI" のみ（2文字）
        String text = "AI";
        KeywordScorer.ScoreResult result = scorer.score(text);
        // aiScore = 2 * 1 / 2 * 10000 = 10000
        assertEquals(10_000.0, result.aiScore(), 0.001);
    }

    @Test
    void score_DXキーワードは低スコア() {
        String text = "DX";
        KeywordScorer.ScoreResult result = scorer.score(text);
        // dxScore = 1 * 1 / 2 * 10000 = 5000
        assertEquals(5_000.0, result.dxScore(), 0.001);
    }

    @Test
    void score_否定文脈のキーワードは除外される() {
        // 「生成AIを導入していない」→ 生成AIはスコアに含まれない
        String text = "生成AIを導入していない。";
        KeywordScorer.ScoreResult result = scorer.score(text);
        assertEquals(0.0, result.genAiScore(), 0.001);
    }

    @Test
    void score_複数キーワードが累積される() {
        // "ChatGPT" と "AI" の両方を含むテキスト
        String text = "ChatGPTはAIです";
        KeywordScorer.ScoreResult result = scorer.score(text);
        // ChatGPT: 重み3, AI: 重み2 → 合計重み付き5ヒット
        // documentLength = 9
        // totalScore = 5 / 9 * 10000 ≈ 5555.6
        assertTrue(result.totalScore() > 0, "複数キーワードのスコアが加算されること");
        assertTrue(result.genAiScore() > 0, "ChatGPTでgenAiScoreが計上されること");
        assertTrue(result.aiScore() > 0, "AIでaiScoreが計上されること");
    }

    @Test
    void score_カテゴリ別スコアの合計がtotalScoreに等しい() {
        String text = "生成AIとDXとAI技術の活用を検討しています";
        KeywordScorer.ScoreResult result = scorer.score(text);
        double sum = result.genAiScore() + result.aiScore() + result.dxScore();
        assertEquals(result.totalScore(), sum, 0.001);
    }
}
