package jp.ac.example.xbrl.analysis;

import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * キーワードスコア(t) → 翌年業績(t+1) のラグOLS回帰を行うクラス。
 *
 * デフォルト回帰式（VariableSpec.defaultSpec()）:
 *   営業利益率(t+1) = β₀ + β₁×totalScore(t) + β₂×log(売上高)(t) + β₃×小売業D + β₄×IT業D + ε
 *
 * VariableSpec を指定することで任意の変数組み合わせで実行できる。
 */
public class LagRegressionAnalyzer {

    private static final int MIN_OBSERVATIONS = 5;

    /**
     * デフォルト仕様でラグ回帰を実行して結果テキストを返す。
     */
    public String analyze(List<MergedRecord> records) {
        return analyze(records, VariableSpec.defaultSpec());
    }

    /**
     * 指定した変数仕様でラグ回帰を実行して結果テキストを返す。
     */
    public String analyze(List<MergedRecord> records, VariableSpec spec) {
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

                Double y     = spec.outcome().apply(next);
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

        return formatReport(observations, spec);
    }

    private String formatReport(List<double[]> observations, VariableSpec spec) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ラグ回帰分析 ===\n");
        sb.append("目的変数: ").append(spec.outcomeLabel()).append("(t+1)\n");
        sb.append("説明変数: ").append(spec.keywordLabel()).append("(t)");
        for (String label : spec.controlLabels()) {
            sb.append(", ").append(label).append("(t)");
        }
        sb.append("\n\n");

        if (observations.size() < MIN_OBSERVATIONS) {
            sb.append(String.format("データが不足しています（%d件、最低%d件必要）。%n",
                observations.size(), MIN_OBSERVATIONS));
            return sb.toString();
        }

        double[] y  = observations.stream().mapToDouble(o -> o[0]).toArray();
        double[][] xFull = observations.stream()
            .map(o -> { double[] row = new double[o.length - 1]; System.arraycopy(o, 1, row, 0, row.length); return row; })
            .toArray(double[][]::new);

        // 定数列（分散がゼロの列）を除去して多重共線性を防ぐ
        int[] nonConstantCols = nonConstantColumnIndices(xFull);
        double[][] x = selectColumns(xFull, nonConstantCols);

        // 変数ラベルを非定数列に絞る
        List<String> activeLabels = new ArrayList<>();
        activeLabels.add("定数項");
        List<String> allXLabels = new ArrayList<>();
        allXLabels.add(spec.keywordLabel() + "(t)");
        for (String cl : spec.controlLabels()) allXLabels.add(cl + "(t)");
        for (int idx : nonConstantCols) activeLabels.add(allXLabels.get(idx));

        if (nonConstantCols.length < xFull[0].length) {
            // 除去された列を報告
            List<String> removed = new ArrayList<>();
            outer: for (int i = 0; i < allXLabels.size(); i++) {
                for (int idx : nonConstantCols) { if (idx == i) continue outer; }
                removed.add(allXLabels.get(i));
            }
            sb.append("[INFO] 定数列のため除外した変数: ").append(String.join(", ", removed)).append("\n\n");
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

            sb.append(String.format("n = %d  R² = %.4f%n%n", n, r2));
            sb.append(String.format("%-24s %10s %10s %8s %8s%n", "変数", "係数(β)", "SE", "t値", "p値"));
            sb.append("-".repeat(65)).append("\n");

            for (int i = 0; i < params.length; i++) {
                double tStat = params[i] / stderr[i];
                double pVal  = 2.0 * tDist.cumulativeProbability(-Math.abs(tStat));
                sb.append(String.format("%-24s %10.4f %10.4f %8.3f %8.4f%s%n",
                    activeLabels.get(i), params[i], stderr[i], tStat, pVal, significance(pVal)));
            }
            sb.append("\n  * p<.10  ** p<.05  *** p<.01\n");
        } catch (MathIllegalArgumentException e) {
            sb.append("回帰計算に失敗しました（行列が特異または多重共線性の可能性）: ")
              .append(e.getMessage()).append("\n");
            sb.append("ヒント: 業種が1種類のみの場合、業種ダミーが定数列になり計算不能になります。\n");
        }

        return sb.toString();
    }

    private static int[] nonConstantColumnIndices(double[][] x) {
        int n = x.length;
        int k = x[0].length;
        List<Integer> indices = new java.util.ArrayList<>();
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
