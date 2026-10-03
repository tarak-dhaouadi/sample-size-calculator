package io.github.tarakdhaouadi.samplesize;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/** Tarak Dhaouadi for sample size - Swing front end. */
public class SampleSizeApp {
    static final String APP_NAME = "Tarak Dhaouadi for sample size";
    static final Color NAVY = new Color(0x1F2A44), NAVY_HOVER = new Color(0x2C3B5E), ACCENT = new Color(0x2E7DD7);
    static final Color MUTED = new Color(0xAEB8CC), ERROR = new Color(0xB3261E);

    // ====================== form helpers ======================
    static final class Form {
        final JPanel panel = new JPanel(new GridBagLayout());
        private int row = 0;
        private final List<Runnable> resetters = new ArrayList<>();
        private final List<Runnable> afterReset = new ArrayList<>();
        private final Runnable onEnter;

        Form(Runnable onEnter) {
            this.onEnter = onEnter;
            panel.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        }
        private GridBagConstraints gc(int x, int w) {
            GridBagConstraints g = new GridBagConstraints();
            g.gridx = x; g.gridy = row; g.gridwidth = w; g.insets = new Insets(3, 2, 3, 6);
            g.anchor = GridBagConstraints.WEST;
            return g;
        }
        void section(String text) {
            JLabel l = new JLabel(text);
            l.setFont(l.getFont().deriveFont(Font.BOLD, l.getFont().getSize2D() + 1f));
            l.setForeground(ACCENT);
            GridBagConstraints g = gc(0, 2);
            g.insets = new Insets(row == 0 ? 2 : 14, 2, 2, 6);
            g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1;
            JPanel p = new JPanel(new BorderLayout(8, 0));
            p.setOpaque(false);
            p.add(l, BorderLayout.WEST);
            p.add(new JSeparator(), BorderLayout.CENTER);
            panel.add(p, g);
            row++;
        }
        void add(String label, JComponent c, String tip) {
            JLabel l = new JLabel(label);
            if (tip != null) { l.setToolTipText(tip); c.setToolTipText(tip); }
            panel.add(l, gc(0, 1));
            GridBagConstraints g = gc(1, 1);
            g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1;
            panel.add(c, g);
            row++;
        }
        JTextField field(String label, String def, String tip) {
            JTextField f = new JTextField(def, 10);
            f.addActionListener(e -> onEnter.run());
            resetters.add(() -> f.setText(def));
            add(label, f, tip);
            return f;
        }
        <T> JComboBox<T> combo(String label, T[] items, String tip) {
            JComboBox<T> c = new JComboBox<>(items);
            resetters.add(() -> c.setSelectedIndex(0));
            add(label, c, tip);
            return c;
        }
        JCheckBox check(String label, boolean sel) {
            JCheckBox c = new JCheckBox(label, sel);
            resetters.add(() -> c.setSelected(sel));
            GridBagConstraints g = gc(0, 2);
            panel.add(c, g);
            row++;
            return c;
        }
        JRadioButton radio(String label, ButtonGroup bg, boolean sel) {
            JRadioButton r = new JRadioButton(label, sel);
            bg.add(r);
            resetters.add(() -> r.setSelected(sel));
            GridBagConstraints g = gc(0, 2);
            g.insets = new Insets(1, 22, 1, 6);
            panel.add(r, g);
            row++;
            return r;
        }
        void onReset(Runnable r) { afterReset.add(r); }
        void reset() { resetters.forEach(Runnable::run); afterReset.forEach(Runnable::run); }
        /** Pushes everything to the top. */
        JPanel finish() {
            GridBagConstraints g = gc(0, 2);
            g.weighty = 1; g.fill = GridBagConstraints.BOTH;
            panel.add(Box.createGlue(), g);
            return panel;
        }
    }

    static double num(JTextField f, String name) throws InputException {
        String s = f.getText().trim().replace(',', '.');
        if (s.isEmpty()) throw new InputException("Please enter a value for \"" + name + "\".");
        try {
            double v = Double.parseDouble(s);
            if (Double.isNaN(v) || Double.isInfinite(v)) throw new NumberFormatException();
            return v;
        } catch (NumberFormatException e) {
            throw new InputException("\"" + name + "\" must be a number (you entered \"" + f.getText().trim() + "\").");
        }
    }
    /** lo < v < hi (exclusive). */
    static double open(JTextField f, String name, double lo, double hi) throws InputException {
        double v = num(f, name);
        if (!(v > lo && v < hi)) throw new InputException("\"" + name + "\" must be greater than " + Calc.num(lo) + " and less than " + Calc.num(hi) + ".");
        return v;
    }
    static double positive(JTextField f, String name) throws InputException {
        double v = num(f, name);
        if (!(v > 0)) throw new InputException("\"" + name + "\" must be greater than 0.");
        return v;
    }
    static int integer(JTextField f, String name, int min) throws InputException {
        double v = num(f, name);
        if (v != Math.rint(v) || v < min) throw new InputException("\"" + name + "\" must be a whole number of at least " + min + ".");
        return (int) v;
    }
    static double zeroToOne(JTextField f, String name) throws InputException { return open(f, name, 0, 1); }

    // ---- reusable adjustment blocks ----
    static final class Cluster {
        final JCheckBox on; final JRadioButton byIcc, byDeff; final JTextField icc, size, deff;
        Cluster(Form f, String sizeLabel) {
            on = f.check("Adjust for clustering", false);
            ButtonGroup bg = new ButtonGroup();
            byIcc = f.radio("ICC and cluster size", bg, true);
            icc = f.field("      Intraclass correlation (ICC)", "0.05", "Intraclass correlation coefficient, between 0 and 1");
            size = f.field("      " + sizeLabel, "10", "Average number of subjects (or pairs) per cluster");
            byDeff = f.radio("Design effect (DEFF)", bg, false);
            deff = f.field("      Design effect", "1.5", "Design effect, 1 or greater");
            Runnable r = this::refresh;
            on.addActionListener(e -> r.run()); byIcc.addActionListener(e -> r.run()); byDeff.addActionListener(e -> r.run());
            f.onReset(r);
            refresh();
        }
        void refresh() {
            boolean o = on.isSelected();
            byIcc.setEnabled(o); byDeff.setEnabled(o);
            icc.setEnabled(o && byIcc.isSelected()); size.setEnabled(o && byIcc.isSelected());
            deff.setEnabled(o && byDeff.isSelected());
        }
        void apply(Adj a) throws InputException {
            if (!on.isSelected()) return;
            if (byIcc.isSelected()) {
                double rho = num(icc, "Intraclass correlation (ICC)");
                if (rho < 0 || rho > 1) throw new InputException("\"Intraclass correlation (ICC)\" must be between 0 and 1.");
                double m = num(size, "Cluster size");
                if (m < 1) throw new InputException("\"Cluster size\" must be at least 1.");
                a.deff = 1 + (m - 1) * rho;
                a.clusterNote = "DEFF = 1 + (m - 1) x ICC = 1 + (" + Calc.num(m) + " - 1) x " + Calc.num(rho);
            } else {
                double d = num(deff, "Design effect");
                if (d < 1) throw new InputException("\"Design effect\" must be 1 or greater.");
                a.deff = d;
            }
        }
    }

