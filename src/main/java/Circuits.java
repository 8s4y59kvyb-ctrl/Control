/** Расчётные формулы для схем заданий. */
final class Circuits {

    private Circuits() {
    }

    /** Общее сопротивление параллельно соединённых резисторов. */
    static double parallel(double... r) {
        double g = 0;
        for (double x : r) g += 1.0 / x;
        return 1.0 / g;
    }

    /** Делитель тока: ток в резисторе r, соединённом параллельно с other, при входном токе total. */
    static double currentShare(double total, double r, double other) {
        return total * other / (r + other);
    }

    /** Делитель напряжения: напряжение на резисторе r, соединённом последовательно с other. */
    static double voltageShare(double total, double r, double other) {
        return total * r / (r + other);
    }
}
