package jp.ac.example.xbrl.analysis;

import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 複数の変数仕様（VariableSpec）でラグ回帰を実行し、結果を比較表として出力するクラス。
 *
 * 変数探索（analyze --type explore）で使用する。
 * 目的変数・キーワード変数・コントロール変数の組み合わせを体系的に検証して、
 * 最適な変数仕様を特定するための比較表を生成する。
 */
public class MultiModelAnalyzer {

    private static final int MIN_OBSERVATIONS = 5;

    /**
     * 単一仕様のラグ回帰結果を保持するレコード。
     */
    public record ModelResult(
        String outcomeLabel,
        String keywordLabel,
        String controlSetLabel,
        int n,
        double r2,
        double betaKeyword,
        double seKeyword,
        double tKeyword,
        double pKeyword,
        boolean converged
    ) {}

    /**
     * 全仕様（VariableSpec.allCombinations()）でラグ回帰を実行し、比較表を返す。
     *
     * @param records 全年度の統合データ
     * @return フォーマット済み比較表テキスト
     */
    public String analyze(List<MergedRecord> records) {
        List<VariableSpec> specs = VariableSpec.allCombinations();
        List<ModelResult> results = new ArrayList<>();
        for (VariableSpec spec : specs) {
            results.add(runLagRegression(records, spec));
        }
        return formatReport(results);
    }

    /**
     * 指定した仕様リストでラグ回帰を実行して比較表を返す。
     */
    public String analyze(List<MergedRecord> records, List<VariableSpec> specs) {
        List<ModelResult> results = specs.stream()
            .map(spec -> runLagRegression(records, spec))
            .collect(Collectors.toList());
        return formatReport(results);
    }

    private ModelResult runLagRegression(List<MergedRecord> records, VariableSpec spec) {
        Map<String, Map<Integer, MergedRecord>> byCompany = records.stream()
            .collect(Collectors.groupingBy(
                MergedRecord::edinetCode,
                Collectors.toMap(MergedRecord::fiscalYear, r -> r)
            ));

        List<double[]> observations = new ArrayList<>();
        for (var entry : byCompany.entrySet()) {
            Map<Integer, MergedRecord> yearMap = entry.getValue();
            for (int year : yearMap.keySet()) {
                MergedRecord current = yearMap.get(year);
                MergedRecord next    = yearMap.get(year + 1);
                if (next == null) continue;

                Double y = spec.outcome().apply(next);
                Double kwVal = spec.keyword().apply(current);
                if (y == null || kwVal == null) continue;

                List<Double> xRow = new ArrayList<>();
                xRow.add(kwVal);
                boolean valid = true;
                for (var ctrl : spec.controls()) {
                    Double cv = ctrl.apply(current);
                    if (cv == null) { valid = false; break; }
                    xRow.add(cv);
                }
                if (!valid) continue;

                double[] row = new double[1 + xRow.size()];
                row[0] = y;
                for (int i = 0; i < xRow.size(); i++) row[i + 1] = xRow.get(i);
                observations.add(row);
            }
        }

        if (observations.size() < MIN_OBSERVATIONS) {
            return new ModelResult(
                spec.outcomeLabel(), spec.keywordLabel(), spec.controlSetLabel(),
                observations.size(), Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, false
            );
        }

        double[] y  = observations.stream().mapToDouble(o -> o[0]).toArray();
        double[][] xFull = observations.stream()
            .map(o -> { double[] row = new double[o.length - 1]; System.arraycopy(o, 1, row, 0, row.length); return row; })
            .toArray(double[][]::new);

        // 定数列（分散がゼロの列）を除去して多重共線性を防ぐ
        int[] nonConstantCols = nonConstantColumnIndices(xFull);
        double[][] x = selectColumns(xFull, nonConstantCols);

        // キーワード列（インデックス0）が除去された場合は計算不可
        boolean keywordColPresent = false;
        for (int idx : nonConstantCols) { if (idx == 0) { keywordColPresent = true; break; } }
        if (!keywordColPresent) {
            return new ModelResult(
                spec.outcomeLabel(), spec.keywordLabel(), spec.controlSetLabel(),
                observations.size(), Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, false
            );
        }

        try {
            OLSMultipleLinearRegression reg = new OLSMultipleLinearRegression();
            reg.newSampleData(y, x);

            double[] params = reg.estimateRegressionParameters();
            double[] stderr = reg.estimateRegressionParametersStandardErrors();
            double   r2     = reg.calculateRSquared();
            int      n      = y.length;
            int      k      = params.length;

            TDistribution tDist = new TDistribution(n - k);
            // params[0]=定数項, params[1]=キーワード係数
            double beta = params[1];
            double se   = stderr[1];
            double tVal = beta / se;
            double pVal = 2.0 * tDist.cumulativeProbability(-Math.abs(tVal));

            return new ModelResult(
                spec.outcomeLabel(), spec.keywordLabel(), spec.controlSetLabel(),
                n, r2, beta, se, tVal, pVal, true
            );
        } catch (MathIllegalArgumentException e) {
            return new ModelResult(
                spec.outcomeLabel(), spec.keywordLabel(), spec.controlSetLabel(),
                observations.size(), Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, false
            );
        }
    }

