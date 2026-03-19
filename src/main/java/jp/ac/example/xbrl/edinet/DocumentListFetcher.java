package jp.ac.example.xbrl.edinet;

import com.fasterxml.jackson.databind.JsonNode;
import jp.ac.example.xbrl.db.CompanyDao;
import jp.ac.example.xbrl.db.DatabaseManager;
import jp.ac.example.xbrl.db.DocumentListDao;
import jp.ac.example.xbrl.db.TaskProgressDao;

import java.sql.Connection;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 指定年度の有価証券報告書の書類一覧をEDINET APIから取得してDBに保存するクラス。
 */
public class DocumentListFetcher {

    /** 有価証券報告書の書類種別コード */
    private static final String DOC_TYPE_CODE_ANNUAL_REPORT = "120";

    private final EdinetApiClient apiClient;
    private final DatabaseManager dbManager;

    public DocumentListFetcher(EdinetApiClient apiClient, DatabaseManager dbManager) {
        this.apiClient = apiClient;
        this.dbManager = dbManager;
    }

    /**
     * 企業情報APIから edinetCode → industryCode のマッピングを構築する。
     */
    private Map<String, String> buildCompanyIndustryMap() throws Exception {
        System.out.println("企業情報を取得中...");
        JsonNode root = apiClient.fetchCompanyList();
        JsonNode results = root.path("results");

        Map<String, String> map = new HashMap<>();
        if (results.isArray()) {
            for (JsonNode company : results) {
                String edinetCode   = company.path("edinetCode").asText("");
                String industryCode = company.path("industryCode").asText("");
                if (!edinetCode.isBlank()) {
                    map.put(edinetCode, industryCode);
                }
            }
        }
        System.out.printf("企業情報を取得しました（%d件）%n", map.size());
        return map;
    }

    /**
     * 指定年度（4月1日〜翌年3月31日）の書類一覧を取得してDBに保存する。
     * 土日はスキップする。
     *
     * @param fiscalYear 対象年度（例: 2023 → 2023-04-01〜2024-03-31）
     * @return 新規登録した書類件数
     */
    public int fetch(int fiscalYear) throws Exception {
        // 書類一覧処理の前に企業情報APIから業種コードマップを構築する
        Map<String, String> companyIndustryMap = buildCompanyIndustryMap();

        LocalDate start = LocalDate.of(fiscalYear, 4, 1);
        LocalDate end = LocalDate.of(fiscalYear + 1, 3, 31);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        int totalSaved = 0;
        int totalDays = 0;
        LocalDate current = start;

        while (!current.isAfter(end)) {
            // 土日はスキップ
            if (current.getDayOfWeek() == DayOfWeek.SATURDAY
                    || current.getDayOfWeek() == DayOfWeek.SUNDAY) {
                current = current.plusDays(1);
                continue;
            }

            String date = current.format(formatter);
            totalDays++;

            try {
                int saved = fetchAndSaveForDate(date, fiscalYear, companyIndustryMap);
                totalSaved += saved;

                if (saved > 0) {
                    System.out.printf("[%s] %d件の有価証券報告書を登録しました%n", date, saved);
                }

                // APIレート制限への配慮
                Thread.sleep(200);

            } catch (Exception e) {
                System.err.printf("[%s] 取得失敗: %s%n", date, e.getMessage());
            }

            current = current.plusDays(1);
        }

        System.out.printf("%n%d営業日を処理し、合計%d件の書類を登録しました%n", totalDays, totalSaved);
        return totalSaved;
    }

    /**
     * 指定日の書類一覧を取得し、有価証券報告書のみDBに保存する。
     *
     * @param companyIndustryMap 企業情報APIから構築した edinetCode → industryCode マップ
     */
    private int fetchAndSaveForDate(String date, int fiscalYear,
                                    Map<String, String> companyIndustryMap) throws Exception {
        JsonNode root = apiClient.fetchDocumentList(date);
        JsonNode results = root.path("results");

        if (!results.isArray()) {
            return 0;
        }

        int savedCount = 0;

        try (Connection conn = dbManager.getConnection()) {
            CompanyDao companyDao = new CompanyDao(conn);
            DocumentListDao documentListDao = new DocumentListDao(conn);
            TaskProgressDao taskProgressDao = new TaskProgressDao(conn);

            for (JsonNode doc : results) {
                String docTypeCode = doc.path("docTypeCode").asText("");

                // 有価証券報告書のみ対象
                if (!DOC_TYPE_CODE_ANNUAL_REPORT.equals(docTypeCode)) {
                    continue;
                }

                String docId = doc.path("docID").asText();
                String edinetCode = doc.path("edinetCode").asText();
                String filerName = doc.path("filerName").asText();
                String docDescription = doc.path("docDescription").asText();
                // 業種コードは書類一覧APIではなく企業情報APIのマップから取得する
                String industryCode = companyIndustryMap.getOrDefault(edinetCode, "");

                if (docId.isBlank() || edinetCode.isBlank()) {
                    continue;
                }

                // 業種コードを RETAIL / IT / UNKNOWN に分類して企業マスタに登録
                String industryCategory = IndustryClassifier.classify(industryCode);
                companyDao.upsert(edinetCode, filerName, industryCode, industryCategory);

                // 書類一覧に登録（重複はスキップ）
                documentListDao.insertIfAbsent(docId, edinetCode, fiscalYear, date, docDescription);

                // ダウンロードタスクを PENDING で登録（既存レコードは上書きしない）
                taskProgressDao.insertIfAbsent(docId, "DOWNLOAD", TaskProgressDao.Status.PENDING);

                savedCount++;
            }
        }

        return savedCount;
    }
}
