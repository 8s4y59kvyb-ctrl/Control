import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверяет генерацию задач темы «Делители»: чередование схем делителя тока и напряжения,
 * случайный выбор первой схемы, соответствие ответов формулам делителей, диапазоны номиналов,
 * а также подсчёт правильных ответов и текст правильного решения.
 */
class DividerTasksTest {

    private static final double EPS = 1e-9;

    @RepeatedTest(20)
    void schemesAlternate() {
        TaskGenerator gen = Theme.DIVIDERS.newGenerator();
        Random rnd = new Random();
        boolean prev = ((DividerScheme) gen.next(Level.HARD, rnd).scheme()).currentMode();
        for (int i = 0; i < 5; i++) {
            boolean now = ((DividerScheme) gen.next(Level.HARD, rnd).scheme()).currentMode();
            assertNotEquals(prev, now);
            prev = now;
        }
    }

    @Test
    void firstSchemeIsRandom() {
        // один Random на все тесты: у соседних seed первый nextBoolean() почти всегда одинаков
        Random rnd = new Random(7);
        boolean sawCurrent = false, sawVoltage = false;
        for (int i = 0; i < 50; i++) {
            boolean mode = ((DividerScheme) Theme.DIVIDERS.newGenerator()
                    .next(Level.HARD, rnd).scheme()).currentMode();
            if (mode) sawCurrent = true; else sawVoltage = true;
        }
        assertTrue(sawCurrent && sawVoltage);
    }

    @RepeatedTest(50)
    void answersMatchDividerFormulas() {
        TaskGenerator gen = Theme.DIVIDERS.newGenerator();
        Random rnd = new Random();
        for (int i = 0; i < 2; i++) {                     // оба вида схемы
            Task task = gen.next(Level.HARD, rnd);
            DividerScheme s = (DividerScheme) task.scheme();
            double x1 = task.answers().get(0).value();
            double x2 = task.answers().get(1).value();
            double r1 = s.r1(), r2 = s.r2();

            assertEquals(2, task.answers().size());
            assertEquals(s.input(), x1 + x2, EPS);        // токи (напряжения) в сумме дают входной
            if (s.currentMode()) {
                assertEquals("I1", task.answers().get(0).symbol());
                assertEquals("А", task.answers().get(0).unit());
                assertEquals("делитель тока", task.kind());
                assertEquals(s.input() * r2 / (r1 + r2), x1, EPS);
                assertEquals(x1 * r1, x2 * r2, EPS);      // напряжения параллельных ветвей равны
                assertInRange(s.input(), 1, 20);
            } else {
                assertEquals("U2", task.answers().get(1).symbol());
                assertEquals("В", task.answers().get(1).unit());
                assertEquals("делитель напряжения", task.kind());
                assertEquals(s.input() * r1 / (r1 + r2), x1, EPS);
                assertEquals(x1 / r1, x2 / r2, EPS);      // ток в последовательной цепи один
                assertInRange(s.input(), 10, 60);
            }
            assertInRange(r1, 10, 100);
            assertInRange(r2, 10, 100);
            assertTrue(task.text().endsWith("отводится 5 минут."));
        }
    }

    @Test
    void countCorrectAndCorrectText() {
        Task task = new Task("делитель тока", "", java.util.List.of(
                new Answer("I1", "А", 6), new Answer("I2", "А", 3)), "", null);
        assertEquals(2, task.countCorrect(new double[]{6, 3}));
        assertEquals(1, task.countCorrect(new double[]{6, 4}));
        assertEquals(0, task.countCorrect(new double[]{3, 6}));
        assertEquals("I1 = 6,00 А, I2 = 3,00 А", task.correctText());
    }

    private static void assertInRange(double v, int min, int max) {
        assertTrue(v >= min && v <= max, v + " вне диапазона " + min + "…" + max);
        assertEquals(Math.rint(v), v, "номинал должен быть целым");
    }
}
