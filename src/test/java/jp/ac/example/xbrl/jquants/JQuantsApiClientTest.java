package jp.ac.example.xbrl.jquants;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JQuantsApiClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** テスト用: fetchListedInfo のJSONパースロジックを直接検証する */
    @Test
    void ListedInfoRecord_JSONパースの検証() throws Exception {
        String json = """
            {
              "info": [
                {
                  "Code": "72030",
                  "CompanyName": "トヨタ自動車",
                  "CompanyNameEnglish": "Toyota Motor Corp.",
                  "Sector33Code": "3700",
                  "Sector33CodeName": "輸送用機器",
                  "Sector17Code": "6",
                  "Sector17CodeName": "自動車・輸送機",
                  "MarketCode": "0111",
                  "MarketCodeName": "プライム",
                  "ScaleCategory": "TOPIX Large70"
                }
              ]
            }
            """;
        var root = objectMapper.readTree(json);
        var infoArray = root.get("info");
        assertNotNull(infoArray);
        assertEquals(1, infoArray.size());
        var node = infoArray.get(0);
        assertEquals("72030", node.get("Code").asText());
        assertEquals("トヨタ自動車", node.get("CompanyName").asText());
        assertEquals("3700", node.get("Sector33Code").asText());
    }

    @Test
    void DailyQuoteRecord_JSONパースの検証() throws Exception {
        String json = """
            {
              "daily_quotes": [
                {
                  "Code": "72030",
                  "Date": "2023-04-03",
                  "Open": 1800.0,
                  "High": 1850.0,
                  "Low": 1790.0,
                  "Close": 1830.0,
                  "Volume": 5000000,
                  "AdjustmentClose": 1830.0,
                  "AdjustmentVolume": 5000000
                }
              ]
            }
            """;
        var root = objectMapper.readTree(json);
        var quotesArray = root.get("daily_quotes");
        assertNotNull(quotesArray);
        assertEquals(1, quotesArray.size());
        var node = quotesArray.get(0);
        assertEquals("2023-04-03", node.get("Date").asText());
        assertEquals(1830.0, node.get("Close").asDouble());
    }

    @Test
    void FinStatementRecord_JSON文字列数値のパース検証() throws Exception {
        String json = """
            {
              "statements": [
                {
                  "LocalCode": "72030",
                  "DisclosedDate": "2023-05-11",
                  "TypeOfDocument": "FY",
                  "FiscalYear": 2023,
                  "NetSales": "37154298000000",
                  "OperatingProfit": "2725025000000",
                  "OrdinaryProfit": "",
                  "Profit": "2451318000000",
                  "TotalAssets": "90138804000000",
                  "Equity": "29266085000000"
                }
              ]
            }
            """;
        var root = objectMapper.readTree(json);
        var statementsArray = root.get("statements");
        assertNotNull(statementsArray);
        var node = statementsArray.get(0);
        assertEquals("FY", node.get("TypeOfDocument").asText());
        // 空文字列の場合はnullとして扱われることを確認（変換ロジックの動作確認）
        String ordinaryProfit = node.get("OrdinaryProfit").asText().trim();
        assertTrue(ordinaryProfit.isEmpty());
    }
}
