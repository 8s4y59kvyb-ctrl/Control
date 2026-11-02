import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Тема «Модуляция»: задания на сопоставление временного представления и амплитудного спектра. */
class ModulationTasksTest {

    private static int seed;

    /** За пять заданий виды модуляции не повторяются; верный номер указывает на тот же вид. */
    @RepeatedTest(50)
    void fiveTasksWithoutRepeats() {
        Random rnd = new Random(seed++);
        TaskGenerator gen = Theme.MODULATION.newGenerator();
        Set<String> kinds = new HashSet<>();
        for (int n = 0; n < 5; n++) {
            Task t = gen.next(Level.EASY, rnd);
            assertTrue(t.isChoice());
            assertEquals(6, t.options().size());
            kinds.add(t.kind());

            ModulationPicture given = (ModulationPicture) t.scheme();
            assertTrue(given.large());
            assertEquals(t.kind(), given.type().abbr);
            int correct = (int) t.answers().get(0).value();
            ModulationPicture right = (ModulationPicture) t.options().get(correct - 1);
            assertEquals(given.type(), right.type());                   // верный вариант — тот же вид модуляции
            assertNotEquals(given.spectrum(), right.spectrum());        // сигнал ↔ спектр
            assertEquals(given.m(), right.m());
            Set<Modulation> types = new HashSet<>();
            for (Scheme o : t.options()) {
                ModulationPicture p = (ModulationPicture) o;
                assertFalse(p.large());
                assertEquals(right.spectrum(), p.spectrum());          // все варианты одного рода
                types.add(p.type());
            }
            assertEquals(6, types.size());
            assertTrue(t.correctText().endsWith("(" + t.kind() + ")"));
            assertTrue(t.text().contains("отводится 1 минута."));
        }
        assertEquals(5, kinds.size());
        Task sixth = gen.next(Level.EASY, rnd);                       // шестое — оставшийся вид
        assertFalse(kinds.contains(sixth.kind()));
    }

    @Test
    void orderAndDirectionAreRandom() {
        Set<String> firsts = new HashSet<>();
        Set<Boolean> directions = new HashSet<>();
        Set<Integer> answers = new HashSet<>();
        for (int s = 0; s < 200; s++) {
            Task t = Theme.MODULATION.newGenerator().next(Level.EASY, new Random(s));
            firsts.add(t.kind());
            directions.add(((ModulationPicture) t.scheme()).spectrum());
            answers.add((int) t.answers().get(0).value());
        }
        assertEquals(6, firsts.size());
        assertEquals(2, directions.size());
        assertEquals(Set.of(1, 2, 3, 4, 5, 6), answers);
    }

    /** Спектр ЛЧМ: в полосе Δf без провалов (пульсации Френеля), за её краями быстро спадает. */
    @Test
    void lfmSpectrumIsNearlyRectangular() {
        double m = 8, min = Double.MAX_VALUE, max = 0;
        for (double x = -2.5; x <= 2.5; x += 0.1) {
            double v = Modulation.lfmSpectrum(x, m);
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        assertTrue(min > 0.6 * max, min + " / " + max);
        assertTrue(Modulation.lfmSpectrum(7, m) < 0.25 * min);
        assertTrue(Modulation.lfmSpectrum(-7, m) < 0.25 * min);
        assertTrue(Modulation.LFM.continuous());
        assertThrows(IllegalStateException.class, () -> Modulation.LFM.spectrum(ModulationPicture.LINES, m));
        assertEquals(-1, Modulation.LFM.message(0), 1e-12);            // пила от −1 до 1
        assertEquals(0, Modulation.LFM.message(0.5), 1e-12);
        assertEquals(1, Modulation.AM.message(0), 1e-12);
    }

    @Test
    void wholeTestGradeByMistakes() {
        assertEquals(5, Theme.MODULATION.finalGrade(List.of(5, 5, 5, 5, 5)));
        assertEquals(4, Theme.MODULATION.finalGrade(List.of(5, 2, 5, 5, 5)));
        assertEquals(3, Theme.MODULATION.finalGrade(List.of(2, 5, 5, 2, 5)));
        assertEquals(2, Theme.MODULATION.finalGrade(List.of(2, 2, 2, 5, 5)));
        assertEquals(2, Theme.MODULATION.finalGrade(List.of(2, 2, 2, 2, 2)));
        assertEquals(2, Theme.OHM.finalGrade(List.of(5, 2)));            // в остальных темах — как раньше
        assertTrue(Theme.MODULATION.rules(Level.EASY).contains("4 верных — 4"));
    }

    @Test
    void besselMatchesTables() {
        assertEquals(0.7652, Modulation.bessel(0, 1), 1e-4);
        assertEquals(-0.2601, Modulation.bessel(0, 3), 1e-4);
        assertEquals(0.3391, Modulation.bessel(1, 3), 1e-4);
        assertEquals(0.4861, Modulation.bessel(2, 3), 1e-4);
        assertEquals(-0.1776, Modulation.bessel(0, 5), 1e-4);
        assertEquals(0.3912, Modulation.bessel(4, 5), 1e-4);
        assertEquals(0.3648, Modulation.bessel(3, 5), 1e-4);
    }

    @Test
    void spectraHaveExpectedLines() {
        int n = ModulationPicture.LINES;
        double[] am = Modulation.AM.spectrum(n, 0.6);
        assertEquals(1, am[n]);
        assertEquals(0.3, am[n - 1], 1e-12);
        assertEquals(0.3, am[n + 1], 1e-12);
        double[] dsb = Modulation.DSB.spectrum(n, 0);
        assertEquals(0, dsb[n]);                                        // несущей нет
        assertEquals(dsb[n - 1], dsb[n + 1]);
        double[] ssb = Modulation.SSB.spectrum(n, 0);
        assertEquals(1, ssb[n + 1]);
        assertEquals(1, java.util.Arrays.stream(ssb).filter(v -> v > 0).count());   // одна боковая
        double[] fm = Modulation.FM.spectrum(n, 3);
        assertArrayEquals(fm, Modulation.PM.spectrum(n, 3));            // при тоне спектры ЧМК и ФМК совпадают
        double power = 0;
        for (double v : fm) power += v * v;
        assertEquals(1, power, 0.01);                                   // Σ Jn² = 1: линий хватает
    }

    /** Сигналы во времени: АМК не превышает 1 + mₐ, у ОМК постоянная амплитуда. */
    @Test
    void signalsStayInRange() {
        for (int i = 0; i <= 1000; i++) {
            double t = 2.0 * i / 1000;
            assertTrue(Math.abs(Modulation.AM.signal(t, 10, 0.6)) <= 1.6 + 1e-9);
            for (Modulation m : Modulation.values()) assertTrue(Math.abs(m.signal(t, 10, 4)) <= 5 + 1e-9);
        }
    }

    @Test
    void picturesDrawWithLabelsOnlyForIndexedTypes() {
        for (Modulation type : Modulation.values()) {
            Map<String, Object> json = ServerSchemeJson.of(new ModulationPicture(type, true, 10, 3, false), new Random());
            assertEquals(300, json.get("width"));
            String ops = json.get("ops").toString();
            boolean indexed = type == Modulation.AM || type == Modulation.PM || type == Modulation.FM;
            assertEquals(indexed, ops.contains("[text, m,"), type.name());
            Map<String, Object> time = ServerSchemeJson.of(new ModulationPicture(type, false, 10, 3, true), new Random());
            assertEquals(600, time.get("width"));
            assertTrue(time.get("ops").toString().contains("polyline"));
            assertEquals(List.of(), time.get("flow"));
        }
    }
}
