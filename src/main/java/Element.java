/**
 * Тип элементов лестничной схемы. В теме 1 — всегда резисторы; в теме 2 тип выбирается случайно,
 * и курсант ищет эквивалентное сопротивление, ёмкость или индуктивность.
 * Резисторы и катушки складываются при последовательном соединении, конденсаторы — при параллельном.
 */
enum Element {
    RESISTOR("R", "Ом", "Z", "общее сопротивление цепи", "омах", "эквивалентного сопротивления"),
    CAPACITOR("C", "мкФ", "Cэкв", "эквивалентную ёмкость цепи", "микрофарадах", "эквивалентной ёмкости"),
    INDUCTOR("L", "мГн", "Lэкв", "эквивалентную индуктивность цепи", "миллигенри", "эквивалентной индуктивности");

    final String letter;          // обозначение на схеме: R1, C1, L1
    final String unit;            // единица номинала и ответа
    final String total;           // искомая величина: Z, Cэкв, Lэкв
    final String totalName;       // «найдите …» в условии
    final String unitWords;       // «ответ дайте в …»
    final String hintName;        // «проверьте расчёт …»

    Element(String letter, String unit, String total, String totalName, String unitWords, String hintName) {
        this.letter = letter;
        this.unit = unit;
        this.total = total;
        this.totalName = totalName;
        this.unitWords = unitWords;
        this.hintName = hintName;
    }

    /** Эквивалент последовательно соединённых элементов. */
    double series(double... v) {
        return this == CAPACITOR ? Circuits.parallel(v) : sum(v);
    }

    /** Эквивалент параллельно соединённых элементов. */
    double parallel(double... v) {
        return this == CAPACITOR ? sum(v) : Circuits.parallel(v);
    }

    /** Проводимость ветви для анимации: ток делится пропорционально ей (у конденсатора — ωC). */
    double admittance(double v) {
        return this == CAPACITOR ? v : 1.0 / v;
    }

    private static double sum(double... v) {
        double s = 0;
        for (double x : v) s += x;
        return s;
    }
}
