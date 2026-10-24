/**
 * Темы контроля. Темы 1 и 2 используют одни и те же схемы; в теме 1 одно задание (10 минут на лёгком
 * уровне, 15 на сложном), в теме 2 — два. Тема 3 — делители тока и напряжения (две схемы, два ответа,
 * два задания по 5 минут, без выбора сложности).
 */
enum Theme {
    OHM("Закон Ома и Кирхгофа в электрических цепях", 10 * 60, 15 * 60, 1, true) {
        @Override
        TaskGenerator newGenerator() {
            return new LadderTasks(false, this::minutesText);     // ищем токи ветвей и напряжения участков
        }
    },
    EQUIVALENT("Эквивалентные преобразования в электрических цепях", 7 * 60, 7 * 60, 2, true) {
        @Override
        TaskGenerator newGenerator() {
            return new LadderTasks(true, this::minutesText);      // ищем общее сопротивление Z
        }
    },
    DIVIDERS("Делители тока и напряжения", 5 * 60, 5 * 60, 2, false) {
        @Override
        TaskGenerator newGenerator() {
            return new DividerTasks(this::minutesText);
        }
    };

    final String title;
    private final int easySec;    // время на одно задание лёгкого уровня
    private final int hardSec;    // … сложного уровня
    final int tasks;              // заданий в тесте
    final boolean hasLevels;      // можно ли выбрать сложность

    Theme(String title, int easySec, int hardSec, int tasks, boolean hasLevels) {
        this.title = title;
        this.easySec = easySec;
        this.hardSec = hardSec;
        this.tasks = tasks;
        this.hasLevels = hasLevels;
    }

    /** Новый генератор заданий; создаётся на каждый тест. */
    abstract TaskGenerator newGenerator();

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

    @Override
    public String toString() {
        return (ordinal() + 1) + ". " + title;
    }
}