    static final class Response {
        final JCheckBox on; final JTextField rate;
        Response(Form f) {
            on = f.check("Adjust for response rate", false);
            rate = f.field("      Anticipated response rate (%)", "80", "Percentage of selected subjects expected to take part, greater than 0 and up to 100");
            on.addActionListener(e -> rate.setEnabled(on.isSelected()));
            f.onReset(() -> rate.setEnabled(on.isSelected()));
            rate.setEnabled(false);
        }
        void apply(Adj a) throws InputException {
            if (!on.isSelected()) return;
            double r = num(rate, "Anticipated response rate (%)");
            if (!(r > 0 && r <= 100)) throw new InputException("\"Anticipated response rate (%)\" must be greater than 0 and at most 100.");
            a.responseRate = r / 100;
        }
    }

    static final class Fpc {
        final JCheckBox on; final JTextField n;
        Fpc(Form f) {
            on = f.check("Adjust for finite population", false);
            n = f.field("      Population size (N)", "1000", "Total number of units in the population");
            on.addActionListener(e -> n.setEnabled(on.isSelected()));
            f.onReset(() -> n.setEnabled(on.isSelected()));
            n.setEnabled(false);
        }
        void apply(Adj a) throws InputException {
            if (!on.isSelected()) return;
            double v = num(n, "Population size (N)");
            if (v < 2) throw new InputException("\"Population size (N)\" must be at least 2.");
            a.fpc = true; a.popN = v;
        }
    }

    /** Power / significance / sidedness block. */
    static final class Options {
        final JTextField alpha, power; final JComboBox<String> tail;
        Options(Form f, boolean withTail) {
            f.section("Options");
            power = f.field("Desired power", "0.80", "Probability of detecting the effect, between 0 and 1");
            alpha = f.field("Level of significance (alpha)", "0.05", "Type I error rate, between 0 and 1");
            tail = withTail ? f.combo("Alternative hypothesis", new String[]{"Two-sided", "One-sided"}, "Applies to the equality test") : null;
        }
        double alpha() throws InputException { return zeroToOne(alpha, "Level of significance (alpha)"); }
        double power() throws InputException { return zeroToOne(power, "Desired power"); }
        boolean twoSided() { return tail == null || tail.getSelectedIndex() == 0; }
    }

    // ====================== modules ======================
    abstract static class Module {
        final String group, title, blurb;
        Form form;
        Module(String group, String title, String blurb) { this.group = group; this.title = title; this.blurb = blurb; }
        abstract JPanel build(Runnable calc);
        abstract Result compute() throws InputException;
        void reset() { form.reset(); }
    }

