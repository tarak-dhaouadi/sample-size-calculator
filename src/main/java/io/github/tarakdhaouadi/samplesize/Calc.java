package io.github.tarakdhaouadi.samplesize;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.function.LongToDoubleFunction;

/** Raised for any invalid user input; the message is shown to the user as-is. */
class InputException extends Exception {
    InputException(String msg) { super(msg); }
}

/** Outcome of one calculation. */
final class Result {
    String headline = "";
    String interpretation = "";
    String details = "";
    long nTest, nRef, nTotal;   // filled for two-group designs
    long n;                     // filled for one-group / paired designs
}

/** Optional adjustments, applied in the order: rounding, finite population, clustering, response rate. */
final class Adj {
    boolean fpc;
    double popN;
    double deff = 1;
    String clusterNote = "";
    double responseRate = 1;   // fraction in (0,1]
    boolean cc;                // continuity correction
    boolean tDist;             // t-distribution adjustment
}

enum Hyp {
    EQUALITY("Equality"), NONINFERIORITY("Non-inferiority"), SUPERIORITY("Superiority"), EQUIVALENCE("Equivalence");
    final String label;
    Hyp(String l) { label = l; }
    @Override public String toString() { return label; }
}

/** Pure sample-size formulas, following the Statulator help pages. */
final class Calc {
    private Calc() {}

    // ---------- formatting helpers ----------
    static String int0(double v) { return NumberFormat.getIntegerInstance(Locale.US).format((long) Math.ceil(v - 1e-9)); }
    static String num(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return Double.toString(v);
        BigDecimal b = new BigDecimal(v).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();
        return b.toPlainString();
    }
    static String pct(double frac) { return num(frac * 100) + "%"; }

    /** A probability-like input (alpha, power, confidence level, proportion) must lie strictly between 0 and 1. */
    private static void prob(double v, String name) throws InputException {
        if (!(v > 0 && v < 1)) throw new InputException("\"" + name + "\" must be greater than 0 and less than 1.");
    }

    private static double checkSize(double n) throws InputException {
        if (Double.isNaN(n) || Double.isInfinite(n) || n > 1e12)
            throw new InputException("The required sample size is too large to be meaningful. Please check your input values.");
        return n;
    }

    /** Rounds the base size up, then applies FPC, clustering and response rate; returns the final rounded-up size. */
    private static double finish(double raw, Adj a, StringBuilder d, String unit) throws InputException {
        checkSize(raw);
        double n = Math.ceil(raw - 1e-9);
        d.append("Base sample size (rounded up): ").append(int0(n)).append('\n');
        if (a.fpc) {
            double before = n;
            n = n / (1 + (n - 1) / a.popN);
            d.append("After finite population correction (N = ").append(num(a.popN)).append("): ")
                    .append(num(n)).append("  [").append(num(before)).append(" / (1 + (n - 1)/N)]\n");
        }
        if (a.deff != 1) {
            n *= a.deff;
            d.append("After clustering (DEFF = ").append(num(a.deff)).append("): ").append(num(n)).append('\n');
            if (!a.clusterNote.isEmpty()) d.append("   ").append(a.clusterNote).append('\n');
        }
        if (a.responseRate < 1) {
            n /= a.responseRate;
            d.append("After response rate (").append(pct(a.responseRate)).append("): ").append(num(n)).append('\n');
        }
        n = Math.ceil(n - 1e-9);
        checkSize(n);
        return n;
    }

    private static double zAlpha(double alpha, Hyp h, boolean twoSided) {
        return (h == Hyp.EQUALITY && twoSided) ? Stats.normQ(1 - alpha / 2) : Stats.normQ(1 - alpha);
    }
    private static double zBeta(double power, Hyp h) {
        return h == Hyp.EQUIVALENCE ? Stats.normQ(1 - (1 - power) / 2) : Stats.normQ(power);
    }
    private static double tAlpha(double alpha, Hyp h, boolean twoSided, double df) {
        return (h == Hyp.EQUALITY && twoSided) ? Stats.tQ(1 - alpha / 2, df) : Stats.tQ(1 - alpha, df);
    }
    private static double tBeta(double power, Hyp h, double df) {
        return h == Hyp.EQUIVALENCE ? Stats.tQ(1 - (1 - power) / 2, df) : Stats.tQ(power, df);
    }

    private static String art(String s) { return ("aeiou".indexOf(Character.toLowerCase(s.charAt(0))) >= 0 ? "an " : "a ") + s; }

