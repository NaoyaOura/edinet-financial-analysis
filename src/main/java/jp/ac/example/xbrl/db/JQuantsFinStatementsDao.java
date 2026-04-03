package jp.ac.example.xbrl.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * jquants_fin_statements テーブルのCRUDを担うクラス。
 */
public class JQuantsFinStatementsDao {

    public record FinStatementRecord(
        String localCode,
        String disclosedDate,
        String typeOfDocument,
        Integer fiscalYear,
        Double netSales,
        Double operatingProfit,
        Double ordinaryProfit,
        Double profit,
        Double totalAssets,
        Double equity
    ) {}

    private final Connection conn;

    public JQuantsFinStatementsDao(Connection conn) {
        this.conn = conn;
    }

    /**
     * 財務情報を upsert する。
     */
    public void upsert(FinStatementRecord r) throws SQLException {
        String sql = """
            INSERT INTO jquants_fin_statements (
                localCode, disclosedDate, typeOfDocument, fiscalYear,
                netSales, operatingProfit, ordinaryProfit, profit, totalAssets, equity
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(localCode, disclosedDate, typeOfDocument) DO UPDATE SET
                fiscalYear      = excluded.fiscalYear,
                netSales        = excluded.netSales,
                operatingProfit = excluded.operatingProfit,
                ordinaryProfit  = excluded.ordinaryProfit,
                profit          = excluded.profit,
                totalAssets     = excluded.totalAssets,
                equity          = excluded.equity
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.localCode());
            ps.setString(2, r.disclosedDate());
            ps.setString(3, r.typeOfDocument());
            setIntOrNull(ps, 4, r.fiscalYear());
            setDoubleOrNull(ps, 5, r.netSales());
            setDoubleOrNull(ps, 6, r.operatingProfit());
            setDoubleOrNull(ps, 7, r.ordinaryProfit());
            setDoubleOrNull(ps, 8, r.profit());
            setDoubleOrNull(ps, 9, r.totalAssets());
            setDoubleOrNull(ps, 10, r.equity());
            ps.executeUpdate();
        }
    }

    /**
     * 指定銘柄コード・会計年度の財務情報を取得する。
     */
    public List<FinStatementRecord> findByLocalCodeAndFiscalYear(String localCode, int fiscalYear) throws SQLException {
        String sql = """
            SELECT * FROM jquants_fin_statements
            WHERE localCode = ? AND fiscalYear = ?
            ORDER BY disclosedDate
            """;
        List<FinStatementRecord> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, localCode);
            ps.setInt(2, fiscalYear);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new FinStatementRecord(
                        rs.getString("localCode"),
                        rs.getString("disclosedDate"),
                        rs.getString("typeOfDocument"),
                        (Integer) rs.getObject("fiscalYear"),
                        (Double) rs.getObject("netSales"),
                        (Double) rs.getObject("operatingProfit"),
                        (Double) rs.getObject("ordinaryProfit"),
                        (Double) rs.getObject("profit"),
                        (Double) rs.getObject("totalAssets"),
                        (Double) rs.getObject("equity")
                    ));
                }
            }
        }
        return results;
    }

    private void setDoubleOrNull(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.REAL);
        } else {
            ps.setDouble(index, value);
        }
    }

    private void setIntOrNull(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }
}
