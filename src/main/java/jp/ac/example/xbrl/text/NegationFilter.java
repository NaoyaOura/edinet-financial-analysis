package jp.ac.example.xbrl.text;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 否定文脈に含まれるキーワードを除外するフィルタクラス。
 * 「導入していない」「検討していない」等の否定パターンにマッチした
 * 前後 NEGATION_WINDOW 文字以内のキーワードヒットを無効とする。
 */
public class NegationFilter {

    /** 否定文脈とみなす範囲（文字数） */
    static final int NEGATION_WINDOW = 50;

    /** 否定パターン一覧 */
    private static final List<Pattern> NEGATION_PATTERNS = List.of(
        Pattern.compile("導入(して|し)?(い)?ない"),
        Pattern.compile("検討(して|し)?(い)?ない"),
        Pattern.compile("活用(して|し)?(い)?ない"),
        Pattern.compile("利用(して|し)?(い)?ない"),
        Pattern.compile("使用(して|し)?(い)?ない"),
        Pattern.compile("予定(は|が)?ない"),
        Pattern.compile("実施(して|し)?(い)?ない"),
        Pattern.compile("対応(して|し)?(い)?ない"),
        Pattern.compile("実現(して|し)?(い)?ない"),
        Pattern.compile("取り組(んで|ん)?(い)?ない")
    );

    /**
     * テキスト内の否定文脈位置のセットを計算し、
     * キーワードのマッチ位置が否定文脈に含まれるかを判定する。
     *
     * @param text         検索対象テキスト
     * @param matchStart   キーワードのマッチ開始位置
     * @param matchEnd     キーワードのマッチ終了位置
     * @return 否定文脈に含まれる場合は true
     */
    public boolean isNegated(String text, int matchStart, int matchEnd) {
        for (Pattern pattern : NEGATION_PATTERNS) {
            Matcher m = pattern.matcher(text);
            while (m.find()) {
                int negStart = m.start();
                int negEnd = m.end();
                // 否定パターンの前後 NEGATION_WINDOW 文字をチェック範囲とする
                int windowStart = Math.max(0, negStart - NEGATION_WINDOW);
                int windowEnd = Math.min(text.length(), negEnd + NEGATION_WINDOW);
                // キーワードの位置がウィンドウ内に含まれるか
                if (matchStart >= windowStart && matchEnd <= windowEnd) {
                    return true;
                }
            }
        }
        return false;
    }
}