    private static String testDesc(Hyp h, boolean twoSided, double alpha, double power) {
        String t;
        switch (h) {
            case NONINFERIORITY: t = "non-inferiority (one-sided)"; break;
            case SUPERIORITY: t = "superiority (one-sided)"; break;
            case EQUIVALENCE: t = "equivalence (two one-sided tests)"; break;
            default: t = twoSided ? "equality (two-sided)" : "equality (one-sided)";
        }
        return t + ", significance level " + num(alpha) + ", power " + pct(power);
    }

    // =====================================================================
    // 1. Estimate a single proportion
    // =====================================================================
    static Result oneProportion(double confLevel, double p, double prec, boolean relative, Adj a) throws InputException {
        prob(confLevel, "Level of confidence"); prob(p, "Expected proportion");
        if (!(prec > 0)) throw new InputException("The precision must be greater than 0.");
        double alpha = 1 - confLevel;
        double z = Stats.normQ(1 - alpha / 2);
        double d = relative ? prec * p : prec;
        double raw = relative ? z * z * (1 - p) / (p * prec * prec) : z * z * p * (1 - p) / (d * d);
        StringBuilder det = new StringBuilder();
        det.append("Confidence level: ").append(pct(confLevel)).append("   z = ").append(num(z)).append('\n');
        det.append("Expected proportion: ").append(num(p)).append('\n');
        det.append("Margin of error: +/-").append(num(d))
                .append(relative ? " (" + pct(prec) + " of the expected proportion)" : " (absolute)").append('\n');
        det.append("Unadjusted sample size: ").append(num(raw)).append("\n\n");
        double n = finish(raw, a, det, "subjects");
        Result r = new Result();
        r.n = (long) n;
        r.headline = "n = " + int0(n);
        r.interpretation = "A sample of " + int0(n) + " subjects is required to estimate the proportion within +/-"
                + num(d) + " of the true value with " + pct(confLevel) + " confidence, assuming an expected proportion of "
                + num(p) + ".";
        if (n * p < 5 || n * (1 - p) < 5)
            det.append("\nWarning: n*p or n*(1-p) is below 5, so the normal approximation may be poor; consider an exact method.\n");
        r.details = det.toString();
        return r;
    }

    // =====================================================================
    // 2. Compare two independent proportions
    // =====================================================================
    /** p0 = reference group, p1 = test group; ratio = n_reference / n_test. margin is entered as a positive number. */
    static Result twoProportions(double p0, double p1, double ratio, Hyp h, double margin,
                                 double alpha, double power, boolean twoSided, Adj a) throws InputException {
        prob(p0, "Proportion in reference group"); prob(p1, "Proportion in test group");
        prob(alpha, "Level of significance (alpha)");
        prob(power, "Desired power");
        if (!(ratio > 0)) throw new InputException("The allocation ratio must be greater than 0.");
        double D;
        switch (h) {
            case NONINFERIORITY:
                D = p1 - p0 + margin;
                if (D <= 0) throw new InputException("For non-inferiority, the expected difference (test - reference) must be greater than -margin.");
                break;
            case SUPERIORITY:
                D = p1 - p0 - margin;
                if (D <= 0) throw new InputException("For superiority, the expected difference (test - reference) must be greater than the margin.");
                break;
            case EQUIVALENCE:
                D = margin - Math.abs(p1 - p0);
                if (D <= 0) throw new InputException("For equivalence, the margin must be larger than the expected absolute difference.");
                break;
            default:
                D = p1 - p0;
                if (D == 0) throw new InputException("The two expected proportions must differ.");
        }
        double za = zAlpha(alpha, h, twoSided), zb = zBeta(power, h);
        double var = p0 * (1 - p0) / ratio + p1 * (1 - p1);
        double n = Math.pow(za + zb, 2) * var / (D * D);
        StringBuilder det = new StringBuilder();
        det.append("Design: ").append(testDesc(h, twoSided, alpha, power)).append('\n');
        det.append("Reference proportion: ").append(num(p0)).append("   Test proportion: ").append(num(p1)).append('\n');
        det.append("Reference : test allocation ratio = ").append(num(ratio)).append('\n');
        if (h != Hyp.EQUALITY) det.append("Margin: ").append(num(margin)).append('\n');
        det.append("z(alpha) = ").append(num(za)).append("   z(beta) = ").append(num(zb)).append('\n');
        det.append("Unadjusted sample size (test group): ").append(num(n)).append('\n');
        if (a.cc) {
            double nc = n / 4 * Math.pow(1 + Math.sqrt(1 + 2 * (ratio + 1) / (n * ratio * Math.abs(D))), 2);
            det.append("With continuity correction (Fleiss): ").append(num(nc)).append('\n');
            n = nc;
        }
        det.append('\n');
        double nt = finish(n, a, det, "subjects");
        double nr = Math.ceil(ratio * nt - 1e-9);
        checkSize(nr);
        Result r = new Result();
        r.nTest = (long) nt; r.nRef = (long) nr; r.nTotal = r.nTest + r.nRef;
        det.append("\nReference group: ").append(int0(nr)).append("\nTest group: ").append(int0(nt))
                .append("\nTotal: ").append(int0(nt + nr)).append('\n');
        r.headline = ratio == 1 ? "n = " + int0(nt) + " per group" : "Total n = " + int0(nt + nr);
        r.interpretation = "A total of " + int0(nt + nr) + " subjects (" + int0(nr) + " reference, " + int0(nt)
                + " test) is required for " + art(testDesc(h, twoSided, alpha, power))
                + " comparison of expected proportions " + num(p0) + " (reference) and " + num(p1) + " (test).";
        r.details = det.toString();
        return r;
    }

