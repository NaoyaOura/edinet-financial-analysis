package jp.ac.example.xbrl.jquants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * J-Quants のリフレッシュトークンを使ってIDトークンを取得するクラス。
 *
 * 認証フロー:
 *   リフレッシュトークン（環境変数）
 *       ↓ POST /v1/token/auth_refresh
 *   IDトークン（有効期間24時間）
 */
public class JQuantsTokenManager {

    private static final String TOKEN_URL = "https://api.jquants.com/v1/token/auth_refresh";

    private final String refreshToken;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public JQuantsTokenManager(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException(
                "JQUANTS_REFRESH_TOKEN が設定されていません。" +
                "J-Quants のダッシュボードからリフレッシュトークンを取得して環境変数に設定してください。"
            );
        }
        this.refreshToken = refreshToken;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * リフレッシュトークンを使ってIDトークンを取得する。
     *
     * @return IDトークン文字列
     * @throws RuntimeException 取得に失敗した場合
     */
    public String getIdToken() {
        try {
            // リフレッシュトークンはクエリパラメータで渡す（Authorizationヘッダーではない）
            // URLエンコードしてクォート等の不正文字を除去する
            String encodedToken = URLEncoder.encode(refreshToken.trim(), StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL + "?refreshtoken=" + encodedToken))
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException(
                    "J-Quants IDトークン取得に失敗しました。ステータスコード: " + response.statusCode()
                    + " レスポンス: " + response.body()
                );
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode idTokenNode = root.get("idToken");
            if (idTokenNode == null || idTokenNode.isNull()) {
                throw new RuntimeException("J-Quants レスポンスに idToken が含まれていません: " + response.body());
            }
            return idTokenNode.asText();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("J-Quants IDトークン取得中にエラーが発生しました: " + e.getMessage(), e);
        }
    }
}
