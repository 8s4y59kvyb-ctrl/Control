import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Темы 1 и 2: цепь E, R1 и R2 || R3; на сложном уровне ещё R4 параллельно R2 и R3.
 * Тема 1 ищет ток источника I, тема 2 — общее сопротивление цепи Z.
 * На лёгком уровне номиналы подбираются так, чтобы промежуточный расчёт и ответ были «ровными».
 */
final class LadderTasks implements TaskGenerator {

    private final boolean findZ;
    private final String timeText;

    LadderTasks(boolean findZ, String timeText) {
        this.findZ = findZ;
        this.timeText = timeText;
    }

    @Override
    public Task next(Level level, Random rnd) {
        // все номиналы — случайные целые числа; при каждом новом задании
        // и при каждом запуске программы они другие
        boolean hard = level == Level.HARD;
        int e, r1, r2, r3, r4;
        do {
            e = 10 + rnd.nextInt(51);                  // 10…60 В
            r1 = 10 + rnd.nextInt(51);                 // 10…60 Ом
            r2 = 10 + rnd.nextInt(91);                 // 10…100 Ом
            r3 = 10 + rnd.nextInt(91);
            r4 = 10 + rnd.nextInt(91);
        } while (!hard && !isRound(e, r1, r2, r3));    // на лёгком уровне — только «ровные» числа

        double parallel = hard ? Circuits.parallel(r2, r3, r4) : Circuits.parallel(r2, r3);
        double zTotal = r1 + parallel;                 // общее сопротивление цепи Z
        Answer answer = findZ ? new Answer("Z", "Ом", zTotal) : new Answer("I", "А", e / zTotal);

        String emf = findZ ? "" : String.format(Locale.US, "E = %d В, ", e);   // в теме 2 ЭДС не нужна
        String values = emf + (hard
                ? String.format(Locale.US, "R1 = %d Ом, R2 = %d Ом, R3 = %d Ом, R4 = %d Ом",
                        r1, r2, r3, r4)
                : String.format(Locale.US, "R1 = %d Ом, R2 = %d Ом, R3 = %d Ом",
                        r1, r2, r3));
        String question = findZ
                ? "Найдите общее сопротивление цепи Z относительно зажимов источника. "
                        + "Ответ дайте в омах с точностью до 0,01. "
                : "Найдите ток I, потребляемый от источника. Ответ дайте в амперах "
                        + "с точностью до 0,01. ";
        String text = "Для цепи, показанной на рисунке, " + values + ". "
                + question + "На выполнение задания отводится " + timeText + ".";
        String hint = findZ
                ? "Проверьте расчёт эквивалентного сопротивления и попробуйте ещё раз."
                : "Проверьте расчёт общего сопротивления цепи и тока по закону Ома и попробуйте ещё раз.";

        return new Task(level.title.toLowerCase() + " уровень", text, List.of(answer), hint,
                new LadderScheme(e, r1, r2, r3, r4, hard, findZ));
    }

    /**
     * Лёгкий уровень: R2 || R3 — целое число Ом, а ток I — не больше одного знака после запятой,
     * чтобы ученик думал о методе, а не о калькуляторе.
     */
    private boolean isRound(int e, int r1, int r2, int r3) {
        if (r2 * r3 % (r2 + r3) != 0) return false;
        int z = r1 + r2 * r3 / (r2 + r3);
        return findZ || e * 10 % z == 0;
    }
}