    // =====================================================================
    // 3. Compare paired proportions (McNemar, Connor 1987)
    // =====================================================================
    static Result pairedProportions(double b, double c, double alpha, double power, boolean twoSided, Adj a) throws InputException {
        prob(alpha, "Level of significance (alpha)");
        prob(power, "Desired power");
        if (b < 0 || c < 0 || b + c > 1) throw new InputException("Discordant proportions must be non-negative and sum to at most 1.");
        if (b == c) throw new InputException("The two discordant proportions must differ (otherwise there is no difference to detect).");
        double pd = c - b, ps = b + c;
        double za = twoSided ? Stats.normQ(1 - alpha / 2) : Stats.normQ(1 - alpha), zb = Stats.normQ(power);
        double n = Math.pow((za * Math.sqrt(ps) + zb * Math.sqrt(ps - pd * pd)) / pd, 2);
        StringBuilder det = new StringBuilder();
        det.append("Test: ").append(twoSided ? "two-sided" : "one-sided").append(", significance level ").append(num(alpha))
                .append(", power ").append(pct(power)).append('\n');
        det.append("Discordant proportions: b = ").append(num(b)).append(" (+ to -), c = ").append(num(c)).append(" (- to +)\n");
        det.append("z(alpha) = ").append(num(za)).append("   z(beta) = ").append(num(zb)).append('\n');
        det.append("Unadjusted number of pairs: ").append(num(n)).append('\n');
        if (a.cc) {
            n += 1 / Math.abs(pd);
            det.append("With continuity correction (+ 1/|c - b|): ").append(num(n)).append('\n');
        }
        det.append('\n');
        double np = finish(n, a, det, "pairs");
        Result r = new Result();
        r.n = (long) np;
        r.headline = "n = " + int0(np) + " pairs";
        r.interpretation = "A sample of " + int0(np) + " pairs is required to detect a difference in paired proportions (discordant b = "
                + num(b) + ", c = " + num(c) + ") with " + pct(power) + " power at significance level " + num(alpha) + ".";
        r.details = det.toString();
        return r;
    }

    /** Derives (b, c) from marginal proportions and correlation. */
    static double[] discordantFromMarginals(double p0, double p1, double rho) throws InputException {
        double b = p0 * (1 - p1) - rho * Math.sqrt(p0 * (1 - p0) * p1 * (1 - p1));
        double c = b + (p1 - p0);
        if (b < -1e-12 || c < -1e-12 || b + c > 1 + 1e-12)
            throw new InputException("These proportions and this correlation are inconsistent (they imply a negative or impossible discordant proportion). Try a smaller correlation.");
        return new double[]{Math.max(b, 0), Math.max(c, 0)};
    }

