/**
 * Темы контроля. Темы 1 и 2 используют одни и те же схемы; в теме 1 два задания — прямая и обратная задачи
 * (10 минут на лёгком уровне, 15 на сложном), в теме 2 — два. Тема 3 — делители тока и напряжения (две схемы, два ответа,
 * два задания по 5 минут, без выбора сложности). Тема 4 — реактивное сопротивление катушки и конденсатора
 * на нескольких частотах (два задания: 7 минут на лёгком уровне, 10 на сложном). Тема 5 — колебательный контур:
 * последовательный (f0, ρ, Q, UL), затем параллельный (f0, ρ, Q, I), два задания по 15 минут.
 * Время и число заданий здесь — значения по умолчанию; преподаватель может изменить их на странице /admin.
 */
enum Theme {
    OHM("Закон Ома и Кирхгофа в электрических цепях", 10 * 60, 15 * 60, 2, true,
            "прямая и обратная задачи по очереди. В прямой по схеме найти токи ветвей и напряжения участков цепи, "
                    + "в обратной по показаниям амперметра и вольтметра найти сопротивление R1 и ЭДС источника.") {
        @Override
        TaskGenerator newGenerator(ThemeRules rules) {
            return new LadderTasks(false, rules::minutesText);    // ищем токи ветвей и напряжения участков
        }
    },
    EQUIVALENT("Эквивалентные преобразования в электрических цепях", 7 * 60, 7 * 60, 2, true,
            "найти общее сопротивление, эквивалентную ёмкость или индуктивность цепи.") {
        @Override
        TaskGenerator newGenerator(ThemeRules rules) {
            return new LadderTasks(true, rules::minutesText);     // ищем общее сопротивление Z
        }
    },
    DIVIDERS("Делители тока и напряжения", 5 * 60, 5 * 60, 2, false,
            "делитель тока и делитель напряжения по очереди, в каждом найти два значения. "
                    + "Выбора сложности нет: три попытки.") {
        @Override
        TaskGenerator newGenerator(ThemeRules rules) {
            return new DividerTasks(rules::minutesText);
        }
    },
    AC_REACTANCE("Переменный ток. Сопротивление реактивных элементов", 7 * 60, 10 * 60, 2, true,
            "сопротивление катушки и конденсатора по очереди на нескольких частотах "
                    + "(5 частот на лёгком уровне, 6 на сложном). После верного ответа — график "
                    + "зависимости сопротивления от частоты.") {
        @Override
        TaskGenerator newGenerator(ThemeRules rules) {
            return new ReactanceTasks(rules::minutesText);  // XL и XC на 5–6 частотах
        }
    },
    RESONANCE("Переменный ток. Резонанс напряжений и токов", 15 * 60, 15 * 60, 2, true,
            "последовательный и параллельный контур R–L–C по очереди. Найти резонансную частоту, волновое "
                    + "сопротивление, добротность, а также напряжение на катушке (последовательный контур) "
                    + "или ток источника (параллельный). После задания — график зависимости от частоты.") {
        @Override
        TaskGenerator newGenerator(ThemeRules rules) {
            return new ResonanceTasks(rules::minutesText);  // f0, ρ, Q и UL или I; график после задания
        }
    };

    private static final String[] COUNT = {"Одно задание", "Два задания", "Три задания", "Четыре задания",
            "Пять заданий"};

    final String title;
    final int tasks;              // заданий в тесте (по умолчанию)
    final boolean hasLevels;      // можно ли выбрать сложность
    private final ThemeRules defaults;
    private final String about;   // что предстоит сделать, для описания при выборе темы

    Theme(String title, int easySec, int hardSec, int tasks, boolean hasLevels, String about) {
        this.title = title;
        this.tasks = tasks;
        this.hasLevels = hasLevels;
        this.defaults = new ThemeRules(easySec, hardSec, tasks);
        this.about = about;
    }

    /** Новый генератор заданий с временем из rules; создаётся на каждый тест. Только для готовых тем ({@link #ready}). */
    abstract TaskGenerator newGenerator(ThemeRules rules);

    /** Генератор с правилами по умолчанию. */
    TaskGenerator newGenerator() {
        return newGenerator(defaults);
    }

    /** Время и число заданий, заданные в коде. */
    ThemeRules defaults() {
        return defaults;
    }

    /** Есть ли в теме задания; тему «в разработке» видно в списке, но тест по ней не начать. */
    boolean ready() {
        return true;
    }

    /** Уровень, по которому решаются задания: в теме без выбора сложности — всегда три попытки. */
    Level effectiveLevel(Level chosen) {
        return hasLevels ? chosen : Level.HARD;
    }

    /** Время на одно задание уровня level по умолчанию (уровень уже приведён через {@link #effectiveLevel}). */
    int timeLimitSec(Level level) {
        return defaults.timeLimitSec(level);
    }

    String minutesText(Level level) {
        return defaults.minutesText(level);
    }

    String description() {
        return description(defaults);
    }

    /** Описание при выборе темы: число заданий, что сделать и время, например «… На каждое задание — 7 минут». */
    String description(ThemeRules rules) {
        String count = rules.tasks() <= COUNT.length ? COUNT[rules.tasks() - 1] : "Заданий: " + rules.tasks();
        String on = rules.tasks() > 1 ? "На каждое задание — " : "На задание — ";
        String time = !hasLevels || rules.easySec() == rules.hardSec()
                ? rules.minutesText(Level.HARD)
                : rules.minutesText(Level.EASY) + " на лёгком уровне, " + rules.minutesText(Level.HARD) + " на сложном";
        return count + ": " + about + " " + on + time + ".";
    }

    @Override
    public String toString() {
        return (ordinal() + 1) + ". " + title + (ready() ? "" : " (в разработке)");
    }
}
