package jp.ac.example.xbrl.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * edinet_jquants_mapping テーブルのCRUDを担うクラス。
 *
 * EDINETコード（edinetCode）と J-Quants 銘柄コード（jquantsCode）の対応を管理する。
 */
public class EdinetJQuantsMappingDao {

    public record MappingRecord(String edinetCode, String jquantsCode) {}

    private final Connection conn;

    public EdinetJQuantsMappingDao(Connection conn) {
        this.conn = conn;
    }

    /**
     * マッピングを upsert する。
     */
    public void upsert(String edinetCode, String jquantsCode) throws SQLException {
        String sql = """
            INSERT INTO edinet_jquants_mapping (edinetCode, jquantsCode)
            VALUES (?, ?)
            ON CONFLICT(edinetCode) DO UPDATE SET
                jquantsCode = excluded.jquantsCode
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, edinetCode);
            ps.setString(2, jquantsCode);
            ps.executeUpdate();
        }
    }

    /**
     * EDINETコードで銘柄コードを検索する。見つからない場合は null を返す。
     */
    public String findJquantsCode(String edinetCode) throws SQLException {
        String sql = "SELECT jquantsCode FROM edinet_jquants_mapping WHERE edinetCode = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, edinetCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("jquantsCode");
                }
            }
        }
        return null;
    }

    /**
     * 全マッピングを取得する。
     */
    public List<MappingRecord> findAll() throws SQLException {
        String sql = "SELECT edinetCode, jquantsCode FROM edinet_jquants_mapping ORDER BY edinetCode";
        List<MappingRecord> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(new MappingRecord(rs.getString("edinetCode"), rs.getString("jquantsCode")));
            }
        }
        return results;
    }
}
