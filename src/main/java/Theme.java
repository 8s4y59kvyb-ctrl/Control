/**
 * Темы контроля. Темы 1 и 2 используют одни и те же схемы; тема 3 — делители тока и напряжения
 * (две схемы, два ответа, 5 минут, без выбора сложности).
 */
enum Theme {
    OHM("Закон Ома и Кирхгофа в электрических цепях", 7 * 60, true) {
        @Override
        TaskGenerator newGenerator() {
            return new LadderTasks(false, minutesText());     // ищем ток источника I
        }
    },
    EQUIVALENT("Эквивалентные преобразования в электрических цепях", 7 * 60, true) {
        @Override
        TaskGenerator newGenerator() {
            return new LadderTasks(true, minutesText());      // ищем общее сопротивление Z
        }
    },
    DIVIDERS("Делители тока и напряжения", 5 * 60, false) {
        @Override
        TaskGenerator newGenerator() {
            return new DividerTasks(minutesText());
        }
    };

    final String title;
    final int timeLimitSec;       // время на одно задание
    final boolean hasLevels;      // можно ли выбрать сложность

    Theme(String title, int timeLimitSec, boolean hasLevels) {
        this.title = title;
        this.timeLimitSec = timeLimitSec;
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
