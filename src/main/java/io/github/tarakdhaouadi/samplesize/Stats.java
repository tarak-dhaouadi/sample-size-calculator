package io.github.tarakdhaouadi.samplesize;

/** Normal, Student-t and (non-central) F distribution helpers, with no external dependencies. */
final class Stats {
    private Stats() {}

    static double logGamma(double z) {
        if (z < 0.5) {
            return Math.log(Math.PI) - Math.log(Math.sin(Math.PI * z)) - logGamma(1 - z);
        }
        final double[] co = {676.5203681218851, -1259.1392167224028, 771.32342877765313,
                -176.61502916214059, 12.507343278686905, -0.13857109526572012,
                9.9843695780195716e-6, 1.5056327351493116e-7};
        z -= 1;
        double x = 0.99999999999980993;
        for (int i = 0; i < co.length; i++) x += co[i] / (z + i + 1);
        double t = z + 7.5;
        return 0.5 * Math.log(2 * Math.PI) + (z + 0.5) * Math.log(t) - t + Math.log(x);
    }

    // ---- regularised incomplete gamma (for the normal distribution) ----
    private static double gammaPSeries(double a, double x) {
        double ap = a, sum = 1 / a, del = sum;
        for (int n = 0; n < 2000; n++) {
            ap += 1;
            del *= x / ap;
            sum += del;
            if (Math.abs(del) < Math.abs(sum) * 1e-16) break;
        }
        return sum * Math.exp(-x + a * Math.log(x) - logGamma(a));
    }

    private static double gammaQCf(double a, double x) {
        final double tiny = 1e-300;
        double b = x + 1 - a, c = 1 / tiny, d = 1 / b, h = d;
        for (int i = 1; i < 2000; i++) {
            double an = -i * (i - a);
            b += 2;
            d = an * d + b;
            if (Math.abs(d) < tiny) d = tiny;
            c = b + an / c;
            if (Math.abs(c) < tiny) c = tiny;
            d = 1 / d;
            double del = d * c;
            h *= del;
            if (Math.abs(del - 1) < 1e-16) break;
        }
        return Math.exp(-x + a * Math.log(x) - logGamma(a)) * h;
    }

    /** Complementary error function for x >= 0. */
    private static double erfc(double x) {
        if (x <= 0) return 1.0;
        double t = x * x;
        return t < 1.5 ? 1 - gammaPSeries(0.5, t) : gammaQCf(0.5, t);
    }

    /** Standard normal upper tail P(Z > x). */
    static double normSf(double x) {
        double v = 0.5 * erfc(Math.abs(x) / Math.sqrt(2));
        return x >= 0 ? v : 1 - v;
    }

    /** Standard normal quantile (inverse CDF), 0 < p < 1. */
    static double normQ(double p) {
        if (!(p > 0 && p < 1)) return Double.NaN;
        if (p == 0.5) return 0;
        if (p < 0.5) return -normQ(1 - p);
        double q = 1 - p, lo = 0, hi = 40;
        for (int i = 0; i < 200; i++) {
            double m = (lo + hi) / 2;
            if (normSf(m) > q) lo = m; else hi = m;
        }
        return (lo + hi) / 2;
    }

    // ---- Student t ----
    private static double betacf(double a, double b, double x) {
        final double fp = 1e-30, eps = 3e-14;
        double qab = a + b, qap = a + 1, qam = a - 1, c = 1, d = 1 - qab * x / qap;
        if (Math.abs(d) < fp) d = fp;
        d = 1 / d;
        double h = d;
        for (int m = 1; m <= 500; m++) {
            int m2 = 2 * m;
            double aa = m * (b - m) * x / ((qam + m2) * (a + m2));
            d = 1 + aa * d; if (Math.abs(d) < fp) d = fp;
            c = 1 + aa / c; if (Math.abs(c) < fp) c = fp;
            d = 1 / d; h *= d * c;
            aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2));
            d = 1 + aa * d; if (Math.abs(d) < fp) d = fp;
            c = 1 + aa / c; if (Math.abs(c) < fp) c = fp;
            d = 1 / d;
            double del = d * c;
            h *= del;
            if (Math.abs(del - 1) < eps) break;
        }
        return h;
    }

    static double regBeta(double x, double a, double b) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        double bt = Math.exp(logGamma(a + b) - logGamma(a) - logGamma(b)
                + a * Math.log(x) + b * Math.log(1 - x));
        if (x < (a + 1) / (a + b + 2)) return bt * betacf(a, b, x) / a;
        return 1 - bt * betacf(b, a, 1 - x) / b;
    }

    static double tCdf(double t, double df) {
        double x = df / (df + t * t);
        double ib = regBeta(x, df / 2, 0.5);
        return t >= 0 ? 1 - 0.5 * ib : 0.5 * ib;
    }

    /** Student t quantile, 0 < p < 1, df >= 1. */
    static double tQ(double p, double df) {
        if (!(p > 0 && p < 1)) return Double.NaN;
        if (df < 1) df = 1;
        if (df > 1e7) return normQ(p);
        if (p == 0.5) return 0;
        if (p < 0.5) return -tQ(1 - p, df);
        double lo = 0, hi = 2;
        while (tCdf(hi, df) < p && hi < 1e9) hi *= 2;
        for (int i = 0; i < 200; i++) {
            double m = (lo + hi) / 2;
            if (tCdf(m, df) < p) lo = m; else hi = m;
        }
        return (lo + hi) / 2;
    }

    // ---- F distribution (central and non-central) ----
    static double fCdf(double f, double d1, double d2) {
        if (f <= 0) return 0;
        return regBeta(d1 * f / (d1 * f + d2), d1 / 2, d2 / 2);
    }

    static double fQ(double p, double d1, double d2) {
        double lo = 0, hi = 1;
        while (fCdf(hi, d1, d2) < p && hi < 1e12) hi *= 2;
        for (int i = 0; i < 200; i++) {
            double m = (lo + hi) / 2;
            if (fCdf(m, d1, d2) < p) lo = m; else hi = m;
        }
        return (lo + hi) / 2;
    }

    /** Non-central F CDF as a Poisson mixture of central beta terms. */
    static double ncfCdf(double f, double d1, double d2, double lambda) {
        if (f <= 0) return 0;
        if (lambda <= 0) return fCdf(f, d1, d2);
        double x = d1 * f / (d1 * f + d2), mu = lambda / 2, sd = Math.sqrt(mu);
        int j0 = (int) Math.max(0, Math.floor(mu - 40 * sd - 50)), j1 = (int) Math.ceil(mu + 40 * sd + 50);
        double sum = 0, lmu = Math.log(mu);
        for (int j = j0; j <= j1; j++) {
            double w = Math.exp(-mu + j * lmu - logGamma(j + 1.0));
            if (w < 1e-300) continue;
            sum += w * regBeta(x, d1 / 2 + j, d2 / 2);
        }
        return Math.min(1, sum);
    }
}
