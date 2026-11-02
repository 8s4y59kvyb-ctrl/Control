import java.util.List;
import java.util.Locale;

/**
 * Итог теста: среднее за задания; если хотя бы за одно стоит 2, итоговая оценка 2.
 * Ветка experiment: двоек нет — за неверный ответ и вышедшее время ставится {@link #MIN}.
 */
final class Grading {

    /** Оценка за нерешённое задание (неверный ответ или время вышло). */
    static final int MIN = 3;

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

    /** Целая оценка без дробной части, иначе с одним знаком после запятой: «5», «4,5». */
    static String format(double g) {
        return g == Math.rint(g)
                ? String.valueOf((int) g)
                : String.format(Locale.US, "%.1f", g).replace('.', ',');
    }
}