    static final class OneProportion extends Module {
        JTextField p, prec;
        OneProportion() { super("Proportions", "Estimate a Single Proportion", "Sample size to estimate a proportion (e.g. a prevalence) with a given precision."); }
        JTextField cl2;
        JComboBox<String> kind; Fpc fpc; Cluster cluster; Response resp;
        JPanel build(Runnable calc) {
            form = new Form(calc);
            form.section("Input values");
            cl2 = form.field("Level of confidence", "0.95", "e.g. 0.95 for 95% confidence");
            p = form.field("Expected proportion", "0.50", "Use 0.50 if unknown (most conservative)");
            kind = form.combo("Precision is", new String[]{"Absolute value", "Relative to expected proportion"}, null);
            prec = form.field("Precision (margin of error)", "0.05", "Half-width of the confidence interval");
            form.section("Adjustments");
            fpc = new Fpc(form); cluster = new Cluster(form, "Cluster size"); resp = new Response(form);
            return form.finish();
        }
        Result compute() throws InputException {

            double c = zeroToOne(cl2, "Level of confidence"), pp = zeroToOne(p, "Expected proportion");
            boolean rel = kind.getSelectedIndex() == 1;
            double d = positive(prec, "Precision (margin of error)");
            if (!rel && d >= 1) throw new InputException("\"Precision (margin of error)\" must be below 1 for an absolute precision.");
            Adj a = new Adj(); fpc.apply(a); cluster.apply(a); resp.apply(a);
            Result res = Calc.oneProportion(c, pp, d, rel, a);
            res.plotSource = () -> Plots.oneProportion(c, pp, d, rel, a);
            return res;
        }
    }
    static final class TwoProportions extends Module {
        JComboBox<Hyp> hyp; JComboBox<String> how; JTextField p0, p1, ratio, margin;
        Options opt; JCheckBox cc; Cluster cluster; Response resp;
        TwoProportions() { super("Proportions", "Compare Two Independent Proportions", "Equality, non-inferiority, superiority and equivalence designs for two groups."); }
        JPanel build(Runnable calc) {
            form = new Form(calc);
            form.section("Input values");
            hyp = form.combo("Hypothesis", Hyp.values(), null);
            p0 = form.field("Proportion in reference group", "0.20", "Expected outcome proportion in the reference (control) group");
            how = form.combo("Specify the test group by", new String[]{"Proportion", "Difference in proportions", "Relative risk", "Odds ratio"}, null);
            p1 = form.field("Test group value", "0.35", "Proportion, difference, relative risk or odds ratio, as selected above");
            margin = form.field("Margin (enter as positive)", "0.10", "Non-inferiority / superiority / equivalence margin");
            ratio = form.field("Ratio reference : test group", "1", "1 = equal group sizes; 2 = reference group twice the size of the test group");
            opt = new Options(form, true);
            form.section("Adjustments");
            cc = form.check("Apply continuity correction", true);
            cluster = new Cluster(form, "Cluster size"); resp = new Response(form);
            Runnable r = () -> { margin.setEnabled(hyp.getSelectedItem() != Hyp.EQUALITY); opt.tail.setEnabled(hyp.getSelectedItem() == Hyp.EQUALITY); };
            hyp.addActionListener(e -> r.run()); form.onReset(r); r.run();
            return form.finish();
        }
        Result compute() throws InputException {

            Hyp h = (Hyp) hyp.getSelectedItem();
            double a0 = zeroToOne(p0, "Proportion in reference group");
            double v = num(p1, "Test group value"), a1;
            switch (how.getSelectedIndex()) {
                case 1: a1 = a0 + v; break;
                case 2: if (v <= 0) throw new InputException("The relative risk must be greater than 0."); a1 = a0 * v; break;
                case 3: if (v <= 0) throw new InputException("The odds ratio must be greater than 0."); double o = v * a0 / (1 - a0); a1 = o / (1 + o); break;
                default: a1 = v;
            }
            if (!(a1 > 0 && a1 < 1)) throw new InputException("The resulting proportion in the test group is " + Calc.num(a1) + ", which is outside (0, 1). Please adjust the input.");
            final double q0 = a0, q1 = a1;
            double m = h == Hyp.EQUALITY ? 0 : positive(margin, "Margin");
            double r = positive(ratio, "Ratio reference : test group");
            Adj a = new Adj(); a.cc = cc.isSelected(); cluster.apply(a); resp.apply(a);
            final double al = opt.alpha(), pw = opt.power(); final boolean ts = opt.twoSided();
            Result res = Calc.twoProportions(q0, q1, r, h, m, al, pw, ts, a);
            res.plotSource = () -> Plots.twoProportions(q0, q1, r, h, m, al, pw, ts, a);
            return res;
        }
    }
    static final class PairedProportions extends Module {
        JComboBox<String> mode; JTextField p0, p1, rho, b, c; Options opt; JCheckBox cc; Cluster cluster;
        PairedProportions() { super("Proportions", "Compare Paired Proportions", "McNemar's test for a binary outcome measured twice on the same subjects."); }
        JPanel build(Runnable calc) {
            form = new Form(calc);
            form.section("Input values");
            mode = form.combo("Specify", new String[]{"Marginal proportions and correlation", "Discordant proportions"}, null);
            p0 = form.field("Proportion in reference (pre)", "0.25", "Expected proportion of positives in the reference / pre group");
            p1 = form.field("Proportion in comparison (post)", "0.35", "Expected proportion of positives in the comparison / post group");
            rho = form.field("Correlation between pairs", "0.30", "Correlation between the paired observations");
            b = form.field("Pairs shifting + to -  (b)", "0.15", "Proportion of pairs positive then negative");
            c = form.field("Pairs shifting - to +  (c)", "0.30", "Proportion of pairs negative then positive");
            opt = new Options(form, true);
            form.section("Adjustments");
            cc = form.check("Apply continuity correction", true);
            cluster = new Cluster(form, "Cluster size (pairs)");
            Runnable r = () -> { boolean m = mode.getSelectedIndex() == 0; p0.setEnabled(m); p1.setEnabled(m); rho.setEnabled(m); b.setEnabled(!m); c.setEnabled(!m); };
            mode.addActionListener(e -> r.run()); form.onReset(r); r.run();
            return form.finish();
        }
        Result compute() throws InputException {

            double bb, cc2;
            if (mode.getSelectedIndex() == 0) {
                double a0 = zeroToOne(p0, "Proportion in reference (pre)"), a1 = zeroToOne(p1, "Proportion in comparison (post)");
                double r = num(rho, "Correlation between pairs");
                if (r < -1 || r > 1) throw new InputException("\"Correlation between pairs\" must be between -1 and 1.");
                double[] d = Calc.discordantFromMarginals(a0, a1, r); bb = d[0]; cc2 = d[1];
            } else {
                bb = num(b, "Pairs shifting + to - (b)"); cc2 = num(c, "Pairs shifting - to + (c)");
            }
            final double fb = bb, fc = cc2;
            Adj a = new Adj(); a.cc = cc.isSelected(); cluster.apply(a);
            final double al = opt.alpha(), pw = opt.power(); final boolean ts = opt.twoSided();
            Result res = Calc.pairedProportions(fb, fc, al, pw, ts, a);
            res.plotSource = () -> Plots.pairedProportions(fb, fc, al, pw, ts, a);
            return res;
        }
    }
    static final class OneMean extends Module {
        JTextField cl, sd, prec; JCheckBox t; Fpc fpc; Cluster cluster; Response resp;
        OneMean() { super("Means", "Estimate a Mean", "Sample size to estimate a mean with a given precision."); }
        JPanel build(Runnable calc) {
            form = new Form(calc);
            form.section("Input values");
            cl = form.field("Level of confidence", "0.95", "e.g. 0.95 for 95% confidence");
            sd = form.field("Expected standard deviation", "18", null);
            prec = form.field("Precision (margin of error)", "3", "Half-width of the confidence interval, in the units of the measurement");
            form.section("Adjustments");
            t = form.check("Adjust for t-distribution", true);
            fpc = new Fpc(form); cluster = new Cluster(form, "Cluster size"); resp = new Response(form);
            return form.finish();
        }
        Result compute() throws InputException {

            double c = zeroToOne(cl, "Level of confidence"), s = positive(sd, "Expected standard deviation"), d = positive(prec, "Precision (margin of error)");
            Adj a = new Adj(); a.tDist = t.isSelected(); fpc.apply(a); cluster.apply(a); resp.apply(a);
            Result res = Calc.oneMean(c, s, d, a);
            res.plotSource = () -> Plots.oneMean(c, s, d, a);
            return res;
        }
    }
    static final class TwoMeans extends Module {
        JComboBox<Hyp> hyp; JComboBox<String> how; JTextField m0, m1, diff, sd, margin, ratio; Options opt; JCheckBox t; Cluster cluster;
        TwoMeans() { super("Means", "Compare Two Independent Means", "Equality, non-inferiority, superiority and equivalence designs for two groups."); }
        JPanel build(Runnable calc) {
            form = new Form(calc);
            form.section("Input values");
            hyp = form.combo("Hypothesis", Hyp.values(), null);
            how = form.combo("Specify", new String[]{"Expected means", "Expected difference between means"}, null);
            m0 = form.field("Mean of reference group", "10", null);
            m1 = form.field("Mean of test group", "15", null);
            diff = form.field("Difference (test - reference)", "5", null);
            sd = form.field("Standard deviation", "12", "Common standard deviation of both groups");
            margin = form.field("Margin (enter as positive)", "3", "Non-inferiority / superiority / equivalence margin");
            ratio = form.field("Ratio reference : test group", "1", "1 = equal group sizes");
            opt = new Options(form, true);
            form.section("Adjustments");
            t = form.check("Adjust for t-distribution", true);
            cluster = new Cluster(form, "Cluster size");
            Runnable r = () -> {
                boolean means = how.getSelectedIndex() == 0;
                m0.setEnabled(means); m1.setEnabled(means); diff.setEnabled(!means);
                margin.setEnabled(hyp.getSelectedItem() != Hyp.EQUALITY); opt.tail.setEnabled(hyp.getSelectedItem() == Hyp.EQUALITY);
            };
            hyp.addActionListener(e -> r.run()); how.addActionListener(e -> r.run()); form.onReset(r); r.run();
            return form.finish();
        }
        Result compute() throws InputException {

            Hyp h = (Hyp) hyp.getSelectedItem();
            double d = how.getSelectedIndex() == 0 ? num(m1, "Mean of test group") - num(m0, "Mean of reference group") : num(diff, "Difference (test - reference)");
            double s = positive(sd, "Standard deviation");
            double m = h == Hyp.EQUALITY ? 0 : positive(margin, "Margin");
            double r = positive(ratio, "Ratio reference : test group");
            Adj a = new Adj(); a.tDist = t.isSelected(); cluster.apply(a);
            final double al = opt.alpha(), pw = opt.power(); final boolean ts = opt.twoSided();
            Result res = Calc.twoMeans(d, s, r, h, m, al, pw, ts, a);
            res.plotSource = () -> Plots.twoMeans(d, s, r, h, m, al, pw, ts, a);
            return res;
        }
    }
    static final class PairedMeans extends Module {
        JComboBox<String> how; JTextField mean, sd, es; Options opt; JCheckBox t; Cluster cluster;
        PairedMeans() { super("Means", "Compare Paired Differences", "Paired t-test for a continuous outcome measured twice on the same subjects."); }
        JPanel build(Runnable calc) {
            form = new Form(calc);
            form.section("Input values");
            how = form.combo("Specify", new String[]{"Mean and SD of the differences", "Effect size"}, null);
            mean = form.field("Expected mean of the differences", "3", null);
            sd = form.field("Expected SD of the differences", "10", null);
            es = form.field("Expected effect size", "0.30", "Mean difference divided by the SD of the differences");
            opt = new Options(form, true);
            form.section("Adjustments");
            t = form.check("Adjust for t-distribution", true);
            cluster = new Cluster(form, "Cluster size (pairs)");
            Runnable r = () -> { boolean m = how.getSelectedIndex() == 0; mean.setEnabled(m); sd.setEnabled(m); es.setEnabled(!m); };
            how.addActionListener(e -> r.run()); form.onReset(r); r.run();
            return form.finish();
        }
        Result compute() throws InputException {

            Adj a = new Adj(); a.tDist = t.isSelected(); cluster.apply(a);
            final double al = opt.alpha(), pw = opt.power(); final boolean ts = opt.twoSided();
            if (how.getSelectedIndex() == 0) {
                double m = num(mean, "Expected mean of the differences"), s = positive(sd, "Expected SD of the differences");
                Result res = Calc.pairedMeans(m, s, al, pw, ts, a);
                res.plotSource = () -> Plots.pairedMeans(m, s, al, pw, ts, a);
                return res;
            }
            double e = Math.abs(num(es, "Expected effect size"));
            Result res = Calc.pairedMeansES(e, Double.NaN, Double.NaN, al, pw, ts, a);
            res.plotSource = () -> Plots.pairedMeansES(e, al, pw, ts, a);
            return res;
        }
    }

