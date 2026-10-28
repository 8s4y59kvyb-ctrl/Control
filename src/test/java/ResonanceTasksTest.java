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
 * f0, ρ, Q и I — параллельного (второе задание), целые ρ, Q и ω0, частота задания не резонансная,
 * график при любом исходе.
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
            assertEquals("последовательный контур", task.kind());
            assertTrue(task.text().endsWith("отводится " + Theme.RESONANCE.minutesText(level) + "."));
            assertTrue(task.showsChart(false));                      // график и после неверного ответа
            assertTrue(task.scheme().flowPaths(new Random()).isEmpty());
        }
    }

    @RepeatedTest(50)
    void secondTaskIsParallelContour() {
        for (Level level : Level.values()) {
            TaskGenerator gen = Theme.RESONANCE.newGenerator();
            Random rnd = new Random();
            assertEquals("последовательный контур", gen.next(level, rnd).kind());
            Task task = gen.next(level, rnd);
            assertEquals("последовательный контур", gen.next(level, rnd).kind());   // дальше снова по очереди
            assertEquals("параллельный контур", task.kind());

            ParallelResonanceScheme s = (ParallelResonanceScheme) task.scheme();
            double l = s.l() * 1e-3, c = s.c() * 1e-6;
            double f0 = 1 / (2 * Math.PI * Math.sqrt(l * c)), rho = Math.sqrt(l / c), q = s.r() / rho;
            double bl = 1 / (2 * Math.PI * s.f() * l), bc = 2 * Math.PI * s.f() * c;     // проводимости 1/XL и 1/XC
            double i = s.u() * Math.sqrt(1.0 / (s.r() * s.r()) + (bl - bc) * (bl - bc));

            List<Answer> a = task.answers();
            assertEquals(List.of("f0", "ρ", "Q", "I"), a.stream().map(Answer::symbol).toList());
            boolean ma = i < 1;                                               // меньше 1 А — в миллиамперах
            assertEquals(List.of("Гц", "Ом", "", ma ? "мА" : "А"), a.stream().map(Answer::unit).toList());
            double scale = ma ? 1000 : 1;
            assertEquals(f0, a.get(0).value(), 1e-9 * f0);
            assertEquals(rho, a.get(1).value(), 1e-9 * rho);
            assertEquals(q, a.get(2).value(), 1e-9 * q);
            assertEquals(scale * i, a.get(3).value(), 1e-9 * scale * i);

            assertTrue(List.of(2.0, 4.0, 5.0, 10.0).contains(Math.rint(q)));     // Q = R/ρ — целое
            assertEquals(Math.rint(q), q, 1e-9);
            assertTrue(i > (double) s.u() / s.r(), "вне резонанса ток больше, чем I0 = U/R");
            assertTrue(a.get(3).value() >= 1, "ответ заметно больше допуска 0,01");
            assertTrue(task.text().contains(ma ? "(в миллиамперах)" : "(в амперах)"), task.text());
            assertNotEquals(Math.round(f0), s.f());
            assertTrue(task.text().startsWith("Параллельный колебательный контур"));
            assertTrue(task.text().endsWith("отводится " + Theme.RESONANCE.minutesText(level) + "."));
            assertTrue(task.showsChart(false));
            assertTrue(((ResonanceChart) task.chart()).parallel());
        }
    }

    @Test
    void tickLabels() {
        assertEquals("250", ResonanceChart.tick(250));
        assertEquals("2,5", ResonanceChart.tick(2.5));
        assertEquals("0,25", ResonanceChart.tick(0.25));
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
        TaskGenerator gen = Theme.RESONANCE.newGenerator();
        Random rnd = new Random();
        Task series = gen.next(Level.HARD, rnd), parallel = gen.next(Level.HARD, rnd);
        for (Scheme sc : List.of(series.scheme(), series.chart(), parallel.scheme(), parallel.chart())) {
            Map<String, Object> json = ServerSchemeJson.of(sc, new Random(0));
            assertFalse(((List<?>) json.get("ops")).isEmpty());
            assertTrue(((List<?>) json.get("flow")).isEmpty());
        }
    }
}
