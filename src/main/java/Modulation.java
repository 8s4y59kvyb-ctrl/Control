/**
 * Виды модуляции темы «Модуляция»: модуляция одним тоном u(t) = cos Ωt, у ЛЧМ — пилой (частота в каждом
 * периоде растёт линейно, спектр сплошной, почти прямоугольный шириной Δf).
 * Временное представление и амплитудный спектр каждого вида рисует {@link ModulationPicture}.
 */
enum Modulation {
    AM("АМК", "амплитудно-модулированное колебание"),
    DSB("БМК", "балансно-модулированное колебание"),
    SSB("ОМК", "однополосно-модулированное колебание"),
    PM("ФМК", "фазомодулированное колебание"),
    FM("ЧМК", "частотно-модулированное колебание"),
    LFM("ЛЧМ", "колебание с линейной частотной модуляцией");

    final String abbr;
    final String title;

    Modulation(String abbr, String title) {
        this.abbr = abbr;
        this.title = title;
    }

    /** Модулирующий сигнал u(t) от −1 до 1; t — в периодах модуляции. У ЛЧМ — пила, у остальных — cos Ωt. */
    double message(double t) {
        return this == LFM ? 2 * (t - Math.floor(t)) - 1 : Math.cos(2 * Math.PI * t);
    }

    /** Спектр сплошной (ЛЧМ), а не из отдельных линий. */
    boolean continuous() {
        return this == LFM;
    }

    /**
     * Мгновенное значение колебания в момент t (в периодах модулирующего сигнала): k — число периодов
     * несущей на период модуляции, m — индекс модуляции (у АМК — коэффициент mₐ, у ЛЧМ — девиация Δf
     * в единицах Ω, у БМК и ОМК не нужен).
     */
    double signal(double t, double k, double m) {
        double w = 2 * Math.PI * t;                       // Ωt
        return switch (this) {
            case AM -> (1 + m * Math.cos(w)) * Math.cos(k * w);
            case DSB -> Math.cos(w) * Math.cos(k * w);
            case SSB -> Math.cos((k + 1) * w);           // верхняя боковая: одна гармоника ω0 + Ω
            case PM -> Math.cos(k * w + m * Math.cos(w));   // фаза следует за u(t)
            case FM -> Math.cos(k * w + m * Math.sin(w));   // частота следует за u(t)
            case LFM -> {                                // частота за период растёт от k − m/2 до k + m/2
                double tau = t - Math.floor(t);
                yield Math.cos(2 * Math.PI * (k * tau + m * (tau * tau - tau) / 2));
            }
        };
    }

    /**
     * Амплитудный спектр одного периода ЛЧМ на частоте ω0 + xΩ (x — расстройка от несущей в единицах Ω),
     * m — девиация Δf/Ω: численный интеграл ∫ exp(jπm(τ² − τ) − j2πxτ) dτ по периоду.
     */
    static double lfmSpectrum(double x, double m) {
        int n = 400;
        double re = 0, im = 0;
        for (int i = 0; i < n; i++) {
            double tau = (i + 0.5) / n;
            double ph = Math.PI * m * (tau * tau - tau) - 2 * Math.PI * x * tau;
            re += Math.cos(ph);
            im += Math.sin(ph);
        }
        return Math.hypot(re, im) / n;
    }

    /**
     * Амплитуды спектральных линий на частотах ω0 + nΩ, n = −N…N (индекс массива — n + N).
     * АМК: несущая 1 и боковые mₐ/2; БМК: только боковые; ОМК: одна линия ω0 + Ω; ФМК и ЧМК: |Jn(m)|.
     */
    double[] spectrum(int n, double m) {
        double[] a = new double[2 * n + 1];
        switch (this) {
            case AM -> {
                a[n] = 1;
                a[n - 1] = a[n + 1] = m / 2;
            }
            case DSB -> a[n - 1] = a[n + 1] = 0.5;
            case SSB -> a[n + 1] = 1;
            case PM, FM -> {
                for (int i = -n; i <= n; i++) a[i + n] = Math.abs(bessel(Math.abs(i), m));
            }
            case LFM -> throw new IllegalStateException("спектр ЛЧМ сплошной: lfmSpectrum");
        }
        return a;
    }

    /** Функция Бесселя первого рода Jn(x), ряд Тейлора; хватает для x ≤ 10. */
    static double bessel(int n, double x) {
        double term = 1, half = x / 2;
        for (int i = 1; i <= n; i++) term *= half / i;   // (x/2)^n / n!
        double sum = 0;
        for (int k = 0; k < 60 && (k == 0 || Math.abs(term) > 1e-15); k++) {
            sum += term;
            term *= -half * half / ((k + 1.0) * (k + 1 + n));
        }
        return sum;
    }
}
