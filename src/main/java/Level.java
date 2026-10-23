/**
 * Уровни сложности:
 *   Лёгкий  — цепь E, R1 и R2 || R3; 1 попытка (верно: 5, неверно: 2);
 *   Сложный — к R2 и R3 добавлен параллельный резистор R4 (по диагонали);
 *             3 попытки (5 / 4 / 3, иначе 2).
 */
enum Level {
    EASY("Лёгкий", 1),
    HARD("Сложный", 3);

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
        return this == EASY
                ? "Одна попытка:\nверно — 5\nневерно — 2\nвремя вышло — 2"
                : "1-я попытка — 5\n2-я попытка — 4\n3-я попытка — 3\nнет верного ответа — 2\nвремя вышло — 2";
    }
}
