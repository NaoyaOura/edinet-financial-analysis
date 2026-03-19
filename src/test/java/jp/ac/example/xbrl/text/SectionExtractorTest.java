package jp.ac.example.xbrl.text;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class SectionExtractorTest {

    private final SectionExtractor extractor = new SectionExtractor();

    // --- stripHtmlTags ---

    @Test
    void stripHtmlTags_タグを除去する() {
        String html = "<p>テスト</p><p>文字列</p>";
        String result = extractor.stripHtmlTags(html);
        assertTrue(result.contains("テスト"), "テキストが残ること");
        assertTrue(result.contains("文字列"), "テキストが残ること");
        assertFalse(result.contains("<p>"), "タグが除去されること");
    }

    @Test
    void stripHtmlTags_scriptタグの中身を除去する() {
        String html = "<p>本文</p><script>alert('xss')</script><p>後続</p>";
        String result = extractor.stripHtmlTags(html);
        assertFalse(result.contains("alert"), "scriptの中身が残ってはいけない");
        assertTrue(result.contains("本文"));
        assertTrue(result.contains("後続"));
    }

    @Test
    void stripHtmlTags_HTMLエンティティを変換する() {
        String html = "A &amp; B &lt;C&gt; D&nbsp;E";
        String result = extractor.stripHtmlTags(html);
        assertTrue(result.contains("A & B"));
        assertTrue(result.contains("<C>"));
    }

    // --- extractFromFile ---

    @Test
    void extractFromFile_対象セクションを含むHTMLから抽出できる(@TempDir File tmpDir) throws IOException {
        String html = """
            <html><body>
            <p>１．経営方針、経営環境及び対処すべき課題等</p>
            <p>当社は生成AIを積極的に導入しています。</p>
            <p>２．事業等のリスク</p>
            <p>別のセクション</p>
            </body></html>
            """;
        File f = new File(tmpDir, "test.html");
        Files.writeString(f.toPath(), html, StandardCharsets.UTF_8);

        String result = extractor.extractFromFile(f);
        assertTrue(result.contains("生成AI"), "対象セクションのテキストが含まれること");
        assertFalse(result.contains("別のセクション"), "次セクション以降は含まれないこと");
    }

    @Test
    void extractFromFile_対象セクションがない場合は空文字(@TempDir File tmpDir) throws IOException {
        String html = "<html><body><p>関係ないコンテンツ</p></body></html>";
        File f = new File(tmpDir, "empty.html");
        Files.writeString(f.toPath(), html, StandardCharsets.UTF_8);

        String result = extractor.extractFromFile(f);
        assertTrue(result.isEmpty(), "対象セクションがなければ空文字を返す");
    }

    @Test
    void extractFromFile_存在しないファイルは空文字() {
        File noFile = new File("/nonexistent/path/file.html");
        String result = extractor.extractFromFile(noFile);
        assertEquals("", result);
    }

    // --- extract (ディレクトリ) ---

    @Test
    void extract_ディレクトリ配下のHTMLを再帰的に処理する(@TempDir File tmpDir) throws IOException {
        // サブディレクトリ内にHTMLを配置
        File subDir = new File(tmpDir, "sub");
        subDir.mkdir();
        String html = """
            <html><body>
            <p>サステナビリティに関する考え方及び取組</p>
            <p>DXを推進しています。</p>
            </body></html>
            """;
        File f = new File(subDir, "sustainability.html");
        Files.writeString(f.toPath(), html, StandardCharsets.UTF_8);

        String result = extractor.extract(tmpDir);
        assertTrue(result.contains("DXを推進"), "サブディレクトリのHTMLからも抽出できること");
    }
}
