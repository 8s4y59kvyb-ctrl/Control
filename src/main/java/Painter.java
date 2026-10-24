/**
 * Минимальный набор операций рисования, которых хватает схемам.
 * Отдельный интерфейс нужен, чтобы геометрия схем (Scheme) не зависела от JavaFX:
 * в настольном приложении её выполняет {@link FxPainter}, в веб-версии — запись в JSON для браузера.
 * Цвета задаются строкой «#rrggbb».
 */
interface Painter {

    void setStroke(String color);

    void setFill(String color);

    void setLineWidth(double width);

    /** Жирный шрифт заданного размера. */
    void setBoldFont(double size);

    void strokeLine(double x1, double y1, double x2, double y2);

    void fillOval(double x, double y, double w, double h);

    void strokeOval(double x, double y, double w, double h);

    void fillRect(double x, double y, double w, double h);

    void strokeRect(double x, double y, double w, double h);

    void fillPolygon(double[] xs, double[] ys, int n);

    void fillText(String text, double x, double y);

    void save();

    void restore();

    void translate(double x, double y);

    void rotate(double degrees);
}
