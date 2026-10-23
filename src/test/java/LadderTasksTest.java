import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверяет генерацию задач на лестничной схеме (темы «Закон Ома» и «Эквивалентное сопротивление»):
 * правильность ответа, наличие R4 на сложном уровне, диапазоны и целочисленность номиналов,
 * тексты подсказок и условия, а также воспроизводимость задачи при одинаковом seed.
 */
class LadderTasksTest {

    private static final double EPS = 1e-9;

    @RepeatedTest(50)
    void ohmEasyAnswerIsSourceCurrent() {
        Task task = Theme.OHM.newGenerator().next(Level.EASY, new Random());
        LadderScheme s = (LadderScheme) task.scheme();
        double expected = s.e() / (s.r1() + s.r2() * s.r3() / (s.r2() + s.r3()));

        assertEquals(1, task.answers().size());
        assertEquals("I", task.answers().get(0).symbol());
        assertEquals("А", task.answers().get(0).unit());
        assertEquals(expected, task.answers().get(0).value(), EPS);
        assertFalse(s.withR4());
        assertFalse(task.text().contains("R4"));
        assertTrue(task.text().contains("E = "));
    }

    @RepeatedTest(50)
    void ohmHardAddsR4InParallel() {
        Task task = Theme.OHM.newGenerator().next(Level.HARD, new Random());
        LadderScheme s = (LadderScheme) task.scheme();
        double parallel = 1 / (1 / s.r2() + 1 / s.r3() + 1 / s.r4());

        assertTrue(s.withR4());
        assertTrue(task.text().contains("R4 = " + (int) s.r4() + " Ом"));
        assertEquals(s.e() / (s.r1() + parallel), task.answers().get(0).value(), EPS);
    }

    @RepeatedTest(50)
    void equivalentAnswerIsTotalResistanceWithoutEmf() {
        Task task = Theme.EQUIVALENT.newGenerator().next(Level.HARD, new Random());
        LadderScheme s = (LadderScheme) task.scheme();
        double parallel = 1 / (1 / s.r2() + 1 / s.r3() + 1 / s.r4());

        assertEquals("Z", task.answers().get(0).symbol());
        assertEquals("Ом", task.answers().get(0).unit());
        assertEquals(s.r1() + parallel, task.answers().get(0).value(), EPS);
        assertTrue(s.findZ());
        assertFalse(task.text().contains("E = "));          // в теме 2 ЭДС не нужна
    }

    @RepeatedTest(50)
    void easyLevelGivesRoundNumbers() {
        Random rnd = new Random();
        for (Theme theme : new Theme[]{Theme.OHM, Theme.EQUIVALENT}) {
            Task task = theme.newGenerator().next(Level.EASY, rnd);
            LadderScheme s = (LadderScheme) task.scheme();
            double parallel = s.r2() * s.r3() / (s.r2() + s.r3());
            double answer = task.answers().get(0).value();

            assertEquals(Math.rint(parallel), parallel, EPS, "R2 || R3 должно быть целым");
            assertEquals(Math.rint(answer * 10), answer * 10, EPS, "ответ — не больше одного знака после запятой");
            assertInRange(s.e(), 10, 60);
            assertInRange(s.r1(), 10, 60);
            assertInRange(s.r2(), 10, 100);
            assertInRange(s.r3(), 10, 100);
        }
    }

    @RepeatedTest(50)
    void valuesAreIntegersInRange() {
        LadderScheme s = (LadderScheme) Theme.OHM.newGenerator().next(Level.HARD, new Random()).scheme();
        assertInRange(s.e(), 10, 60);
        assertInRange(s.r1(), 10, 60);
        assertInRange(s.r2(), 10, 100);
        assertInRange(s.r3(), 10, 100);
        assertInRange(s.r4(), 10, 100);
    }

    @Test
    void kindHintAndTimeDependOnThemeAndLevel() {
        Task easy = Theme.OHM.newGenerator().next(Level.EASY, new Random(1));
        Task hard = Theme.EQUIVALENT.newGenerator().next(Level.HARD, new Random(1));

        assertEquals("лёгкий уровень", easy.kind());
        assertEquals("сложный уровень", hard.kind());
        assertTrue(easy.hint().contains("закону Ома"));
        assertTrue(hard.hint().contains("эквивалентного сопротивления"));
        assertTrue(easy.text().endsWith("отводится 7 минут."));
    }

    @Test
    void sameSeedGivesSameTask() {
        Task a = Theme.OHM.newGenerator().next(Level.HARD, new Random(42));
        Task b = Theme.OHM.newGenerator().next(Level.HARD, new Random(42));
        assertEquals(a, b);
    }

    private static void assertInRange(double v, int min, int max) {
        assertTrue(v >= min && v <= max, v + " вне диапазона " + min + "…" + max);
        assertEquals(Math.rint(v), v, "номинал должен быть целым");
    }
}