    // =====================================================================
    // 4. Estimate a single mean
    // =====================================================================
    static Result oneMean(double confLevel, double sd, double prec, Adj a) throws InputException {
        prob(confLevel, "Level of confidence");
        if (!(sd > 0) || !(prec > 0)) throw new InputException("The standard deviation and the precision must be greater than 0.");
        double alpha = 1 - confLevel;
        double z = Stats.normQ(1 - alpha / 2);
        double raw = z * z * sd * sd / (prec * prec);
        StringBuilder det = new StringBuilder();
        det.append("Confidence level: ").append(pct(confLevel)).append("   z = ").append(num(z)).append('\n');
        det.append("Expected SD: ").append(num(sd)).append("   Margin of error: +/-").append(num(prec)).append('\n');
        det.append("Normal-based sample size: ").append(num(raw)).append('\n');
        if (a.tDist) {
            double n = Math.ceil(raw);
            for (int i = 0; i < 100; i++) {
                double t = Stats.tQ(1 - alpha / 2, Math.max(1, n - 1));
                double m = Math.ceil(t * t * sd * sd / (prec * prec) - 1e-9);
                if (m == n) break;
                n = m;
            }
            det.append("t-distribution adjusted sample size: ").append(int0(n)).append('\n');
            raw = n;
        }
        det.append('\n');
        double n = finish(raw, a, det, "subjects");
        Result r = new Result();
        r.n = (long) n;
        r.headline = "n = " + int0(n);
        r.interpretation = "A sample of " + int0(n) + " subjects is required to estimate the mean within +/-" + num(prec)
                + " units with " + pct(confLevel) + " confidence, assuming a standard deviation of " + num(sd) + ".";
        r.details = det.toString();
        return r;
    }

    // =====================================================================
    // 5. Compare two independent means
    // =====================================================================
    /** delta = expected (test - reference) mean difference; ratio = n_reference / n_test; margin entered as positive. */
    static Result twoMeans(double delta, double sd, double ratio, Hyp h, double margin,
                           double alpha, double power, boolean twoSided, Adj a) throws InputException {
        prob(alpha, "Level of significance (alpha)");
        prob(power, "Desired power");
        if (!(sd > 0)) throw new InputException("The standard deviation must be greater than 0.");
        if (!(ratio > 0)) throw new InputException("The allocation ratio must be greater than 0.");
        double D;
        switch (h) {
            case NONINFERIORITY:
                D = delta + margin;
                if (D <= 0) throw new InputException("For non-inferiority, the expected difference (test - reference) must be greater than -margin.");
                break;
            case SUPERIORITY:
                D = delta - margin;
                if (D <= 0) throw new InputException("For superiority, the expected difference (test - reference) must be greater than the margin.");
                break;
            case EQUIVALENCE:
                D = margin - Math.abs(delta);
                if (D <= 0) throw new InputException("For equivalence, the margin must be larger than the expected absolute difference.");
                break;
            default:
                D = delta;
                if (D == 0) throw new InputException("The expected difference between means must not be zero.");
        }
        double za = zAlpha(alpha, h, twoSided), zb = zBeta(power, h);
        double k = (ratio + 1) / ratio * sd * sd / (D * D);
        double n = Math.pow(za + zb, 2) * k;
        StringBuilder det = new StringBuilder();
        det.append("Design: ").append(testDesc(h, twoSided, alpha, power)).append('\n');
        det.append("Expected difference (test - reference): ").append(num(delta)).append("   SD: ").append(num(sd)).append('\n');
        det.append("Reference : test allocation ratio = ").append(num(ratio)).append('\n');
        if (h != Hyp.EQUALITY) det.append("Margin: ").append(num(margin)).append('\n');
        det.append("z(alpha) = ").append(num(za)).append("   z(beta) = ").append(num(zb)).append('\n');
        det.append("Normal-based sample size (test group): ").append(num(n)).append('\n');
        if (a.tDist) {
            double m = Math.ceil(n);
            for (int i = 0; i < 100; i++) {
                double df = Math.max(1, (ratio + 1) * m - 2);
                double nn = Math.ceil(Math.pow(tAlpha(alpha, h, twoSided, df) + tBeta(power, h, df), 2) * k - 1e-9);
                if (nn == m) break;
                m = nn;
            }
            det.append("t-distribution adjusted sample size: ").append(int0(m)).append('\n');
            n = m;
        }
        det.append('\n');
        double nt = finish(n, a, det, "subjects");
        double nr = Math.ceil(ratio * nt - 1e-9);
        checkSize(nr);
        Result r = new Result();
        r.nTest = (long) nt; r.nRef = (long) nr; r.nTotal = r.nTest + r.nRef;
        det.append("\nReference group: ").append(int0(nr)).append("\nTest group: ").append(int0(nt))
                .append("\nTotal: ").append(int0(nt + nr)).append('\n');
        r.headline = ratio == 1 ? "n = " + int0(nt) + " per group" : "Total n = " + int0(nt + nr);
        r.interpretation = "A total of " + int0(nt + nr) + " subjects (" + int0(nr) + " reference, " + int0(nt)
                + " test) is required for " + art(testDesc(h, twoSided, alpha, power)) + " comparison of means with an expected difference of "
                + num(delta) + " and a standard deviation of " + num(sd) + ".";
        r.details = det.toString();
        return r;
    }

