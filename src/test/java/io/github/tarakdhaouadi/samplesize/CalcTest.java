package io.github.tarakdhaouadi.samplesize;

import java.util.Random;

/**
 * Numerical tests of the calculation engine (no test framework needed).
 * Run with build.sh test / build.bat test; exits with a non-zero status on any failure.
 */
public class CalcTest {
    static int checks = 0, fails = 0;

    static void eq(String name, long got, long exp) {
        checks++;
        if (got != exp) { fails++; System.out.println("FAIL " + name + ": got " + got + ", expected " + exp); }
    }
    static void near(String name, double got, double exp, double tol) {
        checks++;
        if (!(Math.abs(got - exp) <= tol)) { fails++; System.out.println("FAIL " + name + ": got " + got + ", expected " + exp); }
    }
    static void isTrue(String name, boolean cond) {
        checks++;
        if (!cond) { fails++; System.out.println("FAIL " + name); }
    }
    static Adj adj() { return new Adj(); }
    static Adj withCC() { Adj a = new Adj(); a.cc = true; return a; }
    static Adj withT() { Adj a = new Adj(); a.tDist = true; return a; }

    public static void main(String[] args) throws Exception {
        distributions();
        proportions();
        means();
        correlations();
        survival();
        otherDesigns();
        invariants();
        errors();
        fuzz();
        System.out.println(checks + " checks, " + fails + " failure(s)");
        System.exit(fails == 0 ? 0 : 1);
    }

    // ---------------------------------------------------------------- distributions
    static void distributions() {
        near("z .975", Stats.normQ(0.975), 1.959964, 1e-5);
        near("z .8", Stats.normQ(0.8), 0.841621, 1e-5);
        near("z .9", Stats.normQ(0.9), 1.281552, 1e-5);
        near("z .95", Stats.normQ(0.95), 1.644854, 1e-5);
        near("z .99", Stats.normQ(0.99), 2.326348, 1e-5);
        near("z .995", Stats.normQ(0.995), 2.575829, 1e-5);
        near("z .9995", Stats.normQ(0.9995), 3.290527, 1e-5);
        near("z symmetry", Stats.normQ(0.025), -Stats.normQ(0.975), 1e-12);
        near("t .975 df1", Stats.tQ(0.975, 1), 12.706205, 1e-4);
        near("t .975 df5", Stats.tQ(0.975, 5), 2.570582, 1e-5);
        near("t .975 df10", Stats.tQ(0.975, 10), 2.228139, 1e-5);
        near("t .975 df30", Stats.tQ(0.975, 30), 2.042272, 1e-5);
        near("t .975 df120", Stats.tQ(0.975, 120), 1.979930, 1e-5);
        near("t .995 df10", Stats.tQ(0.995, 10), 3.169273, 1e-5);
        near("t .95 df20", Stats.tQ(0.95, 20), 1.724718, 1e-5);
        near("F .95 (2,10)", Stats.fQ(0.95, 2, 10), 4.102821, 1e-4);
        near("F .95 (3,20)", Stats.fQ(0.95, 3, 20), 3.098391, 1e-4);
        near("F .95 (5,10)", Stats.fQ(0.95, 5, 10), 3.325835, 1e-4);
        for (int df : new int[]{3, 10, 40, 200})   // F(1, df) quantile equals the squared t quantile
            near("F(1,df) = t^2, df=" + df, Stats.fQ(0.95, 1, df), Math.pow(Stats.tQ(0.975, df), 2), 1e-6);
        near("ncF with lambda=0 is the central F", Stats.ncfCdf(3.0, 4, 20, 0), Stats.fCdf(3.0, 4, 20), 1e-12);
        isTrue("ncF cdf decreases with lambda", Stats.ncfCdf(3.0, 4, 20, 5) > Stats.ncfCdf(3.0, 4, 20, 10));
    }

