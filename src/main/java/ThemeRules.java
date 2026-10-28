/**
 * Правила темы, которые преподаватель может менять: время на одно задание по уровням и число заданий в тесте.
 * Значения по умолчанию задаёт {@link Theme#defaults()}, изменённые хранятся в базе ({@link ServerSettings}).
 */
record ThemeRules(int easySec, int hardSec, int tasks) {

    /** Время на одно задание уровня level (уже приведённого через {@link Theme#effectiveLevel}). */
    int timeLimitSec(Level level) {
        return level == Level.HARD ? hardSec : easySec;
    }

    /** «1 минута», «3 минуты», «10 минут» — для текста задания и описания темы. */
    String minutesText(Level level) {
        return minutes(timeLimitSec(level) / 60);
    }

    /** Число с существительным «минута» в нужной форме. */
    static String minutes(int n) {
        int last2 = n % 100, last = n % 10;
        String word = last2 >= 11 && last2 <= 14 ? "минут"
                : last == 1 ? "минута"
                : last >= 2 && last <= 4 ? "минуты"
                : "минут";
        return n + " " + word;
    }
}