    // ---------- correlations ----------
    static final class Correlation extends Module {
        final boolean spearman; JTextField r, r0; Options opt; Response resp;
        Correlation(boolean spearman) {
            super("Correlations", spearman ? "Spearman Correlation" : "Pearson Correlation",
                    spearman ? "Sample size to detect a rank correlation (Fisher z with Bonett-Wright correction)." : "Sample size to detect a linear correlation (Fisher z transformation).");
            this.spearman = spearman;
        }
        JPanel build(Runnable calc) {
            form = new Form(calc);
            form.section("Input values");
            r = form.field("Expected correlation", "0.30", "Expected correlation coefficient, between -1 and 1");
            if (!spearman) r0 = form.field("Correlation under H0", "0", "Usually 0 (no correlation)");
            opt = new Options(form, true);
            form.section("Adjustments");
            resp = new Response(form);
            return form.finish();
        }
        Result compute() throws InputException {

            double rr = open(r, "Expected correlation", -1, 1);
            double r00 = spearman ? 0 : open(r0, "Correlation under H0", -1, 1);
            Adj a = new Adj(); resp.apply(a);
            final double al = opt.alpha(), pw = opt.power(); final boolean ts = opt.twoSided();
            Result res = Calc.correlation(spearman, rr, r00, al, pw, ts, a);
            res.plotSource = () -> Plots.correlation(spearman, rr, r00, al, pw, ts, a);
            return res;
        }
    }

