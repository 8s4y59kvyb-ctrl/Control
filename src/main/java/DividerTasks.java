import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Тема 3: схемы чередуются (первая выбирается случайно), уровней сложности нет.
 * Делитель тока: I1 = Iвх·R2/(R1+R2), I2 = Iвх·R1/(R1+R2).
 * Делитель напряжения: U1 = U·R1/(R1+R2), U2 = U·R2/(R1+R2).
 */
final class DividerTasks implements TaskGenerator {

    private static final String HINT = "Проверьте расчёт и попробуйте ещё раз.";

    private final String timeText;
    private Boolean lastCurrent;          // предыдущая схема: true — делитель тока; null — заданий ещё не было

    DividerTasks(String timeText) {
        this.timeText = timeText;
    }

    @Override
    public Task next(Level level, Random rnd) {
        boolean current = (lastCurrent == null) ? rnd.nextBoolean() : !lastCurrent;
        lastCurrent = current;
        double r1 = 10 + rnd.nextInt(91);                // 10…100 Ом
        double r2 = 10 + rnd.nextInt(91);

        if (current) {
            double in = 1 + rnd.nextInt(20);             // входной ток 1…20 А
            String text = String.format(Locale.US,
                    "Входной ток цепи Iвх = %.0f А. Резисторы R1 = %.0f Ом и R2 = %.0f Ом "
                            + "соединены параллельно. Рассчитайте токи I1 и I2 в резисторах "
                            + "(делитель тока). Ответ дайте в амперах с точностью до 0,01. "
                            + "На выполнение задания отводится %s.",
                    in, r1, r2, timeText);
            return new Task("делитель тока", text,
                    List.of(new Answer("I1", "А", Circuits.currentShare(in, r1, r2)),
                            new Answer("I2", "А", Circuits.currentShare(in, r2, r1))),
                    HINT, new DividerScheme(true, in, r1, r2));
        }
        double u = 10 + rnd.nextInt(51);                 // входное напряжение 10…60 В
        String text = String.format(Locale.US,
                "К последовательно соединённым резисторам R1 = %.0f Ом и R2 = %.0f Ом "
                        + "приложено входное напряжение U = %.0f В. Рассчитайте падения "
                        + "напряжения U1 и U2 на резисторах (делитель напряжения). "
                        + "Ответ дайте в вольтах с точностью до 0,01. "
                        + "На выполнение задания отводится %s.",
                r1, r2, u, timeText);
        return new Task("делитель напряжения", text,
                List.of(new Answer("U1", "В", Circuits.voltageShare(u, r1, r2)),
                        new Answer("U2", "В", Circuits.voltageShare(u, r2, r1))),
                HINT, new DividerScheme(false, u, r1, r2));
    }
}
