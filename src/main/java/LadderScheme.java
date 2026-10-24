import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Схема тем 1 и 2: источник E, R1 в верхнем проводе, R2 и R3 — вертикальные ветви.
 * withR4 — сложный уровень: добавлен диагональный резистор R4, параллельный R2 и R3.
 * findZ — тема 2: ищем Z, значение E и ток I не показываем.
 */
record LadderScheme(double e, double r1, double r2, double r3, double r4,
                    boolean withR4, boolean findZ) implements Scheme {

    private static final double XS = 80;     // источник
    private static final double XA = 285;    // ветвь R2
    private static final double XB = 505;    // ветвь R3, правый край

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

        // резисторы R1, R2, R3
        g.setFill(BODY);
        g.fillRect(140, TOP - 14, 90, 28);
        g.strokeRect(140, TOP - 14, 90, 28);
        g.fillRect(XA - 14, 100, 28, 100);
        g.strokeRect(XA - 14, 100, 28, 100);
        g.fillRect(XB - 14, 100, 28, 100);
        g.strokeRect(XB - 14, 100, 28, 100);

        // диагональный резистор R4: от верхнего узла R2 до правого нижнего угла схемы,
        // электрически параллельно R2 и R3
        double cx = (XA + XB) / 2, cy = (TOP + BOTTOM) / 2;
        if (withR4) {
            double dx = XB - XA, dy = BOTTOM - TOP;
            double len = Math.hypot(dx, dy);
            double ux = dx / len, uy = dy / len;
            double half = 50;                                   // половина длины корпуса

            g.setStroke(WIRE);
            g.setLineWidth(2.2);
            g.strokeLine(XA, TOP, cx - half * ux, cy - half * uy);
            g.strokeLine(cx + half * ux, cy + half * uy, XB, BOTTOM);
            g.setFill(WIRE);                                    // чёрные узлы, как у R2
            g.fillOval(XA - 4, TOP - 4, 8, 8);
            g.fillOval(XB - 4, BOTTOM - 4, 8, 8);

            g.save();
            g.translate(cx, cy);
            g.rotate(Math.toDegrees(Math.atan2(dy, dx)));
            g.setFill(BODY);
            g.fillRect(-half, -12, 2 * half, 24);
            g.strokeRect(-half, -12, 2 * half, 24);
            g.restore();
        }

        // стрелка тока (только в теме 1)
        g.setFill(BLUE);
        g.setStroke(BLUE);
        if (!findZ) {
            g.strokeLine(100, TOP - 22, 130, TOP - 22);
            g.fillPolygon(new double[]{130, 122, 122}, new double[]{TOP - 22, TOP - 27, TOP - 17}, 3);
        }

        // подписи
        g.setFill(LABEL);
        g.setBoldFont(15);
        g.fillText("R1 = " + (int) r1 + " Ом", 142, TOP - 28);
        g.fillText("R2", XA + 22, 155);
        g.fillText((int) r2 + " Ом", XA + 22, 173);
        g.fillText("R3", XB + 22, 155);
        g.fillText((int) r3 + " Ом", XB + 22, 173);
        if (findZ) {
            g.fillText("E", 52, 200);
        } else {
            g.fillText("E = " + (int) e + " В", 6, 200);
        }
        if (withR4) {
            g.fillText("R4", cx + 15, cy - 38);
            g.fillText((int) r4 + " Ом", cx + 15, cy - 20);
        }
        g.setFill(BLUE);
        if (findZ) {
            g.setBoldFont(20);
            g.fillText("Z = ?", 110, 160);
        } else {
            g.fillText("I", 109, TOP - 32);
        }
        g.setFill(PLUS);
        g.fillText("+", XS + 22, 134);
    }

    /**
     * Замкнутый путь тока: плюс источника → верхний провод → одна из ветвей (R2, R3 или R4)
     * → нижний провод → минус источника.
     */
    @Override
    public List<FlowPath> flowPaths(Random rnd) {
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