    // ---------- survival ----------
    static final class HazardRatio extends Module {
        JTextField hr, ratio, pev; Options opt; Response resp;
        HazardRatio() { super("Survival", "Hazard Ratio", "Number of events and subjects needed to detect a hazard ratio (Schoenfeld)."); }
        JPanel build(Runnable calc) {
            form = new Form(calc);
            form.section("Input values");
            hr = form.field("Expected hazard ratio", "0.70", "Hazard in the test group divided by hazard in the reference group");
            pev = form.field("Overall event probability", "0.60", "Expected proportion of all subjects who will have the event during the study, between 0 and 1");
            ratio = form.field("Ratio reference : test group", "1", "1 = equal group sizes");
            opt = new Options(form, true);
            form.section("Adjustments");
            resp = new Response(form);
            return form.finish();
        }
        Result compute() throws InputException {

            double h = positive(hr, "Expected hazard ratio");
            double p = num(pev, "Overall event probability");
            if (!(p > 0 && p <= 1)) throw new InputException("\"Overall event probability\" must be greater than 0 and at most 1.");
            double r = positive(ratio, "Ratio reference : test group");
            Adj a = new Adj(); resp.apply(a);
            final double al = opt.alpha(), pw = opt.power(); final boolean ts = opt.twoSided();
            Result res = Calc.hazardRatio(h, r, p, al, pw, ts, a);
            res.plotSource = () -> Plots.hazardRatio(h, r, p, al, pw, ts, a);
            return res;
        }
    }
    static final class LogRank extends Module {
        JComboBox<String> how; JTextField s0, s1, m0, m1, time, ratio; Options opt; Response resp;
        LogRank() { super("Survival", "Log-rank Test", "Sample size to compare two survival curves, from survival proportions or median survival."); }
        JPanel build(Runnable calc) {
            form = new Form(calc);
            form.section("Input values");
            how = form.combo("Specify", new String[]{"Survival proportions", "Median survival times"}, null);
            s0 = form.field("Survival in reference group", "0.50", "Proportion surviving to the end of follow-up, reference group");
            s1 = form.field("Survival in test group", "0.65", "Proportion surviving to the end of follow-up, test group");
            m0 = form.field("Median survival, reference", "12", "In any time unit, the same as the follow-up time");
            m1 = form.field("Median survival, test", "18", "In the same time unit");
            time = form.field("Follow-up time", "24", "Length of follow-up, same time unit as the medians");
            ratio = form.field("Ratio reference : test group", "1", "1 = equal group sizes");
            opt = new Options(form, true);
            form.section("Adjustments");
            resp = new Response(form);
            Runnable r = () -> { boolean p = how.getSelectedIndex() == 0; s0.setEnabled(p); s1.setEnabled(p); m0.setEnabled(!p); m1.setEnabled(!p); time.setEnabled(!p); };
            how.addActionListener(e -> r.run()); form.onReset(r); r.run();
            return form.finish();
        }
        Result compute() throws InputException {

            double a0, a1; String note = "";
            if (how.getSelectedIndex() == 0) {
                a0 = zeroToOne(s0, "Survival in reference group"); a1 = zeroToOne(s1, "Survival in test group");
            } else {
                double x0 = positive(m0, "Median survival, reference"), x1 = positive(m1, "Median survival, test"), t = positive(time, "Follow-up time");
                a0 = Math.exp(-Math.log(2) * t / x0); a1 = Math.exp(-Math.log(2) * t / x1);
                note = "Medians " + Calc.num(x0) + " (reference) and " + Calc.num(x1) + " (test) over " + Calc.num(t) + " of follow-up give survival " + Calc.num(a0) + " and " + Calc.num(a1) + ".";
            }
            final double fs0 = a0, fs1 = a1;
            double r = positive(ratio, "Ratio reference : test group");
            Adj a = new Adj(); resp.apply(a);
            final double al = opt.alpha(), pw = opt.power(); final boolean ts = opt.twoSided();
            Result res = Calc.logRank(fs0, fs1, r, al, pw, ts, a, note);
            res.plotSource = () -> Plots.logRank(fs0, fs1, r, al, pw, ts, a);
            return res;
        }
    }

