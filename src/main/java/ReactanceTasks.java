import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Тема 4: катушка или конденсатор на переменном токе; первое задание — катушка (дана L),
 * второе — конденсатор (дана C), дальше снова по очереди.
 * Курсант находит сопротивление элемента на 5 частотах (лёгкий уровень) или 6 (сложный):
 * XL = 2πfL, XC = 1/(2πfC). Номиналы и шаг частоты — «круглые», частоты — шаг, 2·шаг, 3·шаг…
 * После верного ответа вместо схемы показывается график X(f) ({@link ReactanceChart}).
 */
final class ReactanceTasks implements TaskGenerator {

    private static final int[] L_MH = {10, 20, 50, 100, 200};      // индуктивность, мГн
    private static final int[] C_UF = {10, 20, 50, 100};           // ёмкость, мкФ
    private static final int[] STEP_HZ = {50, 100, 200, 500};      // шаг частоты, Гц

    private final Function<Level, String> timeText;    // «7 минут» по уровню задания
    private boolean nextInductor = true;  // следующее задание: true — катушка

    ReactanceTasks(Function<Level, String> timeText) {
        this.timeText = timeText;
    }

    @Override
    public Task next(Level level, Random rnd) {
        boolean inductor = nextInductor;
        nextInductor = !inductor;
        int value = inductor ? L_MH[rnd.nextInt(L_MH.length)] : C_UF[rnd.nextInt(C_UF.length)];
        int step = STEP_HZ[rnd.nextInt(STEP_HZ.length)];
        int n = level == Level.HARD ? 6 : 5;

        double[] f = new double[n], x = new double[n];
        List<Answer> answers = new ArrayList<>();
        String symbol = inductor ? "XL" : "XC";
        for (int k = 0; k < n; k++) {
            f[k] = step * (k + 1);
            x[k] = reactance(inductor, value, f[k]);
            answers.add(new Answer(symbol + " (" + (int) f[k] + " Гц)", "Ом", x[k]));
        }
        String freqs = Arrays.stream(f).mapToObj(v -> String.valueOf((int) v))
                .collect(Collectors.joining(", "));

        String text = inductor
                ? String.format(Locale.US,
                        "Катушка индуктивности L = %d мГн подключена к источнику переменного напряжения. "
                                + "Рассчитайте индуктивное сопротивление XL катушки на частотах f = %s Гц. "
                                + "Ответ дайте в омах с точностью до 0,01. На выполнение задания отводится %s.",
                        value, freqs, timeText.apply(level))
                : String.format(Locale.US,
                        "Конденсатор ёмкостью C = %d мкФ подключён к источнику переменного напряжения. "
                                + "Рассчитайте ёмкостное сопротивление XC конденсатора на частотах f = %s Гц. "
                                + "Ответ дайте в омах с точностью до 0,01. На выполнение задания отводится %s.",
                        value, freqs, timeText.apply(level));
        String hint = inductor
                ? "Проверьте расчёт: XL = 2πfL, индуктивность — в генри (1 мГн = 0,001 Гн). Попробуйте ещё раз."
                : "Проверьте расчёт: XC = 1 / (2πfC), ёмкость — в фарадах (1 мкФ = 0,000001 Ф). Попробуйте ещё раз.";

        return new Task(inductor ? "катушка индуктивности" : "конденсатор", text, answers, hint,
                new ReactanceScheme(inductor, value, f), new ReactanceChart(inductor, f, x));
    }

    /** Сопротивление катушки (value в мГн) или конденсатора (value в мкФ) на частоте f, Ом. */
    static double reactance(boolean inductor, double value, double f) {
        return inductor ? 2 * Math.PI * f * value * 1e-3 : 1 / (2 * Math.PI * f * value * 1e-6);
    }
}
