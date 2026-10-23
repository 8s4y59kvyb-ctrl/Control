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

    /** Целая оценка без дробной части, иначе с одним знаком после запятой: «5», «4,5». */
    static String format(double g) {
        return g == Math.rint(g)
                ? String.valueOf((int) g)
                : String.format(Locale.US, "%.1f", g).replace('.', ',');
    }
}