    // ---------- other designs ----------
    static final class Anova extends Module {
        JComboBox<String> how; JTextField k, f, means, sd; Options opt; Response resp;
        Anova() { super("Other designs", "ANOVA (one-way)", "Sample size per group to compare several means with an F test."); }
        JPanel build(Runnable calc) {
            form = new Form(calc);
            form.section("Input values");
            how = form.combo("Specify", new String[]{"Cohen's f", "Group means and common SD"}, null);
            k = form.field("Number of groups", "3", "At least 2");
            f = form.field("Effect size (Cohen's f)", "0.25", "0.10 small, 0.25 medium, 0.40 large");
            means = form.field("Expected group means", "10; 12; 15", "Separate the means with semicolons or spaces");
            sd = form.field("Common standard deviation", "5", null);
            opt = new Options(form, false);
            form.section("Adjustments");
            resp = new Response(form);
            Runnable r = () -> { boolean c = how.getSelectedIndex() == 0; k.setEnabled(c); f.setEnabled(c); means.setEnabled(!c); sd.setEnabled(!c); };
            how.addActionListener(e -> r.run()); form.onReset(r); r.run();
            return form.finish();
        }
        Result compute() throws InputException {

            int groups; double cf; String note = "";
            if (how.getSelectedIndex() == 0) {
                groups = integer(k, "Number of groups", 2); cf = positive(f, "Effect size (Cohen's f)");
            } else {
                String[] tok = means.getText().trim().split("[;\\s]+");
                if (tok.length < 2 || tok[0].isEmpty()) throw new InputException("Enter at least two group means, separated by semicolons or spaces.");
                double[] m = new double[tok.length]; double sum = 0;
                for (int i = 0; i < tok.length; i++) {
                    try { m[i] = Double.parseDouble(tok[i].replace(',', '.')); }
                    catch (NumberFormatException e) { throw new InputException("\"" + tok[i] + "\" is not a valid mean. Separate the means with semicolons or spaces."); }
                    sum += m[i];
                }
                double mu = sum / m.length, ss = 0;
                for (double v : m) ss += (v - mu) * (v - mu);
                double s = positive(sd, "Common standard deviation");
                groups = m.length; cf = Math.sqrt(ss / m.length) / s;
                if (cf == 0) throw new InputException("The group means are all equal, so there is no effect to detect.");
                note = groups + " group means with SD " + Calc.num(s) + " give Cohen's f = " + Calc.num(cf) + ".";
            }
            final int fg = groups; final double ff = cf;
            Adj a = new Adj(); resp.apply(a);
            final double al = opt.alpha(), pw = opt.power();
            Result res = Calc.anova(fg, ff, al, pw, a, note);
            res.plotSource = () -> Plots.anova(fg, ff, al, pw, a);
            return res;
        }
    }
    static final class Regression extends Module {
        JComboBox<String> how; JTextField p, q, f2, r2full, r2test; Options opt; Response resp;
        Regression() { super("Other designs", "Linear Regression", "Sample size for the F test of a multiple linear regression (whole model or a subset of predictors)."); }
        JPanel build(Runnable calc) {
            form = new Form(calc);
            form.section("Input values");
            p = form.field("Total predictors in the model", "3", "Number of independent variables");
            q = form.field("Predictors being tested", "3", "Same as the total to test the whole model; fewer to test a subset");
            how = form.combo("Specify the effect as", new String[]{"Cohen's f-squared", "R-squared values"}, null);
            f2 = form.field("Effect size (Cohen's f2)", "0.15", "0.02 small, 0.15 medium, 0.35 large");
            r2full = form.field("R-squared of the full model", "0.13", "Between 0 and 1");
            r2test = form.field("R-squared due to tested predictors", "0.13", "Equal to the full-model R-squared when testing the whole model");
            opt = new Options(form, false);
            form.section("Adjustments");
            resp = new Response(form);
            Runnable r = () -> { boolean c = how.getSelectedIndex() == 0; f2.setEnabled(c); r2full.setEnabled(!c); r2test.setEnabled(!c); };
            how.addActionListener(e -> r.run()); form.onReset(r); r.run();
            return form.finish();
        }
        Result compute() throws InputException {

            int pp = integer(p, "Total predictors in the model", 1), qq = integer(q, "Predictors being tested", 1);
            double e; String note = "";
            if (how.getSelectedIndex() == 0) e = positive(f2, "Effect size (Cohen's f2)");
            else {
                double rf = zeroToOne(r2full, "R-squared of the full model"), rt = zeroToOne(r2test, "R-squared due to tested predictors");
                if (rt > rf) throw new InputException("The R-squared due to the tested predictors cannot exceed the R-squared of the full model.");
                e = rt / (1 - rf);
                note = "Cohen's f2 = R2(tested) / (1 - R2(full)) = " + Calc.num(e) + ".";
            }
            final double fe = e;
            Adj a = new Adj(); resp.apply(a);
            final double al = opt.alpha(), pw = opt.power();
            Result res = Calc.regression(pp, qq, fe, al, pw, a, note);
            res.plotSource = () -> Plots.regression(pp, qq, fe, al, pw, a);
            return res;
        }
    }
    static final class Diagnostic extends Module {
        JComboBox<String> which; JTextField se, sp, prev, prec, cl; Response resp;
        Diagnostic() { super("Other designs", "Diagnostic Accuracy", "Sample size to estimate the sensitivity and specificity of a test with a given precision."); }
        JPanel build(Runnable calc) {
            form = new Form(calc);
            form.section("Input values");
            which = form.combo("Estimate", new String[]{"Sensitivity and specificity", "Sensitivity only", "Specificity only"}, null);
            se = form.field("Expected sensitivity", "0.90", "Between 0 and 1");
            sp = form.field("Expected specificity", "0.85", "Between 0 and 1");
            prev = form.field("Disease prevalence", "0.20", "Proportion of the studied population with the disease");
            prec = form.field("Precision (margin of error)", "0.05", "Half-width of the confidence interval");
            cl = form.field("Level of confidence", "0.95", "e.g. 0.95 for 95% confidence");
            form.section("Adjustments");
            resp = new Response(form);
            Runnable r = () -> { int w = which.getSelectedIndex(); se.setEnabled(w != 2); sp.setEnabled(w != 1); };
            which.addActionListener(e -> r.run()); form.onReset(r); r.run();
            return form.finish();
        }
        Result compute() throws InputException {

            int w = which.getSelectedIndex();
            double a0 = w != 2 ? zeroToOne(se, "Expected sensitivity") : 0.5, a1 = w != 1 ? zeroToOne(sp, "Expected specificity") : 0.5;
            double pv = zeroToOne(prev, "Disease prevalence"), d = positive(prec, "Precision (margin of error)"), c = zeroToOne(cl, "Level of confidence");
            if (d >= 1) throw new InputException("\"Precision (margin of error)\" must be below 1.");
            Adj a = new Adj(); resp.apply(a);
            Result res = Calc.diagnostic(w != 2, w != 1, a0, a1, pv, d, c, a);
            res.plotSource = () -> Plots.diagnostic(w != 2, w != 1, a0, a1, pv, d, c, a);
            return res;
        }
    }

