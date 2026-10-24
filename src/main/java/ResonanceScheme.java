import java.util.List;
import java.util.Random;

/**
 * Схема темы 5: источник переменного напряжения и последовательный контур — резистор и катушка
 * на верхнем проводе, конденсатор на нижнем. r — Ом, l — мГн, c — мкФ, u — В, f — Гц.
 */
record ResonanceScheme(int r, int l, int c, int u, int f) implements Scheme {

    private static final double XS = 140;                       // источник
    private static final double XR = 510;                       // правый край
    private static final double SRC_R = 30;                     // радиус окружности источника
    private static final double MID = (TOP + BOTTOM) / 2;
    private static final double R_X = 225, R_HALF = 32, R_WIDTH = 11;   // резистор
    private static final double L_X = 375, L_HALF = 40;         // катушка
    private static final double C_X = 325;                      // конденсатор

    private static final double PLATE_GAP = 5;    // половина зазора между пластинами конденсатора
    private static final double PLATE = 18;       // половина длины пластины
    private static final int COILS = 4;           // витков у катушки
    private static final int ARC_STEPS = 10;      // отрезков на виток (у Painter нет дуг)

    @Override
    public void draw(Painter g) {
        // провода
        g.strokeLine(XS, TOP, R_X - R_HALF, TOP);
        g.strokeLine(R_X + R_HALF, TOP, L_X - L_HALF, TOP);
        g.strokeLine(L_X + L_HALF, TOP, XR, TOP);
        g.strokeLine(XR, TOP, XR, BOTTOM);
        g.strokeLine(XS, BOTTOM, C_X - PLATE_GAP, BOTTOM);
        g.strokeLine(C_X + PLATE_GAP, BOTTOM, XR, BOTTOM);
        g.strokeLine(XS, TOP, XS, MID - SRC_R);
        g.strokeLine(XS, MID + SRC_R, XS, BOTTOM);

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
        g.fillRect(R_X - R_HALF, TOP - R_WIDTH, 2 * R_HALF, 2 * R_WIDTH);
        g.strokeRect(R_X - R_HALF, TOP - R_WIDTH, 2 * R_HALF, 2 * R_WIDTH);

        // катушка: витки — полуокружности над проводом
        double rad = L_HALF / COILS;
        for (int k = 0; k < COILS; k++) {
            double x0 = L_X - L_HALF + rad * (2 * k + 1);
            double ax = x0 - rad, ay = TOP;
            for (int i = 1; i <= ARC_STEPS; i++) {
                double a = Math.PI * (1 - (double) i / ARC_STEPS);
                double nx = x0 + rad * Math.cos(a), ny = TOP - rad * Math.sin(a);
                g.strokeLine(ax, ay, nx, ny);
                ax = nx;
                ay = ny;
            }
        }

        // конденсатор
        g.setLineWidth(3.5);
        g.strokeLine(C_X - PLATE_GAP, BOTTOM - PLATE, C_X - PLATE_GAP, BOTTOM + PLATE);
        g.strokeLine(C_X + PLATE_GAP, BOTTOM - PLATE, C_X + PLATE_GAP, BOTTOM + PLATE);
        g.setLineWidth(2.2);

        // напряжение на катушке
        Scheme.arrow(g, L_X + L_HALF, TOP + 22, L_X - L_HALF, TOP + 22, BLUE);
        g.setStroke(WIRE);

        // подписи
        g.setBoldFont(15);
        g.setFill(LABEL);
        g.fillText("U = " + u + " В", 8, MID - 4);
        g.fillText("f = " + f + " Гц", 8, MID + 18);
        g.fillText("R = " + r + " Ом", R_X - 38, TOP - 20);
        g.fillText("L = " + l + " мГн", L_X - 45, TOP - 22);
        g.fillText("C = " + c + " мкФ", C_X - 42, BOTTOM + 40);
        g.setFill(BLUE);
        g.fillText("UL", L_X - 9, TOP + 46);
        g.setBoldFont(18);
        g.fillText("f0, ρ, Q, UL = ?", 215, MID + 22);
    }

    /** Переменный ток меняет направление — анимации движения тока в этой теме нет. */
    @Override
    public List<FlowPath> flowPaths(Random rnd) {
        return List.of();
    }
}
