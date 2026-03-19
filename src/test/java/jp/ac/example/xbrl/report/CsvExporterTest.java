package jp.ac.example.xbrl.report;

import jp.ac.example.xbrl.db.DatabaseManager;
import jp.ac.example.xbrl.db.FinancialDataDao;
import jp.ac.example.xbrl.db.KeywordScoreDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvExporterTest {

    @TempDir
    File tempDir;

    private File dbFile;
    private DatabaseManager dbManager;
    private Connection conn;
    private File outputDir;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = new File(tempDir, "test.db");
        dbManager = new DatabaseManager(dbFile.getAbsolutePath());
        dbManager.initializeSchema();
        conn = dbManager.getConnection();
        outputDir = new File(tempDir, "output");

        // テストデータ投入
        FinancialDataDao financialDao = new FinancialDataDao(conn);
        financialDao.upsert(new FinancialDataDao.FinancialRecord(
            "E00001", 2023,
            1_000_000.0, 300_000.0, 100_000.0, 90_000.0, 70_000.0,
            500_000.0, 200_000.0, 100_000.0, 250_000.0, 250_000.0, 50_000.0,
            80_000.0, 150_000.0, 30_000.0, 1000,
            5_000.0, 10_000.0, 15_000.0, 20_000.0,
            90_000.0, -20_000.0
        ));
        financialDao.upsert(new FinancialDataDao.FinancialRecord(
            "E00002", 2023,
            2_000_000.0, null, 200_000.0, null, null,
            null, null, null, null, null, null,
            null, null, null, null,
            null, null, null, null,
            null, null
        ));

        KeywordScoreDao keywordDao = new KeywordScoreDao(conn);
        keywordDao.upsert("E00001", 2023, 10.5, 5.2, 3.1, 18.8, 5000);
        keywordDao.upsert("E00002", 2023, 0.0, 0.0, 0.0, 0.0, 100);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) conn.close();
    }

    // --- exportFinancial ---

    @Test
    void exportFinancial_年度指定でCSVが出力される() throws Exception {
        CsvExporter exporter = new CsvExporter(conn);
        File f = exporter.exportFinancial(outputDir, 2023);

        assertEquals("financial_data_2023.csv", f.getName());
        List<String> lines = Files.readAllLines(f.toPath());
        assertEquals(3, lines.size(), "ヘッダ + 2データ行");
        assertTrue(lines.get(0).startsWith("edinetCode,fiscalYear"), "ヘッダ行の確認");
        assertTrue(lines.get(1).contains("E00001"), "1件目のデータ");
        assertTrue(lines.get(2).contains("E00002"), "2件目のデータ");
    }

    @Test
    void exportFinancial_全年度出力はサフィックスなし() throws Exception {
        CsvExporter exporter = new CsvExporter(conn);
        File f = exporter.exportFinancial(outputDir, 0);
        assertEquals("financial_data.csv", f.getName());
    }

    @Test
    void exportFinancial_NULL値は空文字として出力される() throws Exception {
        CsvExporter exporter = new CsvExporter(conn);
        File f = exporter.exportFinancial(outputDir, 2023);
        List<String> lines = Files.readAllLines(f.toPath());

        // E00002 は grossProfit が null → カンマが連続する
        String e00002Line = lines.stream()
            .filter(l -> l.startsWith("E00002"))
            .findFirst().orElseThrow();
        assertTrue(e00002Line.contains(",,"), "NULLは空文字（カンマ連続）として出力されること");
    }

    // --- exportKeywords ---

    @Test
    void exportKeywords_年度指定でCSVが出力される() throws Exception {
        CsvExporter exporter = new CsvExporter(conn);
        File f = exporter.exportKeywords(outputDir, 2023);

        assertEquals("keyword_scores_2023.csv", f.getName());
        List<String> lines = Files.readAllLines(f.toPath());
        assertEquals(3, lines.size(), "ヘッダ + 2データ行");
        assertTrue(lines.get(0).contains("genAiScore"), "ヘッダにgenAiScoreが含まれること");
        assertTrue(lines.get(1).contains("E00001"));
    }

    @Test
    void exportKeywords_全年度出力はサフィックスなし() throws Exception {
        CsvExporter exporter = new CsvExporter(conn);
        File f = exporter.exportKeywords(outputDir, 0);
        assertEquals("keyword_scores.csv", f.getName());
    }

    // --- exportMerged ---

    @Test
    void exportMerged_両テーブルのJOIN結果が出力される() throws Exception {
        CsvExporter exporter = new CsvExporter(conn);
        File f = exporter.exportMerged(outputDir, 2023);

        assertEquals("merged_2023.csv", f.getName());
        List<String> lines = Files.readAllLines(f.toPath());
        assertEquals(3, lines.size(), "ヘッダ + 2データ行（両テーブルにE00001,E00002が存在）");

        // ヘッダに財務指標とキーワードスコアの両方が含まれること
        assertTrue(lines.get(0).contains("netSales"), "財務指標列が含まれること");
        assertTrue(lines.get(0).contains("totalScore"), "キーワードスコア列が含まれること");
    }

    @Test
    void exportMerged_全年度はサフィックスなし() throws Exception {
        CsvExporter exporter = new CsvExporter(conn);
        File f = exporter.exportMerged(outputDir, 0);
        assertEquals("merged.csv", f.getName());
    }

    @Test
    void exportMerged_片方にしかないデータは出力されない() throws Exception {
        // E00003 を financial_data にのみ追加（keyword_scores には存在しない）
        new FinancialDataDao(conn).upsert(new FinancialDataDao.FinancialRecord(
            "E00003", 2023,
            500_000.0, null, null, null, null,
            null, null, null, null, null, null,
            null, null, null, null,
            null, null, null, null, null, null
        ));

        CsvExporter exporter = new CsvExporter(conn);
        File f = exporter.exportMerged(outputDir, 2023);
        List<String> lines = Files.readAllLines(f.toPath());

        // E00003 はkeyword_scoresにないのでINNER JOINで除外される
        long e00003Count = lines.stream().filter(l -> l.contains("E00003")).count();
        assertEquals(0, e00003Count, "片方にしか存在しないレコードはmergedに含まれないこと");
        assertEquals(3, lines.size(), "ヘッダ + E00001 + E00002 の3行");
    }

    // --- 出力先ディレクトリの自動作成 ---

    @Test
    void 出力先ディレクトリが存在しなくても自動作成される() throws Exception {
        File nonExistDir = new File(tempDir, "new/nested/dir");
        assertFalse(nonExistDir.exists());

        CsvExporter exporter = new CsvExporter(conn);
        File f = exporter.exportFinancial(nonExistDir, 2023);

        assertTrue(nonExistDir.exists(), "ディレクトリが自動作成されること");
        assertTrue(f.exists(), "CSVファイルが作成されること");
    }
}
