package jp.ac.example.xbrl.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    @AfterEach
    void tearDown() {
        // テスト間でシングルトンをリセットする
        AppConfig.resetInstance();
    }

    @Test
    void EDINET_API_KEYが設定されていない場合は警告のみでインスタンス生成できる() {
        // APIキー未設定でも analyze 等のコマンドが動作できるよう例外を投げない
        String apiKey = System.getenv("EDINET_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            // すでに設定されている場合はスキップ
            return;
        }
        assertDoesNotThrow(AppConfig::getInstance);
        assertNull(AppConfig.getInstance().getEdinetApiKey());
    }

    @Test
    void DB_PATHが未設定の場合はデフォルト値が使われる() {
        // このテストはEDINET_API_KEYが設定されている環境でのみ有効
        String apiKey = System.getenv("EDINET_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }
        AppConfig config = AppConfig.getInstance();
        assertEquals("./data/xbrl.db", config.getDbPath());
    }

    @Test
    void OUTPUT_DIRが未設定の場合はデフォルト値が使われる() {
        String apiKey = System.getenv("EDINET_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }
        AppConfig config = AppConfig.getInstance();
        assertEquals("./output", config.getOutputDir());
    }
}
