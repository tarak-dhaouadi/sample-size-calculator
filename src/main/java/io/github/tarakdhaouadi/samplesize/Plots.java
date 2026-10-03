package io.github.tarakdhaouadi.samplesize;

import java.util.ArrayList;
import java.util.List;

/** One chart: a title, axes, one or more curves and an optional highlighted point (the current result). */
final class Plot {
    static final class Series {
        final String label; final double[] x, y; final int rgb;
        Series(String label, double[] x, double[] y, int rgb) { this.label = label; this.x = x; this.y = y; this.rgb = rgb; }
    }
    String title = "", subtitle = "", xLabel = "", yLabel = "";
    final List<Series> series = new ArrayList<>();
    boolean dots;                 // red dots on a single blue curve (power-curve style)
    boolean integerX;             // x axis shows whole numbers only
    double markX = Double.NaN, markY = Double.NaN;
    String markLabel = "";
    int markRgb = 0x000000, markLabelRgb = 0xFF0000;

    boolean hasData() {
        for (Series s : series) for (double v : s.y) if (!Double.isNaN(v)) return true;
        return false;
    }
}

/**
 * The two charts shown for each calculator:
 * <ul>
 *   <li>{@code size}: sample size against a key input, with three curves for increasing effect sizes;</li>
 *   <li>{@code power}: sample size against the power (or the confidence level for estimation designs).</li>
 * </ul>
 * Every point is computed with the same {@link Calc} functions as the result itself.
 */
final class Plots {
    Plot size, power;
    String powerTabTitle = "Power plot";

    interface Fn { double apply(double x, double e) throws InputException; }
    interface Fn1 { double apply(double x) throws InputException; }

    static final int GOLD = 0xFFD700, SKY = 0x87CEEB, RED = 0xFF0000, PURPLE = 0xA020F0;
    private static final int[] CURVE_COLORS = {GOLD, SKY, RED};

    // ------------------------------------------------------------------ helpers
    static double[] seq(double from, double to, double step) {
        int n = (int) Math.round((to - from) / step);
        double[] a = new double[n + 1];
        for (int i = 0; i <= n; i++) a[i] = Math.round((from + i * step) * 1e9) / 1e9;
        return a;
    }
    static double[] lin(double from, double to, int points) {
        double[] a = new double[points];
        for (int i = 0; i < points; i++) a[i] = from + (to - from) * i / (points - 1);
        return a;
    }
    private static double ev(Fn f, double x, double e) {
        try {
            double v = f.apply(x, e);
            return (Double.isNaN(v) || Double.isInfinite(v) || v <= 0) ? Double.NaN : v;
        } catch (InputException ex) { return Double.NaN; }
    }
    private static double ev1(Fn1 f, double x) { return ev((a, b) -> f.apply(a), x, 0); }

    /** A round step (1, 2, 2.5 or 5 x 10^k) close to x. */
    static double niceStep(double x) {
        double mag = Math.pow(10, Math.floor(Math.log10(x))), best = mag, bestD = Double.MAX_VALUE;
        for (double m : new double[]{1, 2, 2.5, 5, 10}) {
            double d = Math.abs(Math.log(m * mag / x));
            if (d < bestD) { bestD = d; best = m * mag; }
        }
        return best;
    }
    private static double round9(double v) { return Math.round(v * 1e9) / 1e9; }

