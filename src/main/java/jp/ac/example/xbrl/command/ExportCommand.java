package jp.ac.example.xbrl.command;

import jp.ac.example.xbrl.config.AppConfig;
import jp.ac.example.xbrl.db.DatabaseManager;
import jp.ac.example.xbrl.report.CsvExporter;

import java.io.File;
import java.sql.Connection;

/**
 * SQLiteのデータをCSVとして出力するコマンド。
 *
 * 使い方:
 *   export [--type financial|keywords|merged|all] [--year <年度>] [--output <ディレクトリ>]
 *
 * --type のデフォルトは all（3種すべて出力）。
 * --year 未指定の場合は全年度を出力する。
 */
public class ExportCommand {

    private final AppConfig config;
    private final DatabaseManager dbManager;

    public ExportCommand(AppConfig config, DatabaseManager dbManager) {
        this.config = config;
        this.dbManager = dbManager;
    }

    public void execute(String[] args) {
        String type = parseStringOption(args, "--type", "all");
        int fiscalYear = parseIntOption(args, "--year", 0);
        String outputPath = parseStringOption(args, "--output", config.getOutputDir());

        File outputDir = new File(outputPath);
        System.out.println("=== export 開始 ===");
        System.out.printf("出力先: %s%n", outputDir.getAbsolutePath());
        if (fiscalYear > 0) {
            System.out.printf("年度: %d%n", fiscalYear);
        }

        try (Connection conn = dbManager.getConnection()) {
            CsvExporter exporter = new CsvExporter(conn);

            switch (type) {
                case "financial" -> exportFinancial(exporter, outputDir, fiscalYear);
                case "keywords"  -> exportKeywords(exporter, outputDir, fiscalYear);
                case "merged"    -> exportMerged(exporter, outputDir, fiscalYear);
                case "all" -> {
                    exportFinancial(exporter, outputDir, fiscalYear);
                    exportKeywords(exporter, outputDir, fiscalYear);
                    exportMerged(exporter, outputDir, fiscalYear);
                }
                default -> {
                    System.err.println("不明な --type: " + type +
                        "（financial / keywords / merged / all のいずれかを指定してください）");
                    return;
                }
            }
            System.out.println("完了しました。");
        } catch (Exception e) {
            System.err.println("エクスポート中にエラーが発生しました: " + e.getMessage());
        }
    }

    private void exportFinancial(CsvExporter exporter, File outputDir, int fiscalYear) {
        try {
            File f = exporter.exportFinancial(outputDir, fiscalYear);
            System.out.println("出力: " + f.getName());
        } catch (Exception e) {
            System.err.println("financial_data の出力に失敗しました: " + e.getMessage());
        }
    }

    private void exportKeywords(CsvExporter exporter, File outputDir, int fiscalYear) {
        try {
            File f = exporter.exportKeywords(outputDir, fiscalYear);
            System.out.println("出力: " + f.getName());
        } catch (Exception e) {
            System.err.println("keyword_scores の出力に失敗しました: " + e.getMessage());
        }
    }

    private void exportMerged(CsvExporter exporter, File outputDir, int fiscalYear) {
        try {
            File f = exporter.exportMerged(outputDir, fiscalYear);
            System.out.println("出力: " + f.getName());
        } catch (Exception e) {
            System.err.println("merged の出力に失敗しました: " + e.getMessage());
        }
    }

    private String parseStringOption(String[] args, String key, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (key.equals(args[i])) return args[i + 1];
        }
        return defaultValue;
    }

    private int parseIntOption(String[] args, String key, int defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (key.equals(args[i])) {
                try { return Integer.parseInt(args[i + 1]); }
                catch (NumberFormatException e) { return defaultValue; }
            }
        }
        return defaultValue;
    }
}