    // =====================================================================
    // 6. Compare paired differences (paired t-test)
    // =====================================================================
    static Result pairedMeans(double meanDiff, double sd, double alpha, double power, boolean twoSided, Adj a) throws InputException {
        if (!(sd > 0)) throw new InputException("The standard deviation must be greater than 0.");
        double es = Math.abs(meanDiff / sd);
        return pairedMeansES(es, meanDiff, sd, alpha, power, twoSided, a);
    }

    static Result pairedMeansES(double es, double meanDiff, double sd, double alpha, double power, boolean twoSided, Adj a) throws InputException {
        prob(alpha, "Level of significance (alpha)");
        prob(power, "Desired power");
        if (es <= 0) throw new InputException("The expected effect size must not be zero.");
        double za = twoSided ? Stats.normQ(1 - alpha / 2) : Stats.normQ(1 - alpha), zb = Stats.normQ(power);
        double n = Math.pow((za + zb) / es, 2);
        StringBuilder det = new StringBuilder();
        det.append("Test: ").append(twoSided ? "two-sided" : "one-sided").append(", significance level ").append(num(alpha))
                .append(", power ").append(pct(power)).append('\n');
        if (!Double.isNaN(sd)) det.append("Mean difference: ").append(num(meanDiff)).append("   SD of differences: ").append(num(sd)).append('\n');
        det.append("Effect size (mean difference / SD): ").append(num(es)).append('\n');
        det.append("z(alpha) = ").append(num(za)).append("   z(beta) = ").append(num(zb)).append('\n');
        det.append("Normal-based number of pairs: ").append(num(n)).append('\n');
        if (a.tDist) {
            double m = Math.ceil(n);
            for (int i = 0; i < 100; i++) {
                double df = Math.max(1, m - 1);
                double ta = twoSided ? Stats.tQ(1 - alpha / 2, df) : Stats.tQ(1 - alpha, df);
                double nn = Math.ceil(Math.pow((ta + Stats.tQ(power, df)) / es, 2) - 1e-9);
                if (nn == m) break;
                m = nn;
            }
            det.append("t-distribution adjusted number of pairs: ").append(int0(m)).append('\n');
            n = m;
        }
        det.append('\n');
        double np = finish(n, a, det, "pairs");
        Result r = new Result();
        r.n = (long) np;
        r.headline = "n = " + int0(np) + " pairs";
        r.interpretation = "A sample of " + int0(np) + " pairs is required to detect a standardised mean difference of "
                + num(es) + " with " + pct(power) + " power at significance level " + num(alpha) + " ("
                + (twoSided ? "two-sided" : "one-sided") + ").";
        r.details = det.toString();
        return r;
    }

    // =====================================================================
    // 7-8. Pearson / Spearman correlation (Fisher z, Bonett-Wright for Spearman)
    // =====================================================================
    private static double atanh(double x) { return 0.5 * Math.log((1 + x) / (1 - x)); }