    /** The current effect plus two larger ones, a round step apart (for example 0.2, 0.25, 0.3). */
    static double[] effects(double e0, double stepIfZero) {
        if (Math.abs(e0) < 1e-12) return new double[]{0, stepIfZero, 2 * stepIfZero};
        double st = niceStep(0.25 * Math.abs(e0)) * Math.signum(e0);
        return new double[]{e0, round9(e0 + st), round9(e0 + 2 * st)};
    }
    /** Hazard-ratio style effects: the current ratio and two stronger ones, rounded to two decimals. */
    static double[] ratioEffects(double hr) {
        double l = Math.log(hr);
        double a = Math.round(Math.exp(l * 1.25) * 100) / 100.0, b = Math.round(Math.exp(l * 1.5) * 100) / 100.0;
        if (a == hr || a == 1) a = Math.exp(l * 1.25);
        if (b == a || b == 1) b = Math.exp(l * 1.5);
        return new double[]{hr, a, b};
    }
    /** The current power plus the next two usual levels above it. */
    static double[] powerLevels(double power) {
        double[] up = {0.90, 0.95, 0.99, 0.999}, down = {0.80, 0.70, 0.60};
        List<Double> l = new ArrayList<>();
        l.add(power);
        for (double u : up) if (u > power + 1e-9 && l.size() < 3) l.add(u);
        for (double d : down) if (d < power - 1e-9 && l.size() < 3) l.add(d);
        double[] r = new double[l.size()];
        for (int i = 0; i < r.length; i++) r[i] = l.get(i);
        return r;
    }
    private static String[] labels(String prefix, double[] eff) {
        String[] s = new String[eff.length];
        for (int i = 0; i < s.length; i++) s[i] = prefix + Calc.num(eff[i]);
        return s;
    }
    private static String[] powerLabels(double[] levels) {
        String[] s = new String[levels.length];
        for (int i = 0; i < s.length; i++) s[i] = "Power of " + Calc.pct(levels[i]);
        return s;
    }
    private static String testText(Hyp h, boolean two, double alpha) {
        String t = h == Hyp.EQUALITY ? (two ? "Two-sided Test" : "One-sided Test")
                : h == Hyp.NONINFERIORITY ? "Non-inferiority Test" : h == Hyp.SUPERIORITY ? "Superiority Test" : "Equivalence Test";
        return "Alpha = " + Calc.num(alpha) + ", " + t;
    }
    private static String sideText(boolean two, double alpha) {
        return "Alpha = " + Calc.num(alpha) + ", " + (two ? "Two-sided Test" : "One-sided Test");
    }
    private static String mark(double y) { return Double.isNaN(y) ? "" : "N = " + Calc.int0(y); }

    private static Plot curves(String subtitle, String xl, String yl, double[] xs, double[] eff, String[] labels, Fn fn, double mx, boolean intX) {
        Plot p = new Plot();
        p.title = "Sample Size Computation"; p.subtitle = subtitle; p.xLabel = xl; p.yLabel = yl; p.integerX = intX;
        for (int i = 0; i < eff.length; i++) {
            double[] ys = new double[xs.length];
            for (int j = 0; j < xs.length; j++) ys[j] = ev(fn, xs[j], eff[i]);
            p.series.add(new Plot.Series(labels[i], xs, ys, CURVE_COLORS[i % 3]));
        }
        p.markX = mx; p.markY = ev(fn, mx, eff[0]); p.markLabel = mark(p.markY);
        return p;
    }

    private static Plot powerCurve(String title, String subtitle, String xl, String yl, double[] xs, Fn1 fn, double mx) {
        Plot p = new Plot();
        p.title = title; p.subtitle = subtitle; p.xLabel = xl; p.yLabel = yl; p.dots = true;
        double[] ys = new double[xs.length];
        for (int j = 0; j < xs.length; j++) ys[j] = ev1(fn, xs[j]);
        p.series.add(new Plot.Series("", xs, ys, SKY));
        p.markX = mx; p.markY = ev1(fn, mx); p.markLabel = mark(p.markY);
        p.markRgb = PURPLE; p.markLabelRgb = PURPLE;
        return p;
    }

    private static final double[] POWERS = seq(0.50, 0.99, 0.01), CONFS = seq(0.80, 0.99, 0.01), PROPS = seq(0.01, 0.99, 0.01);

    // ------------------------------------------------------------------ proportions
    static Plots oneProportion(double conf, double p, double prec, boolean rel, Adj a) {
        Plots r = new Plots(); r.powerTabTitle = "Confidence plot";
        Fn f = (x, e) -> { if (!rel && e >= 1) throw new InputException("precision"); return Calc.oneProportion(conf, x, e, rel, a).n; };
        double[] eff = effects(prec, prec * 0.25);
        r.size = curves("Confidence Level = " + Calc.pct(conf), "Expected Proportion", "Estimated Sample Size", PROPS, eff,
                labels(rel ? "Relative Precision of " : "Precision of ", eff), f, p, false);
        r.power = powerCurve("Sample Size vs. Confidence Level for Estimating a Single Proportion",
                "Expected Proportion = " + Calc.num(p) + ", Precision = " + Calc.num(prec) + (rel ? " (relative)" : ""),
                "Confidence Level", "Sample Size", CONFS, x -> Calc.oneProportion(x, p, prec, rel, a).n, conf);
        return r;
    }

