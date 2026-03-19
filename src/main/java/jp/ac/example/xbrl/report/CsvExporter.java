package jp.ac.example.xbrl.report;

import jp.ac.example.xbrl.db.FinancialDataDao;
import jp.ac.example.xbrl.db.FinancialDataDao.FinancialRecord;
import jp.ac.example.xbrl.db.KeywordScoreDao;
import jp.ac.example.xbrl.db.KeywordScoreDao.KeywordScoreRecord;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * SQLiteのデータをCSVファイルとして出力するクラス。
 *
 * 出力種別:
 *   financial  : financial_data テーブル
 *   keywords   : keyword_scores テーブル
 *   merged     : financial_data + keyword_scores の結合データ（分析メインデータ）
 */
public class CsvExporter {

    private final Connection conn;

    public CsvExporter(Connection conn) {
        this.conn = conn;
    }

    /**
     * financial_data テーブルをCSV出力する。
     *
     * @param outputDir 出力ディレクトリ
     * @param fiscalYear 0 の場合は全年度
     * @return 出力したファイル
     */
    public File exportFinancial(File outputDir, int fiscalYear) throws SQLException, IOException {
        outputDir.mkdirs();
        String fileName = fiscalYear > 0
            ? "financial_data_" + fiscalYear + ".csv"
            : "financial_data.csv";
        File file = new File(outputDir, fileName);

        List<FinancialRecord> records = fiscalYear > 0
            ? new FinancialDataDao(conn).findByFiscalYear(fiscalYear)
            : findAllFinancial();

        try (BufferedWriter w = new BufferedWriter(new FileWriter(file))) {
            w.write("edinetCode,fiscalYear,netSales,grossProfit,operatingIncome,ordinaryIncome," +
                    "profitLoss,assets,currentAssets,currentLiabilities,liabilities,equity," +
                    "cashAndDeposits,inventories,sgaExpenses,personnelExpenses,numberOfEmployees," +
                    "researchAndDevelopment,software,intangibleAssets,capitalExpenditure," +
                    "operatingCashFlow,investingCashFlow");
            w.newLine();
            for (FinancialRecord r : records) {
                w.write(joinCsv(
                    r.edinetCode(), r.fiscalYear(),
                    r.netSales(), r.grossProfit(), r.operatingIncome(), r.ordinaryIncome(),
                    r.profitLoss(), r.assets(), r.currentAssets(), r.currentLiabilities(),
                    r.liabilities(), r.equity(), r.cashAndDeposits(), r.inventories(),
                    r.sgaExpenses(), r.personnelExpenses(), r.numberOfEmployees(),
                    r.researchAndDevelopment(), r.software(), r.intangibleAssets(),
                    r.capitalExpenditure(), r.operatingCashFlow(), r.investingCashFlow()
                ));
                w.newLine();
            }
        }
        return file;
    }

    /**
     * keyword_scores テーブルをCSV出力する。
     *
     * @param outputDir 出力ディレクトリ
     * @param fiscalYear 0 の場合は全年度
     * @return 出力したファイル
     */
    public File exportKeywords(File outputDir, int fiscalYear) throws SQLException, IOException {
        outputDir.mkdirs();
        String fileName = fiscalYear > 0
            ? "keyword_scores_" + fiscalYear + ".csv"
            : "keyword_scores.csv";
        File file = new File(outputDir, fileName);

        List<KeywordScoreRecord> records = fiscalYear > 0
            ? new KeywordScoreDao(conn).findByFiscalYear(fiscalYear)
            : findAllKeywords();

        try (BufferedWriter w = new BufferedWriter(new FileWriter(file))) {
            w.write("edinetCode,fiscalYear,genAiScore,aiScore,dxScore,totalScore,documentLength");
            w.newLine();
            for (KeywordScoreRecord r : records) {
                w.write(joinCsv(
                    r.edinetCode(), r.fiscalYear(),
                    r.genAiScore(), r.aiScore(), r.dxScore(), r.totalScore(), r.documentLength()
                ));
                w.newLine();
            }
        }
        return file;
    }

