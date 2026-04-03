package jp.ac.example.xbrl.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JQuantsListedInfoDaoTest {

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

    private JQuantsListedInfoDao.ListedInfoRecord sampleRecord(String code) {
        return new JQuantsListedInfoDao.ListedInfoRecord(
            code, "テスト企業" + code, "Test Corp " + code,
            "6100", "小売業", "17", "小売",
            "0111", "プライム", "TOPIX Large70"
        );
    }

    @Test
    void upsertとfindAllができること() throws Exception {
        try (Connection conn = dbManager.getConnection()) {
            JQuantsListedInfoDao dao = new JQuantsListedInfoDao(conn);
            dao.upsert(sampleRecord("10010"));
            dao.upsert(sampleRecord("10020"));

            List<JQuantsListedInfoDao.ListedInfoRecord> all = dao.findAll();
            assertEquals(2, all.size());
            assertEquals("10010", all.get(0).code());
        }
    }

    @Test
    void upsertで既存レコードが更新されること() throws Exception {
        try (Connection conn = dbManager.getConnection()) {
            JQuantsListedInfoDao dao = new JQuantsListedInfoDao(conn);
            dao.upsert(sampleRecord("10010"));
            dao.upsert(new JQuantsListedInfoDao.ListedInfoRecord(
                "10010", "更新後企業名", null, "5250", "情報通信業",
                null, null, null, null, null
            ));

            JQuantsListedInfoDao.ListedInfoRecord updated = dao.findByCode("10010");
            assertNotNull(updated);
            assertEquals("更新後企業名", updated.companyName());
            assertEquals("5250", updated.sector33Code());
        }
    }

    @Test
    void findByCodeで存在しない場合nullが返ること() throws Exception {
        try (Connection conn = dbManager.getConnection()) {
            JQuantsListedInfoDao dao = new JQuantsListedInfoDao(conn);
            assertNull(dao.findByCode("99999"));
        }
    }
}