    static Plots twoProportions(double p0, double p1, double ratio, Hyp h, double margin, double alpha, double power, boolean two, Adj a) {
        Plots r = new Plots();
        Fn f = (x, e) -> {
            double q = x + e;
            if (!(q > 0 && q < 1)) throw new InputException("range");
            return Calc.twoProportions(x, q, ratio, h, margin, alpha, power, two, a).nTest;
        };
        double d0 = p1 - p0;
        double[] eff = effects(d0, 0.05);
        String yl = ratio == 1 ? "Sample Size per Group" : "Test Group Sample Size";
        r.size = curves("Power = " + Calc.pct(power) + ", " + testText(h, two, alpha), "Expected Outcome Proportion in the Reference Group",
                "Estimated Sample Size", PROPS, eff, labels("Difference in Proportions of ", eff), f, p0, false);
        r.power = powerCurve("Sample Size vs. Power for Comparing Two Independent Proportions",
                (h == Hyp.EQUALITY ? "Expected Proportion Difference = " + Calc.num(d0) : "Expected Proportions = " + Calc.num(p0) + " vs " + Calc.num(p1) + ", Margin = " + Calc.num(margin))
                        + ", " + testText(h, two, alpha),
                "Power (1 - \u03B2)", yl, POWERS, x -> Calc.twoProportions(p0, p1, ratio, h, margin, alpha, x, two, a).nTest, power);
        return r;
    }

    static Plots pairedProportions(double b, double c, double alpha, double power, boolean two, Adj a) {
        Plots r = new Plots();
        Fn f = (x, e) -> Calc.pairedProportions((x - e) / 2, (x + e) / 2, alpha, power, two, a).n;
        double[] eff = effects(c - b, 0.05);
        r.size = curves("Power = " + Calc.pct(power) + ", " + sideText(two, alpha), "Proportion of Discordant Pairs (b + c)",
                "Estimated Number of Pairs", PROPS, eff, labels("Difference in Discordant Proportions of ", eff), f, b + c, false);
        r.power = powerCurve("Sample Size vs. Power for Comparing Paired Proportions",
                "Discordant Proportions b = " + Calc.num(b) + ", c = " + Calc.num(c) + ", " + sideText(two, alpha),
                "Power (1 - \u03B2)", "Number of Pairs", POWERS, x -> Calc.pairedProportions(b, c, alpha, x, two, a).n, power);
        return r;
    }

    // ------------------------------------------------------------------ means
    static Plots oneMean(double conf, double sd, double prec, Adj a) {
        Plots r = new Plots(); r.powerTabTitle = "Confidence plot";
        Fn f = (x, e) -> Calc.oneMean(conf, x, e, a).n;
        double[] eff = effects(prec, prec * 0.25);
        r.size = curves("Confidence Level = " + Calc.pct(conf), "Expected Standard Deviation", "Estimated Sample Size",
                lin(0.25 * sd, 2 * sd, 50), eff, labels("Precision of ", eff), f, sd, false);
        r.power = powerCurve("Sample Size vs. Confidence Level for Estimating a Mean",
                "Expected SD = " + Calc.num(sd) + ", Precision = " + Calc.num(prec), "Confidence Level", "Sample Size", CONFS,
                x -> Calc.oneMean(x, sd, prec, a).n, conf);
        return r;
    }

    static Plots twoMeans(double delta, double sd, double ratio, Hyp h, double margin, double alpha, double power, boolean two, Adj a) {
        Plots r = new Plots();
        Fn f = (x, e) -> Calc.twoMeans(e, x, ratio, h, margin, alpha, power, two, a).nTest;
        double[] eff = effects(delta, 0.25 * sd);
        String yl = ratio == 1 ? "Sample Size per Group" : "Test Group Sample Size";
        r.size = curves("Power = " + Calc.pct(power) + ", " + testText(h, two, alpha), "Standard Deviation", "Estimated Sample Size",
                lin(0.5 * sd, 2 * sd, 60), eff, labels("Difference in Means of ", eff), f, sd, false);
        r.power = powerCurve("Sample Size vs. Power for Comparing Two Independent Means",
                "Expected Mean Difference = " + Calc.num(delta) + ", SD = " + Calc.num(sd) + ", " + testText(h, two, alpha),
                "Power (1 - \u03B2)", yl, POWERS, x -> Calc.twoMeans(delta, sd, ratio, h, margin, alpha, x, two, a).nTest, power);
        return r;
    }

