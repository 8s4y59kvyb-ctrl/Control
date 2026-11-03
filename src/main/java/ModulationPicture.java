import java.util.List;
import java.util.Random;

/**
 * Картинка темы «Модуляция»: временное представление колебания (вверху модулирующий сигнал u(t), ниже s(t))
 * или его амплитудный спектр (линии на частотах ω0 + nΩ, у АМК, ФМК и ЧМК рядом — индекс модуляции mₐ, mф, mч;
 * у ЛЧМ — сплошной, у ЧмнК подписаны f₁ и f₂).
 * Большая (600×300) — то, что дано в задании; маленькая (300×150) — вариант ответа.
 *
 * @param k число периодов несущей на период модуляции
 * @param m индекс модуляции (у АМК — коэффициент mₐ)
 */
record ModulationPicture(Modulation type, boolean spectrum, double k, double m, boolean large) implements Scheme {

    static final int LINES = 7;                  // линий спектра по каждую сторону от несущей
    private static final double LFM_BASE = 30;   // база ЛЧМ Δf·T для формы спектра
    private static final int POINTS = 700;       // точек на кривой сигнала
    private static final double PERIODS = 2;     // периодов модуляции на картинке
    private static final String MUTED = "#64748b";

    @Override
    public int width() {
        return large ? 600 : 300;
    }

    @Override
    public int height() {
        return large ? 300 : 150;
    }

    @Override
    public void draw(Painter g) {
        if (spectrum) drawSpectrum(g); else drawSignal(g);
        g.setStroke(WIRE);
        g.setLineWidth(2.2);
    }

    private double s() {                         // масштаб: 1 у большой картинки
        return width() / 600.0;
    }

    private double fs() {                        // масштаб подписей: на маленькой картинке те же, что на большой
        return 1;
    }

    private void drawSignal(Painter g) {
        double s = s(), fs = fs(), left = 40 * fs, right = width() - 22 * s;
        double uY = height() * 0.17, uAmp = height() * 0.09;     // модулирующий сигнал
        double sY = height() * 0.62, sAmp = height() * 0.28;     // модулированное колебание
        double peak = type == Modulation.AM ? 1 + m : 1;          // АМК выше единицы: масштаб по максимуму

        axis(g, left, uY, right);
        axis(g, left, sY, right);
        g.setBoldFont(13 * fs);
        g.setFill(MUTED);
        g.fillText("u(t)", 4, uY + 4 * fs);
        g.setFill(LABEL);
        g.fillText("s(t)", 4, sY + 4 * fs);
        g.fillText("t", right + 4 * s, sY + 16 * fs);

        double[] xs = new double[POINTS + 1], us = new double[POINTS + 1], ys = new double[POINTS + 1];
        for (int i = 0; i <= POINTS; i++) {
            double t = PERIODS * i / POINTS;
            xs[i] = left + (right - left) * i / POINTS;
            us[i] = uY - uAmp * type.message(t);
            ys[i] = sY - sAmp * type.signal(t, k, m) / peak;
        }
        g.setStroke(MUTED);
        g.setLineWidth(1.5 * s);
        g.strokePolyline(xs, us, xs.length);
        g.setStroke(BLUE);
        g.setLineWidth(large ? 1.6 : 1.1);
        g.strokePolyline(xs, ys, xs.length);
    }

    /** Ось времени — тонкая линия нуля. */
    private void axis(Painter g, double left, double y, double right) {
        g.setStroke("#cbd5e1");
        g.setLineWidth(1);
        g.strokeLine(left, y, right, y);
    }

