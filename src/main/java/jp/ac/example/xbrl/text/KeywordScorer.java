package jp.ac.example.xbrl.text;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * テキストからキーワード密度スコアを算出するクラス。
 *
 * キーワード密度 = Σ(出現回数 × 重み) ÷ 総文字数 × 10,000
 *
 * カテゴリと重み:
 *   生成AI（強・重み3）: 生成AI、ChatGPT、GPT、LLM、大規模言語モデル 等
 *   AI全般（中・重み2）: AI、人工知能、機械学習、ディープラーニング 等
 *   DX（弱・重み1）:     DX、デジタルトランスフォーメーション 等
 */
public class KeywordScorer {

    /** カテゴリ名の定数 */
    public static final String CATEGORY_GEN_AI = "GEN_AI";
    public static final String CATEGORY_AI = "AI";
    public static final String CATEGORY_DX = "DX";

    /** カテゴリ別キーワードと重みの定義 */
    private static final Map<String, KeywordCategory> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put(CATEGORY_GEN_AI, new KeywordCategory(3, List.of(
            "生成AI", "生成人工知能", "ChatGPT", "GPT-4", "GPT-3", "GPT4", "GPT3",
            "LLM", "大規模言語モデル", "Generative AI", "generative AI"
        )));
        CATEGORIES.put(CATEGORY_AI, new KeywordCategory(2, List.of(
            "人工知能", "機械学習", "ディープラーニング", "深層学習", "自然言語処理",
            "ニューラルネットワーク", "画像認識", "音声認識"
        )));
        // "AI" 単体は最後に追加（他キーワードの部分一致を避けるため長いものを先に処理）
        CATEGORIES.get(CATEGORY_AI).keywords().add("AI");
        CATEGORIES.put(CATEGORY_DX, new KeywordCategory(1, List.of(
            "デジタルトランスフォーメーション", "デジタル変革", "デジタル化", "DX推進", "DX戦略", "DX"
        )));
    }

    private final NegationFilter negationFilter;

    public KeywordScorer(NegationFilter negationFilter) {
        this.negationFilter = negationFilter;
    }

    /**
     * テキストのキーワードスコアを算出して返す。
     *
     * @param text スコア算出対象テキスト
     * @return スコア結果（totalScore・カテゴリ別スコア・テキスト長を含む）
     */
    public ScoreResult score(String text) {
        if (text == null || text.isEmpty()) {
            return new ScoreResult(0.0, 0.0, 0.0, 0.0, 0);
        }

        int documentLength = text.length();
        double genAiWeightedCount = 0.0;
        double aiWeightedCount = 0.0;
        double dxWeightedCount = 0.0;

        for (Map.Entry<String, KeywordCategory> entry : CATEGORIES.entrySet()) {
            String categoryName = entry.getKey();
            KeywordCategory category = entry.getValue();

            for (String keyword : category.keywords()) {
                Pattern pattern = Pattern.compile(Pattern.quote(keyword));
                Matcher matcher = pattern.matcher(text);

                while (matcher.find()) {
                    // 否定文脈に含まれるヒットは除外する
                    if (negationFilter.isNegated(text, matcher.start(), matcher.end())) {
                        continue;
                    }
                    switch (categoryName) {
                        case CATEGORY_GEN_AI -> genAiWeightedCount += category.weight();
                        case CATEGORY_AI     -> aiWeightedCount += category.weight();
                        case CATEGORY_DX     -> dxWeightedCount += category.weight();
                    }
                }
            }
        }

        double factor = documentLength > 0 ? 10_000.0 / documentLength : 0.0;
        double genAiScore = genAiWeightedCount * factor;
        double aiScore = aiWeightedCount * factor;
        double dxScore = dxWeightedCount * factor;
        double totalScore = (genAiWeightedCount + aiWeightedCount + dxWeightedCount) * factor;

        return new ScoreResult(genAiScore, aiScore, dxScore, totalScore, documentLength);
    }

    /**
     * キーワードスコアの算出結果を保持するレコード。
     */
    public record ScoreResult(
        double genAiScore,
        double aiScore,
        double dxScore,
        double totalScore,
        int documentLength
    ) {}

    /**
     * キーワードカテゴリを保持するレコード。
     * keywords は可変リストとして保持し、後から追加できるようにする。
     */
    private record KeywordCategory(int weight, List<String> keywords) {
        // recordのListをMutableにするためファクトリで生成
        private KeywordCategory(int weight, List<String> keywords) {
            this.weight = weight;
            this.keywords = new java.util.ArrayList<>(keywords);
        }
    }
}
