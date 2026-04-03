package jp.ac.example.xbrl.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JQuantsFinStatementsDaoTest {

    private File tempDbFile;
    private DatabaseManager dbManager;

    @BeforeEach
    void setUp() throws Exception {
        tempDbFile = File.createTempFile("xbrl_test_", ".db");
        tempDbFile.deleteOnExit();
        dbManager = new DatabaseManager(tempDbFile.getAbsolutePath());
        dbManager.initializeSchema();
    }

    @AfterEach
    void tearDown() {
        if (tempDbFile != null) tempDbFile.delete();
    }

    private JQuantsFinStatementsDao.FinStatementRecord sampleRecord(String code, int fiscalYear) {
        // disclosedDate は年度ごとに異なる（例: 2023年度 → "2023-05-11"、2022年度 → "2022-05-11"）
        String disclosedDate = fiscalYear + "-05-11";
        return new JQuantsFinStatementsDao.FinStatementRecord(
            code, disclosedDate, "FY", fiscalYear,
            1000000.0, 80000.0, 85000.0, 60000.0, 5000000.0, 2000000.0
        );
    }

    @Test
    void upsertとfindByLocalCodeAndFiscalYearができること() throws Exception {
        try (Connection conn = dbManager.getConnection()) {
            JQuantsFinStatementsDao dao = new JQuantsFinStatementsDao(conn);
            dao.upsert(sampleRecord("72030", 2023));
            dao.upsert(sampleRecord("72030", 2022));

            List<JQuantsFinStatementsDao.FinStatementRecord> results =
                dao.findByLocalCodeAndFiscalYear("72030", 2023);
            assertEquals(1, results.size());
            assertEquals(2023, results.get(0).fiscalYear());
            assertEquals(1000000.0, results.get(0).netSales());
        }
    }

    @Test
    void upsertで既存レコードが更新されること() throws Exception {
        try (Connection conn = dbManager.getConnection()) {
            JQuantsFinStatementsDao dao = new JQuantsFinStatementsDao(conn);
            dao.upsert(sampleRecord("72030", 2023));
            dao.upsert(new JQuantsFinStatementsDao.FinStatementRecord(
                "72030", "2023-05-11", "FY", 2023,
                2000000.0, null, null, null, null, null
            ));

            List<JQuantsFinStatementsDao.FinStatementRecord> results =
                dao.findByLocalCodeAndFiscalYear("72030", 2023);
            assertEquals(1, results.size());
            assertEquals(2000000.0, results.get(0).netSales());
        }
    }

    @Test
    void 存在しない年度は空リストが返ること() throws Exception {
        try (Connection conn = dbManager.getConnection()) {
            JQuantsFinStatementsDao dao = new JQuantsFinStatementsDao(conn);
            List<JQuantsFinStatementsDao.FinStatementRecord> results =
                dao.findByLocalCodeAndFiscalYear("72030", 2020);
            assertTrue(results.isEmpty());
        }
    }
}
