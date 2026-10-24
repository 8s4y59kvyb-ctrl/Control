/**
 * Темы контроля. Темы 1 и 2 используют одни и те же схемы; в теме 1 одно задание (10 минут),
 * в теме 2 — два. Тема 3 — делители тока и напряжения (две схемы, два ответа, два задания по 5 минут,
 * без выбора сложности).
 */
enum Theme {
    OHM("Закон Ома и Кирхгофа в электрических цепях", 10 * 60, 1, true) {
        @Override
        TaskGenerator newGenerator() {
            return new LadderTasks(false, minutesText());     // ищем токи ветвей и напряжения участков
        }
    },
    EQUIVALENT("Эквивалентные преобразования в электрических цепях", 7 * 60, 2, true) {
        @Override
        TaskGenerator newGenerator() {
            return new LadderTasks(true, minutesText());      // ищем общее сопротивление Z
        }
    },
    DIVIDERS("Делители тока и напряжения", 5 * 60, 2, false) {
        @Override
        TaskGenerator newGenerator() {
            return new DividerTasks(minutesText());
        }
    };

    final String title;
    final int timeLimitSec;       // время на одно задание
    final int tasks;              // заданий в тесте
    final boolean hasLevels;      // можно ли выбрать сложность

    Theme(String title, int timeLimitSec, int tasks, boolean hasLevels) {
        this.title = title;
        this.timeLimitSec = timeLimitSec;
        this.tasks = tasks;
        this.hasLevels = hasLevels;
    }

    /** Новый генератор заданий; создаётся на каждый тест. */
    abstract TaskGenerator newGenerator();

    /** Уровень, по которому решаются задания: в теме без выбора сложности — всегда три попытки. */
    Level effectiveLevel(Level chosen) {
        return hasLevels ? chosen : Level.HARD;
    }

    String minutesText() {
        return (timeLimitSec / 60) + " минут";
    }

    @Override
    public String toString() {
        return (ordinal() + 1) + ". " + title;
    }
}
