/**
 * Уровни сложности:
 *   Лёгкий  — цепь E, R1 и R2 || R3;
 *   Сложный — к R2 и R3 добавлен параллельный резистор R4 (по диагонали).
 * Ветка experiment: на обоих уровнях одна попытка (верно: 5, иначе {@link Grading#MIN}).
 */
enum Level {
    EASY("Лёгкий", 1),
    HARD("Сложный", 1);

    final String title;
    final int attempts;

    Level(String title, int attempts) {
        this.title = title;
        this.attempts = attempts;
    }

    /** Оценка за задание, решённое с попытки attempt (считая с 1): лёгкий — 5, сложный — 5 / 4 / 3. */
    int gradeForAttempt(int attempt) {
        return this == EASY ? 5 : 6 - attempt;
    }

    /** Правила выставления оценки для боковой панели. */
    String rules() {
        return "Одна попытка:\nверно — 5\nиначе — " + Grading.MIN;
    }
}
