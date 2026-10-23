/**
 * Замкнутый путь тока для анимации — ломаная от плюса источника к минусу.
 * Точки на пути идут с одинаковой скоростью, а их густота пропорциональна доле тока
 * ветви (чем больше ток, тем больше точек).
 */
final class FlowPath {

    static final double SPACING = 24;   // расстояние между точками при всём токе цепи, px

    final double[] px, py, cum;         // вершины ломаной и накопленная длина до каждой
    final double len, spacing, phase;
    final int count;

    /** share — доля тока цепи в этой ветви (0…1), phaseRnd — случайный сдвиг точек (0…1). */
    FlowPath(double[][] pts, double share, double phaseRnd) {
        int n = pts.length;
        px = new double[n];
        py = new double[n];
        cum = new double[n];
        double total = 0;
        for (int i = 0; i < n; i++) {
            px[i] = pts[i][0];
            py[i] = pts[i][1];
            if (i > 0) total += Math.hypot(px[i] - px[i - 1], py[i] - py[i - 1]);
            cum[i] = total;
        }
        len = total;
        count = Math.max(1, (int) Math.round(len * share / SPACING));
        spacing = len / count;
        phase = phaseRnd * spacing;
    }

    /** Точка на расстоянии s от начала пути. */
    double[] pointAt(double s) {
        int i = 1;
        while (i < cum.length - 1 && cum[i] < s) i++;
        double seg = cum[i] - cum[i - 1];
        double t = seg == 0 ? 0 : (s - cum[i - 1]) / seg;
        return new double[]{px[i - 1] + (px[i] - px[i - 1]) * t, py[i - 1] + (py[i] - py[i - 1]) * t};
    }
}
