import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверяет генерацию задач темы 4: сначала катушка, затем конденсатор, число частот по уровню,
 * частоты с постоянным шагом, ответы по формулам XL = 2πfL и XC = 1/(2πfC), график после ответа.
 */
class ReactanceTasksTest {

    private static final double EPS = 1e-9;

    @RepeatedTest(20)
    void elementsAlternate() {
        TaskGenerator gen = Theme.AC_REACTANCE.newGenerator();
        Random rnd = new Random();
        boolean prev = ((ReactanceScheme) gen.next(Level.EASY, rnd).scheme()).inductor();
        for (int i = 0; i < 5; i++) {
            boolean now = ((ReactanceScheme) gen.next(Level.EASY, rnd).scheme()).inductor();
            assertNotEquals(prev, now);
            prev = now;
        }
    }

    @RepeatedTest(20)
    void firstInductorThenCapacitor() {
        for (Level level : Level.values()) {
            TaskGenerator gen = Theme.AC_REACTANCE.newGenerator();
            Random rnd = new Random();
            assertTrue(((ReactanceScheme) gen.next(level, rnd).scheme()).inductor());     // задание 1 — индуктивность
            assertFalse(((ReactanceScheme) gen.next(level, rnd).scheme()).inductor());    // задание 2 — ёмкость
        }
    }

    @RepeatedTest(50)
    void answersMatchReactanceFormulas() {
        for (Level level : Level.values()) {
            TaskGenerator gen = Theme.AC_REACTANCE.newGenerator();
            Random rnd = new Random();
            for (int i = 0; i < 2; i++) {                  // катушка и конденсатор
                Task task = gen.next(level, rnd);
                ReactanceScheme s = (ReactanceScheme) task.scheme();
                double[] f = s.freqs();
                int n = level == Level.HARD ? 6 : 5;
                assertEquals(n, task.answers().size());
                assertEquals(n, f.length);
                String sym = s.inductor() ? "XL" : "XC";
                assertEquals(s.inductor() ? "катушка индуктивности" : "конденсатор", task.kind());
                for (int k = 0; k < n; k++) {
                    assertEquals(f[0] * (k + 1), f[k], EPS);  // частоты — шаг, 2·шаг, 3·шаг…
                    Answer a = task.answers().get(k);
                    assertEquals(sym + " (" + (int) f[k] + " Гц)", a.symbol());
                    assertEquals("Ом", a.unit());
                    double expected = s.inductor()
                            ? 2 * Math.PI * f[k] * s.value() / 1000
                            : 1 / (2 * Math.PI * f[k] * s.value() / 1e6);
                    assertEquals(expected, a.value(), 1e-9 * expected);
                }
                assertTrue(task.text().endsWith("отводится " + Theme.AC_REACTANCE.minutesText(level) + "."));

                ReactanceChart chart = (ReactanceChart) task.chart();
                assertNotNull(chart);
                assertEquals(s.inductor(), chart.inductor());
                assertTrue(task.scheme().flowPaths(rnd).isEmpty());   // переменный ток — без анимации
                assertTrue(chart.flowPaths(rnd).isEmpty());
            }
        }
    }

    @Test
    void piApproximationIsAccepted() {
        // курсант считает с π ≈ 3,14 — расхождение меньше допуска 2 %
        Task task = Theme.AC_REACTANCE.newGenerator().next(Level.HARD, new Random(3));
        ReactanceScheme s = (ReactanceScheme) task.scheme();
        double[] values = new double[s.freqs().length];
        for (int k = 0; k < values.length; k++) {
            double w = 2 * 3.14 * s.freqs()[k];
            values[k] = s.inductor() ? w * s.value() / 1000 : 1 / (w * s.value() / 1e6);
        }
        assertEquals(values.length, task.countCorrect(values));
    }

    @Test
    void niceCeil() {
        assertEquals(50, ReactanceChart.niceCeil(31.4), EPS);
        assertEquals(100, ReactanceChart.niceCeil(100), EPS);
        assertEquals(1, ReactanceChart.niceCeil(0.53), EPS);
        assertEquals(5000, ReactanceChart.niceCeil(3770), EPS);
        assertEquals(2, ReactanceChart.niceCeil(1.2), EPS);
    }

    @RepeatedTest(20)
    void schemeAndChartDraw() {
        TaskGenerator gen = Theme.AC_REACTANCE.newGenerator();
        Random rnd = new Random();
        for (int i = 0; i < 2; i++) {
            Task task = gen.next(Level.HARD, rnd);
            for (Scheme sc : List.of(task.scheme(), task.chart())) {
                Map<String, Object> json = ServerSchemeJson.of(sc, new Random(0));
                assertFalse(((List<?>) json.get("ops")).isEmpty());
                assertTrue(((List<?>) json.get("flow")).isEmpty());
            }
        }
    }
}