    // ---------------------------------------------------------------- proportions
    static void proportions() throws InputException {
        eq("1P p=.7 d=.05", Calc.oneProportion(.95, .7, .05, false, adj()).n, 323);
        eq("1P p=.5 d=.05", Calc.oneProportion(.95, .5, .05, false, adj()).n, 385);
        eq("1P p=.5 d=.04", Calc.oneProportion(.95, .5, .04, false, adj()).n, 601);
        Adj a = adj(); a.responseRate = 0.8;
        eq("1P response rate .8", Calc.oneProportion(.95, .7, .05, false, a).n, 404);
        a = adj(); a.fpc = true; a.popN = 2000;
        eq("1P finite population N=2000 (rounded up)", Calc.oneProportion(.95, .7, .05, false, a).n, 279);

        eq("2P .60/.75 no CC", Calc.twoProportions(.6, .75, 1, Hyp.EQUALITY, 0, .05, .8, true, adj()).nTest, 150);
        eq("2P .60/.75 CC", Calc.twoProportions(.6, .75, 1, Hyp.EQUALITY, 0, .05, .8, true, withCC()).nTest, 163);
        eq("2P .70/.85 CC", Calc.twoProportions(.7, .85, 1, Hyp.EQUALITY, 0, .05, .8, true, withCC()).nTest, 131);
        eq("2P .60/.75 CC power 90", Calc.twoProportions(.6, .75, 1, Hyp.EQUALITY, 0, .05, .9, true, withCC()).nTest, 213);
        eq("2P .48/.55 CC", Calc.twoProportions(.48, .55, 1, Hyp.EQUALITY, 0, .05, .8, true, withCC()).nTest, 825);
        Result r = Calc.twoProportions(.6, .75, 2, Hyp.EQUALITY, 0, .05, .8, true, adj());
        eq("2P ratio 2: reference = 2 x test", r.nRef, 2 * r.nTest);

        eq("2PP b=.15 c=.05 CC", Calc.pairedProportions(.15, .05, .05, .8, true, withCC()).n, 165);
        eq("2PP b=.20 c=.08 power 90 CC", Calc.pairedProportions(.20, .08, .05, .9, true, withCC()).n, 209);
        eq("2PP b=.12 c=.06 CC", Calc.pairedProportions(.12, .06, .05, .8, true, withCC()).n, 407);
        eq("2PP b=.25 c=.10 CC", Calc.pairedProportions(.25, .10, .05, .8, true, withCC()).n, 127);
        double[] bc = Calc.discordantFromMarginals(.75, .85, .6);
        near("2PP marginals -> b + c", bc[0] + bc[1], 0.17, 0.05);
        eq("2PP from marginals, no CC", Calc.pairedProportions(bc[0], bc[1], .05, .8, true, adj()).n, 108);
    }

    // ---------------------------------------------------------------- means
    static void means() throws InputException {
        eq("1M normal-based", Calc.oneMean(.95, 18, 3, adj()).n, 139);
        eq("1M t-adjusted", Calc.oneMean(.95, 18, 3, withT()).n, 141);
        eq("2M 12/5 normal-based", Calc.twoMeans(5, 12, 1, Hyp.EQUALITY, 0, .05, .8, true, adj()).nTest, 91);
        eq("2M 12/5 t-adjusted", Calc.twoMeans(5, 12, 1, Hyp.EQUALITY, 0, .05, .8, true, withT()).nTest, 92);
        eq("2M non-inferiority margin 3", Calc.twoMeans(0, 12, 1, Hyp.NONINFERIORITY, 3, .05, .8, true, adj()).nTest, 198);
        eq("2M 15/8 normal-based", Calc.twoMeans(8, 15, 1, Hyp.EQUALITY, 0, .05, .8, true, adj()).nTest, 56);
        // Cohen's d = 0.2 / 0.5 / 0.8 with the t correction (G*Power: 394, 64, 26 per group)
        eq("2M d=0.2 t-adjusted", Calc.twoMeans(.2, 1, 1, Hyp.EQUALITY, 0, .05, .8, true, withT()).nTest, 394);
        eq("2M d=0.5 t-adjusted", Calc.twoMeans(.5, 1, 1, Hyp.EQUALITY, 0, .05, .8, true, withT()).nTest, 64);
        eq("2M d=0.8 t-adjusted", Calc.twoMeans(.8, 1, 1, Hyp.EQUALITY, 0, .05, .8, true, withT()).nTest, 26);
        eq("paired mean dz=0.3 normal-based", Calc.pairedMeans(3, 10, .05, .8, true, adj()).n, 88);
        eq("paired mean dz=0.3 t-adjusted (G*Power: 90)", Calc.pairedMeans(3, 10, .05, .8, true, withT()).n, 90);
    }

    // ---------------------------------------------------------------- correlations
    static void correlations() throws InputException {
        eq("Pearson r=.3", Calc.correlation(false, .3, 0, .05, .8, true, adj()).n, 85);
        eq("Spearman rho=.3", Calc.correlation(true, .3, 0, .05, .8, true, adj()).n, 89);
        isTrue("Spearman needs more subjects than Pearson",
                Calc.correlation(true, .4, 0, .05, .8, true, adj()).n > Calc.correlation(false, .4, 0, .05, .8, true, adj()).n);
        eq("Pearson r=-.3 equals r=+.3", Calc.correlation(false, -.3, 0, .05, .8, true, adj()).n, 85);
    }

