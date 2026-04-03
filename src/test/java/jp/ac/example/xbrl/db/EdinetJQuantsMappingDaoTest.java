package jp.ac.example.xbrl.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EdinetJQuantsMappingDaoTest {

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

    @Test
    void upsertとfindJquantsCodeができること() throws Exception {
        try (Connection conn = dbManager.getConnection()) {
            EdinetJQuantsMappingDao dao = new EdinetJQuantsMappingDao(conn);
            dao.upsert("E12345", "72030");

            String jquantsCode = dao.findJquantsCode("E12345");
            assertEquals("72030", jquantsCode);
        }
    }

    @Test
    void 存在しないEDINETコードはnullを返すこと() throws Exception {
        try (Connection conn = dbManager.getConnection()) {
            EdinetJQuantsMappingDao dao = new EdinetJQuantsMappingDao(conn);
            assertNull(dao.findJquantsCode("E99999"));
        }
    }

    @Test
    void upsertで既存マッピングが更新されること() throws Exception {
        try (Connection conn = dbManager.getConnection()) {
            EdinetJQuantsMappingDao dao = new EdinetJQuantsMappingDao(conn);
            dao.upsert("E12345", "72030");
            dao.upsert("E12345", "72031");

            assertEquals("72031", dao.findJquantsCode("E12345"));
        }
    }

    @Test
    void findAllで全マッピングが取得できること() throws Exception {
        try (Connection conn = dbManager.getConnection()) {
            EdinetJQuantsMappingDao dao = new EdinetJQuantsMappingDao(conn);
            dao.upsert("E10001", "10010");
            dao.upsert("E10002", "10020");
            dao.upsert("E10003", "10030");

            List<EdinetJQuantsMappingDao.MappingRecord> all = dao.findAll();
            assertEquals(3, all.size());
        }
    }
}
