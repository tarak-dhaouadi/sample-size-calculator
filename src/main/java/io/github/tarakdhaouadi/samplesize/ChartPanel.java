package io.github.tarakdhaouadi.samplesize;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Draws a {@link Plot} with Java2D (no external charting library); can also render it to an image. */
final class ChartPanel extends JPanel {
    private static final Color GRID = new Color(217, 217, 217), TEXT = new Color(70, 70, 70), MUTED = new Color(120, 120, 120);
    private Plot plot;
    private String message = "Click Calculate to draw the plots.";

    ChartPanel() {
        setBackground(Color.WHITE);
        setOpaque(true);
        setPreferredSize(new Dimension(480, 300));
    }

    void setPlot(Plot p) { plot = p; message = null; repaint(); }
    void setMessage(String m) { plot = null; message = m; repaint(); }
    Plot getPlot() { return plot; }

    BufferedImage render(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        draw(g, w, h);
        g.dispose();
        return img;
    }

    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        draw(g, getWidth(), getHeight());
        g.dispose();
    }

    // ------------------------------------------------------------------ drawing
    private void draw(Graphics2D g, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        Font ui = UIManager.getFont("Label.font");
        Font base = ui != null ? ui : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        if (plot == null) {
            g.setFont(base.deriveFont(Font.PLAIN, 14f));
            g.setColor(MUTED);
            String m = message == null ? "" : message;
            FontMetrics fm = g.getFontMetrics();
            g.drawString(m, Math.max(8, (w - fm.stringWidth(m)) / 2), h / 2);
            return;
        }
        final double s = Math.max(0.9, Math.min(2.2, Math.min(w / 720.0, h / 430.0)));
        Font titleF = base.deriveFont(Font.PLAIN, (float) (17 * s)), subF = base.deriveFont(Font.PLAIN, (float) (12 * s)),
                axisF = base.deriveFont(Font.PLAIN, (float) (13 * s)), tickF = base.deriveFont(Font.PLAIN, (float) (11 * s)),
                legF = base.deriveFont(Font.PLAIN, (float) (11.5 * s)), markF = base.deriveFont(Font.BOLD, (float) (14 * s));

        int pad = (int) (14 * s);
        int y = (int) (8 * s);
        g.setColor(Color.BLACK);
        g.setFont(shrink(g, titleF, plot.title, w - 2 * pad, 11f));
        FontMetrics fm = g.getFontMetrics();
        y += fm.getAscent();
        g.drawString(fit(plot.title, fm, w - 2 * pad), pad, y);
        y += fm.getDescent();
        if (!plot.subtitle.isEmpty()) {
            g.setFont(shrink(g, subF, plot.subtitle, w - 2 * pad, 9f)); g.setColor(new Color(40, 40, 40));
            fm = g.getFontMetrics();
            y += (int) (3 * s) + fm.getAscent();
            g.drawString(fit(plot.subtitle, fm, w - 2 * pad), pad, y);
            y += fm.getDescent();
        }
        // legend (only when there are several labelled curves)
        List<Plot.Series> labelled = new ArrayList<>();
        for (Plot.Series se : plot.series) if (!se.label.isEmpty()) labelled.add(se);
        if (!labelled.isEmpty()) {
            g.setFont(legF); fm = g.getFontMetrics();
            int lx = pad, rowH = fm.getHeight() + (int) (3 * s);
            y += (int) (6 * s);
            int rowTop = y;
            for (Plot.Series se : labelled) {
                int itemW = (int) (22 * s) + (int) (5 * s) + fm.stringWidth(se.label) + (int) (14 * s);
                if (lx + itemW > w - pad && lx > pad) { lx = pad; rowTop += rowH; }
                int cy = rowTop + fm.getHeight() / 2;
                g.setColor(new Color(se.rgb));
                g.setStroke(new BasicStroke((float) (1.6 * s)));
                g.drawLine(lx, cy, lx + (int) (22 * s), cy);
                double r = 2.2 * s;
                g.draw(new Ellipse2D.Double(lx + 11 * s - r, cy - r, 2 * r, 2 * r));
                g.setColor(new Color(40, 40, 40));
                g.drawString(se.label, lx + (int) (27 * s), rowTop + fm.getAscent());
                lx += itemW;
            }
            y = rowTop + rowH;
        }
        // plot rectangle
        g.setFont(tickF); fm = g.getFontMetrics();
        int left = (int) (58 * s) + (int) (6 * s) * 0, right = pad;
        int bottom = (int) (8 * s) + fm.getHeight() + g.getFontMetrics(axisF).getHeight() + (int) (8 * s);
        int x0 = left, x1 = w - right, y0 = y + (int) (6 * s), y1 = h - bottom;
        if (x1 - x0 < 40 || y1 - y0 < 40) return;

        // data ranges
        double xMin = Double.POSITIVE_INFINITY, xMax = Double.NEGATIVE_INFINITY, yMin = Double.POSITIVE_INFINITY, yMax = Double.NEGATIVE_INFINITY;
        // A steep low-end tail would flatten everything else: zoom on the part within 3x the current result,
        // the curves simply run off the top of the frame.
        double cap = Double.POSITIVE_INFINITY;
        if (!plot.dots && !Double.isNaN(plot.markY)) cap = 3.0 * plot.markY;
        for (Plot.Series se : plot.series)
            for (int i = 0; i < se.x.length; i++) {
                if (Double.isNaN(se.y[i]) || se.y[i] > cap) continue;
                xMin = Math.min(xMin, se.x[i]); xMax = Math.max(xMax, se.x[i]);
                yMin = Math.min(yMin, se.y[i]); yMax = Math.max(yMax, se.y[i]);
            }
        boolean hasMark = !Double.isNaN(plot.markY);
        if (hasMark) { xMin = Math.min(xMin, plot.markX); xMax = Math.max(xMax, plot.markX); yMin = Math.min(yMin, plot.markY); yMax = Math.max(yMax, plot.markY); }
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke((float) (1.2 * s)));
        if (!plot.hasData()) {
            g.drawRect(x0, y0, x1 - x0, y1 - y0);
            g.setFont(base.deriveFont(Font.PLAIN, (float) (13 * s))); g.setColor(MUTED);
            String m = "No valid points for these inputs.";
            g.drawString(m, x0 + ((x1 - x0) - g.getFontMetrics().stringWidth(m)) / 2, (y0 + y1) / 2);
            return;
        }
        double yPad = (yMax - yMin) * 0.06; if (yPad <= 0) yPad = Math.max(1, Math.abs(yMax) * 0.1);
        double ylo = Math.max(0, yMin - yPad), yhi = yMax + yPad;
        double xPad = (xMax - xMin) * 0.03; if (xPad <= 0) xPad = 0.5;
        double xlo = xMin - xPad, xhi = xMax + xPad;

        // grid + tick labels
        List<Double> yt = ticks(ylo, yhi, 5, false), xt = ticks(xlo, xhi, 7, plot.integerX);
        g.setFont(tickF); fm = g.getFontMetrics();
        for (double v : yt) {
            int py = my(v, ylo, yhi, y0, y1);
            g.setColor(GRID); g.setStroke(new BasicStroke((float) (1.0 * s)));
            g.drawLine(x0, py, x1, py);
            g.setColor(TEXT);
            String t = fmt(v, yt);
            g.drawString(t, x0 - fm.stringWidth(t) - (int) (5 * s), py + fm.getAscent() / 2 - 1);
        }
        for (double v : xt) {
            int px = mx(v, xlo, xhi, x0, x1);
            g.setColor(GRID); g.setStroke(new BasicStroke((float) (1.0 * s)));
            g.drawLine(px, y0, px, y1);
            g.setColor(TEXT);
            String t = fmt(v, xt);
            g.drawString(t, px - fm.stringWidth(t) / 2, y1 + fm.getAscent() + (int) (4 * s));
        }
        // curves
        Shape oldClip = g.getClip();
        g.setClip(x0, y0, x1 - x0, y1 - y0);
        for (Plot.Series se : plot.series) {
            Color c = new Color(se.rgb);
            g.setColor(c);
            g.setStroke(new BasicStroke((float) ((plot.dots ? 2.4 : 1.6) * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D path = new Path2D.Double();
            boolean pen = false;
            for (int i = 0; i < se.x.length; i++) {
                if (Double.isNaN(se.y[i])) { pen = false; continue; }
                double px = mx(se.x[i], xlo, xhi, x0, x1), py = my(se.y[i], ylo, yhi, y0, y1);
                if (!pen) { path.moveTo(px, py); pen = true; } else path.lineTo(px, py);
            }
            g.draw(path);
            double r = (plot.dots ? 3.2 : 2.0) * s;
            for (int i = 0; i < se.x.length; i++) {
                if (Double.isNaN(se.y[i])) continue;
                double px = mx(se.x[i], xlo, xhi, x0, x1), py = my(se.y[i], ylo, yhi, y0, y1);
                Ellipse2D dot = new Ellipse2D.Double(px - r, py - r, 2 * r, 2 * r);
                if (plot.dots) { g.setColor(new Color(Plots.RED)); g.fill(dot); }
                else { g.setStroke(new BasicStroke((float) (1.0 * s))); g.setColor(c); g.draw(dot); }
            }
        }
        // highlighted result
        if (hasMark) {
            double px = mx(plot.markX, xlo, xhi, x0, x1), py = my(plot.markY, ylo, yhi, y0, y1), L = 11 * s;
            g.setColor(new Color(plot.markRgb));
            g.setStroke(new BasicStroke((float) (1.2 * s)));
            for (int k = 0; k < 4; k++) {
                double ang = Math.PI * k / 4;
                g.draw(new java.awt.geom.Line2D.Double(px - L * Math.cos(ang), py - L * Math.sin(ang), px + L * Math.cos(ang), py + L * Math.sin(ang)));
            }
            g.setFont(markF); fm = g.getFontMetrics();
            int tw = fm.stringWidth(plot.markLabel);
            double tx = px - L - 6 * s - tw;
            if (tx < x0 + 2) tx = px + L + 6 * s;
            int ty = (int) (py + fm.getAscent() / 2.0 - 2);
            g.setColor(Color.WHITE);
            int hl = Math.max(1, (int) Math.round(1.6 * s));
            for (int ox = -hl; ox <= hl; ox++) for (int oy = -hl; oy <= hl; oy++) if (ox != 0 || oy != 0) g.drawString(plot.markLabel, (int) tx + ox, ty + oy);
            g.setColor(new Color(plot.markLabelRgb));
            g.drawString(plot.markLabel, (int) tx, ty);
        }
        g.setClip(oldClip);
        // frame and axis titles
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke((float) (1.3 * s)));
        g.drawRect(x0, y0, x1 - x0, y1 - y0);
        g.setFont(axisF); fm = g.getFontMetrics();
        g.setColor(Color.BLACK);
        g.drawString(fit(plot.xLabel, fm, x1 - x0), x0 + ((x1 - x0) - Math.min(fm.stringWidth(plot.xLabel), x1 - x0)) / 2, h - (int) (6 * s));
        Graphics2D gr = (Graphics2D) g.create();
        gr.rotate(-Math.PI / 2);
        String yl = fit(plot.yLabel, fm, y1 - y0);
        gr.drawString(yl, -(y0 + ((y1 - y0) + fm.stringWidth(yl)) / 2), (int) (14 * s));
        gr.dispose();
    }

    /** The largest font not bigger than {@code f} (and not smaller than {@code minSize}) in which the text fits the width. */
    private static Font shrink(Graphics2D g, Font f, String text, int max, float minSize) {
        float size = f.getSize2D();
        Font cur = f;
        while (size > minSize && g.getFontMetrics(cur).stringWidth(text) > max) { size -= 0.5f; cur = f.deriveFont(size); }
        return cur;
    }

    private static int mx(double v, double lo, double hi, int a, int b) { return (int) Math.round(a + (v - lo) / (hi - lo) * (b - a)); }
    private static int my(double v, double lo, double hi, int a, int b) { return (int) Math.round(b - (v - lo) / (hi - lo) * (b - a)); }

    private static String fit(String t, FontMetrics fm, int max) {
        if (fm.stringWidth(t) <= max) return t;
        String s = t;
        while (s.length() > 4 && fm.stringWidth(s + "...") > max) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    /** "Nice" tick positions (1, 2 or 5 times a power of ten) inside [lo, hi]. */
    static List<Double> ticks(double lo, double hi, int target, boolean integer) {
        double range = hi - lo, rough = range / target;
        double mag = Math.pow(10, Math.floor(Math.log10(rough))), res = rough / mag;
        double step = (res < 1.5 ? 1 : res < 3 ? 2 : res < 7 ? 5 : 10) * mag;
        if (integer) step = Math.max(1, Math.round(step));
        List<Double> out = new ArrayList<>();
        for (double v = Math.ceil(lo / step - 1e-9) * step; v <= hi + 1e-9; v += step) out.add(Math.round(v / step) * step);
        return out;
    }

    private static String fmt(double v, List<Double> all) {
        double step = all.size() > 1 ? Math.abs(all.get(1) - all.get(0)) : 1;
        int dec = step >= 1 ? 0 : Math.min(4, (int) Math.ceil(-Math.log10(step) - 1e-9));
        return String.format(Locale.US, "%." + dec + "f", v);
    }
}
