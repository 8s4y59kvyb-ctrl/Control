import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

/**
 * Тема «Модуляция» (дисциплина «Радиотехнические цепи и сигналы»): задания на сопоставление.
 * Дано временное представление колебания — выбрать его амплитудный спектр, или наоборот (направление случайно).
 * Вариантов всегда шесть — АМК, БМК, ОМК, ФМК, ЧМК, ЛЧМ в случайном порядке; ответ — номер варианта.
 * Виды модуляции идут в случайном порядке без повторов: за пять заданий один из шести видов не встретится.
 * Параметры (периодов несущей на период модуляции, mₐ, mф, mч, Δf ЛЧМ) выбираются случайно один раз на тест.
 */
final class ModulationTasks implements TaskGenerator {

    private static final int[] CARRIER = {10, 12};               // периодов несущей на период модуляции
    private static final double[] M_AM = {0.5, 0.6, 0.7, 0.8};
    private static final int[] M_ANGLE = {3, 4, 5};             // индекс ФМК и ЧМК
    private static final int[] LFM_SWEEP = {6, 8};              // девиация ЛЧМ Δf, в единицах Ω

    private final Function<Level, String> timeText;
    private final List<Modulation> order = new ArrayList<>();   // оставшиеся виды до нового перемешивания
    private double k, mAm, mPm, mFm, mLfm;
    private boolean ready;

    ModulationTasks(Function<Level, String> timeText) {
        this.timeText = timeText;
    }

    @Override
    public Task next(Level level, Random rnd) {
        if (!ready) {
            k = CARRIER[rnd.nextInt(CARRIER.length)];
            mAm = M_AM[rnd.nextInt(M_AM.length)];
            mPm = M_ANGLE[rnd.nextInt(M_ANGLE.length)];
            mFm = M_ANGLE[rnd.nextInt(M_ANGLE.length)];
            mLfm = LFM_SWEEP[rnd.nextInt(LFM_SWEEP.length)];
            ready = true;
        }
        if (order.isEmpty()) {
            order.addAll(Arrays.asList(Modulation.values()));
            Collections.shuffle(order, rnd);
        }
        Modulation type = order.remove(0);
        boolean spectrumGiven = rnd.nextBoolean();

        List<Modulation> variants = new ArrayList<>(Arrays.asList(Modulation.values()));
        Collections.shuffle(variants, rnd);
        List<Scheme> options = new ArrayList<>();
        for (Modulation v : variants) options.add(picture(v, !spectrumGiven, false));

        String text = (spectrumGiven
                ? "Дан амплитудный спектр модулированного колебания. "
                        + "Выберите из вариантов ниже временное представление этого колебания: вверху каждого "
                        + "варианта — модулирующий сигнал u(t), под ним — колебание s(t). "
                : "Дано временное представление модулированного колебания: вверху — модулирующий сигнал u(t), "
                        + "под ним — колебание s(t). Выберите из вариантов ниже его амплитудный спектр. ")
                + "На выполнение задания отводится " + timeText.apply(level) + ".";
        String hint = "Сравните огибающую и частоту заполнения колебания с модулирующим сигналом.";
        return Task.choice(type.abbr, text, hint, picture(type, spectrumGiven, true), options,
                variants.indexOf(type) + 1);
    }

    private ModulationPicture picture(Modulation type, boolean spectrum, boolean large) {
        double m = switch (type) {
            case AM -> mAm;
            case PM -> mPm;
            case FM -> mFm;
            case LFM -> mLfm;
            default -> 0;
        };
        return new ModulationPicture(type, spectrum, k, m, large);
    }
}
