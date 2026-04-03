package jp.ac.example.xbrl.jquants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * J-Quants API v2 へのHTTPリクエストを担うクラス。
 *
 * 認証方式: x-api-key ヘッダー（V2以降。トークン取得は不要）
 *
 * 対応エンドポイント:
 *   - GET /v2/equities/master      — 上場銘柄情報
 *   - GET /v2/equities/bars/daily  — 日次株価
 *   - GET /v2/fins/summary         — 財務サマリー
 */
public class JQuantsApiClient {

    private static final String BASE_URL = "https://api.jquants.com/v2";
    private static final int MAX_RETRY = 3;
    private static final long RETRY_INTERVAL_MS = 1000L;

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public JQuantsApiClient(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(
                "JQUANTS_API_KEY が設定されていません。" +
                "J-Quants のダッシュボードから API キーを取得して環境変数に設定してください。"
            );
        }
        this.apiKey = apiKey;
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
     * V2: GET /v2/equities/master?date=YYYY-MM-DD
     * レスポンスキー: "data"
     * フィールド名: Code, CoName, CoNameEn, S33, S33Nm, S17, S17Nm, Mkt, MktNm, ScaleCat
     */
    public List<ListedInfoRecord> fetchListedInfo() throws Exception {
        String today = LocalDate.now().toString(); // YYYY-MM-DD
        List<ListedInfoRecord> results = new ArrayList<>();
        String paginationKey = null;

        do {
            String url = BASE_URL + "/equities/master?date=" + today;
            if (paginationKey != null) {
                url += "&pagination_key=" + paginationKey;
            }
            String json = getWithRetry(url);
            JsonNode root = objectMapper.readTree(json);
            JsonNode dataArray = root.get("data");
            if (dataArray != null && dataArray.isArray()) {
                for (JsonNode node : dataArray) {
                    results.add(new ListedInfoRecord(
                        textOrNull(node, "Code"),
                        textOrNull(node, "CoName"),
                        textOrNull(node, "CoNameEn"),
                        textOrNull(node, "S33"),
                        textOrNull(node, "S33Nm"),
                        textOrNull(node, "S17"),
                        textOrNull(node, "S17Nm"),
                        textOrNull(node, "Mkt"),
                        textOrNull(node, "MktNm"),
                        textOrNull(node, "ScaleCat")
                    ));
                }
            }
            JsonNode pkNode = root.get("pagination_key");
            paginationKey = (pkNode != null && !pkNode.isNull()) ? pkNode.asText() : null;
        } while (paginationKey != null && !paginationKey.isBlank());

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
     * V2: GET /v2/equities/bars/daily?code=xxx&from=xxx&to=xxx
     * レスポンスキー: "data"
     * フィールド名: Code, Date, O, H, L, C, Vo, AdjC, AdjVo
     *
     * @param code J-Quants銘柄コード（例: "72030"）
     * @param from 開始日（YYYY-MM-DD）
     * @param to   終了日（YYYY-MM-DD）
     */
    public List<DailyQuoteRecord> fetchDailyQuotes(String code, String from, String to) throws Exception {
        List<DailyQuoteRecord> results = new ArrayList<>();
        String paginationKey = null;

        do {
            String url = BASE_URL + "/equities/bars/daily?code=" + code + "&from=" + from + "&to=" + to;
            if (paginationKey != null) {
                url += "&pagination_key=" + paginationKey;
            }
            String json = getWithRetry(url);
            JsonNode root = objectMapper.readTree(json);
            JsonNode dataArray = root.get("data");
            if (dataArray != null && dataArray.isArray()) {
                for (JsonNode node : dataArray) {
                    results.add(new DailyQuoteRecord(
                        textOrNull(node, "Code"),
                        textOrNull(node, "Date"),
                        doubleOrNull(node, "O"),
                        doubleOrNull(node, "H"),
                        doubleOrNull(node, "L"),
                        doubleOrNull(node, "C"),
                        doubleOrNull(node, "Vo"),
                        doubleOrNull(node, "AdjC"),
                        doubleOrNull(node, "AdjVo")
                    ));
                }
            }
            JsonNode pkNode = root.get("pagination_key");
            paginationKey = (pkNode != null && !pkNode.isNull()) ? pkNode.asText() : null;
        } while (paginationKey != null && !paginationKey.isBlank());

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
     * V2: GET /v2/fins/summary?code=xxx
     * レスポンスキー: "data"
     * フィールド名: Code, DiscDate, DocType, CurFYEn, Sales, OP, NP, TA, Eq
     * fiscalYear は CurFYEn（通期終了日）から導出: 月≤3なら year-1、それ以外は year
     *
     * @param code J-Quants銘柄コード
     */
    public List<FinStatementRecord> fetchFinStatements(String code) throws Exception {
        List<FinStatementRecord> results = new ArrayList<>();
        String paginationKey = null;

        do {
            String url = BASE_URL + "/fins/summary?code=" + code;
            if (paginationKey != null) {
                url += "&pagination_key=" + paginationKey;
            }
            String json = getWithRetry(url);
            JsonNode root = objectMapper.readTree(json);
            JsonNode dataArray = root.get("data");
            if (dataArray != null && dataArray.isArray()) {
                for (JsonNode node : dataArray) {
                    String fyEnd = textOrNull(node, "CurFYEn");
                    Integer fiscalYear = deriveFiscalYear(fyEnd);
                    results.add(new FinStatementRecord(
                        textOrNull(node, "Code"),
                        textOrNull(node, "DiscDate"),
                        textOrNull(node, "DocType"),
                        fiscalYear,
                        doubleFromStringOrNull(node, "Sales"),
                        doubleFromStringOrNull(node, "OP"),
                        null, // 経常利益は fins/summary に含まれない
                        doubleFromStringOrNull(node, "NP"),
                        doubleFromStringOrNull(node, "TA"),
                        doubleFromStringOrNull(node, "Eq")
                    ));
                }
            }
            JsonNode pkNode = root.get("pagination_key");
            paginationKey = (pkNode != null && !pkNode.isNull()) ? pkNode.asText() : null;
        } while (paginationKey != null && !paginationKey.isBlank());

        return results;
    }

    // -------------------------------------------------------------------------
    // 内部ユーティリティ
    // -------------------------------------------------------------------------

    /**
     * 通期終了日（CurFYEn）から年度を導出する。
     * 例: "2023-03-31" → 2022（3月以前終了は前年度）
     *     "2022-12-31" → 2022（4月以降終了は同年度）
     */
    private Integer deriveFiscalYear(String fyEnd) {
        if (fyEnd == null || fyEnd.length() < 7) return null;
        try {
            int year = Integer.parseInt(fyEnd.substring(0, 4));
            int month = Integer.parseInt(fyEnd.substring(5, 7));
            return month <= 3 ? year - 1 : year;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getWithRetry(String url) throws Exception {
        Exception lastException = null;
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("x-api-key", apiKey)
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
                    throw new RuntimeException(
                        "J-Quants API エラー: ステータスコード=" + response.statusCode()
                        + " レスポンス=" + response.body() + " URL=" + url
                    );
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
}
