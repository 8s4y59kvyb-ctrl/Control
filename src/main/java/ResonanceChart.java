import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * График темы 5 на холсте схемы 600×300: напряжение на катушке последовательного контура
 * в зависимости от частоты, UL(f) = U·XL / √(R² + (XL − XC)²). Пунктир — резонансная частота f0
 * и напряжение источника U, точка — частота задания. Показывается вместо схемы после задания.
 */
record ResonanceChart(int r, int l, int c, int u, int f) implements Scheme {

    private static final double LEFT = 80, RIGHT = 560;        // ось частот
    private static final double Y0 = 250, YTOP = 45;           // ось напряжений
    private static final int CURVE_STEPS = 150;
    private static final String GRID = "#e5e7eb";

    @Override
    public void draw(Painter g) {
        double f0 = ResonanceTasks.resonance(l, c);
        double fStep = niceStep(Math.max(2 * f0, 1.1 * f) / 5);
        double fMax = fStep * Math.ceil(Math.max(2 * f0, 1.1 * f) / fStep);
        double peak = 0;
        for (int i = 1; i <= CURVE_STEPS; i++) peak = Math.max(peak, voltage(fMax * i / CURVE_STEPS));
        double uStep = niceStep(peak * 1.1 / 5);
        double uMax = uStep * Math.ceil(peak * 1.1 / uStep);

        // сетка и подписи делений
        g.setBoldFont(11);
        g.setLineWidth(1);
        for (double v = uStep; v <= uMax + 1e-9; v += uStep) {
            double y = py(v, uMax);
            g.setStroke(GRID);
            g.strokeLine(LEFT, y, RIGHT, y);
            g.setFill(LABEL);
            String s = Grading.format(v);
            g.fillText(s, LEFT - 8 - 6.5 * s.length(), y + 4);
        }
        for (double x0 = fStep; x0 <= fMax + 1e-9; x0 += fStep) {
            double x = px(x0, fMax);
            g.setStroke(GRID);
            g.strokeLine(x, YTOP, x, Y0);
            g.setFill(LABEL);
            String s = Grading.format(x0);
            g.fillText(s, x - 3.3 * s.length(), Y0 + 16);
        }
        g.fillText("0", LEFT - 14, Y0 + 4);

        // пунктиры: резонансная частота и напряжение источника
        g.setLineWidth(1.5);
        g.setStroke(PLUS);
        dashed(g, px(f0, fMax), Y0, px(f0, fMax), YTOP - 5);
        g.setStroke(LABEL);
        dashed(g, LEFT, py(u, uMax), RIGHT, py(u, uMax));

        // оси со стрелками
        Scheme.arrow(g, LEFT, Y0, RIGHT + 25, Y0, WIRE);
        Scheme.arrow(g, LEFT, Y0, LEFT, YTOP - 20, WIRE);
        g.setBoldFont(14);
        g.setFill(LABEL);
        g.fillText("f, Гц", RIGHT - 10, Y0 + 36);
        g.fillText("UL, В", LEFT + 8, YTOP - 22);
        g.fillText("U", RIGHT + 6, py(u, uMax) + 5);
        g.setFill(PLUS);
        g.fillText("f0 = " + Grading.format(Math.round(f0)) + " Гц", px(f0, fMax) + 6, YTOP + 6);

        // кривая
        g.setStroke(BLUE);
        g.setLineWidth(2.5);
        double prevX = LEFT, prevY = Y0;
        for (int i = 1; i <= CURVE_STEPS; i++) {
            double fi = fMax * i / CURVE_STEPS;
            double x = px(fi, fMax), y = py(voltage(fi), uMax);
            g.strokeLine(prevX, prevY, x, y);
            prevX = x;
            prevY = y;
        }

        // точка задания
        double x = px(f, fMax), y = py(voltage(f), uMax);
        g.setFill(PLUS);
        g.fillOval(x - 5, y - 5, 10, 10);
        g.setFill(BLUE);
        g.setBoldFont(13);
        String s = "UL = " + String.format(Locale.US, "%.2f", voltage(f)).replace('.', ',') + " В";
        g.fillText(s, f < f0 ? x - 12 - 7 * s.length() : x + 12, y + 4);

        g.setBoldFont(15);
        g.fillText("Резонанс: UL наибольшее вблизи f0, UL ≈ Q·U", LEFT + 70, 22);
        g.setLineWidth(2.2);
    }

    private double voltage(double freq) {
        return ResonanceTasks.inductorVoltage(u, r, l, c, freq);
    }

    private static void dashed(Painter g, double x1, double y1, double x2, double y2) {
        double len = Math.hypot(x2 - x1, y2 - y1), dash = 6;
        for (double t = 0; t < len; t += 2 * dash) {
            double a = t / len, b = Math.min(t + dash, len) / len;
            g.strokeLine(x1 + (x2 - x1) * a, y1 + (y2 - y1) * a, x1 + (x2 - x1) * b, y1 + (y2 - y1) * b);
        }
    }

    private double px(double freq, double fMax) {
        return LEFT + (RIGHT - LEFT) * freq / fMax;
    }

    private double py(double v, double uMax) {
        return Y0 - (Y0 - YTOP) * v / uMax;
    }

    /** Шаг делений — ближайшее сверху из 1; 2; 2,5; 5 ·10ⁿ. */
    static double niceStep(double v) {
        double p = Math.pow(10, Math.floor(Math.log10(v)));
        for (double m : new double[]{1, 2, 2.5, 5, 10}) {
            if (m * p >= v - 1e-9) return m * p;
        }
        return 10 * p;
    }

    /** График неподвижен — анимации тока нет. */
    @Override
    public List<FlowPath> flowPaths(Random rnd) {
        return List.of();
    }
}