    /**
     * financial_data と keyword_scores を (edinetCode, fiscalYear) でJOINした
     * 統合CSVを出力する。分析ツールでそのまま使えるメインデータとして使用する。
     *
     * @param outputDir 出力ディレクトリ
     * @param fiscalYear 0 の場合は全年度
     * @return 出力したファイル
     */
    public File exportMerged(File outputDir, int fiscalYear) throws SQLException, IOException {
        outputDir.mkdirs();
        String fileName = fiscalYear > 0
            ? "merged_" + fiscalYear + ".csv"
            : "merged.csv";
        File file = new File(outputDir, fileName);

        String sql = """
            SELECT
                f.edinetCode, f.fiscalYear,
                f.netSales, f.grossProfit, f.operatingIncome, f.ordinaryIncome,
                f.profitLoss, f.assets, f.currentAssets, f.currentLiabilities,
                f.liabilities, f.equity, f.cashAndDeposits, f.inventories,
                f.sgaExpenses, f.personnelExpenses, f.numberOfEmployees,
                f.researchAndDevelopment, f.software, f.intangibleAssets,
                f.capitalExpenditure, f.operatingCashFlow, f.investingCashFlow,
                k.genAiScore, k.aiScore, k.dxScore, k.totalScore, k.documentLength
            FROM financial_data f
            INNER JOIN keyword_scores k
                ON f.edinetCode = k.edinetCode AND f.fiscalYear = k.fiscalYear
            """ + (fiscalYear > 0 ? "WHERE f.fiscalYear = " + fiscalYear : "") + """

            ORDER BY f.edinetCode, f.fiscalYear
            """;

        try (BufferedWriter w = new BufferedWriter(new FileWriter(file));
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            w.write("edinetCode,fiscalYear," +
                    "netSales,grossProfit,operatingIncome,ordinaryIncome," +
                    "profitLoss,assets,currentAssets,currentLiabilities," +
                    "liabilities,equity,cashAndDeposits,inventories," +
                    "sgaExpenses,personnelExpenses,numberOfEmployees," +
                    "researchAndDevelopment,software,intangibleAssets," +
                    "capitalExpenditure,operatingCashFlow,investingCashFlow," +
                    "genAiScore,aiScore,dxScore,totalScore,documentLength");
            w.newLine();

            while (rs.next()) {
                w.write(joinCsv(
                    rs.getString("edinetCode"), rs.getInt("fiscalYear"),
                    rs.getObject("netSales"), rs.getObject("grossProfit"),
                    rs.getObject("operatingIncome"), rs.getObject("ordinaryIncome"),
                    rs.getObject("profitLoss"), rs.getObject("assets"),
                    rs.getObject("currentAssets"), rs.getObject("currentLiabilities"),
                    rs.getObject("liabilities"), rs.getObject("equity"),
                    rs.getObject("cashAndDeposits"), rs.getObject("inventories"),
                    rs.getObject("sgaExpenses"), rs.getObject("personnelExpenses"),
                    rs.getObject("numberOfEmployees"),
                    rs.getObject("researchAndDevelopment"), rs.getObject("software"),
                    rs.getObject("intangibleAssets"), rs.getObject("capitalExpenditure"),
                    rs.getObject("operatingCashFlow"), rs.getObject("investingCashFlow"),
                    rs.getDouble("genAiScore"), rs.getDouble("aiScore"),
                    rs.getDouble("dxScore"), rs.getDouble("totalScore"),
                    rs.getInt("documentLength")
                ));
                w.newLine();
            }
        }
        return file;
    }

    private List<FinancialRecord> findAllFinancial() throws SQLException {
        String sql = "SELECT * FROM financial_data ORDER BY edinetCode, fiscalYear";
        List<FinancialRecord> results = new java.util.ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(new FinancialRecord(
                    rs.getString("edinetCode"), rs.getInt("fiscalYear"),
                    (Double) rs.getObject("netSales"), (Double) rs.getObject("grossProfit"),
                    (Double) rs.getObject("operatingIncome"), (Double) rs.getObject("ordinaryIncome"),
                    (Double) rs.getObject("profitLoss"), (Double) rs.getObject("assets"),
                    (Double) rs.getObject("currentAssets"), (Double) rs.getObject("currentLiabilities"),
                    (Double) rs.getObject("liabilities"), (Double) rs.getObject("equity"),
                    (Double) rs.getObject("cashAndDeposits"), (Double) rs.getObject("inventories"),
                    (Double) rs.getObject("sgaExpenses"), (Double) rs.getObject("personnelExpenses"),
                    (Integer) rs.getObject("numberOfEmployees"),
                    (Double) rs.getObject("researchAndDevelopment"), (Double) rs.getObject("software"),
                    (Double) rs.getObject("intangibleAssets"), (Double) rs.getObject("capitalExpenditure"),
                    (Double) rs.getObject("operatingCashFlow"), (Double) rs.getObject("investingCashFlow")
                ));
            }
        }
        return results;
    }

    private List<KeywordScoreRecord> findAllKeywords() throws SQLException {
        String sql = "SELECT * FROM keyword_scores ORDER BY edinetCode, fiscalYear";
        List<KeywordScoreRecord> results = new java.util.ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(new KeywordScoreRecord(
                    rs.getString("edinetCode"), rs.getInt("fiscalYear"),
                    rs.getDouble("genAiScore"), rs.getDouble("aiScore"),
                    rs.getDouble("dxScore"), rs.getDouble("totalScore"),
                    rs.getInt("documentLength")
                ));
            }
        }
        return results;
    }

    /**
     * 可変長引数をCSV行に変換する。NULLは空文字として出力する。
     */
    private String joinCsv(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(',');
            Object v = values[i];
            if (v == null) {
                // NULLは空文字
            } else {
                String s = v.toString();
                // カンマ・改行・ダブルクォートを含む場合はクォートで囲む
                if (s.contains(",") || s.contains("\n") || s.contains("\"")) {
                    sb.append('"').append(s.replace("\"", "\"\"")).append('"');
                } else {
                    sb.append(s);
                }
            }
        }
        return sb.toString();
    }
}
