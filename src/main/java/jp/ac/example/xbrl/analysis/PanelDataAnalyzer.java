package jp.ac.example.xbrl.analysis;

import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * パネルデータ分析（固定効果モデル）を行うクラス。
 *
 * within推定量: 企業ごとに各変数の平均を引いた「企業内変動」でOLS回帰を行うことで、
 * 観察されない企業固有の不変要因（固定効果）を除去する。
 *
 * デフォルト回帰式（企業内変動ベース）:
 *   (営業利益率 - 企業平均) = β₁×(totalScore - 企業平均) + β₂×(log売上高 - 企業平均) + ε
 *
 * VariableSpec を指定することで任意の変数組み合わせで実行できる。
 */
public class PanelDataAnalyzer {

    private static final int MIN_OBSERVATIONS    = 5;
    private static final int MIN_YEARS_PER_COMPANY = 2;

    /**
     * デフォルト仕様（営業利益率 × totalScore + log売上高）で固定効果モデルを実行する。
     */
    public String analyze(List<MergedRecord> records) {
        VariableSpec spec = new VariableSpec(
            "営業利益率(%)",
            MergedRecord::operatingMargin,
            "totalScore",
            r -> r.totalScore(),
            List.of("log(売上高)"),
            List.of(MergedRecord::logNetSales)
        );
        return analyze(records, spec);
    }

    /**
     * 指定した変数仕様で固定効果モデルを実行する。
     *
     * 注意: within推定では時不変の変数（業種ダミー等）は demeaned 後にゼロになり
     * 計算不能になる可能性があるため、controls は時変変数（log売上高等）を推奨する。
     */
    public String analyze(List<MergedRecord> records, VariableSpec spec) {
        Map<String, List<MergedRecord>> byCompany = records.stream()
            .collect(Collectors.groupingBy(MergedRecord::edinetCode));

        List<double[]> demeaned = new ArrayList<>();
        for (List<MergedRecord> compRecords : byCompany.values()) {
            if (compRecords.size() < MIN_YEARS_PER_COMPANY) continue;

            // 企業内の各変数平均を計算
            OptionalDouble meanOutcome = compRecords.stream()
                .map(spec.outcome())
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average();
            OptionalDouble meanKeyword = compRecords.stream()
                .map(spec.keyword())
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average();

            if (meanOutcome.isEmpty() || meanKeyword.isEmpty()) continue;

            List<OptionalDouble> meanControls = spec.controls().stream()
                .map((Function<Function<MergedRecord, Double>, OptionalDouble>) ctrl ->
                    compRecords.stream()
                        .map(ctrl)
                        .filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue)
                        .average()
                )
                .collect(Collectors.toList());

            if (meanControls.stream().anyMatch(OptionalDouble::isEmpty)) continue;

            for (MergedRecord r : compRecords) {
                Double outcome  = spec.outcome().apply(r);
                Double keyword  = spec.keyword().apply(r);
                if (outcome == null || keyword == null) continue;

                List<Double> ctrlVals = new ArrayList<>();
                boolean valid = true;
                for (int i = 0; i < spec.controls().size(); i++) {
                    Double cv = spec.controls().get(i).apply(r);
                    if (cv == null) { valid = false; break; }
                    ctrlVals.add(cv - meanControls.get(i).getAsDouble());
                }
                if (!valid) continue;

                double[] row = new double[2 + ctrlVals.size()];
                row[0] = outcome  - meanOutcome.getAsDouble();
                row[1] = keyword  - meanKeyword.getAsDouble();
                for (int i = 0; i < ctrlVals.size(); i++) row[2 + i] = ctrlVals.get(i);
                demeaned.add(row);
            }
        }

        return formatReport(demeaned, spec);
    }

    private String formatReport(List<double[]> demeaned, VariableSpec spec) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== パネルデータ分析（固定効果モデル） ===\n");
        sb.append("目的変数: ").append(spec.outcomeLabel()).append("（企業内平均からの乖離）\n");
        sb.append("説明変数: ").append(spec.keywordLabel());
        for (String cl : spec.controlLabels()) sb.append(", ").append(cl);
        sb.append("（同じく企業内平均乖離）\n\n");

        if (demeaned.size() < MIN_OBSERVATIONS) {
            sb.append(String.format("データが不足しています（%d件、最低%d件必要）。%n",
                demeaned.size(), MIN_OBSERVATIONS));
            return sb.toString();
        }

        double[] y  = demeaned.stream().mapToDouble(d -> d[0]).toArray();
        double[][] x = demeaned.stream()
            .map(d -> { double[] row = new double[d.length - 1]; System.arraycopy(d, 1, row, 0, row.length); return row; })
            .toArray(double[][]::new);

        try {
            OLSMultipleLinearRegression reg = new OLSMultipleLinearRegression();
            reg.setNoIntercept(true);
            reg.newSampleData(y, x);

            double[] params = reg.estimateRegressionParameters();
            double[] stderr = reg.estimateRegressionParametersStandardErrors();
            double   r2     = reg.calculateRSquared();
            int      n      = y.length;
            int      k      = params.length;

            TDistribution tDist = new TDistribution(n - k);

            sb.append(String.format("n = %d（観測数）  R² = %.4f%n%n", n, r2));
            sb.append(String.format("%-24s %10s %10s %8s %8s%n", "変数", "係数(β)", "SE", "t値", "p値"));
            sb.append("-".repeat(65)).append("\n");

            List<String> labels = new ArrayList<>();
            labels.add(spec.keywordLabel());
            labels.addAll(spec.controlLabels());

            for (int i = 0; i < params.length; i++) {
                double tStat = params[i] / stderr[i];
                double pVal  = 2.0 * tDist.cumulativeProbability(-Math.abs(tStat));
                sb.append(String.format("%-24s %10.4f %10.4f %8.3f %8.4f%s%n",
                    labels.get(i), params[i], stderr[i], tStat, pVal, significance(pVal)));
            }
            sb.append("\n  * p<.10  ** p<.05  *** p<.01\n");
        } catch (MathIllegalArgumentException e) {
            sb.append("回帰計算に失敗しました（行列が特異）: ").append(e.getMessage()).append("\n");
            sb.append("ヒント: 業種ダミー等の時不変変数は within変換後がゼロになり計算不能になります。\n");
        }

        return sb.toString();
    }

    private static String significance(double p) {
        if (p < 0.01) return " ***";
        if (p < 0.05) return "  **";
        if (p < 0.10) return "   *";
        return "";
    }
}
