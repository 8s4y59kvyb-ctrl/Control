import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Function;

/**
 * Тема 5: последовательный колебательный контур R–L–C на переменном напряжении U частотой f.
 * Курсант находит резонансную частоту f0 = 1/(2π√(LC)), волновое сопротивление ρ = √(L/C),
 * добротность Q = ρ/R и напряжение на катушке UL = I·XL на частоте f, где I = U/√(R² + (XL − XC)²).
 * Номиналы подобраны так, что ρ, Q и ω0 = 2πf0 — целые (ω0 — 500, 1000, 2000 или 5000 рад/с, Q — 2, 4, 5 или 10);
 * частота f — круглая (до 10 Гц), не равна резонансной.
 * После задания при любом исходе вместо схемы показывается график UL(f) ({@link ResonanceChart}).
 */
final class ResonanceTasks implements TaskGenerator {

    private static final int[] OMEGA0 = {500, 1000, 2000, 5000};  // резонансная угловая частота, рад/с
    private static final int[] RHO = {10, 20, 50, 100, 200};      // волновое сопротивление, Ом
    private static final int[] Q = {2, 4, 5, 10};                 // добротность: пик на графике хорошо виден
    private static final int R_MIN = 2;                           // Ом
    private static final int[] U_V = {10, 20, 50};                // напряжение источника, В
    private static final double[] F_RATIO = {0.5, 0.7, 0.8, 0.9, 1.1, 1.2, 1.3, 1.5};   // f / f0

    /** Пары {L, мГн; C, мкФ; ρ, Ом}, у которых L = ρ/ω0 и C = 1/(ρω0) — целые. */
    private static final List<int[]> CIRCUITS = new ArrayList<>();

    static {
        for (int w : OMEGA0) {
            for (int rho : RHO) {
                if (rho * 1000 % w == 0 && 1_000_000 % (rho * w) == 0) {
                    CIRCUITS.add(new int[]{rho * 1000 / w, 1_000_000 / (rho * w), rho});
                }
            }
        }
    }

    private final Function<Level, String> timeText;    // «10 минут» по уровню задания

    ResonanceTasks(Function<Level, String> timeText) {
        this.timeText = timeText;
    }

    @Override
    public Task next(Level level, Random rnd) {
        int[] lc = CIRCUITS.get(rnd.nextInt(CIRCUITS.size()));
        int l = lc[0], c = lc[1], r;
        do {
            int q = Q[rnd.nextInt(Q.length)];
            r = lc[2] % q == 0 ? lc[2] / q : 0;
        } while (r < R_MIN);
        int u = U_V[rnd.nextInt(U_V.length)];
        double f0 = resonance(l, c);
        int f = (int) Math.round(f0 * F_RATIO[rnd.nextInt(F_RATIO.length)] / 10) * 10;

        List<Answer> answers = List.of(
                new Answer("f0", "Гц", f0),
                new Answer("ρ", "Ом", impedance(l, c)),
                new Answer("Q", "", quality(l, c, r)),
                new Answer("UL", "В", inductorVoltage(u, r, l, c, f)));

        String text = String.format(Locale.US,
                "Последовательный колебательный контур из резистора R = %d Ом, катушки индуктивности L = %d мГн "
                        + "и конденсатора C = %d мкФ подключён к источнику переменного напряжения U = %d В частотой "
                        + "f = %d Гц. Найдите резонансную частоту f0, волновое сопротивление ρ, добротность Q контура "
                        + "и напряжение UL на катушке при частоте f. Ответ дайте с точностью до 0,01. "
                        + "На выполнение задания отводится %s.",
                r, l, c, u, f, timeText.apply(level));
        String hint = "Проверьте расчёт: f0 = 1 / (2π√(LC)), ρ = √(L / C), Q = ρ / R, UL = I·XL, "
                + "где I = U / √(R² + (XL − XC)²); L — в генри, C — в фарадах. Попробуйте ещё раз.";

        return new Task("колебательный контур", text, answers, hint,
                new ResonanceScheme(r, l, c, u, f), new ResonanceChart(r, l, c, u, f), true);
    }

    /** Резонансная частота, Гц (l в мГн, c в мкФ). */
    static double resonance(double l, double c) {
        return 1 / (2 * Math.PI * Math.sqrt(l * 1e-3 * c * 1e-6));
    }

    /** Волновое сопротивление ρ = √(L/C), Ом. */
    static double impedance(double l, double c) {
        return Math.sqrt(l * 1e-3 / (c * 1e-6));
    }

    static double quality(double l, double c, double r) {
        return impedance(l, c) / r;
    }

    /** Напряжение на катушке на частоте f, В: UL = U·XL / √(R² + (XL − XC)²). */
    static double inductorVoltage(double u, double r, double l, double c, double f) {
        double xl = ReactanceTasks.reactance(true, l, f), xc = ReactanceTasks.reactance(false, c, f);
        return u * xl / Math.hypot(r, xl - xc);
    }
}
