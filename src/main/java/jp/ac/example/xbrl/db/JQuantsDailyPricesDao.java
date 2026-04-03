package jp.ac.example.xbrl.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * jquants_daily_prices テーブルのCRUDを担うクラス。
 */
public class JQuantsDailyPricesDao {

    public record DailyPriceRecord(
        String code,
        String date,
        Double open,
        Double high,
        Double low,
        Double close,
        Double volume,
        Double adjustmentClose,
        Double adjustmentVolume
    ) {}

    private final Connection conn;

    public JQuantsDailyPricesDao(Connection conn) {
        this.conn = conn;
    }

    /**
     * 日次株価を upsert する。
     */
    public void upsert(DailyPriceRecord r) throws SQLException {
        String sql = """
            INSERT INTO jquants_daily_prices (
                code, date, open, high, low, close, volume, adjustmentClose, adjustmentVolume
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(code, date) DO UPDATE SET
                open             = excluded.open,
                high             = excluded.high,
                low              = excluded.low,
                close            = excluded.close,
                volume           = excluded.volume,
                adjustmentClose  = excluded.adjustmentClose,
                adjustmentVolume = excluded.adjustmentVolume
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.code());
            ps.setString(2, r.date());
            setDoubleOrNull(ps, 3, r.open());
            setDoubleOrNull(ps, 4, r.high());
            setDoubleOrNull(ps, 5, r.low());
            setDoubleOrNull(ps, 6, r.close());
            setDoubleOrNull(ps, 7, r.volume());
            setDoubleOrNull(ps, 8, r.adjustmentClose());
            setDoubleOrNull(ps, 9, r.adjustmentVolume());
            ps.executeUpdate();
        }
    }

    /**
     * 指定銘柄・期間の日次株価を取得する。
     *
     * @param code J-Quants銘柄コード
     * @param from 開始日（YYYY-MM-DD、含む）
     * @param to   終了日（YYYY-MM-DD、含む）
     */
    public List<DailyPriceRecord> findByCodeAndDateRange(String code, String from, String to) throws SQLException {
        String sql = """
            SELECT * FROM jquants_daily_prices
            WHERE code = ? AND date >= ? AND date <= ?
            ORDER BY date
            """;
        List<DailyPriceRecord> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, from);
            ps.setString(3, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new DailyPriceRecord(
                        rs.getString("code"),
                        rs.getString("date"),
                        (Double) rs.getObject("open"),
                        (Double) rs.getObject("high"),
                        (Double) rs.getObject("low"),
                        (Double) rs.getObject("close"),
                        (Double) rs.getObject("volume"),
                        (Double) rs.getObject("adjustmentClose"),
                        (Double) rs.getObject("adjustmentVolume")
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
}
