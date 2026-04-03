package jp.ac.example.xbrl.jquants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * J-Quants API へのHTTPリクエストを担うクラス。
 *
 * 対応エンドポイント:
 *   - GET /v1/listed/info        — 上場銘柄情報
 *   - GET /v1/prices/daily_quotes — 日次株価
 *   - GET /v1/fins/statements    — 財務情報
 */
public class JQuantsApiClient {

    private static final String BASE_URL = "https://api.jquants.com/v1";
    private static final int MAX_RETRY = 3;
    private static final long RETRY_INTERVAL_MS = 1000L;

    private final JQuantsTokenManager tokenManager;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public JQuantsApiClient(JQuantsTokenManager tokenManager) {
        this.tokenManager = tokenManager;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
        this.objectMapper = new ObjectMapper();
    }

    // -------------------------------------------------------------------------
    // 上場銘柄情報
    // -------------------------------------------------------------------------

    public record ListedInfoRecord(
        String code,
        String companyName,
        String companyNameEn,
        String sector33Code,
        String sector33CodeName,
        String sector17Code,
        String sector17CodeName,
        String marketCode,
        String marketCodeName,
        String scaleCategory
    ) {}

    /**
     * 全上場銘柄情報を取得する。
     */
    public List<ListedInfoRecord> fetchListedInfo() throws Exception {
        String json = getWithRetry(BASE_URL + "/listed/info");
        JsonNode root = objectMapper.readTree(json);
        JsonNode infoArray = root.get("info");
        List<ListedInfoRecord> results = new ArrayList<>();
        if (infoArray != null && infoArray.isArray()) {
            for (JsonNode node : infoArray) {
                results.add(new ListedInfoRecord(
                    textOrNull(node, "Code"),
                    textOrNull(node, "CompanyName"),
                    textOrNull(node, "CompanyNameEnglish"),
                    textOrNull(node, "Sector33Code"),
                    textOrNull(node, "Sector33CodeName"),
                    textOrNull(node, "Sector17Code"),
                    textOrNull(node, "Sector17CodeName"),
                    textOrNull(node, "MarketCode"),
                    textOrNull(node, "MarketCodeName"),
                    textOrNull(node, "ScaleCategory")
                ));
            }
        }
        return results;
    }

    // -------------------------------------------------------------------------
    // 日次株価
    // -------------------------------------------------------------------------

    public record DailyQuoteRecord(
        String code,
        String date,
        Double open,
        Double high,
        Double low,
        Double close,
        Double volume,
        Double adjustmentClose,
        Double adjustmentVolume
    ) {}

    /**
     * 指定銘柄・期間の日次株価を取得する。
     *
     * @param code J-Quants銘柄コード（例: "72030"）
     * @param from 開始日（YYYY-MM-DD）
     * @param to   終了日（YYYY-MM-DD）
     */
    public List<DailyQuoteRecord> fetchDailyQuotes(String code, String from, String to) throws Exception {
        String url = BASE_URL + "/prices/daily_quotes?code=" + code + "&from=" + from + "&to=" + to;
        String json = getWithRetry(url);
        JsonNode root = objectMapper.readTree(json);
        JsonNode quotesArray = root.get("daily_quotes");
        List<DailyQuoteRecord> results = new ArrayList<>();
        if (quotesArray != null && quotesArray.isArray()) {
            for (JsonNode node : quotesArray) {
                results.add(new DailyQuoteRecord(
                    textOrNull(node, "Code"),
                    textOrNull(node, "Date"),
                    doubleOrNull(node, "Open"),
                    doubleOrNull(node, "High"),
                    doubleOrNull(node, "Low"),
                    doubleOrNull(node, "Close"),
                    doubleOrNull(node, "Volume"),
                    doubleOrNull(node, "AdjustmentClose"),
                    doubleOrNull(node, "AdjustmentVolume")
                ));
            }
        }
        return results;
    }

    // -------------------------------------------------------------------------
    // 財務情報
    // -------------------------------------------------------------------------

    public record FinStatementRecord(
        String localCode,
        String disclosedDate,
        String typeOfDocument,
        Integer fiscalYear,
        Double netSales,
        Double operatingProfit,
        Double ordinaryProfit,
        Double profit,
        Double totalAssets,
        Double equity
    ) {}

    /**
     * 指定銘柄の通期財務情報を取得する。
     *
     * @param code J-Quants銘柄コード
     */
    public List<FinStatementRecord> fetchFinStatements(String code) throws Exception {
        String url = BASE_URL + "/fins/statements?code=" + code;
        String json = getWithRetry(url);
        JsonNode root = objectMapper.readTree(json);
        JsonNode statementsArray = root.get("statements");
        List<FinStatementRecord> results = new ArrayList<>();
        if (statementsArray != null && statementsArray.isArray()) {
            for (JsonNode node : statementsArray) {
                results.add(new FinStatementRecord(
                    textOrNull(node, "LocalCode"),
                    textOrNull(node, "DisclosedDate"),
                    textOrNull(node, "TypeOfDocument"),
                    intOrNull(node, "FiscalYear"),
                    doubleFromStringOrNull(node, "NetSales"),
                    doubleFromStringOrNull(node, "OperatingProfit"),
                    doubleFromStringOrNull(node, "OrdinaryProfit"),
                    doubleFromStringOrNull(node, "Profit"),
                    doubleFromStringOrNull(node, "TotalAssets"),
                    doubleFromStringOrNull(node, "Equity")
                ));
            }
        }
        return results;
    }

    // -------------------------------------------------------------------------
    // 内部ユーティリティ
    // -------------------------------------------------------------------------

    private String getWithRetry(String url) throws Exception {
        String idToken = tokenManager.getIdToken();
        Exception lastException = null;
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + idToken)
                    .GET()
                    .timeout(Duration.ofSeconds(60))
                    .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return response.body();
                }
                if (response.statusCode() == 429) {
                    Thread.sleep(RETRY_INTERVAL_MS * 5);
                } else {
                    throw new RuntimeException("J-Quants API エラー: ステータスコード=" + response.statusCode() + " URL=" + url);
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                lastException = e;
                Thread.sleep(RETRY_INTERVAL_MS);
            }
        }
        throw new RuntimeException("J-Quants API リトライ上限に達しました: " + url, lastException);
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return (n == null || n.isNull()) ? null : n.asText();
    }

    private Double doubleOrNull(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return (n == null || n.isNull()) ? null : n.asDouble();
    }

    private Double doubleFromStringOrNull(JsonNode node, String field) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) return null;
        String text = n.asText().trim();
        if (text.isEmpty()) return null;
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer intOrNull(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return (n == null || n.isNull()) ? null : n.asInt();
    }
}