    static Result correlation(boolean spearman, double r, double r0, double alpha, double power, boolean twoSided, Adj a) throws InputException {
        if (!(r > -1 && r < 1) || !(r0 > -1 && r0 < 1)) throw new InputException("Correlations must be greater than -1 and less than 1.");
        prob(alpha, "Level of significance (alpha)");
        prob(power, "Desired power");
        double C = atanh(r) - atanh(r0);
        if (Math.abs(C) < 1e-12) throw new InputException("The expected correlation must differ from the correlation under the null hypothesis.");
        double za = twoSided ? Stats.normQ(1 - alpha / 2) : Stats.normQ(1 - alpha), zb = Stats.normQ(power);
        double infl = spearman ? 1 + r * r / 2 : 1;
        double n = Math.pow((za + zb) / C, 2) * infl + 3;
        StringBuilder det = new StringBuilder();
        det.append("Method: ").append(spearman ? "Fisher z with Bonett-Wright variance correction (Spearman)" : "Fisher z transformation (Pearson)").append('\n');
        det.append("Test: ").append(twoSided ? "two-sided" : "one-sided").append(", significance level ").append(num(alpha))
                .append(", power ").append(pct(power)).append('\n');
        det.append("Expected correlation: ").append(num(r)).append("   Correlation under H0: ").append(num(r0)).append('\n');
        det.append("Fisher z difference: ").append(num(C)).append("   z(alpha) = ").append(num(za)).append("   z(beta) = ").append(num(zb)).append('\n');
        if (spearman) det.append("Variance inflation (1 + r^2/2): ").append(num(infl)).append('\n');
        det.append("Unadjusted sample size: ").append(num(n)).append("\n\n");
        double nn = finish(n, a, det, "subjects");
        Result res = new Result();
        res.n = (long) nn;
        res.headline = "n = " + int0(nn);
        res.interpretation = "A sample of " + int0(nn) + " subjects is required to detect a " + (spearman ? "Spearman" : "Pearson")
                + " correlation of " + num(r) + (r0 != 0 ? " (versus " + num(r0) + ")" : " (versus no correlation)")
                + " with " + pct(power) + " power at significance level " + num(alpha) + ".";
        res.details = det.toString();
        return res;
    }

    // =====================================================================
    // 9-10. Survival: Schoenfeld events, hazard ratio, log-rank
    // =====================================================================
    private static double schoenfeldEvents(double hr, double ratio, double alpha, double power, boolean twoSided) {
        double z = (twoSided ? Stats.normQ(1 - alpha / 2) : Stats.normQ(1 - alpha)) + Stats.normQ(power);
        double lh = Math.log(hr);
        return z * z * (1 + ratio) * (1 + ratio) / (ratio * lh * lh);
    }

    private static Result survivalResult(double totalRaw, double events, double hr, double ratio, double alpha, double power,
                                         boolean twoSided, Adj a, StringBuilder det, String basis) throws InputException {
        double N = finish(totalRaw, a, det, "subjects");
        double nt = Math.ceil(N / (1 + ratio) - 1e-9), nr = Math.ceil(ratio * nt - 1e-9);
        checkSize(nr);
        Result r = new Result();
        r.nTest = (long) nt; r.nRef = (long) nr; r.nTotal = r.nTest + r.nRef; r.n = r.nTotal;
        det.append("\nReference group: ").append(int0(nr)).append("\nTest group: ").append(int0(nt))
                .append("\nTotal: ").append(int0(nt + nr)).append('\n');
        r.headline = "N = " + int0(nt + nr) + " (" + int0(events) + " events)";
        r.interpretation = "A total of " + int0(nt + nr) + " subjects (" + int0(nr) + " reference, " + int0(nt) + " test) is required to observe "
                + int0(events) + " events, which gives " + pct(power) + " power to detect a hazard ratio of " + num(hr)
                + " at significance level " + num(alpha) + " (" + (twoSided ? "two-sided" : "one-sided") + " log-rank test). " + basis;
        r.details = det.toString();
        return r;
    }

    static Result hazardRatio(double hr, double ratio, double pEvent, double alpha, double power, boolean twoSided, Adj a) throws InputException {
        prob(alpha, "Level of significance (alpha)");
        prob(power, "Desired power");
        if (!(hr > 0)) throw new InputException("The hazard ratio must be greater than 0.");
        if (!(ratio > 0)) throw new InputException("The allocation ratio must be greater than 0.");
        if (!(pEvent > 0 && pEvent <= 1)) throw new InputException("The event probability must be greater than 0 and at most 1.");
        if (hr == 1) throw new InputException("The expected hazard ratio must differ from 1.");
        double d = Math.ceil(schoenfeldEvents(hr, ratio, alpha, power, twoSided) - 1e-9);
        StringBuilder det = new StringBuilder();
        det.append("Method: Schoenfeld formula for the number of events\n");
        det.append("Test: ").append(twoSided ? "two-sided" : "one-sided").append(", significance level ").append(num(alpha)).append(", power ").append(pct(power)).append('\n');
        det.append("Hazard ratio (test / reference): ").append(num(hr)).append("   Reference : test allocation ratio = ").append(num(ratio)).append('\n');
        det.append("Required number of events: ").append(int0(d)).append('\n');
        det.append("Expected proportion of subjects with an event: ").append(num(pEvent)).append('\n');
        det.append("Sample size = events / event probability = ").append(num(d / pEvent)).append("\n\n");
        return survivalResult(d / pEvent, d, hr, ratio, alpha, power, twoSided, a, det,
                "Assumes an overall event probability of " + num(pEvent) + ".");
    }

