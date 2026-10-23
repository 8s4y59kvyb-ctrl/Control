import java.util.Locale;

/** Искомая величина задания: обозначение (I, Z, U1…), единица измерения и правильное значение. */
record Answer(String symbol, String unit, double value) {

    static final double TOLERANCE = 0.02; // допустимая погрешность 2 %

    /** Ответ верный, если он отличается от правильного не более чем на 2 % или на 0,01. */
    boolean accepts(double entered) {
        return Math.abs(entered - value) <= Math.max(TOLERANCE * value, 0.01);
    }

    /** Правильный ответ для сообщений, например «I = 0,75 А». */
    String text() {
        return String.format(Locale.US, "%s = %.2f %s", symbol, value, unit).replace('.', ',');
    }

    /** Число из поля ответа (запятая или точка); NaN и Infinity считаются неверным вводом. */
    static double parse(String input) {
        double v = Double.parseDouble(input.trim().replace(',', '.'));
        if (!Double.isFinite(v)) throw new NumberFormatException("not finite: " + v);
        return v;
    }
}
