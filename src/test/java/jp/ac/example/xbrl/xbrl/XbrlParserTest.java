package jp.ac.example.xbrl.xbrl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class XbrlParserTest {

    @TempDir
    Path tempDir;

    private XbrlParser parser;

    @BeforeEach
    void setUp() {
        parser = new XbrlParser();
    }

    @Test
    void 連結財務諸表の売上高が正しく抽出されること() throws Exception {
        File xbrl = createXbrl("""
            <xbrl xmlns:jppfs_cor="http://xbrl.ifrs.org/taxonomy/2014-03-05/jppfs_cor"
                  xmlns:xbrli="http://www.xbrl.org/2003/instance">
              <jppfs_cor:NetSales contextRef="CurrentYearConsolidatedDuration"
                  unitRef="JPY" decimals="0">500000000</jppfs_cor:NetSales>
              <jppfs_cor:NetSales contextRef="CurrentYearNonConsolidatedDuration"
                  unitRef="JPY" decimals="0">300000000</jppfs_cor:NetSales>
            </xbrl>
            """);

        Map<String, Double> values = parser.parse(xbrl);
        // 連結を優先するため500000000が返る
        assertEquals(500_000_000.0, values.get("NetSales"));
    }

    @Test
    void decimalsがマイナスでも値がそのまま返されること() throws Exception {
        // EDINET XBRLでは unitRef="JPY" の値はすでに円単位。
        // decimals="-3" は精度指標（千円単位で有効）であり、スケーリング係数ではない。
        // 実データ例: decimals="-3" で値 22514514000（円）
        File xbrl = createXbrl("""
            <xbrl xmlns:jppfs_cor="http://xbrl.ifrs.org/taxonomy/2014-03-05/jppfs_cor">
              <jppfs_cor:NetSales contextRef="CurrentYearConsolidatedDuration"
                  unitRef="JPY" decimals="-3">500000000</jppfs_cor:NetSales>
            </xbrl>
            """);

        Map<String, Double> values = parser.parse(xbrl);
        assertEquals(500_000_000.0, values.get("NetSales"));
    }

    @Test
    void decimalsがマイナス6でも値がそのまま返されること() throws Exception {
        // decimals="-6" は百万円単位での精度指標。値自体は円換算済み。
        File xbrl = createXbrl("""
            <xbrl xmlns:jppfs_cor="http://xbrl.ifrs.org/taxonomy/2014-03-05/jppfs_cor">
              <jppfs_cor:OperatingIncome contextRef="CurrentYearConsolidatedDuration"
                  unitRef="JPY" decimals="-6">50000000000</jppfs_cor:OperatingIncome>
            </xbrl>
            """);

        Map<String, Double> values = parser.parse(xbrl);
        assertEquals(50_000_000_000.0, values.get("OperatingIncome"));
    }

    @Test
    void 前期比較データは除外されること() throws Exception {
        File xbrl = createXbrl("""
            <xbrl xmlns:jppfs_cor="http://xbrl.ifrs.org/taxonomy/2014-03-05/jppfs_cor">
              <jppfs_cor:NetSales contextRef="PriorYearConsolidatedDuration"
                  unitRef="JPY" decimals="0">999999</jppfs_cor:NetSales>
              <jppfs_cor:NetSales contextRef="CurrentYearConsolidatedDuration"
                  unitRef="JPY" decimals="0">500000000</jppfs_cor:NetSales>
            </xbrl>
            """);

        Map<String, Double> values = parser.parse(xbrl);
        assertEquals(500_000_000.0, values.get("NetSales"));
    }

    @Test
    void XBRLファイルがディレクトリから正しく検索されること() throws Exception {
        File subDir = new File(tempDir.toFile(), "XBRL");
        subDir.mkdirs();
        File xbrlFile = new File(subDir, "report.xbrl");
        try (FileWriter fw = new FileWriter(xbrlFile)) {
            fw.write("<xbrl/>");
        }

        File found = parser.findXbrlFile(tempDir.toFile());
        assertNotNull(found);
        assertEquals("report.xbrl", found.getName());
    }

    @Test
    void XBRLファイルが存在しない場合はnullを返すこと() {
        File found = parser.findXbrlFile(tempDir.toFile());
        assertNull(found);
    }

    @Test
    void parseValueが数値以外の文字列でnullを返すこと() {
        assertNull(parser.parseValue("N/A", "0"));
        assertNull(parser.parseValue("", "0"));
        assertNull(parser.parseValue("abc", "-3"));
    }

    // --- テスト用ヘルパー ---

    private File createXbrl(String content) throws Exception {
        File file = new File(tempDir.toFile(), "test.xbrl");
        try (FileWriter fw = new FileWriter(file)) {
            fw.write(content);
        }
        return file;
    }
}
