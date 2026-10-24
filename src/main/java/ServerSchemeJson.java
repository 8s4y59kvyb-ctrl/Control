import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Записывает рисование схемы в список команд для браузера. Геометрию по-прежнему задают
 * {@link Scheme#draw}, поэтому веб-схема не расходится с настольной.
 * Команда — массив: имя и аргументы, например ["line", x1, y1, x2, y2].
 */
final class ServerSchemeJson implements Painter {

    static final int WIDTH = 600, HEIGHT = 300;     // размер холста, как в настольном приложении

    private final List<Object> ops = new ArrayList<>();

    private void op(Object... args) {
        ops.add(List.of(args));
    }

    /** Схема целиком: команды рисования, пути тока для анимации и начальные цвет и толщина пера. */
    static Map<String, Object> of(Scheme scheme, Random rnd) {
        ServerSchemeJson rec = new ServerSchemeJson();
        scheme.draw(rec);

        List<Object> flow = new ArrayList<>();
        for (FlowPath p : scheme.flowPaths(rnd)) {
            List<Object> pts = new ArrayList<>();
            for (int i = 0; i < p.px.length; i++) pts.add(List.of(p.px[i], p.py[i]));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("points", pts);
            m.put("count", p.count);
            m.put("spacing", p.spacing);
            m.put("phase", p.phase);
            m.put("length", p.len);
            flow.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("width", WIDTH);
        out.put("height", HEIGHT);
        out.put("wire", Scheme.WIRE);       // исходные цвет и толщина пера, как в drawScheme настольного приложения
        out.put("lineWidth", 2.2);
        out.put("flowColor", Scheme.BLUE);
        out.put("ops", rec.ops);
        out.put("flow", flow);
        return out;
    }

    @Override public void setStroke(String color) { op("stroke", color); }
    @Override public void setFill(String color) { op("fill", color); }
    @Override public void setLineWidth(double width) { op("lineWidth", width); }
    @Override public void setBoldFont(double size) { op("font", size); }
    @Override public void strokeLine(double x1, double y1, double x2, double y2) { op("line", x1, y1, x2, y2); }
    @Override public void fillOval(double x, double y, double w, double h) { op("fillOval", x, y, w, h); }
    @Override public void strokeOval(double x, double y, double w, double h) { op("strokeOval", x, y, w, h); }
    @Override public void fillRect(double x, double y, double w, double h) { op("fillRect", x, y, w, h); }
    @Override public void strokeRect(double x, double y, double w, double h) { op("strokeRect", x, y, w, h); }

    @Override
    public void fillPolygon(double[] xs, double[] ys, int n) {
        List<Object> pts = new ArrayList<>();
        for (int i = 0; i < n; i++) pts.add(List.of(xs[i], ys[i]));
        op("polygon", pts);
    }

    @Override public void fillText(String text, double x, double y) { op("text", text, x, y); }
    @Override public void save() { op("save"); }
    @Override public void restore() { op("restore"); }
    @Override public void translate(double x, double y) { op("translate", x, y); }
    @Override public void rotate(double degrees) { op("rotate", degrees); }
}
