package jp.ac.example.xbrl.command;

import jp.ac.example.xbrl.config.AppConfig;
import jp.ac.example.xbrl.db.DatabaseManager;
import jp.ac.example.xbrl.db.EdinetJQuantsMappingDao;
import jp.ac.example.xbrl.db.JQuantsFinStatementsDao;
import jp.ac.example.xbrl.jquants.JQuantsApiClient;

import java.sql.Connection;
import java.util.List;

/**
 * jquants-fetch-fins コマンド。
 *
 * edinet_jquants_mapping に登録済みの銘柄の通期財務情報を取得して jquants_fin_statements に保存する。
 */
public class JQuantsFetchFinsCommand {

    private final AppConfig config;
    private final DatabaseManager dbManager;

    public JQuantsFetchFinsCommand(AppConfig config, DatabaseManager dbManager) {
        this.config = config;
        this.dbManager = dbManager;
    }

    public void execute(String[] args) throws Exception {
        int fiscalYear = parseIntOption(args, "--year", 0);
        String targetCode = parseStringOption(args, "--code");

        if (fiscalYear == 0) {
            throw new IllegalArgumentException("--year オプションは必須です。例: jquants-fetch-fins --year 2023");
        }

        String apiKey = config.getJquantsApiKey();
        if (apiKey == null) {
            throw new IllegalStateException("環境変数 JQUANTS_API_KEY が設定されていません。");
        }

        JQuantsApiClient apiClient = new JQuantsApiClient(apiKey);

        // 対象銘柄コード一覧を取得
        List<EdinetJQuantsMappingDao.MappingRecord> mappings;
        try (Connection conn = dbManager.getConnection()) {
            EdinetJQuantsMappingDao mappingDao = new EdinetJQuantsMappingDao(conn);
            mappings = mappingDao.findAll();
        }

        if (targetCode != null) {
            mappings = mappings.stream()
                .filter(m -> m.jquantsCode().equals(targetCode))
                .toList();
        }

        System.out.println("財務情報取得対象: " + mappings.size() + " 銘柄 / 年度: " + fiscalYear);

        int successCount = 0;
        int errorCount = 0;

        for (EdinetJQuantsMappingDao.MappingRecord mapping : mappings) {
            String jquantsCode = mapping.jquantsCode();
            try {
                List<JQuantsApiClient.FinStatementRecord> statements = apiClient.fetchFinStatements(jquantsCode);
                try (Connection conn = dbManager.getConnection()) {
                    JQuantsFinStatementsDao dao = new JQuantsFinStatementsDao(conn);
                    for (JQuantsApiClient.FinStatementRecord s : statements) {
                        // 通期（FY）かつ対象年度のみ保存
                        if ("FY".equals(s.typeOfDocument()) && fiscalYear == s.fiscalYear()) {
                            dao.upsert(new JQuantsFinStatementsDao.FinStatementRecord(
                                s.localCode(), s.disclosedDate(), s.typeOfDocument(), s.fiscalYear(),
                                s.netSales(), s.operatingProfit(), s.ordinaryProfit(),
                                s.profit(), s.totalAssets(), s.equity(),
                                s.cashFlowsFromOperating(), s.cashFlowsFromInvesting(),
                                s.cashFlowsFromFinancing(), s.cashAndEquivalents()
                            ));
                        }
                    }
                }
                successCount++;
                // レート制限対策
                Thread.sleep(300);
            } catch (Exception e) {
                System.err.println("財務情報取得失敗 [" + jquantsCode + "]: " + e.getMessage());
                errorCount++;
            }
        }

        System.out.println("財務情報取得完了: 成功=" + successCount + " 件、失敗=" + errorCount + " 件");
    }

    private int parseIntOption(String[] args, String option, int defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(option)) {
                try {
                    return Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(option + " の値が不正です: " + args[i + 1]);
                }
            }
        }
        return defaultValue;
    }

    private String parseStringOption(String[] args, String option) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(option)) {
                return args[i + 1];
            }
        }
        return null;
    }
}
