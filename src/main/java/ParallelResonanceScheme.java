import java.util.List;
import java.util.Random;

/**
 * Схема темы 5, параллельный контур: источник переменного напряжения слева, справа три вертикальные
 * ветви — резистор, катушка и конденсатор, соединённые параллельно. r — Ом, l — мГн, c — мкФ, u — В, f — Гц.
 */
record ParallelResonanceScheme(int r, int l, int c, int u, int f) implements Scheme {

    private static final double XS = 130;                       // источник
    private static final double X_R = 280, X_L = 395, X_C = 510;   // ветви
    private static final double SRC_R = 30;                     // радиус окружности источника
    private static final double MID = (TOP + BOTTOM) / 2;
    private static final double R_HALF = 32, R_WIDTH = 11;      // резистор
    private static final double L_HALF = 40;                    // катушка

    private static final double PLATE_GAP = 5;    // половина зазора между пластинами конденсатора
    private static final double PLATE = 18;       // половина длины пластины
    private static final int COILS = 4;           // витков у катушки
    private static final int ARC_STEPS = 10;      // отрезков на виток (у Painter нет дуг)

    @Override
    public void draw(Painter g) {
        // провода: общий верхний и нижний, к ним — источник и три ветви
        g.strokeLine(XS, TOP, X_C, TOP);
        g.strokeLine(XS, BOTTOM, X_C, BOTTOM);
        g.strokeLine(XS, TOP, XS, MID - SRC_R);
        g.strokeLine(XS, MID + SRC_R, XS, BOTTOM);
        g.strokeLine(X_R, TOP, X_R, MID - R_HALF);
        g.strokeLine(X_R, MID + R_HALF, X_R, BOTTOM);
        g.strokeLine(X_L, TOP, X_L, MID - L_HALF);
        g.strokeLine(X_L, MID + L_HALF, X_L, BOTTOM);
        g.strokeLine(X_C, TOP, X_C, MID - PLATE_GAP);
        g.strokeLine(X_C, MID + PLATE_GAP, X_C, BOTTOM);
        // узлы
        g.setFill(WIRE);
        for (double x : new double[]{X_R, X_L}) {
            g.fillOval(x - 4, TOP - 4, 8, 8);
            g.fillOval(x - 4, BOTTOM - 4, 8, 8);
        }

        // источник переменного напряжения: окружность с синусоидой
        g.setFill("#ffffff");
        g.fillOval(XS - SRC_R, MID - SRC_R, 2 * SRC_R, 2 * SRC_R);
        g.strokeOval(XS - SRC_R, MID - SRC_R, 2 * SRC_R, 2 * SRC_R);
        double px = XS - 18, py = MID;
        for (int i = 1; i <= 20; i++) {
            double nx = XS - 18 + 36.0 * i / 20, ny = MID - 9 * Math.sin(2 * Math.PI * i / 20);
            g.strokeLine(px, py, nx, ny);
            px = nx;
            py = ny;
        }

        // резистор
        g.setFill(BODY);
        g.fillRect(X_R - R_WIDTH, MID - R_HALF, 2 * R_WIDTH, 2 * R_HALF);
        g.strokeRect(X_R - R_WIDTH, MID - R_HALF, 2 * R_WIDTH, 2 * R_HALF);

        // катушка: витки — полуокружности справа от провода
        double rad = L_HALF / COILS;
        for (int k = 0; k < COILS; k++) {
            double y0 = MID - L_HALF + rad * (2 * k + 1);
            double ax = X_L, ay = y0 - rad;
            for (int i = 1; i <= ARC_STEPS; i++) {
                double a = Math.PI * (0.5 - (double) i / ARC_STEPS);
                double nx = X_L + rad * Math.cos(a), ny = y0 - rad * Math.sin(a);
                g.strokeLine(ax, ay, nx, ny);
                ax = nx;
                ay = ny;
            }
        }

        // конденсатор
        g.setLineWidth(3.5);
        g.strokeLine(X_C - PLATE, MID - PLATE_GAP, X_C + PLATE, MID - PLATE_GAP);
        g.strokeLine(X_C - PLATE, MID + PLATE_GAP, X_C + PLATE, MID + PLATE_GAP);
        g.setLineWidth(2.2);

        // токи: источника I на верхнем проводе, ветвей IL и IC у нижних выводов
        Scheme.arrow(g, XS + 30, TOP - 16, XS + 70, TOP - 16, BLUE);
        Scheme.arrow(g, X_L - 12, BOTTOM - 40, X_L - 12, BOTTOM - 14, BLUE);
        Scheme.arrow(g, X_C - 12, BOTTOM - 40, X_C - 12, BOTTOM - 14, BLUE);
        g.setStroke(WIRE);

        // подписи
        g.setBoldFont(15);
        g.setFill(LABEL);
        g.fillText("U = " + u + " В", 4, MID - 44);
        g.fillText("f = " + f + " Гц", 4, MID + 58);
        g.fillText("R", X_R + 18, MID - 4);
        g.fillText(r + " Ом", X_R + 18, MID + 14);
        g.fillText("L", X_L + 20, MID - 4);
        g.fillText(l + " мГн", X_L + 20, MID + 14);
        g.fillText("C", X_C + 24, MID - 4);
        g.fillText(c + " мкФ", X_C - 70, MID + 40);
        g.setFill(BLUE);
        g.fillText("I", XS + 46, TOP - 24);
        g.fillText("IL", X_L - 34, BOTTOM - 22);
        g.fillText("IC", X_C - 36, BOTTOM - 22);
        g.setBoldFont(18);
        g.fillText("f0, ρ, Q, I = ?", XS + 40, BOTTOM + 40);
    }

    /** Переменный ток меняет направление — анимации движения тока в этой теме нет. */
    @Override
    public List<FlowPath> flowPaths(Random rnd) {
        return List.of();
    }
}
