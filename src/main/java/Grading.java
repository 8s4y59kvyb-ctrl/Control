import java.util.List;
import java.util.Locale;

/** Итог теста: среднее за задания; если хотя бы за одно стоит 2, итоговая оценка 2. */
final class Grading {

    private Grading() {
    }

    static boolean hasFail(List<Integer> grades) {
        return grades.contains(2);
    }

    static double average(List<Integer> grades) {
        return grades.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    static double finalGrade(List<Integer> grades) {
        return hasFail(grades) ? 2 : average(grades);
    }

    /**
     * Оценка за тест целиком по числу ошибок (тема «Модуляция»): за каждую ошибку минус балл от пятёрки,
     * но не ниже 2. Из 5 заданий: 5 верных — 5, 4 — 4, 3 — 3, меньше — 2. Задание с оценкой 2 — ошибка.
     */
    static double byMistakes(List<Integer> grades) {
        long mistakes = grades.stream().filter(g -> g < 3).count();
        return Math.max(2, 5 - mistakes);
    }

    /** Итог с прибавкой одного балла, но не выше 5. */
    static double withBonus(double grade) {
        return Math.min(5, grade + 1);
    }

    /** Целая оценка без дробной части, иначе с одним знаком после запятой: «5», «4,5». */
    static String format(double g) {
        return g == Math.rint(g)
                ? String.valueOf((int) g)
                : String.format(Locale.US, "%.1f", g).replace('.', ',');
    }
}
