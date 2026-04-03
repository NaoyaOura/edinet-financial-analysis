package jp.ac.example.xbrl.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * jquants_listed_info テーブルのCRUDを担うクラス。
 */
public class JQuantsListedInfoDao {

    public record ListedInfoRecord(
        String code,
        String companyName,
        String companyNameEn,
        String sector33Code,
        String sector33CodeName,
        String sector17Code,
        String sector17CodeName,
        String marketCode,
        String marketCodeName,
        String scaleCategory
    ) {}

    private final Connection conn;

    public JQuantsListedInfoDao(Connection conn) {
        this.conn = conn;
    }

    /**
     * 上場銘柄情報を upsert する。
     */
    public void upsert(ListedInfoRecord r) throws SQLException {
        String sql = """
            INSERT INTO jquants_listed_info (
                code, companyName, companyNameEn,
                sector33Code, sector33CodeName,
                sector17Code, sector17CodeName,
                marketCode, marketCodeName, scaleCategory, updatedAt
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(code) DO UPDATE SET
                companyName      = excluded.companyName,
                companyNameEn    = excluded.companyNameEn,
                sector33Code     = excluded.sector33Code,
                sector33CodeName = excluded.sector33CodeName,
                sector17Code     = excluded.sector17Code,
                sector17CodeName = excluded.sector17CodeName,
                marketCode       = excluded.marketCode,
                marketCodeName   = excluded.marketCodeName,
                scaleCategory    = excluded.scaleCategory,
                updatedAt        = excluded.updatedAt
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.code());
            ps.setString(2, r.companyName());
            ps.setString(3, r.companyNameEn());
            ps.setString(4, r.sector33Code());
            ps.setString(5, r.sector33CodeName());
            ps.setString(6, r.sector17Code());
            ps.setString(7, r.sector17CodeName());
            ps.setString(8, r.marketCode());
            ps.setString(9, r.marketCodeName());
            ps.setString(10, r.scaleCategory());
            ps.setString(11, LocalDateTime.now().toString());
            ps.executeUpdate();
        }
    }

    /**
     * 全件取得する。
     */
    public List<ListedInfoRecord> findAll() throws SQLException {
        String sql = "SELECT * FROM jquants_listed_info ORDER BY code";
        List<ListedInfoRecord> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(new ListedInfoRecord(
                    rs.getString("code"),
                    rs.getString("companyName"),
                    rs.getString("companyNameEn"),
                    rs.getString("sector33Code"),
                    rs.getString("sector33CodeName"),
                    rs.getString("sector17Code"),
                    rs.getString("sector17CodeName"),
                    rs.getString("marketCode"),
                    rs.getString("marketCodeName"),
                    rs.getString("scaleCategory")
                ));
            }
        }
        return results;
    }

    /**
     * 銘柄コードで検索する。
     */
    public ListedInfoRecord findByCode(String code) throws SQLException {
        String sql = "SELECT * FROM jquants_listed_info WHERE code = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new ListedInfoRecord(
                        rs.getString("code"),
                        rs.getString("companyName"),
                        rs.getString("companyNameEn"),
                        rs.getString("sector33Code"),
                        rs.getString("sector33CodeName"),
                        rs.getString("sector17Code"),
                        rs.getString("sector17CodeName"),
                        rs.getString("marketCode"),
                        rs.getString("marketCodeName"),
                        rs.getString("scaleCategory")
                    );
                }
            }
        }
        return null;
    }
}