    // ====================== sidebar button ======================
    static final class NavButton extends JToggleButton {
        private boolean hover;
        NavButton(String text) {
            super(text);
            setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false); setOpaque(false);
            setHorizontalAlignment(LEFT); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }
        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(getText()) + 44, fm.getHeight() + 13);
        }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (isSelected()) { g2.setColor(ACCENT); g2.fillRoundRect(8, 1, getWidth() - 16, getHeight() - 2, 8, 8); }
            else if (hover) { g2.setColor(NAVY_HOVER); g2.fillRoundRect(8, 1, getWidth() - 16, getHeight() - 2, 8, 8); }
            g2.setColor(Color.WHITE);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(), 22, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /** Round profile photo loaded from the jar (photo.jpg); invisible if the resource is missing. */
    static final class Avatar extends JComponent {
        private BufferedImage img;
        Avatar() {
            try (InputStream in = SampleSizeApp.class.getResourceAsStream("/photo.jpg")) {
                if (in != null) img = ImageIO.read(in);
            } catch (Exception ignored) { }
            Dimension d = img == null ? new Dimension(0, 0) : new Dimension(60, 60);
            setPreferredSize(d); setMinimumSize(d); setMaximumSize(d);
        }
        @Override protected void paintComponent(Graphics g) {
            if (img == null) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int s = Math.min(getWidth(), getHeight()) - 4;
            g2.setClip(new java.awt.geom.Ellipse2D.Float(2, 2, s, s));
            g2.drawImage(img, 2, 2, s, s, null);
            g2.setClip(null);
            g2.setColor(new Color(255, 255, 255, 190));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(2, 2, s, s);
            g2.dispose();
        }
    }

    // ====================== plots: background drawing, enlarge, save ======================
    /** Keeps the chart at a pleasant aspect ratio at the top of its tab. */
    static final class AspectPanel extends JPanel {
        private final ChartPanel chart;
        AspectPanel(ChartPanel chart) { super(null); this.chart = chart; add(chart); setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12)); }
        @Override public void doLayout() {
            Insets in = getInsets();
            int w = getWidth() - in.left - in.right, h = getHeight() - in.top - in.bottom;
            chart.setBounds(in.left, in.top, Math.max(0, w), Math.max(0, Math.min(h, (int) (w * 0.64))));
        }
    }

    static JPanel plotTab(ChartPanel chart, java.util.function.Supplier<String> moduleName, String kind) {
        JPanel tab = new JPanel(new BorderLayout());
        AspectPanel holder = new AspectPanel(chart);
        chart.setBorder(BorderFactory.createLineBorder(new Color(0xD0D4DC)));
        JButton enlarge = new JButton("Enlarge"), save = new JButton("Save as PNG...");
        enlarge.addActionListener(e -> enlarge(tab, chart, moduleName.get()));
        save.addActionListener(e -> savePng(tab, chart, moduleName.get().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "") + "-" + kind + ".png"));
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        bar.add(enlarge); bar.add(save);
        tab.add(holder, BorderLayout.CENTER);
        tab.add(bar, BorderLayout.SOUTH);
        return tab;
    }

    /** Computes the two charts off the screen thread and shows them when ready (ignored if superseded). */
    static void startPlots(Result r, int[] token, ChartPanel c1, ChartPanel c2, JTabbedPane tabs) {
        final int mine = ++token[0];
        if (r.plotSource == null) { c1.setMessage("No plot for this result."); c2.setMessage("No plot for this result."); return; }
        c1.setMessage("Drawing the plot..."); c2.setMessage("Drawing the plot...");
        new SwingWorker<Plots, Void>() {
            @Override protected Plots doInBackground() throws Exception { return r.plotSource.call(); }
            @Override protected void done() {
                if (mine != token[0]) return;
                try {
                    Plots p = get();
                    c1.setPlot(p.size); c2.setPlot(p.power);
                    tabs.setTitleAt(2, p.powerTabTitle);
                } catch (Exception ex) {
                    String m = "The plots could not be drawn for these values.";
                    c1.setMessage(m); c2.setMessage(m);
                }
            }
        }.execute();
    }

    static void enlarge(Component parent, ChartPanel src, String title) {
        if (src.getPlot() == null) return;
        Window w = SwingUtilities.getWindowAncestor(parent);
        JDialog d = new JDialog(w, title, Dialog.ModalityType.MODELESS);
        ChartPanel big = new ChartPanel();
        big.setPlot(src.getPlot());
        big.setPreferredSize(new Dimension(940, 570));
        d.setContentPane(big);
        d.pack();
        d.setLocationRelativeTo(w);
        d.setVisible(true);
    }

    static void savePng(Component parent, ChartPanel src, String defaultName) {
        if (src.getPlot() == null) {
            JOptionPane.showMessageDialog(parent, "Click Calculate first to draw the plot.", APP_NAME, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new java.io.File(defaultName));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG image (*.png)", "png"));
        if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        java.io.File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".png")) f = new java.io.File(f.getPath() + ".png");
        if (f.exists() && JOptionPane.showConfirmDialog(parent, f.getName() + " already exists. Replace it?", APP_NAME,
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            ImageIO.write(src.render(1400, 850), "png", f);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, "The image could not be saved:\n" + ex.getMessage(), APP_NAME, JOptionPane.ERROR_MESSAGE);
        }
    }

    // ====================== main window ======================
    /** All calculators, in sidebar order. */
    static Module[] createModules() {
        return new Module[]{new OneProportion(), new TwoProportions(), new PairedProportions(), new OneMean(), new TwoMeans(), new PairedMeans(),
                new Correlation(false), new Correlation(true), new HazardRatio(), new LogRank(), new Anova(), new Regression(), new Diagnostic()};
    }

    static JPanel buildContent() {
        Module[] mods = createModules();

        JPanel root = new JPanel(new BorderLayout());

        // --- results area ---
        JTextArea headline = new JTextArea(" ");
        headline.setEditable(false); headline.setLineWrap(true); headline.setWrapStyleWord(true);
        headline.setOpaque(false); headline.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        headline.setFont(headline.getFont().deriveFont(Font.BOLD, 28f));
        headline.setForeground(ACCENT);
        JTextArea text = new JTextArea();
        text.setEditable(false); text.setLineWrap(true); text.setWrapStyleWord(true);
        text.setBorder(BorderFactory.createEmptyBorder(6, 2, 6, 2));
        text.setOpaque(false);
        JScrollPane textScroll = new JScrollPane(text);
        textScroll.setBorder(BorderFactory.createEmptyBorder());
        textScroll.setOpaque(false); textScroll.getViewport().setOpaque(false);
        JButton copy = new JButton("Copy result");
        copy.setEnabled(false);
        JLabel resTitle = new JLabel("Result");
        resTitle.setFont(resTitle.getFont().deriveFont(Font.BOLD, resTitle.getFont().getSize2D() + 1f));
        resTitle.setForeground(ACCENT);
        JPanel results = new JPanel(new BorderLayout(0, 6));
        results.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JPanel resTop = new JPanel(new BorderLayout());
        resTop.setOpaque(false);
        resTop.add(resTitle, BorderLayout.NORTH);
        resTop.add(headline, BorderLayout.CENTER);
        results.add(resTop, BorderLayout.NORTH);
        results.add(textScroll, BorderLayout.CENTER);
        JPanel copyBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        copyBar.setOpaque(false);
        copyBar.add(copy);
        results.add(copyBar, BorderLayout.SOUTH);

        final String[] lastText = {""};

        // --- plot tabs ---
        ChartPanel chart1 = new ChartPanel(), chart2 = new ChartPanel();
        JTabbedPane tabs = new JTabbedPane();
        final Module[] current = {mods[0]};
        tabs.addTab("Result", results);
        tabs.addTab("Sample size plot", plotTab(chart1, () -> current[0].title, "sample-size"));
        tabs.addTab("Power plot", plotTab(chart2, () -> current[0].title, "power"));
        final int[] token = {0};
        Runnable resetPlots = () -> {
            token[0]++;
            chart1.setMessage("Click Calculate to draw the plots.");
            chart2.setMessage("Click Calculate to draw the plots.");
            tabs.setTitleAt(2, "Power plot");
        };

        Runnable clear = () -> {
            headline.setText(" "); headline.setForeground(ACCENT);
            text.setText("Enter your values on the left and click Calculate.");
            copy.setEnabled(false);
            resetPlots.run();
        };
        Runnable calc = () -> {
            try {
                Result r = current[0].compute();
                headline.setForeground(ACCENT);
                headline.setText(r.headline);
                lastText[0] = current[0].title + "\n" + r.headline + "\n\n" + r.interpretation + "\n\nCalculation details\n" + r.details;
                text.setText(r.interpretation + "\n\nCalculation details\n" + r.details);
                copy.setEnabled(true);
                startPlots(r, token, chart1, chart2, tabs);
            } catch (InputException ex) {
                headline.setForeground(ERROR);
                headline.setText("Please check your input");
                text.setText(ex.getMessage());
                copy.setEnabled(false);
                resetPlots.run();
            } catch (Exception ex) {
                headline.setForeground(ERROR);
                headline.setText("Calculation error");
                text.setText("Something unexpected went wrong with these values: " + ex);
                copy.setEnabled(false);
                resetPlots.run();
            }
            text.setCaretPosition(0);
        };
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(lastText[0]), null));

        // --- header + forms ---
        JLabel title = new JLabel(), blurb = new JLabel();
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        blurb.setForeground(new Color(0x5A6478));
        JPanel header = new JPanel(new GridLayout(2, 1, 0, 2));
        header.setBorder(BorderFactory.createEmptyBorder(14, 20, 8, 20));
        header.add(title); header.add(blurb);

        CardLayout cards = new CardLayout();
        JPanel cardPanel = new JPanel(cards);
        for (int i = 0; i < mods.length; i++) {
            JScrollPane sp = new JScrollPane(mods[i].build(calc), ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            sp.setBorder(BorderFactory.createEmptyBorder());
            sp.getVerticalScrollBar().setUnitIncrement(16);
            cardPanel.add(sp, "m" + i);
        }
        JButton calcBtn = new JButton("Calculate"), resetBtn = new JButton("Reset");
        calcBtn.setFont(calcBtn.getFont().deriveFont(Font.BOLD));
        calcBtn.addActionListener(e -> calc.run());
        resetBtn.addActionListener(e -> { current[0].reset(); clear.run(); });
        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        btnBar.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        btnBar.add(calcBtn); btnBar.add(resetBtn);
        JPanel formSide = new JPanel(new BorderLayout());
        formSide.add(cardPanel, BorderLayout.CENTER);
        formSide.add(btnBar, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, formSide, tabs);
        split.setResizeWeight(0.4);
        tabs.setMinimumSize(new Dimension(380, 200)); formSide.setMinimumSize(new Dimension(480, 200));
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setContinuousLayout(true);
        JPanel main = new JPanel(new BorderLayout());
        main.add(header, BorderLayout.NORTH);
        main.add(split, BorderLayout.CENTER);

        // --- sidebar ---
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(NAVY);
        side.setBorder(BorderFactory.createEmptyBorder(16, 0, 12, 0));
        JLabel brand = new JLabel("Tarak Dhaouadi");
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 19f)); brand.setForeground(Color.WHITE);
        JLabel brand2 = new JLabel("for sample size");
        brand2.setFont(brand2.getFont().deriveFont(Font.PLAIN, 14f)); brand2.setForeground(MUTED);
        JPanel brandText = new JPanel(new GridLayout(2, 1));
        brandText.setOpaque(false);
        brandText.add(brand); brandText.add(brand2);
        JPanel brandBox = new JPanel(new BorderLayout(14, 0));
        brandBox.setOpaque(false); brandBox.setBorder(BorderFactory.createEmptyBorder(0, 22, 14, 18));
        brandBox.add(brandText, BorderLayout.CENTER);
        JPanel photoWrap = new JPanel(new GridBagLayout());
        photoWrap.setOpaque(false); photoWrap.add(new Avatar());
        brandBox.add(photoWrap, BorderLayout.EAST);
        brandBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        brandBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, brandBox.getPreferredSize().height));
        side.add(brandBox);

        ButtonGroup bg = new ButtonGroup();
        String lastGroup = "";
        List<NavButton> buttons = new ArrayList<>();
        for (int i = 0; i < mods.length; i++) {
            if (!mods[i].group.equals(lastGroup)) {
                lastGroup = mods[i].group;
                JLabel gl = new JLabel(lastGroup.toUpperCase());
                gl.setFont(gl.getFont().deriveFont(Font.BOLD, 11f)); gl.setForeground(MUTED);
                gl.setBorder(BorderFactory.createEmptyBorder(10, 22, 4, 22));
                gl.setAlignmentX(Component.LEFT_ALIGNMENT);
                side.add(gl);
            }
            NavButton b = new NavButton(mods[i].title);
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            bg.add(b); buttons.add(b); side.add(b);
            final int idx = i;
            b.addActionListener(e -> {
                current[0] = mods[idx];
                cards.show(cardPanel, "m" + idx);
                title.setText(mods[idx].title); blurb.setText(mods[idx].blurb);
                clear.run();
            });
        }
        side.add(Box.createVerticalGlue());

        JScrollPane sideScroll = new JScrollPane(side, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sideScroll.setBorder(BorderFactory.createEmptyBorder());
        sideScroll.getViewport().setBackground(NAVY);
        sideScroll.getVerticalScrollBar().setUnitIncrement(16);
        root.add(sideScroll, BorderLayout.WEST);
        root.add(main, BorderLayout.CENTER);
        buttons.get(0).doClick();
        SwingUtilities.invokeLater(() -> split.setDividerLocation(0.48));
        return root;
    }

    static void setUiFont() {
        String[] prefer = {"Segoe UI", "Dialog"};
        String name = "Dialog";
        java.util.Set<String> have = new java.util.HashSet<>(java.util.Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String p : prefer) if (have.contains(p)) { name = p; break; }
        FontUIResource f = new FontUIResource(name, Font.PLAIN, 14);
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object k = keys.nextElement();
            if (UIManager.get(k) instanceof FontUIResource) UIManager.put(k, f);
        }
    }

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(null, "An unexpected error occurred, but the program is still running:\n" + e,
                        APP_NAME, JOptionPane.ERROR_MESSAGE)));
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) { }
            setUiFont();
            JFrame f = new JFrame(APP_NAME);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setContentPane(buildContent());
            Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
            f.setMinimumSize(new Dimension(1060, 640));
            f.setSize(Math.min(1340, scr.width - 40), Math.min(780, scr.height - 60));
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}
