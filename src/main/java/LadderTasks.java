import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Function;

/**
 * Темы 1 и 2: цепь E, R1 и R2 || R3; на сложном уровне ещё R4 параллельно R2 и R3.
 * Тема 1 ищет токи всех ветвей и напряжения участков (U1 на R1, Uab на параллельном участке),
 * а через задание — обратную задачу: по показаниям приборов I2 и U1 найти Uab, I1, R1 и E. Тема 2 — эквивалент цепи: в ней вместо резисторов случайно
 * могут оказаться конденсаторы (ищем Cэкв) или катушки индуктивности (ищем Lэкв); в одном тесте типы не повторяются.
 * На лёгком уровне номиналы подбираются так, чтобы промежуточный расчёт и ответ были «ровными».
 */
final class LadderTasks implements TaskGenerator {

    private static final double EPS = 1e-9;
    private static final double MIN_GIVEN_CURRENT = 0.1;   // обратная задача: показание I2 не меньше 0,1 А

    private final boolean findZ;
    private final Function<Level, String> timeText;    // «10 минут» по уровню задания
    private final EnumSet<Element> used = EnumSet.noneOf(Element.class);   // типы, уже выданные в этом тесте
    private boolean nextInverse;          // тема 1: следующее задание — обратная задача (первое всегда прямое)

    LadderTasks(boolean findZ, Function<Level, String> timeText) {
        this.findZ = findZ;
        this.timeText = timeText;
    }

    @Override
    public Task next(Level level, Random rnd) {
        // все номиналы — случайные целые числа; при каждом новом задании
        // и при каждом запуске программы они другие
        boolean hard = level == Level.HARD;
        boolean inverse = !findZ && nextInverse;
        if (!findZ) nextInverse = !nextInverse;
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
        } while (!hard && !isRound(e, r2, r3, parallel, zTotal)   // на лёгком уровне — только «ровные» числа
                || inverse && e / zTotal * parallel / r2 < MIN_GIVEN_CURRENT);
        if (inverse) return inverse(level, hard, e, r1, r2, r3, r4, parallel, zTotal);

        List<Answer> answers = new ArrayList<>();
        if (findZ) {
            answers.add(new Answer(el.total, el.unit, zTotal));
        } else {
            double i1 = e / zTotal;                    // ток источника, он же ток R1
            double uab = i1 * parallel;                // напряжение на параллельном участке
            answers.add(new Answer("I1", "А", i1));
            answers.add(new Answer("I2", "А", uab / r2));
            answers.add(new Answer("I3", "А", uab / r3));
            if (hard) answers.add(new Answer("I4", "А", uab / r4));
            answers.add(new Answer("U1", "В", i1 * r1));
            answers.add(new Answer("Uab", "В", uab));
        }

        String emf = findZ ? "" : String.format(Locale.US, "E = %d В, ", e);   // в теме 2 ЭДС не нужна
        String values = emf + (hard
                ? String.format(Locale.US, "%2$s1 = %3$d %1$s, %2$s2 = %4$d %1$s, %2$s3 = %5$d %1$s, %2$s4 = %6$d %1$s",
                        el.unit, el.letter, r1, r2, r3, r4)
                : String.format(Locale.US, "%2$s1 = %3$d %1$s, %2$s2 = %4$d %1$s, %2$s3 = %5$d %1$s",
                        el.unit, el.letter, r1, r2, r3));
        String question = findZ
                ? "Найдите " + el.totalName + " " + el.total + " относительно зажимов источника. "
                        + "Ответ дайте в " + el.unitWords + " с точностью до 0,01. "
                : "Найдите токи во всех ветвях (" + (hard ? "I1, I2, I3, I4" : "I1, I2, I3")
                        + ") и падения напряжения на участках цепи: U1 на резисторе R1 и Uab на параллельном "
                        + "участке между узлами a и b. Ответы дайте в амперах и вольтах с точностью до 0,01. ";
        String text = "Для цепи, показанной на рисунке, " + values + ". "
                + question + "На выполнение задания отводится " + timeText.apply(level) + ".";
        String hint = findZ
                ? "Проверьте расчёт " + el.hintName + " и попробуйте ещё раз."
                : "Проверьте расчёт: общее сопротивление цепи, ток I1 по закону Ома, напряжения U1 и Uab, "
                        + "затем токи ветвей. Попробуйте ещё раз.";

        return new Task(level.title.toLowerCase() + " уровень", text, answers, hint,
                new LadderScheme(e, r1, r2, r3, r4, hard, findZ, el));
    }

    /**
     * Обратная задача темы 1: известны R2, R3 (R4) и показания приборов — ток I2 и напряжение U1 на R1.
     * Ответы считаются от показаний, округлённых так, как они написаны в условии, — расхождения с текстом нет.
     */
    private Task inverse(Level level, boolean hard, int e, int r1, int r2, int r3, int r4,
                         double parallel, double zTotal) {
        double i1Real = e / zTotal;
        double i2 = round2(i1Real * parallel / r2), u1 = round2(i1Real * r1);
        double uab = i2 * r2;
        double i1 = uab / r2 + uab / r3 + (hard ? uab / r4 : 0);
        List<Answer> answers = List.of(
                new Answer("Uab", "В", uab),
                new Answer("I1", "А", i1),
                new Answer("R1", "Ом", u1 / i1),
                new Answer("E", "В", u1 + uab));

        String values = hard
                ? String.format(Locale.US, "R2 = %d Ом, R3 = %d Ом, R4 = %d Ом", r2, r3, r4)
                : String.format(Locale.US, "R2 = %d Ом, R3 = %d Ом", r2, r3);
        String text = "Для цепи, показанной на рисунке, " + values + ". Амперметр в ветви R2 показывает ток I2 = "
                + number(i2) + " А, вольтметр на резисторе R1 — напряжение U1 = " + number(u1) + " В. "
                + "Найдите напряжение Uab на параллельном участке, ток источника I1, сопротивление R1 и ЭДС E. "
                + "Ответы дайте в вольтах, амперах и омах с точностью до 0,01. "
                + "На выполнение задания отводится " + timeText.apply(level) + ".";
        String hint = "Проверьте расчёт: Uab = I2·R2, затем токи остальных ветвей и I1 по первому закону Кирхгофа, "
                + "R1 = U1 / I1, E = U1 + Uab. Попробуйте ещё раз.";
        return new Task("обратная задача, " + level.title.toLowerCase() + " уровень", text, answers, hint,
                new LadderScheme(e, r1, r2, r3, r4, hard, false, Element.RESISTOR, i2, u1));
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /** Показание прибора для условия: «0,25», «12,5», «3». */
    static String number(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                .replace('.', ',');
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
     * Лёгкий уровень: эквивалент параллельных ветвей — целое число, а ответ (Z, Cэкв, Lэкв или ток I1) —
     * не больше одного знака после запятой, чтобы ученик думал о методе, а не о калькуляторе.
     * В теме 1 тогда U1 и Uab тоже с одним знаком, а токи ветвей I2 и I3 — не больше чем с двумя.
     */
    private boolean isRound(int e, int r2, int r3, double parallel, double zTotal) {
        if (!isWhole(parallel)) return false;
        if (findZ) return isWhole(10 * zTotal);
        double i1 = e / zTotal, uab = i1 * parallel;
        return isWhole(10 * i1) && isWhole(100 * uab / r2) && isWhole(100 * uab / r3);
    }

    private static boolean isWhole(double v) {
        return Math.abs(v - Math.rint(v)) < EPS;
    }
}
