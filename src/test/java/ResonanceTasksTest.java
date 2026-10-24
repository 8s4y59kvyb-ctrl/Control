import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверяет генерацию задач темы 5: ответы f0, ρ, Q и UL по формулам последовательного контура,
 * целые ρ, Q и ω0, частота задания не резонансная, график UL(f) при любом исходе.
 */
class ResonanceTasksTest {

    @RepeatedTest(50)
    void answersMatchFormulas() {
        for (Level level : Level.values()) {
            Task task = Theme.RESONANCE.newGenerator().next(level, new Random());
            ResonanceScheme s = (ResonanceScheme) task.scheme();
            double l = s.l() * 1e-3, c = s.c() * 1e-6;
            double f0 = 1 / (2 * Math.PI * Math.sqrt(l * c)), rho = Math.sqrt(l / c), q = rho / s.r();
            double xl = 2 * Math.PI * s.f() * l, xc = 1 / (2 * Math.PI * s.f() * c);
            double ul = s.u() / Math.sqrt(s.r() * s.r() + (xl - xc) * (xl - xc)) * xl;

            List<Answer> a = task.answers();
            assertEquals(List.of("f0", "ρ", "Q", "UL"), a.stream().map(Answer::symbol).toList());
            assertEquals(List.of("Гц", "Ом", "", "В"), a.stream().map(Answer::unit).toList());
            assertEquals(f0, a.get(0).value(), 1e-9 * f0);
            assertEquals(rho, a.get(1).value(), 1e-9 * rho);
            assertEquals(q, a.get(2).value(), 1e-9 * q);
            assertEquals(ul, a.get(3).value(), 1e-9 * ul);

            assertTrue(List.of(2.0, 4.0, 5.0, 10.0).contains(Math.rint(q)));    // красивые числа
            assertEquals(Math.rint(q), q, 1e-9);
            assertEquals(Math.rint(rho), rho, 1e-9);
            double w0 = 2 * Math.PI * f0;
            assertTrue(List.of(500.0, 1000.0, 2000.0, 5000.0).contains(Math.rint(w0)));
            assertEquals(Math.rint(w0), w0, 1e-6);
            assertTrue(s.r() >= 2);
            assertEquals(0, s.f() % 10);
            assertNotEquals(Math.round(f0), s.f());
            assertEquals("колебательный контур", task.kind());
            assertTrue(task.text().endsWith("отводится " + Theme.RESONANCE.minutesText(level) + "."));
            assertTrue(task.showsChart(false));                      // график и после неверного ответа
            assertTrue(task.scheme().flowPaths(new Random()).isEmpty());
        }
    }

    @Test
    void unitlessAnswerText() {
        assertEquals("Q = 3,16", new Answer("Q", "", 3.162).text());
    }

    @Test
    void reactanceChartOnlyAfterCorrectAnswer() {
        Task task = Theme.AC_REACTANCE.newGenerator().next(Level.EASY, new Random(1));
        assertTrue(task.showsChart(true));
        assertFalse(task.showsChart(false));
    }

    @Test
    void niceStep() {
        assertEquals(250, ResonanceChart.niceStep(201), 1e-9);
        assertEquals(2, ResonanceChart.niceStep(1.3), 1e-9);
        assertEquals(10, ResonanceChart.niceStep(10), 1e-9);
        assertEquals(50, ResonanceChart.niceStep(26), 1e-9);
    }

    @RepeatedTest(20)
    void schemeAndChartDraw() {
        Task task = Theme.RESONANCE.newGenerator().next(Level.HARD, new Random());
        for (Scheme sc : List.of(task.scheme(), task.chart())) {
            Map<String, Object> json = ServerSchemeJson.of(sc, new Random(0));
            assertFalse(((List<?>) json.get("ops")).isEmpty());
            assertTrue(((List<?>) json.get("flow")).isEmpty());
        }
    }
}
