package jp.ac.example.xbrl.text;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 有価証券報告書の HTML ファイルから分析対象セクションのテキストを抽出するクラス。
 *
 * 対象セクション:
 *   - 経営方針、経営環境及び対処すべき課題等
 *   - サステナビリティに関する考え方及び取組
 *   - 経営者による財政状態、経営成績及びキャッシュ・フローの状況の分析（MD&A）
 */
public class SectionExtractor {

    /** 対象セクションの見出しパターン */
    private static final List<Pattern> SECTION_HEADING_PATTERNS = List.of(
        Pattern.compile("経営方針.{0,20}課題"),
        Pattern.compile("サステナビリティ.{0,20}取組"),
        Pattern.compile("経営者による.{0,30}分析")
    );

    /** セクション終端とみなす次のセクション見出しパターン */
    private static final Pattern NEXT_SECTION_PATTERN =
        Pattern.compile("^\\s{0,4}[０-９0-9一二三四五六七八九十]+[．.、]");

    /**
     * 展開済み書類ディレクトリから HTML ファイルを探し、対象セクションのテキストを結合して返す。
     *
     * @param docDir data/raw/{docId}/ ディレクトリ
     * @return 抽出したテキストの結合（見つからない場合は空文字）
     */
    public String extract(File docDir) {
        List<File> htmlFiles = findHtmlFiles(docDir);
        StringBuilder sb = new StringBuilder();

        for (File htmlFile : htmlFiles) {
            String text = extractFromFile(htmlFile);
            if (!text.isEmpty()) {
                sb.append(text).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * HTML ファイルからタグを除去してプレーンテキストに変換し、対象セクションを抽出する。
     */
    String extractFromFile(File htmlFile) {
        try {
            // Shift_JIS・UTF-8 の両方を試みる
            String content = readWithFallback(htmlFile);
            String plainText = stripHtmlTags(content);
            return extractTargetSections(plainText);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * HTML タグを除去してプレーンテキストを返す。
     */
    String stripHtmlTags(String html) {
        // scriptタグとstyleタグをまるごと除去する
        String text = html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", "");
        // HTMLタグを除去する
        text = text.replaceAll("<[^>]+>", "");
        // HTMLエンティティを変換する
        text = text.replace("&nbsp;", " ")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&amp;", "&")
                   .replace("&#xA0;", " ");
        // 連続する空白・改行を整理する
        text = text.replaceAll("[ \t]+", " ");
        text = text.replaceAll("\n{3,}", "\n\n");
        return text.trim();
    }

    /**
     * プレーンテキストから対象セクションを抽出して結合する。
     */
    private String extractTargetSections(String text) {
        StringBuilder result = new StringBuilder();
        String[] lines = text.split("\n");

        for (Pattern headingPattern : SECTION_HEADING_PATTERNS) {
            boolean inSection = false;
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];

                if (!inSection) {
                    if (headingPattern.matcher(line).find()) {
                        inSection = true;
                        result.append(line).append("\n");
                    }
                } else {
                    // 次のセクション見出しが来たら終了
                    if (i > 0 && NEXT_SECTION_PATTERN.matcher(line).find()
                            && !headingPattern.matcher(line).find()) {
                        inSection = false;
                        break;
                    }
                    result.append(line).append("\n");
                }
            }
        }
        return result.toString();
    }

    /**
     * ディレクトリ配下の HTML ファイルを再帰的に収集する。
     */
    private List<File> findHtmlFiles(File dir) {
        List<File> results = new ArrayList<>();
        if (!dir.isDirectory()) return results;
        for (File f : dir.listFiles()) {
            if (f.isFile() && (f.getName().endsWith(".html") || f.getName().endsWith(".htm"))) {
                results.add(f);
            } else if (f.isDirectory()) {
                results.addAll(findHtmlFiles(f));
            }
        }
        return results;
    }

    /**
     * UTF-8 で読み込みを試み、失敗した場合は Shift_JIS で再試行する。
     */
    private String readWithFallback(File file) throws IOException {
        try {
            return Files.readString(file.toPath(), Charset.forName("UTF-8"));
        } catch (IOException e) {
            return Files.readString(file.toPath(), Charset.forName("Shift_JIS"));
        }
    }
}
