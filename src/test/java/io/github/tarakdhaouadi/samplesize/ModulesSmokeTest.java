package io.github.tarakdhaouadi.samplesize;

import javax.swing.*;
import java.awt.*;
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
            } catch (Throwable t) { fails++; System.out.println("FAIL " + m.title + " with default values: " + t); }
            // 2. random input
            List<JTextField> fields = new ArrayList<>(); List<JComboBox<?>> combos = new ArrayList<>(); List<JCheckBox> boxes = new ArrayList<>();
            collect(panel, fields, combos, boxes);
            for (int i = 0; i < 300; i++) {
                for (JTextField f : fields) if (rnd.nextInt(3) > 0) f.setText(junk[rnd.nextInt(junk.length)]);
                for (JComboBox<?> c : combos) if (rnd.nextInt(2) == 0) c.setSelectedIndex(rnd.nextInt(c.getItemCount()));
                for (JCheckBox b : boxes) if (rnd.nextInt(2) == 0) b.doClick();
                checks++;
                try { m.compute(); }
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
