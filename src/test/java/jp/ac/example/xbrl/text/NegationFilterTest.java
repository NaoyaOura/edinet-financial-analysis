package jp.ac.example.xbrl.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NegationFilterTest {

    private final NegationFilter filter = new NegationFilter();

    @Test
    void isNegated_否定文脈のウィンドウ内は真() {
        // 「AIを導入していない」→ AIが否定文脈の近傍にある
        String text = "当社ではAIを導入していない。";
        int aiStart = text.indexOf("AI");
        int aiEnd = aiStart + 2;
        assertTrue(filter.isNegated(text, aiStart, aiEnd));
    }

    @Test
    void isNegated_否定文脈から離れた位置は偽() {
        // AIの後に50文字以上の間隔をあけてから否定表現
        String padding = "あ".repeat(60);
        String text = "当社ではAIを活用しています。" + padding + "導入していない。";
        int aiStart = text.indexOf("AI");
        int aiEnd = aiStart + 2;
        assertFalse(filter.isNegated(text, aiStart, aiEnd));
    }

    @Test
    void isNegated_複数の否定パターンで動作する() {
        String[] negations = {
            "生成AIを検討していない",
            "生成AIを活用していない",
            "生成AIを利用していない",
            "生成AIを使用していない",
            "生成AIの予定はない",
            "生成AIを実施していない",
            "生成AIに対応していない",
            "生成AIを実現していない",
            "生成AIに取り組んでいない"
        };
        for (String text : negations) {
            int start = text.indexOf("生成AI");
            int end = start + 3;
            assertTrue(filter.isNegated(text, start, end), "否定パターンで真になるべき: " + text);
        }
    }

    @Test
    void isNegated_肯定文脈では偽() {
        String text = "当社では生成AIを積極的に導入しています。";
        int start = text.indexOf("生成AI");
        int end = start + 3;
        assertFalse(filter.isNegated(text, start, end));
    }

    @Test
    void isNegated_ウィンドウ境界内は真() {
        // "AI"(位置0-2) + 48文字 + "導入していない"(位置50〜)
        // windowStart = max(0, 50 - 50) = 0 → matchStart(0) >= 0 → 真
        String prefix = "あ".repeat(NegationFilter.NEGATION_WINDOW - 2);
        String text = "AI" + prefix + "導入していない";
        assertTrue(filter.isNegated(text, 0, 2));
    }
}
