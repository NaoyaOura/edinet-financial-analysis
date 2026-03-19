package jp.ac.example.xbrl.edinet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IndustryClassifierTest {

    @Test
    void classify_小売業コードはRETAIL() {
        assertEquals("RETAIL", IndustryClassifier.classify("6100")); // 小売業
    }

    @Test
    void classify_情報通信業コードはIT() {
        assertEquals("IT", IndustryClassifier.classify("5250")); // 情報・通信業
    }

    @Test
    void classify_対象外コードはUNKNOWN() {
        assertEquals("UNKNOWN", IndustryClassifier.classify("0050")); // 水産・農林業
        assertEquals("UNKNOWN", IndustryClassifier.classify("3050")); // 食料品
        assertEquals("UNKNOWN", IndustryClassifier.classify("6050")); // 卸売業
        assertEquals("UNKNOWN", IndustryClassifier.classify("3600")); // 機械（旧コードが誤分類されないことを確認）
        assertEquals("UNKNOWN", IndustryClassifier.classify("5000")); // 存在しないコード
    }

    @Test
    void classify_nullと空文字はUNKNOWN() {
        assertEquals("UNKNOWN", IndustryClassifier.classify(null));
        assertEquals("UNKNOWN", IndustryClassifier.classify(""));
        assertEquals("UNKNOWN", IndustryClassifier.classify("  "));
    }

    @Test
    void classify_前後の空白は無視される() {
        assertEquals("RETAIL", IndustryClassifier.classify(" 6100 "));
        assertEquals("IT",     IndustryClassifier.classify(" 5250 "));
    }
}