    static Plots pairedMeans(double meanDiff, double sd, double alpha, double power, boolean two, Adj a) {
        Plots r = new Plots();
        Fn f = (x, e) -> Calc.pairedMeans(e, x, alpha, power, two, a).n;
        double[] eff = effects(meanDiff, 0.1 * sd);
        r.size = curves("Power = " + Calc.pct(power) + ", " + sideText(two, alpha), "Standard Deviation of the Differences",
                "Estimated Number of Pairs", lin(0.5 * sd, 2 * sd, 60), eff, labels("Mean Difference of ", eff), f, sd, false);
        r.power = powerCurve("Sample Size vs. Power for Comparing Paired Differences",
                "Mean Difference = " + Calc.num(meanDiff) + ", SD = " + Calc.num(sd) + ", " + sideText(two, alpha),
                "Power (1 - \u03B2)", "Number of Pairs", POWERS, x -> Calc.pairedMeans(meanDiff, sd, alpha, x, two, a).n, power);
        return r;
    }

    static Plots pairedMeansES(double es, double alpha, double power, boolean two, Adj a) {
        Plots r = new Plots();
        double[] lv = powerLevels(power);
        Fn f = (x, e) -> Calc.pairedMeansES(x, Double.NaN, Double.NaN, alpha, e, two, a).n;
        r.size = curves(sideText(two, alpha), "Expected Effect Size (Mean Difference / SD)", "Estimated Number of Pairs",
                seq(0.05, 1.5, 0.025), lv, powerLabels(lv), f, es, false);
        r.power = powerCurve("Sample Size vs. Power for Comparing Paired Differences",
                "Effect Size = " + Calc.num(es) + ", " + sideText(two, alpha),
                "Power (1 - \u03B2)", "Number of Pairs", POWERS, x -> Calc.pairedMeansES(es, Double.NaN, Double.NaN, alpha, x, two, a).n, power);
        return r;
    }

    // ------------------------------------------------------------------ correlations
    static Plots correlation(boolean spearman, double rho, double rho0, double alpha, double power, boolean two, Adj a) {
        Plots r = new Plots();
        String kind = spearman ? "Spearman" : "Pearson";
        double[] lv = powerLevels(power);
        Fn f = (x, e) -> Calc.correlation(spearman, x, rho0, alpha, e, two, a).n;
        boolean nullZero = rho0 == 0;
        double mx = nullZero ? Math.abs(rho) : rho;
        r.size = curves(sideText(two, alpha), "Expected " + kind + " Correlation", "Estimated Sample Size",
                nullZero ? seq(0.05, 0.95, 0.01) : seq(-0.95, 0.95, 0.01), lv, powerLabels(lv), f, mx, false);
        r.power = powerCurve("Sample Size vs. Power for a " + kind + " Correlation",
                "Expected Correlation = " + Calc.num(rho) + (nullZero ? "" : " (null " + Calc.num(rho0) + ")") + ", " + sideText(two, alpha),
                "Power (1 - \u03B2)", "Sample Size", POWERS, x -> Calc.correlation(spearman, rho, rho0, alpha, x, two, a).n, power);
        return r;
    }

    // ------------------------------------------------------------------ survival
    static Plots hazardRatio(double hr, double ratio, double pEvent, double alpha, double power, boolean two, Adj a) {
        Plots r = new Plots();
        Fn f = (x, e) -> Calc.hazardRatio(e, ratio, x, alpha, power, two, a).n;
        double[] eff = ratioEffects(hr);
        r.size = curves("Power = " + Calc.pct(power) + ", " + sideText(two, alpha), "Overall Probability of Observing the Event",
                "Estimated Total Sample Size", seq(0.05, 1.0, 0.01), eff, labels("Hazard Ratio of ", eff), f, pEvent, false);
        r.power = powerCurve("Sample Size vs. Power for Detecting a Hazard Ratio",
                "Hazard Ratio = " + Calc.num(hr) + ", Event Probability = " + Calc.num(pEvent) + ", " + sideText(two, alpha),
                "Power (1 - \u03B2)", "Total Sample Size", POWERS, x -> Calc.hazardRatio(hr, ratio, pEvent, alpha, x, two, a).n, power);
        return r;
    }

    static Plots logRank(double sRef, double sTest, double ratio, double alpha, double power, boolean two, Adj a) {
        Plots r = new Plots();
        double hr = Math.log(sTest) / Math.log(sRef);
        Fn f = (x, e) -> Calc.logRank(x, Math.pow(x, e), ratio, alpha, power, two, a, "").n;
        double[] eff = ratioEffects(hr);
        r.size = curves("Power = " + Calc.pct(power) + ", " + sideText(two, alpha), "Expected Survival Proportion in the Reference Group",
                "Estimated Total Sample Size", seq(0.05, 0.95, 0.01), eff, labels("Hazard Ratio of ", eff), f, sRef, false);
        r.power = powerCurve("Sample Size vs. Power for the Log-rank Test",
                "Survival " + Calc.num(sRef) + " vs " + Calc.num(sTest) + " (HR = " + Calc.num(hr) + "), " + sideText(two, alpha),
                "Power (1 - \u03B2)", "Total Sample Size", POWERS, x -> Calc.logRank(sRef, sTest, ratio, alpha, x, two, a, "").n, power);
        return r;
    }

