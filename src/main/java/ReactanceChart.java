import java.util.List;
import java.util.Random;

/**
 * График темы 4 на холсте схемы 600×300: сопротивление катушки (прямая XL = 2πfL)
 * или конденсатора (гипербола XC = 1/(2πfC)) в зависимости от частоты, точки — частоты задания.
 * Показывается вместо схемы после верного ответа.
 */
record ReactanceChart(boolean inductor, double[] freqs, double[] values) implements Scheme {

    private static final double LEFT = 80, RIGHT = 560;        // ось частот
    private static final double Y0 = 250, YTOP = 45;           // ось сопротивлений
    private static final int Y_TICKS = 5;
    private static final int CURVE_STEPS = 60;
    private static final String GRID = "#e5e7eb";

    @Override
    public void draw(Painter g) {
        int n = freqs.length;
        double fMax = freqs[n - 1] + freqs[0];                 // запас на один шаг справа
        double xMax = niceCeil(max(values));

        // сетка и подписи делений
        g.setBoldFont(11);
        g.setLineWidth(1);
        for (int i = 1; i <= Y_TICKS; i++) {
            double v = xMax * i / Y_TICKS, y = py(v, xMax);
            g.setStroke(GRID);
            g.strokeLine(LEFT, y, RIGHT, y);
            g.setFill(LABEL);
            String s = Grading.format(v);
            g.fillText(s, LEFT - 8 - 6.5 * s.length(), y + 4);
        }
        for (double f : freqs) {
            double x = px(f, fMax);
            g.setStroke(GRID);
            g.strokeLine(x, YTOP, x, Y0);
            g.setFill(LABEL);
            String s = String.valueOf((int) f);
            g.fillText(s, x - 3.3 * s.length(), Y0 + 16);
        }
        g.fillText("0", LEFT - 14, Y0 + 4);

        // оси со стрелками
        Scheme.arrow(g, LEFT, Y0, RIGHT + 25, Y0, WIRE);
        Scheme.arrow(g, LEFT, Y0, LEFT, YTOP - 20, WIRE);
        String sym = inductor ? "XL" : "XC";
        g.setBoldFont(14);
        g.setFill(LABEL);
        g.fillText("f, Гц", RIGHT - 10, Y0 + 36);
        g.fillText(sym + ", Ом", LEFT + 8, YTOP - 22);

        // кривая: прямая от нуля у катушки, гипербола между крайними частотами у конденсатора
        g.setStroke(BLUE);
        g.setLineWidth(2.5);
        double fFrom = inductor ? 0 : freqs[0] * 0.8, fTo = freqs[n - 1];
        double prevX = 0, prevY = 0;
        for (int i = 0; i <= CURVE_STEPS; i++) {
            double f = fFrom + (fTo - fFrom) * i / CURVE_STEPS;
            double x = px(f, fMax), y = py(curve(f), xMax);
            if (i > 0) g.strokeLine(prevX, prevY, x, Math.max(y, YTOP - 15));
            prevX = x;
            prevY = Math.max(y, YTOP - 15);
        }

        // точки задания
        g.setFill(PLUS);
        for (int k = 0; k < n; k++) {
            g.fillOval(px(freqs[k], fMax) - 4.5, py(values[k], xMax) - 4.5, 9, 9);
        }

        g.setFill(BLUE);
        g.setBoldFont(15);
        g.fillText(inductor ? "XL = 2πfL — растёт пропорционально частоте"
                            : "XC = 1/(2πfC) — убывает с ростом частоты", LEFT + 90, 22);
        g.setLineWidth(2.2);
    }

    /** Значение на кривой: через первую точку задания, X ~ f (катушка) или X ~ 1/f (конденсатор). */
    private double curve(double f) {
        return inductor ? values[0] * f / freqs[0] : values[0] * freqs[0] / f;
    }

    private double px(double f, double fMax) {
        return LEFT + (RIGHT - LEFT) * f / fMax;
    }

    private double py(double v, double xMax) {
        return Y0 - (Y0 - YTOP) * v / xMax;
    }

    private static double max(double[] v) {
        double m = 0;
        for (double x : v) m = Math.max(m, x);
        return m;
    }

    /** Ближайшее сверху «круглое» число: 1, 2 или 5 ·10ⁿ. */
    static double niceCeil(double v) {
        double p = Math.pow(10, Math.floor(Math.log10(v)));
        for (double m : new double[]{1, 2, 5, 10}) {
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
