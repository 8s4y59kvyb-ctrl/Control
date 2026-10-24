import java.util.List;
import java.util.Random;

/**
 * Схема темы 4: источник переменного напряжения и один элемент — катушка (inductor = true, value в мГн)
 * или конденсатор (value в мкФ). freqs — частоты задания, Гц.
 */
record ReactanceScheme(boolean inductor, double value, double[] freqs) implements Scheme {

    private static final double XS = 150;                       // источник
    private static final double XE = 420;                       // ось элемента
    private static final double SRC_R = 30;                     // радиус окружности источника
    private static final double MID = (TOP + BOTTOM) / 2;
    private static final double HALF = 50;                      // половина длины элемента

    private static final double PLATE_GAP = 5;    // половина зазора между пластинами конденсатора
    private static final double PLATE = 18;       // половина длины пластины
    private static final int COILS = 4;           // витков у катушки
    private static final int ARC_STEPS = 10;      // отрезков на виток (у Painter нет дуг)

    @Override
    public void draw(Painter g) {
        // провода
        g.strokeLine(XS, TOP, XE, TOP);
        g.strokeLine(XS, BOTTOM, XE, BOTTOM);
        g.strokeLine(XS, TOP, XS, MID - SRC_R);
        g.strokeLine(XS, MID + SRC_R, XS, BOTTOM);
        g.strokeLine(XE, TOP, XE, MID - HALF);
        g.strokeLine(XE, MID + HALF, XE, BOTTOM);

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

        // элемент
        if (inductor) {
            // витки — полуокружности справа от провода
            double r = HALF / COILS;
            for (int c = 0; c < COILS; c++) {
                double y0 = MID - HALF + r * (2 * c + 1);
                double ax = XE, ay = y0 - r;
                for (int i = 1; i <= ARC_STEPS; i++) {
                    double a = Math.PI * ((double) i / ARC_STEPS - 0.5);
                    double nx = XE + r * Math.cos(a), ny = y0 + r * Math.sin(a);
                    g.strokeLine(ax, ay, nx, ny);
                    ax = nx;
                    ay = ny;
                }
            }
        } else {
            g.strokeLine(XE, MID - HALF, XE, MID - PLATE_GAP);
            g.strokeLine(XE, MID + PLATE_GAP, XE, MID + HALF);
            g.setLineWidth(3.5);
            g.strokeLine(XE - PLATE, MID - PLATE_GAP, XE + PLATE, MID - PLATE_GAP);
            g.strokeLine(XE - PLATE, MID + PLATE_GAP, XE + PLATE, MID + PLATE_GAP);
            g.setLineWidth(2.2);
        }

        // подписи
        g.setBoldFont(15);
        g.setFill(LABEL);
        g.fillText("u ~", XS - 70, MID + 5);
        g.fillText("f = " + (int) freqs[0] + "…" + (int) freqs[freqs.length - 1] + " Гц",
                XS - 60, BOTTOM + 30);
        g.fillText(inductor ? "L = " + (int) value + " мГн" : "C = " + (int) value + " мкФ", XE + 30, MID - 8);
        g.setFill(BLUE);
        g.fillText(inductor ? "XL = ?" : "XC = ?", XE + 30, MID + 16);
    }

    /** Переменный ток меняет направление — анимации движения тока в этой теме нет. */
    @Override
    public List<FlowPath> flowPaths(Random rnd) {
        return List.of();
    }
}
