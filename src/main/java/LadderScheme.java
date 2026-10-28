import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Схема тем 1 и 2: источник E, R1 в верхнем проводе, R2 и R3 — вертикальные ветви.
 * withR4 — сложный уровень: добавлен диагональный элемент R4, параллельный R2 и R3.
 * findZ — тема 2: ищем эквивалент цепи, значение E, токи и узлы a, b не показываем.
 * element — что стоит на месте R1…R4: резисторы, конденсаторы (C1…C4) или катушки (L1…L4);
 * r1…r4 — их номиналы в Ом, мкФ или мГн.
 * i2, u1 — обратная задача темы 1: показания приборов на схеме, а R1 и E неизвестны («?»); иначе NaN.
 */
record LadderScheme(double e, double r1, double r2, double r3, double r4,
                    boolean withR4, boolean findZ, Element element, double i2, double u1) implements Scheme {

    /** Схема прямой задачи: все номиналы известны. */
    LadderScheme(double e, double r1, double r2, double r3, double r4, boolean withR4, boolean findZ, Element element) {
        this(e, r1, r2, r3, r4, withR4, findZ, element, Double.NaN, Double.NaN);
    }

    boolean inverse() {
        return !Double.isNaN(i2);
    }

    private static final double XS = 80;     // источник
    private static final double XA = 285;    // ветвь R2
    private static final double XB = 505;    // ветвь R3, правый край

    private static final double PLATE_GAP = 5;    // половина зазора между пластинами конденсатора
    private static final double PLATE = 16;       // половина длины пластины
    private static final int COILS = 4;           // витков у катушки
    private static final int ARC_STEPS = 10;      // отрезков на виток (у Painter нет дуг)

    @Override
    public void draw(Painter g) {
        // провода
        g.strokeLine(XS, TOP, 140, TOP);
        g.strokeLine(230, TOP, XB, TOP);
        g.strokeLine(XS, BOTTOM, XB, BOTTOM);
        g.strokeLine(XA, TOP, XA, 100);
        g.strokeLine(XA, 200, XA, BOTTOM);
        g.strokeLine(XB, TOP, XB, 100);
        g.strokeLine(XB, 200, XB, BOTTOM);
        // узлы
        g.fillOval(XA - 4, TOP - 4, 8, 8);
        g.fillOval(XA - 4, BOTTOM - 4, 8, 8);

        Scheme.voltageSource(g, XS);

        // элементы 1, 2, 3
        part(g, 185, TOP, 0, 45, 14);
        part(g, XA, 150, 90, 50, 14);
        part(g, XB, 150, 90, 50, 14);

        // диагональный элемент 4: от верхнего узла ветви 2 до правого нижнего угла схемы,
        // электрически параллельно ветвям 2 и 3
        double cx = (XA + XB) / 2, cy = (TOP + BOTTOM) / 2;
        double dx = XB - XA, dy = BOTTOM - TOP;
        double len = Math.hypot(dx, dy);
        double ux = dx / len, uy = dy / len;
        double half = 50;                                       // половина длины корпуса
        if (withR4) {
            g.setStroke(WIRE);
            g.setLineWidth(2.2);
            g.strokeLine(XA, TOP, cx - half * ux, cy - half * uy);
            g.strokeLine(cx + half * ux, cy + half * uy, XB, BOTTOM);
            g.setFill(WIRE);                                    // чёрные узлы, как у ветви 2
            g.fillOval(XA - 4, TOP - 4, 8, 8);
            g.fillOval(XB - 4, BOTTOM - 4, 8, 8);

            part(g, cx, cy, Math.toDegrees(Math.atan2(dy, dx)), half, 12);
        }

        // стрелки токов I1…I4 (только в теме 1): I1 — над R1, токи ветвей — у нижних выводов
        g.setFill(BLUE);
        g.setStroke(BLUE);
        double i4x = 0, i4y = 0;                                // точка подписи I4
        if (!findZ) {
            g.strokeLine(100, TOP - 22, 130, TOP - 22);
            g.fillPolygon(new double[]{130, 122, 122}, new double[]{TOP - 22, TOP - 27, TOP - 17}, 3);
            Scheme.arrow(g, XA + 10, 208, XA + 10, 232, BLUE);
            Scheme.arrow(g, XB + 10, 208, XB + 10, 232, BLUE);
            if (withR4) {
                // вдоль нижнего вывода R4, со стороны ветви 2
                double ex = cx + half * ux, ey = cy + half * uy;    // конец корпуса R4
                double nx = -uy * 10, ny = ux * 10;                // сдвиг от провода влево-вниз
                Scheme.arrow(g, ex + (XB - ex) * 0.2 + nx, ey + (BOTTOM - ey) * 0.2 + ny,
                        ex + (XB - ex) * 0.7 + nx, ey + (BOTTOM - ey) * 0.7 + ny, BLUE);
                i4x = ex + (XB - ex) * 0.45 + 2.4 * nx - 8;
                i4y = ey + (BOTTOM - ey) * 0.45 + 2.4 * ny + 5;
            }
        }

        // подписи
        String n = element.letter, u = " " + element.unit;
        g.setFill(LABEL);
        g.setBoldFont(15);
        g.fillText(n + "1 = " + (inverse() ? "?" : (int) r1 + u), 142, TOP - 28);
        g.fillText(n + "2", XA + 22, 155);
        g.fillText((int) r2 + u, XA + 22, 173);
        g.fillText(n + "3", XB + 22, 155);
        g.fillText((int) r3 + u, XB + 22, 173);
        if (findZ) {
            g.fillText("E", 52, 200);
        } else {
            g.fillText("E = " + (inverse() ? "?" : (int) e + " В"), 6, 200);
        }
        if (withR4) {
            g.fillText(n + "4", cx + 15, cy - 38);
            g.fillText((int) r4 + u, cx + 15, cy - 20);
        }
        g.setFill(BLUE);
        if (findZ) {
            g.setBoldFont(20);
            g.fillText(element.total + " = ?", 110, 160);
        } else {
            g.fillText("I1", 105, TOP - 32);
            g.fillText(inverse() ? "I2 = " + LadderTasks.number(i2) + " А" : "I2", XA + 19, 228);
            g.fillText("I3", XB + 19, 228);
            if (withR4) g.fillText("I4", i4x, i4y);
            if (inverse()) g.fillText("U1 = " + LadderTasks.number(u1) + " В", 140, TOP + 36);   // под R1
            g.setFill(LABEL);                                   // узлы параллельного участка
            g.fillText("a", XA - 16, TOP - 8);
            g.fillText("b", XA - 16, BOTTOM + 20);
        }
        g.setFill(PLUS);
        g.fillText("+", XS + 22, 134);
    }

    /**
     * Элемент с центром (x, y), повёрнутый на angle градусов от горизонтали; half — половина длины,
     * width — половина ширины корпуса резистора. Конденсатор и катушка сами дорисовывают выводы до концов.
     */
    private void part(Painter g, double x, double y, double angle, double half, double width) {
        g.save();
        g.translate(x, y);
        g.rotate(angle);
        g.setStroke(WIRE);
        g.setLineWidth(2.2);
        switch (element) {
            case RESISTOR -> {
                g.setFill(BODY);
                g.fillRect(-half, -width, 2 * half, 2 * width);
                g.strokeRect(-half, -width, 2 * half, 2 * width);
            }
            case CAPACITOR -> {
                g.strokeLine(-half, 0, -PLATE_GAP, 0);
                g.strokeLine(PLATE_GAP, 0, half, 0);
                g.setLineWidth(3.5);
                g.strokeLine(-PLATE_GAP, -PLATE, -PLATE_GAP, PLATE);
                g.strokeLine(PLATE_GAP, -PLATE, PLATE_GAP, PLATE);
            }
            case INDUCTOR -> {
                // витки — полуокружности по одну сторону от провода
                double r = half / COILS;
                for (int c = 0; c < COILS; c++) {
                    double x0 = -half + r * (2 * c + 1);
                    double px = x0 - r, py = 0;
                    for (int i = 1; i <= ARC_STEPS; i++) {
                        double a = Math.PI * (1 - (double) i / ARC_STEPS);
                        double nx = x0 + r * Math.cos(a), ny = -r * Math.sin(a);
                        g.strokeLine(px, py, nx, ny);
                        px = nx;
                        py = ny;
                    }
                }
            }
        }
        g.restore();
        g.setLineWidth(2.2);
    }

    /**
     * Замкнутый путь тока: плюс источника → верхний провод → одна из ветвей (2, 3 или 4)
     * → нижний провод → минус источника. Через конденсатор постоянный ток не течёт — путей нет.
     */
    @Override
    public List<FlowPath> flowPaths(Random rnd) {
        if (element == Element.CAPACITOR) return List.of();
        double g2 = 1.0 / r2, g3 = 1.0 / r3, g4 = withR4 ? 1.0 / r4 : 0;
        double sum = g2 + g3 + g4;

        List<FlowPath> paths = new ArrayList<>();
        double[][] viaR2 = {{XS, PLUS_Y}, {XS, TOP}, {XA, TOP}, {XA, BOTTOM}, {XS, BOTTOM}, {XS, MINUS_Y}};
        double[][] viaR3 = {{XS, PLUS_Y}, {XS, TOP}, {XB, TOP}, {XB, BOTTOM}, {XS, BOTTOM}, {XS, MINUS_Y}};
        paths.add(new FlowPath(viaR2, g2 / sum, rnd.nextDouble()));
        paths.add(new FlowPath(viaR3, g3 / sum, rnd.nextDouble()));
        if (withR4) {
            double[][] viaR4 = {{XS, PLUS_Y}, {XS, TOP}, {XA, TOP}, {XB, BOTTOM}, {XS, BOTTOM}, {XS, MINUS_Y}};
            paths.add(new FlowPath(viaR4, g4 / sum, rnd.nextDouble()));
        }
        return paths;
    }
}