    /** sRef / sTest = expected survival proportions at the end of follow-up. */
    static Result logRank(double sRef, double sTest, double ratio, double alpha, double power, boolean twoSided, Adj a, String inputNote) throws InputException {
        prob(alpha, "Level of significance (alpha)");
        prob(power, "Desired power");
        prob(sRef, "Survival in reference group"); prob(sTest, "Survival in test group");
        if (!(ratio > 0)) throw new InputException("The allocation ratio must be greater than 0.");
        if (sRef == sTest) throw new InputException("The two expected survival proportions must differ.");
        double hr = Math.log(sTest) / Math.log(sRef);
        double pRef = 1 - sRef, pTest = 1 - sTest;
        double d = Math.ceil(schoenfeldEvents(hr, ratio, alpha, power, twoSided) - 1e-9);
        double pAvg = (ratio * pRef + pTest) / (1 + ratio);
        StringBuilder det = new StringBuilder();
        det.append("Method: log-rank test, Schoenfeld events, exponential survival\n");
        if (!inputNote.isEmpty()) det.append(inputNote).append('\n');
        det.append("Test: ").append(twoSided ? "two-sided" : "one-sided").append(", significance level ").append(num(alpha)).append(", power ").append(pct(power)).append('\n');
        det.append("Survival at end of follow-up: reference ").append(num(sRef)).append(", test ").append(num(sTest)).append('\n');
        det.append("Implied hazard ratio (test / reference): ").append(num(hr)).append("   Reference : test allocation ratio = ").append(num(ratio)).append('\n');
        det.append("Required number of events: ").append(int0(d)).append('\n');
        det.append("Expected event probability: reference ").append(num(pRef)).append(", test ").append(num(pTest)).append(", average ").append(num(pAvg)).append('\n');
        det.append("Sample size = events / average event probability = ").append(num(d / pAvg)).append("\n\n");
        return survivalResult(d / pAvg, d, hr, ratio, alpha, power, twoSided, a, det,
                "Assumes everyone is followed to the end of follow-up with no other censoring.");
    }

    // =====================================================================
    // 11-12. One-way ANOVA and linear regression (F tests, non-central F)
    // =====================================================================
    private static long searchN(LongToDoubleFunction pw, long nMin, double target) throws InputException {
        long lo = nMin, hi = nMin;
        while (pw.applyAsDouble(hi) < target) {
            lo = hi + 1; hi *= 2;
            if (hi > 100_000_000L) throw new InputException("The required sample size is too large to be meaningful. Please check your input values.");
        }
        while (lo < hi) {
            long m = (lo + hi) / 2;
            if (pw.applyAsDouble(m) >= target) hi = m; else lo = m + 1;
        }
        return lo;
    }

    static Result anova(int k, double f, double alpha, double power, Adj a, String inputNote) throws InputException {
        prob(alpha, "Level of significance (alpha)");
        prob(power, "Desired power");
        if (k < 2) throw new InputException("ANOVA needs at least 2 groups.");
        if (!(f > 0)) throw new InputException("Cohen's f must be greater than 0.");
        final double f2 = f * f;
        long n = searchN(m -> {
            double d1 = k - 1, d2 = (double) k * m - k;
            return 1 - Stats.ncfCdf(Stats.fQ(1 - alpha, d1, d2), d1, d2, f2 * k * m);
        }, 2, power);
        StringBuilder det = new StringBuilder();
        det.append("Method: one-way ANOVA F test, equal group sizes, non-central F distribution\n");
        if (!inputNote.isEmpty()) det.append(inputNote).append('\n');
        det.append("Number of groups: ").append(k).append("   Cohen's f: ").append(num(f)).append("   (lambda = f^2 x N)\n");
        det.append("Significance level ").append(num(alpha)).append(", power ").append(pct(power)).append('\n');
        det.append("Smallest n per group reaching the power: ").append(n).append("\n\n");
        double np = finish(n, a, det, "subjects");
        Result r = new Result();
        r.n = (long) np; r.nTotal = (long) np * k;
        det.append("\nPer group: ").append(int0(np)).append("\nTotal: ").append(int0(np * k)).append('\n');
        r.headline = "n = " + int0(np) + " per group";
        r.interpretation = "A total of " + int0(np * k) + " subjects (" + int0(np) + " in each of " + k + " groups) is required to detect an effect of Cohen's f = "
                + num(f) + " with " + pct(power) + " power at significance level " + num(alpha) + ".";
        r.details = det.toString();
        return r;
    }

