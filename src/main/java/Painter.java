/**
 * Минимальный набор операций рисования, которых хватает схемам.
 * Геометрия схем (Scheme) не зависит от способа вывода: {@link ServerSchemeJson} записывает
 * команды в JSON, а рисует их браузер.
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
