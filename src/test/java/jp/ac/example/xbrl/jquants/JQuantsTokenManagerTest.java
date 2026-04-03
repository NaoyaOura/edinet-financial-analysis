package jp.ac.example.xbrl.jquants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JQuantsTokenManagerTest {

    @Test
    void nullトークンで例外がスローされる() {
        assertThrows(IllegalArgumentException.class, () -> new JQuantsTokenManager(null));
    }

    @Test
    void 空文字トークンで例外がスローされる() {
        assertThrows(IllegalArgumentException.class, () -> new JQuantsTokenManager(""));
    }

    @Test
    void 空白のみトークンで例外がスローされる() {
        assertThrows(IllegalArgumentException.class, () -> new JQuantsTokenManager("   "));
    }

    @Test
    void 有効なトークンでインスタンスが生成される() {
        assertDoesNotThrow(() -> new JQuantsTokenManager("dummy-refresh-token"));
    }
}