    /** p = total predictors, q = predictors tested, f2 = Cohen's f-squared. */
    static Result regression(int p, int q, double f2, double alpha, double power, Adj a, String inputNote) throws InputException {
        prob(alpha, "Level of significance (alpha)");
        prob(power, "Desired power");
        if (p < 1 || q < 1 || q > p) throw new InputException("Predictors tested must be between 1 and the total number of predictors.");
        if (!(f2 > 0)) throw new InputException("Cohen's f-squared must be greater than 0.");
        long n = searchN(m -> {
            double d1 = q, d2 = m - p - 1;
            return 1 - Stats.ncfCdf(Stats.fQ(1 - alpha, d1, d2), d1, d2, f2 * m);
        }, p + 2L, power);
        StringBuilder det = new StringBuilder();
        det.append("Method: F test in multiple linear regression, non-central F distribution\n");
        if (!inputNote.isEmpty()) det.append(inputNote).append('\n');
        det.append("Total predictors: ").append(p).append("   Predictors tested: ").append(q).append("   Cohen's f2: ").append(num(f2)).append("   (lambda = f2 x N)\n");
        det.append("Significance level ").append(num(alpha)).append(", power ").append(pct(power)).append('\n');
        det.append("Smallest N reaching the power: ").append(n).append("\n\n");
        double np = finish(n, a, det, "subjects");
        Result r = new Result();
        r.n = (long) np;
        r.headline = "N = " + int0(np);
        r.interpretation = "A sample of " + int0(np) + " subjects is required to detect an effect of Cohen's f2 = " + num(f2) + " for " + q
                + " tested predictor(s) in a model with " + p + " predictor(s), with " + pct(power) + " power at significance level " + num(alpha) + ".";
        r.details = det.toString();
        return r;
    }

    // =====================================================================
    // 13. Diagnostic accuracy (Buderer): precision of sensitivity / specificity
    // =====================================================================
    static Result diagnostic(boolean doSens, boolean doSpec, double se, double sp, double prev, double prec, double conf, Adj a) throws InputException {
        prob(conf, "Level of confidence"); prob(prev, "Disease prevalence"); prob(se, "Expected sensitivity"); prob(sp, "Expected specificity");
        if (!(prec > 0 && prec < 1)) throw new InputException("The precision must be greater than 0 and less than 1.");
        double z = Stats.normQ(1 - (1 - conf) / 2);
        double nCases = z * z * se * (1 - se) / (prec * prec), nCtrl = z * z * sp * (1 - sp) / (prec * prec);
        double totSe = nCases / prev, totSp = nCtrl / (1 - prev);
        StringBuilder det = new StringBuilder();
        det.append("Method: Buderer formula, normal approximation\n");
        det.append("Confidence level: ").append(pct(conf)).append("   z = ").append(num(z)).append("   Precision: +/-").append(num(prec)).append('\n');
        det.append("Disease prevalence: ").append(num(prev)).append('\n');
        double raw = 0;
        if (doSens) {
            det.append("Sensitivity ").append(num(se)).append(": ").append(int0(nCases)).append(" diseased subjects -> total ").append(int0(totSe)).append('\n');
            raw = Math.max(raw, totSe);
        }
        if (doSpec) {
            det.append("Specificity ").append(num(sp)).append(": ").append(int0(nCtrl)).append(" non-diseased subjects -> total ").append(int0(totSp)).append('\n');
            raw = Math.max(raw, totSp);
        }
        det.append("\n");
        double n = finish(raw, a, det, "subjects");
        Result r = new Result();
        r.n = (long) n;
        r.headline = "N = " + int0(n);
        String what = doSens && doSpec ? "sensitivity and specificity" : doSens ? "sensitivity" : "specificity";
        r.interpretation = "A total of " + int0(n) + " subjects is required to estimate " + what + " within +/-" + num(prec) + " with "
                + pct(conf) + " confidence, assuming a disease prevalence of " + num(prev) + ".";
        r.details = det.toString();
        return r;
    }
}
