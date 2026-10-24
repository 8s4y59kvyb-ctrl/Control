import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/** Рисование схем на холсте JavaFX; используется только настольным приложением. */
final class FxPainter implements Painter {

    private final GraphicsContext g;

    FxPainter(GraphicsContext g) {
        this.g = g;
    }

    @Override public void setStroke(String color) { g.setStroke(Color.web(color)); }
    @Override public void setFill(String color) { g.setFill(Color.web(color)); }
    @Override public void setLineWidth(double width) { g.setLineWidth(width); }
    @Override public void setBoldFont(double size) { g.setFont(Font.font("System", FontWeight.BOLD, size)); }
    @Override public void strokeLine(double x1, double y1, double x2, double y2) { g.strokeLine(x1, y1, x2, y2); }
    @Override public void fillOval(double x, double y, double w, double h) { g.fillOval(x, y, w, h); }
    @Override public void strokeOval(double x, double y, double w, double h) { g.strokeOval(x, y, w, h); }
    @Override public void fillRect(double x, double y, double w, double h) { g.fillRect(x, y, w, h); }
    @Override public void strokeRect(double x, double y, double w, double h) { g.strokeRect(x, y, w, h); }
    @Override public void fillPolygon(double[] xs, double[] ys, int n) { g.fillPolygon(xs, ys, n); }
    @Override public void fillText(String text, double x, double y) { g.fillText(text, x, y); }
    @Override public void save() { g.save(); }
    @Override public void restore() { g.restore(); }
    @Override public void translate(double x, double y) { g.translate(x, y); }
    @Override public void rotate(double degrees) { g.rotate(degrees); }
}
