import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверяет генерацию задач на лестничной схеме (темы «Закон Ома» и «Эквивалентные преобразования»):
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
    void equivalentAnswerMatchesElementType() {
        Task task = Theme.EQUIVALENT.newGenerator().next(Level.HARD, new Random());
        LadderScheme s = (LadderScheme) task.scheme();
        Answer a = task.answers().get(0);
        double expected = switch (s.element()) {
            case RESISTOR, INDUCTOR -> s.r1() + 1 / (1 / s.r2() + 1 / s.r3() + 1 / s.r4());
            case CAPACITOR -> 1 / (1 / s.r1() + 1 / (s.r2() + s.r3() + s.r4()));   // C1 последовательно с C2 + C3 + C4
        };

        assertEquals(s.element().total, a.symbol());
        assertEquals(s.element().unit, a.unit());
        assertEquals(expected, a.value(), EPS);
        assertTrue(s.findZ());
        assertTrue(task.text().contains(s.element().letter + "4 = " + (int) s.r4() + " " + s.element().unit));
        assertFalse(task.text().contains("E = "));          // в теме 2 ЭДС не нужна
    }

    @Test
    void equivalentThemeUsesAllElementTypes() {
        Random rnd = new Random(7);
        java.util.Set<Element> seen = java.util.EnumSet.noneOf(Element.class);
        for (int i = 0; i < 100; i++) {
            seen.add(((LadderScheme) Theme.EQUIVALENT.newGenerator().next(Level.EASY, rnd).scheme()).element());
        }
        assertEquals(java.util.EnumSet.allOf(Element.class), seen);
    }

    @RepeatedTest(50)
    void elementTypesDoNotRepeatWithinTest() {
        TaskGenerator gen = Theme.EQUIVALENT.newGenerator();
        Random rnd = new Random();
        java.util.Set<Element> seen = java.util.EnumSet.noneOf(Element.class);
        for (int i = 0; i < Element.values().length; i++) {
            Element el = ((LadderScheme) gen.next(Level.HARD, rnd).scheme()).element();
            assertTrue(seen.add(el), "тип " + el + " повторился в одном тесте");
        }
        gen.next(Level.HARD, rnd);                          // все типы выданы — круг начинается заново
    }

    @RepeatedTest(20)
    void ohmThemeAlwaysUsesResistors() {
        Task task = Theme.OHM.newGenerator().next(Level.HARD, new Random());
        assertEquals(Element.RESISTOR, ((LadderScheme) task.scheme()).element());
    }

    @Test
    void capacitorAndInductorFormulas() {
        assertEquals(30, Element.CAPACITOR.parallel(10, 20), EPS);
        assertEquals(20, Element.CAPACITOR.series(30, 60), EPS);
        assertEquals(30, Element.INDUCTOR.series(10, 20), EPS);
        assertEquals(20, Element.INDUCTOR.parallel(30, 60), EPS);
    }

    @RepeatedTest(50)
    void easyLevelGivesRoundNumbers() {
        Random rnd = new Random();
        for (Theme theme : new Theme[]{Theme.OHM, Theme.EQUIVALENT}) {
            Task task = theme.newGenerator().next(Level.EASY, rnd);
            LadderScheme s = (LadderScheme) task.scheme();
            double parallel = s.element().parallel(s.r2(), s.r3());
            double answer = task.answers().get(0).value();

            assertEquals(Math.rint(parallel), parallel, EPS, "эквивалент ветвей 2 и 3 должен быть целым");
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
        assertTrue(hard.hint().contains(((LadderScheme) hard.scheme()).element().hintName));
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
