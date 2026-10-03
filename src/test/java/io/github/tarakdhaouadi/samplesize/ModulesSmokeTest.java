package io.github.tarakdhaouadi.samplesize;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds every calculator panel (headless) and checks that
 * (1) the default values give a result, (2) Reset works, and
 * (3) random junk typed into the fields only ever produces a friendly InputException, never a crash.
 */
public class ModulesSmokeTest {
    static int checks = 0, fails = 0;

    static void collect(Container c, List<JTextField> fields, List<JComboBox<?>> combos, List<JCheckBox> boxes) {
        for (Component k : c.getComponents()) {
            if (k instanceof JTextField) fields.add((JTextField) k);
            else if (k instanceof JComboBox) combos.add((JComboBox<?>) k);
            else if (k instanceof JCheckBox) boxes.add((JCheckBox) k);
            if (k instanceof Container) collect((Container) k, fields, combos, boxes);
        }
    }

    /** Both charts of a result: data present, marker equal to the result, curves in the right direction, drawable. */
    static void checkPlots(String name, Result r, boolean thorough) {
        checks++;
        try {
            Plots p = r.plotSource.call();
            long expected = r.n > 0 ? r.n : r.nTest;
            for (Plot pl : new Plot[]{p.size, p.power}) {
                if (!pl.hasData()) throw new IllegalStateException("plot without data: " + pl.title);
                if (Double.isNaN(pl.markY) || Math.round(pl.markY) != expected)
                    throw new IllegalStateException("marker " + pl.markY + " differs from the result " + expected + " in " + pl.title);
                for (Plot.Series se : pl.series) for (double v : se.y) if (!Double.isNaN(v) && !(v > 0)) throw new IllegalStateException("bad value " + v);
            }
            // the power / confidence curve never decreases
            double[] y = p.power.series.get(0).y;
            for (int i = 1; i < y.length; i++)
                if (!Double.isNaN(y[i]) && !Double.isNaN(y[i - 1]) && y[i] < y[i - 1])
                    throw new IllegalStateException("power curve decreases at index " + i + " in " + p.power.title);
            if (thorough) {
                ChartPanel c = new ChartPanel();
                for (Plot pl : new Plot[]{p.size, p.power}) {
                    c.setPlot(pl);
                    BufferedImage img = c.render(900, 560);
                    int ink = 0;
                    for (int yy = 0; yy < img.getHeight(); yy += 2) for (int xx = 0; xx < img.getWidth(); xx += 2) if ((img.getRGB(xx, yy) & 0xFFFFFF) != 0xFFFFFF) ink++;
                    if (ink < 1500) throw new IllegalStateException("chart looks empty: " + pl.title);
                }
            }
        } catch (Throwable t) { fails++; System.out.println("FAIL plots for " + name + ": " + t); }
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        SampleSizeApp.Module[] mods = SampleSizeApp.createModules();
        checks++;
        if (mods.length != 13) { fails++; System.out.println("FAIL expected 13 calculators, found " + mods.length); }
        Random rnd = new Random(2026);
        String[] junk = {"", " ", "abc", "-1", "0", "0.5", "1", "2", "10", "1e400", "NaN", "Infinity", "1,5", "0.05", "100", "1e-9", "0.95", "5; 7; 9", "3 4 5"};
        for (SampleSizeApp.Module m : mods) {
            JPanel panel = m.build(() -> { });
            // 1. defaults give a result
            checks++;
            try {
                Result r = m.compute();
                if (r.headline == null || r.headline.isEmpty() || r.interpretation.isEmpty()) throw new IllegalStateException("empty result");
                checkPlots(m.title, r, true);
            } catch (Throwable t) { fails++; System.out.println("FAIL " + m.title + " with default values: " + t); }
            // 2. random input
            List<JTextField> fields = new ArrayList<>(); List<JComboBox<?>> combos = new ArrayList<>(); List<JCheckBox> boxes = new ArrayList<>();
            collect(panel, fields, combos, boxes);
            for (int i = 0; i < 300; i++) {
                for (JTextField f : fields) if (rnd.nextInt(3) > 0) f.setText(junk[rnd.nextInt(junk.length)]);
                for (JComboBox<?> c : combos) if (rnd.nextInt(2) == 0) c.setSelectedIndex(rnd.nextInt(c.getItemCount()));
                for (JCheckBox b : boxes) if (rnd.nextInt(2) == 0) b.doClick();
                checks++;
                try {
                    Result rr = m.compute();
                    if (i % 12 == 0) checkPlots(m.title + " (random input)", rr, i % 60 == 0);   // plots must never crash either
                }
                catch (InputException ok) { /* friendly message */ }
                catch (Throwable t) { fails++; System.out.println("FAIL (crash) " + m.title + ": " + t); break; }
            }
            // 3. reset restores working defaults
            m.reset();
            checks++;
            try { m.compute(); } catch (Throwable t) { fails++; System.out.println("FAIL " + m.title + " after Reset: " + t); }
        }
        // the whole window content can be built
        checks++;
        try { SampleSizeApp.buildContent(); } catch (Throwable t) { fails++; System.out.println("FAIL building the window: " + t); }
        System.out.println(checks + " UI checks, " + fails + " failure(s)");
        System.exit(fails == 0 ? 0 : 1);
    }
}