    private void drawSpectrum(Painter g) {
        double s = s(), fs = fs(), left = 30 * fs, right = width() - 30 * fs;
        double y0 = height() * 0.8, top = height() * 0.2;
        double c = (left + right) / 2, step = (right - left) / (2 * LINES + 2);

        Scheme.arrow(g, left, y0, right + 20 * fs, y0, WIRE);
        Scheme.arrow(g, left, y0, left, top - 16 * fs, WIRE);
        g.setBoldFont(13 * fs);
        g.setFill(LABEL);
        g.fillText("A", left - 16 * fs, top - 4 * fs);
        g.fillText("f", right + 10 * fs, y0 + 16 * fs);
        g.setStroke(WIRE);
        g.setLineWidth(1.5);
        g.strokeLine(c, y0 - 4 * s, c, y0 + 4 * s);              // отметка несущей, даже если её нет в спектре
        g.fillText("f₀", c - 7 * fs, y0 + 18 * fs);

        if (type == Modulation.LFM) {
            drawLfm(g, c, step, y0, top);
            return;
        }
        double[] a = type.spectrum(LINES, m);
        double max = 0;
        for (double v : a) max = Math.max(max, v);
        g.setStroke(BLUE);
        g.setFill(BLUE);
        g.setLineWidth(large ? 4 : 3);
        for (int i = 0; i < a.length; i++) {
            if (a[i] < 0.01) continue;
            double x = c + (i - LINES) * step, y = y0 - (y0 - top) * a[i] / max;
            g.strokeLine(x, y0, x, y);
            g.fillOval(x - 3 * s, y - 3 * s, 6 * s, 6 * s);
        }

        if (type == Modulation.FSK) {                              // частоты импульса и паузы
            g.setFill(LABEL);
            g.setBoldFont(13 * fs);
            g.fillText("f₂", c - Modulation.FSK_SHIFT * step - 7 * fs, y0 + 18 * fs);
            g.fillText("f₁", c + Modulation.FSK_SHIFT * step - 7 * fs, y0 + 18 * fs);
        }

        String sub = switch (type) {
            case AM -> "а";
            case PM -> "ф";
            case FM -> "ч";
            default -> null;
        };
        if (sub != null) {                                        // «mч = 3»: индекс с буквой типа модуляции
            double f = 16 * fs, x = right - 4.9 * f, y = top - 4 * fs;
            g.setFill(LABEL);
            g.setBoldFont(f);
            g.fillText("m", x, y);
            g.setBoldFont(f * 0.7);
            g.fillText(sub, x + f * 0.9, y + f * 0.3);
            g.setBoldFont(f);
            g.fillText("= " + Grading.format(m), x + f * 2, y);       // «ф» шире прочих букв
        }
    }

    /**
     * Сплошной спектр ЛЧМ: почти прямоугольный, шириной m·Ω на картинке, с пульсациями у краёв; над ним — размер Δf.
     * Форма считается для базы {@link #LFM_BASE} (при малой базе вершина не плоская) и сжимается до ширины m.
     */
    private void drawLfm(Painter g, double c, double step, double y0, double top) {
        int n = 240;
        double span = LINES + 0.5;                                // расстройка от несущей, в единицах Ω
        double[] vals = new double[n + 1];
        double max = 0;
        for (int i = 0; i <= n; i++) {
            vals[i] = Modulation.lfmSpectrum((-span + 2 * span * i / n) * LFM_BASE / m, LFM_BASE);
            max = Math.max(max, vals[i]);
        }
        double[] xs = new double[n + 3], ys = new double[n + 3];
        for (int i = 0; i <= n; i++) {
            xs[i] = c + (-span + 2 * span * i / n) * step;
            ys[i] = y0 - (y0 - top) * 0.85 * vals[i] / max;
        }
        xs[n + 1] = xs[n];                                        // замыкаем по оси частот для заливки
        ys[n + 1] = y0;
        xs[n + 2] = xs[0];
        ys[n + 2] = y0;
        g.setFill("#dbeafe");
        g.fillPolygon(xs, ys, n + 3);
        g.setStroke(BLUE);
        g.setLineWidth(large ? 2.5 : 2);
        g.strokePolyline(xs, ys, n + 1);

        double fs = fs(), half = m / 2 * step, y = top - 2 * fs;  // размер Δf: стрелки между краями полосы
        Scheme.arrow(g, c, y, c - half, y, WIRE);
        Scheme.arrow(g, c, y, c + half, y, WIRE);
        g.setFill(LABEL);
        g.setBoldFont(14 * fs);
        g.fillText("Δf", c - 8 * fs, y - 6 * fs);
    }

    /** Картинка неподвижна — анимации тока нет. */
    @Override
    public List<FlowPath> flowPaths(Random rnd) {
        return List.of();
    }
}
