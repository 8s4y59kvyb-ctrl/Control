import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;
import java.util.Random;

/**
 * Схемы темы 3. currentMode = true — делитель тока (источник тока input и два параллельных резистора),
 * false — делитель напряжения (источник ЭДС input и два последовательных резистора).
 */
record DividerScheme(boolean currentMode, double input, double r1, double r2) implements Scheme {

    private static final double XS = 100;                       // источник
    // делитель тока
    private static final double X1 = 290, X2 = 470;             // оси резисторов R1 и R2
    private static final double J_TOP = 122, J_BOTTOM = 178;    // окружность источника тока
    // делитель напряжения
    private static final double A1 = 180, B1 = 280, A2 = 350, B2 = 450, XR = 520;   // выводы R1 и R2, правый край

    @Override
    public void draw(GraphicsContext g) {
        g.setFont(Font.font("System", FontWeight.BOLD, 15));
        if (currentMode) {
            drawCurrentDivider(g);
        } else {
            drawVoltageDivider(g);
        }
    }

    private void drawCurrentDivider(GraphicsContext g) {
        g.strokeLine(XS, TOP, X2, TOP);
        g.strokeLine(XS, BOTTOM, X2, BOTTOM);
        g.strokeLine(XS, TOP, XS, J_TOP);
        g.strokeLine(XS, J_BOTTOM, XS, BOTTOM);
        for (double x : new double[]{X1, X2}) {
            g.strokeLine(x, TOP, x, 100);
            g.strokeLine(x, 200, x, BOTTOM);
        }
        g.fillOval(X1 - 4, TOP - 4, 8, 8);                   // узлы
        g.fillOval(X1 - 4, BOTTOM - 4, 8, 8);

        // источник тока: окружность со стрелкой вверх
        g.setFill(Color.WHITE);
        g.fillOval(XS - 28, J_TOP, 56, J_BOTTOM - J_TOP);
        g.strokeOval(XS - 28, J_TOP, 56, J_BOTTOM - J_TOP);
        // резисторы
        g.setFill(BODY);
        for (double x : new double[]{X1, X2}) {
            g.fillRect(x - 14, 100, 28, 100);
            g.strokeRect(x - 14, 100, 28, 100);
        }
        Scheme.arrow(g, XS, 168, XS, 132, WIRE);
        Scheme.arrow(g, X1 - 32, 112, X1 - 32, 152, BLUE);   // токи в ветвях
        Scheme.arrow(g, X2 - 32, 112, X2 - 32, 152, BLUE);

        g.setFill(LABEL);
        g.fillText("R1", X1 + 22, 155);
        g.fillText((int) r1 + " Ом", X1 + 22, 173);
        g.fillText("R2", X2 + 22, 155);
        g.fillText((int) r2 + " Ом", X2 + 22, 173);
        g.fillText("Iвх = " + (int) input + " А", 6, 200);
        g.setFill(BLUE);
        g.fillText("I1 = ?", X1 - 92, 137);
        g.fillText("I2 = ?", X2 - 92, 137);
    }

    private void drawVoltageDivider(GraphicsContext g) {
        g.strokeLine(XS, TOP, A1, TOP);
        g.strokeLine(B1, TOP, A2, TOP);
        g.strokeLine(B2, TOP, XR, TOP);
        g.strokeLine(XR, TOP, XR, BOTTOM);
        g.strokeLine(XS, BOTTOM, XR, BOTTOM);

        Scheme.voltageSource(g, XS);

        // резисторы
        g.setFill(BODY);
        g.fillRect(A1, TOP - 14, B1 - A1, 28);
        g.strokeRect(A1, TOP - 14, B1 - A1, 28);
        g.fillRect(A2, TOP - 14, B2 - A2, 28);
        g.strokeRect(A2, TOP - 14, B2 - A2, 28);

        // напряжения на резисторах: выносные линии и стрелки (острие у плюсового вывода)
        g.setStroke(BLUE);
        g.setLineWidth(1.2);
        for (double x : new double[]{A1, B1, A2, B2}) {
            g.strokeLine(x, TOP + 14, x, 112);
        }
        Scheme.arrow(g, B1, 112, A1, 112, BLUE);
        Scheme.arrow(g, B2, 112, A2, 112, BLUE);

        g.setFill(LABEL);
        g.fillText("R1 = " + (int) r1 + " Ом", A1 + 4, TOP - 24);
        g.fillText("R2 = " + (int) r2 + " Ом", A2 + 4, TOP - 24);
        g.fillText("U = " + (int) input + " В", 6, 200);
        g.setFill(BLUE);
        g.fillText("U1 = ?", (A1 + B1) / 2 - 25, 136);
        g.fillText("U2 = ?", (A2 + B2) / 2 - 25, 136);
        g.setFill(PLUS);
        g.fillText("+", XS + 22, 134);
    }

    @Override
    public List<FlowPath> flowPaths(Random rnd) {
        if (currentMode) {
            // ток источника делится между R1 и R2 обратно пропорционально сопротивлениям
            double g1 = 1.0 / r1, g2 = 1.0 / r2, sum = g1 + g2;
            double[][] viaR1 = {{XS, J_TOP}, {XS, TOP}, {X1, TOP}, {X1, BOTTOM}, {XS, BOTTOM}, {XS, J_BOTTOM}};
            double[][] viaR2 = {{XS, J_TOP}, {XS, TOP}, {X2, TOP}, {X2, BOTTOM}, {XS, BOTTOM}, {XS, J_BOTTOM}};
            return List.of(new FlowPath(viaR1, g1 / sum, rnd.nextDouble()),
                           new FlowPath(viaR2, g2 / sum, rnd.nextDouble()));
        }
        // последовательная цепь: во всех точках ток одинаков
        double[][] loop = {{XS, PLUS_Y}, {XS, TOP}, {XR, TOP}, {XR, BOTTOM}, {XS, BOTTOM}, {XS, MINUS_Y}};
        return List.of(new FlowPath(loop, 1.0, rnd.nextDouble()));
    }
}
