import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Темы 1 и 2: цепь E, R1 и R2 || R3; на сложном уровне ещё R4 параллельно R2 и R3.
 * Тема 1 ищет ток источника I, тема 2 — эквивалент цепи: в ней вместо резисторов случайно
 * могут оказаться конденсаторы (ищем Cэкв) или катушки индуктивности (ищем Lэкв); в одном тесте типы не повторяются.
 * На лёгком уровне номиналы подбираются так, чтобы промежуточный расчёт и ответ были «ровными».
 */
final class LadderTasks implements TaskGenerator {

    private static final double EPS = 1e-9;

    private final boolean findZ;
    private final String timeText;
    private final EnumSet<Element> used = EnumSet.noneOf(Element.class);   // типы, уже выданные в этом тесте

    LadderTasks(boolean findZ, String timeText) {
        this.findZ = findZ;
        this.timeText = timeText;
    }

    @Override
    public Task next(Level level, Random rnd) {
        // все номиналы — случайные целые числа; при каждом новом задании
        // и при каждом запуске программы они другие
        boolean hard = level == Level.HARD;
        Element el = findZ ? nextElement(rnd) : Element.RESISTOR;
        int e, r1, r2, r3, r4;
        double parallel, zTotal;
        do {
            e = 10 + rnd.nextInt(51);                  // 10…60 В
            r1 = 10 + rnd.nextInt(51);                 // 10…60 Ом (мкФ, мГн)
            r2 = 10 + rnd.nextInt(91);                 // 10…100
            r3 = 10 + rnd.nextInt(91);
            r4 = 10 + rnd.nextInt(91);
            parallel = hard ? el.parallel(r2, r3, r4) : el.parallel(r2, r3);
            zTotal = el.series(r1, parallel);          // эквивалент всей цепи
        } while (!hard && !isRound(e, parallel, zTotal));   // на лёгком уровне — только «ровные» числа

        Answer answer = findZ ? new Answer(el.total, el.unit, zTotal) : new Answer("I", "А", e / zTotal);

        String emf = findZ ? "" : String.format(Locale.US, "E = %d В, ", e);   // в теме 2 ЭДС не нужна
        String values = emf + (hard
                ? String.format(Locale.US, "%2$s1 = %3$d %1$s, %2$s2 = %4$d %1$s, %2$s3 = %5$d %1$s, %2$s4 = %6$d %1$s",
                        el.unit, el.letter, r1, r2, r3, r4)
                : String.format(Locale.US, "%2$s1 = %3$d %1$s, %2$s2 = %4$d %1$s, %2$s3 = %5$d %1$s",
                        el.unit, el.letter, r1, r2, r3));
        String question = findZ
                ? "Найдите " + el.totalName + " " + el.total + " относительно зажимов источника. "
                        + "Ответ дайте в " + el.unitWords + " с точностью до 0,01. "
                : "Найдите ток I, потребляемый от источника. Ответ дайте в амперах "
                        + "с точностью до 0,01. ";
        String text = "Для цепи, показанной на рисунке, " + values + ". "
                + question + "На выполнение задания отводится " + timeText + ".";
        String hint = findZ
                ? "Проверьте расчёт " + el.hintName + " и попробуйте ещё раз."
                : "Проверьте расчёт общего сопротивления цепи и тока по закону Ома и попробуйте ещё раз.";

        return new Task(level.title.toLowerCase() + " уровень", text, List.of(answer), hint,
                new LadderScheme(e, r1, r2, r3, r4, hard, findZ, el));
    }

    /** Случайный тип элементов, ещё не встречавшийся в этом тесте; когда выданы все — круг начинается заново. */
    private Element nextElement(Random rnd) {
        if (used.size() == Element.values().length) used.clear();
        List<Element> free = new ArrayList<>(EnumSet.complementOf(used));
        Element el = free.get(rnd.nextInt(free.size()));
        used.add(el);
        return el;
    }

    /**
     * Лёгкий уровень: эквивалент параллельных ветвей — целое число, а ответ (Z, Cэкв, Lэкв или ток I) —
     * не больше одного знака после запятой, чтобы ученик думал о методе, а не о калькуляторе.
     */
    private boolean isRound(int e, double parallel, double zTotal) {
        return isWhole(parallel) && isWhole(10 * (findZ ? zTotal : e / zTotal));
    }

    private static boolean isWhole(double v) {
        return Math.abs(v - Math.rint(v)) < EPS;
    }
}
