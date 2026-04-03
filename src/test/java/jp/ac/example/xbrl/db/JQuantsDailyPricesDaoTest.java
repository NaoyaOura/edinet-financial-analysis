package jp.ac.example.xbrl.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JQuantsDailyPricesDaoTest {

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

    private JQuantsDailyPricesDao.DailyPriceRecord sampleRecord(String code, String date, double close) {
        return new JQuantsDailyPricesDao.DailyPriceRecord(
            code, date, close - 10, close + 10, close - 20, close, 1000000.0, close, 1000000.0
        );
    }

    @Test
    void upsertとfindByCodeAndDateRangeができること() throws Exception {
        try (Connection conn = dbManager.getConnection()) {
            JQuantsDailyPricesDao dao = new JQuantsDailyPricesDao(conn);
            dao.upsert(sampleRecord("72030", "2023-04-03", 1800.0));
            dao.upsert(sampleRecord("72030", "2023-04-04", 1820.0));
            dao.upsert(sampleRecord("72030", "2023-04-05", 1810.0));

            List<JQuantsDailyPricesDao.DailyPriceRecord> results =
                dao.findByCodeAndDateRange("72030", "2023-04-03", "2023-04-04");
            assertEquals(2, results.size());
            assertEquals("2023-04-03", results.get(0).date());
            assertEquals(1800.0, results.get(0).close());
        }
    }

    @Test
    void 期間外のデータが含まれないこと() throws Exception {
        try (Connection conn = dbManager.getConnection()) {
            JQuantsDailyPricesDao dao = new JQuantsDailyPricesDao(conn);
            dao.upsert(sampleRecord("72030", "2023-03-31", 1750.0));
            dao.upsert(sampleRecord("72030", "2023-04-03", 1800.0));
            dao.upsert(sampleRecord("72030", "2024-04-01", 2000.0));

            List<JQuantsDailyPricesDao.DailyPriceRecord> results =
                dao.findByCodeAndDateRange("72030", "2023-04-01", "2024-03-31");
            assertEquals(1, results.size());
            assertEquals("2023-04-03", results.get(0).date());
        }
    }

    @Test
    void upsertで既存レコードが更新されること() throws Exception {
        try (Connection conn = dbManager.getConnection()) {
            JQuantsDailyPricesDao dao = new JQuantsDailyPricesDao(conn);
            dao.upsert(sampleRecord("72030", "2023-04-03", 1800.0));
            dao.upsert(new JQuantsDailyPricesDao.DailyPriceRecord(
                "72030", "2023-04-03", null, null, null, 1900.0, null, 1900.0, null
            ));

            List<JQuantsDailyPricesDao.DailyPriceRecord> results =
                dao.findByCodeAndDateRange("72030", "2023-04-03", "2023-04-03");
            assertEquals(1, results.size());
            assertEquals(1900.0, results.get(0).close());
        }
    }
}