    // ---------------------------------------------------------------- survival
    static void survival() throws InputException {
        eq("HR .7: 247 events -> 124 + 124", Calc.hazardRatio(.7, 1, 1.0, .05, .8, true, adj()).n, 248);
        eq("HR .5: 66 events", Calc.hazardRatio(.5, 1, 1.0, .05, .8, true, adj()).n, 66);
        eq("HR 1/.5 equals HR .5", Calc.hazardRatio(2, 1, 1.0, .05, .8, true, adj()).n, 66);
        eq("Log-rank S .50 vs .65", Calc.logRank(.5, .65, 1, .05, .8, true, adj(), "").nTotal, 328);
    }

    // ---------------------------------------------------------------- other designs
    static void otherDesigns() throws InputException {
        eq("ANOVA 3 groups f=.25 (G*Power: 159)", Calc.anova(3, .25, .05, .8, adj(), "").nTotal, 159);
        eq("ANOVA 4 groups f=.25 (G*Power: 180)", Calc.anova(4, .25, .05, .8, adj(), "").nTotal, 180);
        eq("ANOVA 3 groups f=.40 (G*Power: 66)", Calc.anova(3, .4, .05, .8, adj(), "").nTotal, 66);
        eq("Regression 3 predictors f2=.15 (G*Power: 77)", Calc.regression(3, 3, .15, .05, .8, adj(), "").n, 77);
        eq("Regression 1 predictor f2=.15 (G*Power: 55)", Calc.regression(1, 1, .15, .05, .8, adj(), "").n, 55);
        eq("Diagnostic Se=.9, d=.05, prevalence .2", Calc.diagnostic(true, false, .9, .85, .2, .05, .95, adj()).n, 692);
    }

    // ---------------------------------------------------------------- properties that must always hold
    static void invariants() throws InputException {
        long n80 = Calc.twoProportions(.4, .55, 1, Hyp.EQUALITY, 0, .05, .80, true, adj()).nTest;
        long n90 = Calc.twoProportions(.4, .55, 1, Hyp.EQUALITY, 0, .05, .90, true, adj()).nTest;
        isTrue("more power needs more subjects", n90 > n80);
        long big = Calc.twoProportions(.4, .70, 1, Hyp.EQUALITY, 0, .05, .80, true, adj()).nTest;
        isTrue("a bigger effect needs fewer subjects", big < n80);
        isTrue("a smaller alpha needs more subjects",
                Calc.twoProportions(.4, .55, 1, Hyp.EQUALITY, 0, .01, .80, true, adj()).nTest > n80);
        isTrue("one-sided needs fewer subjects than two-sided",
                Calc.twoProportions(.4, .55, 1, Hyp.EQUALITY, 0, .05, .80, false, adj()).nTest < n80);
        isTrue("continuity correction increases n",
                Calc.twoProportions(.4, .55, 1, Hyp.EQUALITY, 0, .05, .80, true, withCC()).nTest > n80);
        Adj rr = adj(); rr.responseRate = 0.5;
        long inflated = Calc.twoProportions(.4, .55, 1, Hyp.EQUALITY, 0, .05, .80, true, rr).nTest;
        isTrue("response rate 50% about doubles n", Math.abs(inflated - 2 * n80) <= 2);
        Adj cl = adj(); cl.deff = 2;
        isTrue("design effect 2 about doubles n", Math.abs(Calc.oneProportion(.95, .5, .05, false, cl).n - 2 * 385) <= 2);
        long t1 = Calc.twoMeans(5, 12, 2, Hyp.EQUALITY, 0, .05, .8, true, adj()).nTotal;
        long t2 = Calc.twoMeans(5, 12, .5, Hyp.EQUALITY, 0, .05, .8, true, adj()).nTotal;
        isTrue("ratio r and 1/r need the same total", Math.abs(t1 - t2) <= 2);
        isTrue("t-adjustment never reduces n",
                Calc.oneMean(.95, 18, 3, withT()).n >= Calc.oneMean(.95, 18, 3, adj()).n);
        isTrue("ANOVA: more groups need more subjects in total",
                Calc.anova(5, .25, .05, .8, adj(), "").nTotal > Calc.anova(3, .25, .05, .8, adj(), "").nTotal);
        isTrue("regression: more predictors need more subjects",
                Calc.regression(8, 8, .15, .05, .8, adj(), "").n > Calc.regression(3, 3, .15, .05, .8, adj(), "").n);
        Result eqv = Calc.twoProportions(.5, .5, 1, Hyp.EQUIVALENCE, .10, .05, .8, true, adj());
        isTrue("equivalence design returns a size", eqv.nTest > 0);
        isTrue("non-inferiority needs fewer subjects than equality at the same effect",
                Calc.twoProportions(.5, .55, 1, Hyp.NONINFERIORITY, .10, .05, .8, true, adj()).nTest
                        < Calc.twoProportions(.5, .55, 1, Hyp.EQUALITY, 0, .05, .8, true, adj()).nTest);
    }

