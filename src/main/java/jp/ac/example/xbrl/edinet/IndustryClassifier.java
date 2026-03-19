package jp.ac.example.xbrl.edinet;

/**
 * EDINET の業種コード（東証33業種分類）を研究用カテゴリに変換するクラス。
 *
 * 研究対象:
 *   RETAIL: 6100 = 小売業
 *   IT    : 5250 = 情報・通信業
 *
 * それ以外はすべて UNKNOWN として分析対象外とする。
 *
 * コード一覧参考: https://jpx-jquants.com/ja/spec/eq-master/sector33code
 */
public class IndustryClassifier {

    /** 小売業（東証33業種コード） */
    public static final String CODE_RETAIL = "6100";
    /** 情報・通信業（東証33業種コード） */
    public static final String CODE_IT     = "5250";

    public static final String CATEGORY_RETAIL   = "RETAIL";
    public static final String CATEGORY_IT       = "IT";
    public static final String CATEGORY_UNKNOWN  = "UNKNOWN";

    /**
     * 業種コードをカテゴリ文字列に変換する。
     *
     * @param industryCode EDINET API の industryCode フィールド値
     * @return "RETAIL" / "IT" / "UNKNOWN"
     */
    public static String classify(String industryCode) {
        if (industryCode == null || industryCode.isBlank()) {
            return CATEGORY_UNKNOWN;
        }
        return switch (industryCode.trim()) {
            case CODE_RETAIL -> CATEGORY_RETAIL;
            case CODE_IT     -> CATEGORY_IT;
            default          -> CATEGORY_UNKNOWN;
        };
    }

    private IndustryClassifier() {}
}