    private String formatReport(List<ModelResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 変数探索：ラグ回帰 多仕様比較 ===\n");
        sb.append("（目的変数はすべて t+1 期の値。キーワード・コントロール変数は t 期の値）\n\n");

        // 目的変数ごとにグループ化
        Map<String, List<ModelResult>> byOutcome = new LinkedHashMap<>();
        for (ModelResult r : results) {
            byOutcome.computeIfAbsent(r.outcomeLabel(), k -> new ArrayList<>()).add(r);
        }

        String header = String.format(
            "  %-14s  %-30s  %4s  %6s  %9s  %8s%n",
            "キーワード変数", "コントロール変数", "n", "R²", "β(KW)", "p値"
        );
        String separator = "  " + "─".repeat(78) + "\n";

        for (Map.Entry<String, List<ModelResult>> entry : byOutcome.entrySet()) {
            sb.append("# 目的変数: ").append(entry.getKey()).append("(t+1)\n");
            sb.append(header);
            sb.append(separator);

            for (ModelResult r : entry.getValue()) {
                if (!r.converged()) {
                    sb.append(String.format(
                        "  %-14s  %-30s  %4d  %6s  %9s  %8s%n",
                        r.keywordLabel(), r.controlSetLabel(), r.n, "—", "—", "計算不可"
                    ));
                } else {
                    sb.append(String.format(
                        "  %-14s  %-30s  %4d  %6.4f  %9.4f  %8.4f%s%n",
                        r.keywordLabel(), r.controlSetLabel(), r.n, r.r2(),
                        r.betaKeyword(), r.pKeyword(), significance(r.pKeyword())
                    ));
                }
            }
            sb.append("\n");
        }

        sb.append("  * p<.10  ** p<.05  *** p<.01\n\n");
        sb.append(bestModelSummary(results));
        return sb.toString();
    }

    private String bestModelSummary(List<ModelResult> results) {
        List<ModelResult> converged = results.stream().filter(ModelResult::converged).toList();
        if (converged.isEmpty()) return "";

        ModelResult bestR2 = converged.stream()
            .max((a, b) -> Double.compare(a.r2(), b.r2())).orElseThrow();
        List<ModelResult> significant = converged.stream()
            .filter(r -> r.pKeyword() < 0.05 && r.betaKeyword() > 0)
            .toList();

        StringBuilder sb = new StringBuilder("--- 探索結果サマリー ---\n");
        sb.append(String.format("最高 R²: %.4f  [%s × %s × %s]%n",
            bestR2.r2(), bestR2.outcomeLabel(), bestR2.keywordLabel(), bestR2.controlSetLabel()));

        if (!significant.isEmpty()) {
            sb.append(String.format("キーワード係数が正かつ有意（p<.05）の仕様: %d件%n", significant.size()));
            for (ModelResult r : significant) {
                sb.append(String.format("  → %s × %s × %s  β=%.4f  p=%.4f%s%n",
                    r.outcomeLabel(), r.keywordLabel(), r.controlSetLabel(),
                    r.betaKeyword(), r.pKeyword(), significance(r.pKeyword())));
            }
        } else {
            sb.append("キーワード係数が正かつ有意（p<.05）の仕様は見つかりませんでした。\n");
        }
        return sb.toString();
    }

    /** 各列の分散が非ゼロな列のインデックス配列を返す（定数列を除外） */
    private static int[] nonConstantColumnIndices(double[][] x) {
        int n = x.length;
        int k = x[0].length;
        List<Integer> indices = new ArrayList<>();
        for (int col = 0; col < k; col++) {
            double mean = 0;
            for (double[] row : x) mean += row[col];
            mean /= n;
            double var = 0;
            for (double[] row : x) var += (row[col] - mean) * (row[col] - mean);
            if (var > 1e-12) indices.add(col);
        }
        return indices.stream().mapToInt(Integer::intValue).toArray();
    }

    /** 指定列インデックスの部分行列を返す */
    private static double[][] selectColumns(double[][] x, int[] cols) {
        double[][] result = new double[x.length][cols.length];
        for (int r = 0; r < x.length; r++) {
            for (int c = 0; c < cols.length; c++) {
                result[r][c] = x[r][cols[c]];
            }
        }
        return result;
    }

    private static String significance(double p) {
        if (p < 0.01) return " ***";
        if (p < 0.05) return "  **";
        if (p < 0.10) return "   *";
        return "";
    }
}
