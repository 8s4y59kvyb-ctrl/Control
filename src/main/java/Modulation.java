/**
 * Виды модуляции темы «Модуляция»: модуляция одним тоном u(t) = cos Ωt, у ЛЧМ — пилой (частота в каждом
 * периоде растёт линейно, спектр сплошной, почти прямоугольный шириной Δf). Манипуляция (АмнК, ФмнК, ЧмнК) —
 * периодической последовательностью импульсов (импульс — в начале периода) со скважностью {@link #DUTY},
 * у ЧмнК — {@link #FSK_DUTY};
 * колебание — периодические радиоимпульсы, спектр линейчатый (линии через Ω, огибающая sin x / x).
 * Временное представление и амплитудный спектр каждого вида рисует {@link ModulationPicture}.
 */
enum Modulation {
    AM("АМК", "амплитудно-модулированное колебание"),
    DSB("БМК", "балансно-модулированное колебание"),
    SSB("ОМК", "однополосно-модулированное колебание"),
    PM("ФМК", "фазомодулированное колебание"),
    FM("ЧМК", "частотно-модулированное колебание"),
    LFM("ЛЧМ", "колебание с линейной частотной модуляцией"),
    ASK("АмнК", "амплитудно-манипулированное колебание"),
    PSK("ФмнК", "фазоманипулированное колебание"),
    FSK("ЧмнК", "частотно-манипулированное колебание");

    /** Скважность импульсов манипуляции: период / длительность импульса. */
    static final int DUTY = 3;
    /** Скважность у ЧмнК: импульс и пауза равны (меандр). */
    static final int FSK_DUTY = 2;
    /**
     * Сдвиг частот ЧмнК от несущей, в единицах Ω: f₁ = f₀ + 3Ω на импульсе, f₂ = f₀ − 3Ω на паузе.
     * Целое, и сдвиг × (2/скважность − 1) — тоже целое: тогда фаза непрерывна, а колебание периодично
     * (спектр линейчатый). При скважности 2 годится любое целое.
     */
    static final int FSK_SHIFT = 3;

    final String abbr;
    final String title;

    Modulation(String abbr, String title) {
        this.abbr = abbr;
        this.title = title;
    }

    /**
     * Модулирующий сигнал u(t) от −1 до 1; t — в периодах модуляции. У ЛЧМ — пила, у манипуляции —
     * импульсы (1 на импульсе, −1 на паузе), у остальных — cos Ωt.
     */
    double message(double t) {
        if (keyed()) return pulse(t) ? 1 : -1;
        return this == LFM ? 2 * (t - Math.floor(t)) - 1 : Math.cos(2 * Math.PI * t);
    }

    /** Манипуляция последовательностью импульсов (АмнК, ФмнК, ЧмнК). */
    boolean keyed() {
        return this == ASK || this == PSK || this == FSK;
    }

    /** Спектр сплошной (ЛЧМ), а не из отдельных линий. */
    boolean continuous() {
        return this == LFM;
    }

    /** Скважность импульсов манипуляции этого вида. */
    int duty() {
        return this == FSK ? FSK_DUTY : DUTY;
    }

    /** Идёт ли импульс в момент t (в периодах модуляции): первая 1/скважность часть каждого периода. */
    boolean pulse(double t) {
        return (t - Math.floor(t)) * duty() < 1;
    }

    /** ∫u dt от 0 до t для импульсов u = ±1: нужен для непрерывной фазы ЧмнК. */
    private double messageIntegral(double t) {
        double whole = Math.floor(t), tau = t - whole, on = 1.0 / duty();
        double inPeriod = tau < on ? tau : on - (tau - on);
        return whole * (2 * on - 1) + inPeriod;
    }

    /** Комплексная огибающая манипуляции: s(t) = Re[A(t)·exp(jω₀t)]; возвращает {Re A, Im A}. */
    private double[] envelope(double t) {
        return switch (this) {
            case ASK -> new double[] {pulse(t) ? 1 : 0, 0};
            case PSK -> new double[] {message(t), 0};
            case FSK -> {
                double ph = 2 * Math.PI * FSK_SHIFT * messageIntegral(t);
                yield new double[] {Math.cos(ph), Math.sin(ph)};
            }
            default -> throw new IllegalStateException(name());
        };
    }

    /**
     * Мгновенное значение колебания в момент t (в периодах модулирующего сигнала): k — число периодов
     * несущей на период модуляции, m — индекс модуляции (у АМК — коэффициент mₐ, у ЛЧМ — девиация Δf
     * в единицах Ω, у БМК, ОМК и манипуляции не нужен). У ЧмнК частоты k ± {@link #FSK_SHIFT}, фаза непрерывна.
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
            case ASK -> pulse(t) ? Math.cos(k * w) : 0;                 // радиоимпульсы и паузы
            case PSK -> message(t) * Math.cos(k * w);                   // скачок фазы на π в начале и конце импульса
            case FSK -> Math.cos(2 * Math.PI * (k * t + FSK_SHIFT * messageIntegral(t)));
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
     * АМК: несущая 1 и боковые mₐ/2; БМК: только боковые; ОМК: одна линия ω0 + Ω; ФМК и ЧМК: |Jn(m)|;
     * манипуляция: |Aₙ| — коэффициенты ряда Фурье комплексной огибающей за период (численно).
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
            case ASK, PSK, FSK -> {
                int steps = 600 * duty();                     // граница импульса попадает между точками
                for (int i = -n; i <= n; i++) {
                    double re = 0, im = 0;
                    for (int j = 0; j < steps; j++) {
                        double t = (j + 0.5) / steps, ph = -2 * Math.PI * i * t;
                        double[] e = envelope(t);
                        re += e[0] * Math.cos(ph) - e[1] * Math.sin(ph);
                        im += e[0] * Math.sin(ph) + e[1] * Math.cos(ph);
                    }
                    a[i + n] = Math.hypot(re, im) / steps;
                }
            }
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
