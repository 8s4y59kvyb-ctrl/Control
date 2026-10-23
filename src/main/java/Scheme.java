import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.Random;

/**
 * Схема задания на холсте 600×300: рисунок и пути тока для анимации.
 * Рисунок и пути строятся из одних и тех же координат реализации.
 */
interface Scheme {

    double TOP = 60;          // верхний провод
    double BOTTOM = 240;      // нижний провод
    double PLUS_Y = 135;      // длинная пластина источника ЭДС (+)
    double MINUS_Y = 148;     // короткая пластина (−)

    Color WIRE = Color.web("#1f2937");
    Color BODY = Color.web("#fef3c7");      // корпус резистора
    Color LABEL = Color.web("#111827");
    Color BLUE = Color.web("#2563eb");
    Color PLUS = Color.web("#dc2626");

    /** Рисует схему; перо уже настроено: цвет WIRE, толщина 2.2. */
    void draw(GraphicsContext g);

    /** Пути тока; доли токов ветвей пропорциональны их проводимостям. */
    List<FlowPath> flowPaths(Random rnd);

    /** Источник ЭДС на вертикальном проводе x между верхним и нижним проводами. */
    static void voltageSource(GraphicsContext g, double x) {
        g.strokeLine(x, TOP, x, PLUS_Y);
        g.strokeLine(x - 18, PLUS_Y, x + 18, PLUS_Y);        // длинная пластина (+)
        g.setLineWidth(4);
        g.strokeLine(x - 9, MINUS_Y, x + 9, MINUS_Y);        // короткая пластина (−)
        g.setLineWidth(2.2);
        g.strokeLine(x, MINUS_Y, x, BOTTOM);
    }

    /** Стрелка от (x1,y1) к (x2,y2), острие в конце. */
    static void arrow(GraphicsContext g, double x1, double y1, double x2, double y2, Color color) {
        g.setStroke(color);
        g.setFill(color);
        g.setLineWidth(2.2);
        g.strokeLine(x1, y1, x2, y2);
        double ang = Math.atan2(y2 - y1, x2 - x1), h = 9;
        g.fillPolygon(
                new double[]{x2, x2 - h * Math.cos(ang - 0.4), x2 - h * Math.cos(ang + 0.4)},
                new double[]{y2, y2 - h * Math.sin(ang - 0.4), y2 - h * Math.sin(ang + 0.4)}, 3);
    }
}