    // ------------------------------------------------------------------ other designs
    /** 2..max(10, k0) in at most about ten steps, always including k0. */
    private static double[] counts(int from, int k0) {
        int max = Math.max(10, k0), step = Math.max(1, (int) Math.ceil((max - from) / 9.0));
        List<Double> l = new ArrayList<>();
        for (int k = from; k <= max; k += step) l.add((double) k);
        if (!l.contains((double) k0)) { l.add((double) k0); java.util.Collections.sort(l); }
        double[] r = new double[l.size()];
        for (int i = 0; i < r.length; i++) r[i] = l.get(i);
        return r;
    }

    static Plots anova(int k0, double f0, double alpha, double power, Adj a) {
        Plots r = new Plots();
        Fn f = (x, e) -> Calc.anova((int) Math.round(x), e, alpha, power, a, "").n;
        double[] eff = effects(f0, f0 * 0.25);
        r.size = curves("Power = " + Calc.pct(power) + ", Alpha = " + Calc.num(alpha), "Number of Groups", "Estimated Sample Size per Group",
                counts(2, k0), eff, labels("Cohen's f of ", eff), f, k0, true);
        r.power = powerCurve("Sample Size vs. Power for One-way ANOVA",
                k0 + " Groups, Cohen's f = " + Calc.num(f0) + ", Alpha = " + Calc.num(alpha),
                "Power (1 - \u03B2)", "Sample Size per Group", POWERS, x -> Calc.anova(k0, f0, alpha, x, a, "").n, power);
        return r;
    }

    static Plots regression(int p0, int q0, double f2, double alpha, double power, Adj a) {
        Plots r = new Plots();
        int extra = p0 - q0;
        Fn f = (x, e) -> Calc.regression((int) Math.round(x) + extra, (int) Math.round(x), e, alpha, power, a, "").n;
        double[] eff = effects(f2, f2 * 0.25);
        r.size = curves("Power = " + Calc.pct(power) + ", Alpha = " + Calc.num(alpha) + (extra > 0 ? ", " + extra + " other predictor(s)" : ""),
                "Number of Predictors Tested", "Estimated Sample Size", counts(1, q0), eff, labels("Cohen's f\u00B2 of ", eff), f, q0, true);
        r.power = powerCurve("Sample Size vs. Power for Linear Regression",
                q0 + " of " + p0 + " Predictor(s) Tested, Cohen's f\u00B2 = " + Calc.num(f2) + ", Alpha = " + Calc.num(alpha),
                "Power (1 - \u03B2)", "Sample Size", POWERS, x -> Calc.regression(p0, q0, f2, alpha, x, a, "").n, power);
        return r;
    }

    static Plots diagnostic(boolean sens, boolean spec, double se, double sp, double prev, double prec, double conf, Adj a) {
        Plots r = new Plots(); r.powerTabTitle = "Confidence plot";
        Fn f = (x, e) -> { if (e >= 1) throw new InputException("precision"); return Calc.diagnostic(sens, spec, se, sp, x, e, conf, a).n; };
        double[] eff = effects(prec, prec * 0.25);
        String what = sens && spec ? "Sensitivity " + Calc.num(se) + ", Specificity " + Calc.num(sp) : sens ? "Sensitivity " + Calc.num(se) : "Specificity " + Calc.num(sp);
        r.size = curves("Confidence Level = " + Calc.pct(conf) + ", " + what, "Disease Prevalence", "Estimated Total Sample Size",
                seq(0.05, 0.95, 0.01), eff, labels("Precision of ", eff), f, prev, false);
        r.power = powerCurve("Sample Size vs. Confidence Level for Diagnostic Accuracy",
                what + ", Prevalence = " + Calc.num(prev) + ", Precision = " + Calc.num(prec),
                "Confidence Level", "Total Sample Size", CONFS, x -> Calc.diagnostic(sens, spec, se, sp, prev, prec, x, a).n, conf);
        return r;
    }
}
