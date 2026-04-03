package jp.ac.example.xbrl.command;

import jp.ac.example.xbrl.config.AppConfig;
import jp.ac.example.xbrl.db.CompanyDao;
import jp.ac.example.xbrl.db.DatabaseManager;
import jp.ac.example.xbrl.db.EdinetJQuantsMappingDao;
import jp.ac.example.xbrl.db.JQuantsListedInfoDao;
import jp.ac.example.xbrl.jquants.JQuantsApiClient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * jquants-fetch-info コマンド。
 *
 * 処理フロー:
 *   1. J-Quants /listed/info から全上場銘柄情報を取得し jquants_listed_info に保存
 *   2. companies.secCode と jquants_listed_info.code を照合して edinet_jquants_mapping を自動生成
 */
public class JQuantsFetchInfoCommand {

    private final AppConfig config;
    private final DatabaseManager dbManager;

    public JQuantsFetchInfoCommand(AppConfig config, DatabaseManager dbManager) {
        this.config = config;
        this.dbManager = dbManager;
    }

    public void execute(String[] args) throws Exception {
        String apiKey = config.getJquantsApiKey();
        if (apiKey == null) {
            throw new IllegalStateException(
                "環境変数 JQUANTS_API_KEY が設定されていません。" +
                "J-Quants のダッシュボードから API キーを取得して設定してください。"
            );
        }

        JQuantsApiClient apiClient = new JQuantsApiClient(apiKey);

        // 1. 上場銘柄情報を取得して保存
        System.out.println("J-Quants から上場銘柄情報を取得中...");
        List<JQuantsApiClient.ListedInfoRecord> apiRecords = apiClient.fetchListedInfo();
        System.out.println("取得件数: " + apiRecords.size() + " 件");

        try (Connection conn = dbManager.getConnection()) {
            JQuantsListedInfoDao listedInfoDao = new JQuantsListedInfoDao(conn);
            for (JQuantsApiClient.ListedInfoRecord r : apiRecords) {
                listedInfoDao.upsert(new JQuantsListedInfoDao.ListedInfoRecord(
                    r.code(), r.companyName(), r.companyNameEn(),
                    r.sector33Code(), r.sector33CodeName(),
                    r.sector17Code(), r.sector17CodeName(),
                    r.marketCode(), r.marketCodeName(),
                    r.scaleCategory()
                ));
            }
        }
        System.out.println("jquants_listed_info に保存しました。");

        // 2. companies.secCode と jquants_listed_info.code を照合してマッピングを自動生成
        System.out.println("EDINET ↔ J-Quants マッピングを自動生成中...");
        int mappedCount = 0;
        int skippedCount = 0;

        List<String[]> companiesWithSecCode = fetchCompaniesWithSecCode();
        try (Connection conn = dbManager.getConnection()) {
            EdinetJQuantsMappingDao mappingDao = new EdinetJQuantsMappingDao(conn);
            JQuantsListedInfoDao listedInfoDao = new JQuantsListedInfoDao(conn);

            for (String[] row : companiesWithSecCode) {
                String edinetCode = row[0];
                String secCode = row[1];
                if (secCode == null || secCode.isBlank()) {
                    skippedCount++;
                    continue;
                }
                // J-Quantsは5桁コード（末尾0）を使う場合がある。先頭4桁と5桁の両方で照合する
                String code4 = secCode.length() >= 4 ? secCode.substring(0, 4) : secCode;
                String code5 = code4 + "0";

                JQuantsListedInfoDao.ListedInfoRecord match = listedInfoDao.findByCode(code5);
                if (match == null) {
                    match = listedInfoDao.findByCode(code4);
                }
                if (match != null) {
                    mappingDao.upsert(edinetCode, match.code());
                    mappedCount++;
                } else {
                    skippedCount++;
                }
            }
        }
        System.out.println("マッピング生成完了: " + mappedCount + " 件マッピング、" + skippedCount + " 件スキップ（secCode未登録または一致なし）");
    }

    /**
     * companies テーブルから secCode を持つ企業一覧を取得する。
     */
    private List<String[]> fetchCompaniesWithSecCode() throws Exception {
        List<String[]> results = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT edinetCode, secCode FROM companies WHERE secCode IS NOT NULL AND secCode != ''");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(new String[]{rs.getString("edinetCode"), rs.getString("secCode")});
            }
        }
        return results;
    }
}
