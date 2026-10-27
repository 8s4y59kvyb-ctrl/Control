/**
 * Темы контроля. Темы 1 и 2 используют одни и те же схемы; в теме 1 одно задание (10 минут на лёгком
 * уровне, 15 на сложном), в теме 2 — два. Тема 3 — делители тока и напряжения (две схемы, два ответа,
 * два задания по 5 минут, без выбора сложности). Тема 4 — реактивное сопротивление катушки и конденсатора
 * на нескольких частотах (два задания: 7 минут на лёгком уровне, 10 на сложном). Тема 5 — последовательный
 * колебательный контур: f0, ρ, Q и UL (два задания по 15 минут).
 */
enum Theme {
    OHM("Закон Ома и Кирхгофа в электрических цепях", 10 * 60, 15 * 60, 1, true,
            "Одно задание: по схеме найти токи ветвей и напряжения участков цепи.") {
        @Override
        TaskGenerator newGenerator() {
            return new LadderTasks(false, this::minutesText);     // ищем токи ветвей и напряжения участков
        }
    },
    EQUIVALENT("Эквивалентные преобразования в электрических цепях", 7 * 60, 7 * 60, 2, true,
            "Два задания: найти общее сопротивление, эквивалентную ёмкость или индуктивность цепи.") {
        @Override
        TaskGenerator newGenerator() {
            return new LadderTasks(true, this::minutesText);      // ищем общее сопротивление Z
        }
    },
    DIVIDERS("Делители тока и напряжения", 5 * 60, 5 * 60, 2, false,
            "Два задания: делитель тока и делитель напряжения, в каждом найти два значения. "
                    + "Выбора сложности нет: три попытки.") {
        @Override
        TaskGenerator newGenerator() {
            return new DividerTasks(this::minutesText);
        }
    },
    AC_REACTANCE("Переменный ток. Сопротивление реактивных элементов", 7 * 60, 10 * 60, 2, true,
            "Два задания: сопротивление катушки, затем конденсатора на нескольких частотах "
                    + "(5 частот на лёгком уровне, 6 на сложном). После верного ответа — график "
                    + "зависимости сопротивления от частоты.") {
        @Override
        TaskGenerator newGenerator() {
            return new ReactanceTasks(this::minutesText);   // XL и XC на 5–6 частотах
        }
    },
    RESONANCE("Переменный ток. Последовательный колебательный контур", 15 * 60, 15 * 60, 2, true,
            "Два задания: в последовательном контуре R–L–C найти резонансную частоту, волновое сопротивление, "
                    + "добротность и напряжение на катушке. После задания — график напряжения на катушке "
                    + "от частоты.") {
        @Override
        TaskGenerator newGenerator() {
            return new ResonanceTasks(this::minutesText);   // f0, ρ, Q и UL; график UL(f) после задания
        }
    };

    final String title;
    private final int easySec;    // время на одно задание лёгкого уровня
    private final int hardSec;    // … сложного уровня
    final int tasks;              // заданий в тесте
    final boolean hasLevels;      // можно ли выбрать сложность
    private final String about;   // что предстоит сделать, для описания при выборе темы

    Theme(String title, int easySec, int hardSec, int tasks, boolean hasLevels, String about) {
        this.title = title;
        this.easySec = easySec;
        this.hardSec = hardSec;
        this.tasks = tasks;
        this.hasLevels = hasLevels;
        this.about = about;
    }

    /** Новый генератор заданий; создаётся на каждый тест. Только для готовых тем ({@link #ready}). */
    abstract TaskGenerator newGenerator();

    /** Есть ли в теме задания; тему «в разработке» видно в списке, но тест по ней не начать. */
    boolean ready() {
        return true;
    }

    /** Уровень, по которому решаются задания: в теме без выбора сложности — всегда три попытки. */
    Level effectiveLevel(Level chosen) {
        return hasLevels ? chosen : Level.HARD;
    }

    /** Время на одно задание уровня level (уже приведённого через {@link #effectiveLevel}). */
    int timeLimitSec(Level level) {
        return level == Level.HARD ? hardSec : easySec;
    }

    String minutesText(Level level) {
        return (timeLimitSec(level) / 60) + " минут";
    }

    /** Описание при выборе темы: задания и время, например «… На каждое задание — 7 минут». */
    String description() {
        String on = tasks > 1 ? "На каждое задание — " : "На задание — ";
        String time = !hasLevels || easySec == hardSec
                ? minutesText(Level.HARD)
                : minutesText(Level.EASY) + " на лёгком уровне, " + minutesText(Level.HARD) + " на сложном";
        return about + " " + on + time + ".";
    }

    @Override
    public String toString() {
        return (ordinal() + 1) + ". " + title + (ready() ? "" : " (в разработке)");
    }
}