    // ---------------------------------------------------------------- invalid input is reported, not thrown
    static void errors() {
        expectError("equal proportions", () -> Calc.twoProportions(.5, .5, 1, Hyp.EQUALITY, 0, .05, .8, true, adj()));
        expectError("tiny precision", () -> Calc.oneProportion(.95, .5, 1e-12, false, adj()));
        expectError("equivalence margin too small", () -> Calc.twoProportions(.5, .6, 1, Hyp.EQUIVALENCE, .05, .05, .8, true, adj()));
        expectError("non-inferiority already violated", () -> Calc.twoMeans(-5, 10, 1, Hyp.NONINFERIORITY, 3, .05, .8, true, adj()));
        expectError("hazard ratio of 1", () -> Calc.hazardRatio(1, 1, .5, .05, .8, true, adj()));
        expectError("identical correlation", () -> Calc.correlation(false, .3, .3, .05, .8, true, adj()));
        expectError("one ANOVA group", () -> Calc.anova(1, .25, .05, .8, adj(), ""));
        expectError("inconsistent marginals", () -> Calc.discordantFromMarginals(.1, .9, .99));
    }

    interface Call { void run() throws Exception; }
    static void expectError(String name, Call c) {
        checks++;
        try { c.run(); fails++; System.out.println("FAIL (no error raised): " + name); }
        catch (InputException expected) { /* good */ }
        catch (Exception other) { fails++; System.out.println("FAIL (wrong exception) " + name + ": " + other); }
    }

    // ---------------------------------------------------------------- random inputs must never crash the engine
    static void fuzz() {
        Random rnd = new Random(12345);
        double[] pool = {0, 1, -1, 0.5, 0.05, 0.8, 0.95, 0.999, 1e-9, 1e9, 3, 10, 100,
                Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (int i = 0; i < 4000; i++) {
            double[] v = new double[7];
            for (int j = 0; j < v.length; j++) v[j] = rnd.nextInt(3) == 0 ? pool[rnd.nextInt(pool.length)] : rnd.nextDouble() * (j % 2 == 0 ? 1 : 20) - 0.05;
            Adj a = new Adj(); a.cc = rnd.nextBoolean(); a.tDist = rnd.nextBoolean();
            if (rnd.nextInt(4) == 0) a.responseRate = 0.2 + 0.8 * rnd.nextDouble();
            Hyp h = Hyp.values()[rnd.nextInt(4)];
            boolean two = rnd.nextBoolean();
            // ANOVA / regression search is bounded to moderate effects to keep the test fast
            double f = 0.05 + rnd.nextDouble() * 2;
            String label = "fuzz #" + i + " " + java.util.Arrays.toString(v);
            Call[] calls = {
                    () -> Calc.oneProportion(v[0], v[1], v[2], rnd.nextBoolean(), a),
                    () -> Calc.twoProportions(v[0], v[1], v[2], h, v[3], v[4], v[5], two, a),
                    () -> Calc.pairedProportions(v[0], v[1], v[2], v[3], two, a),
                    () -> Calc.oneMean(v[0], v[1], v[2], a),
                    () -> Calc.twoMeans(v[0], v[1], v[2], h, v[3], v[4], v[5], two, a),
                    () -> Calc.pairedMeans(v[0], v[1], v[2], v[3], two, a),
                    () -> Calc.correlation(rnd.nextBoolean(), v[0], v[1], v[2], v[3], two, a),
                    () -> Calc.hazardRatio(v[0], v[1], v[2], v[3], v[4], two, a),
                    () -> Calc.logRank(v[0], v[1], v[2], v[3], v[4], two, a, ""),
                    () -> Calc.anova(2 + rnd.nextInt(6), f, 0.01 + 0.09 * rnd.nextDouble(), 0.5 + 0.45 * rnd.nextDouble(), a, ""),
                    () -> Calc.regression(1 + rnd.nextInt(8), 1, f * f, 0.01 + 0.09 * rnd.nextDouble(), 0.5 + 0.45 * rnd.nextDouble(), a, ""),
                    () -> Calc.diagnostic(true, true, v[0], v[1], v[2], v[3], v[4], a)};
            for (Call c : calls) {
                checks++;
                try { c.run(); }
                catch (InputException ok) { /* reported as a message to the user */ }
                catch (Throwable t) { fails++; System.out.println("FAIL (crash) " + label + ": " + t); }
            }
        }
    }
}
