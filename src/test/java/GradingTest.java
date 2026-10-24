import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверяет выставление оценок: итоговую оценку как среднее (и двойку при любой неудаче),
 * формат вывода оценки, оценки по попыткам для уровней сложности, а также
 * настройки тем — эффективный уровень и лимит времени.
 */
class GradingTest {

    @Test
    void finalGradeIsAverage() {
        assertEquals(4.5, Grading.finalGrade(List.of(5, 4)));
        assertEquals(3, Grading.finalGrade(List.of(3, 3)));
    }

    @Test
    void anyTwoMakesFinalGradeTwo() {
        assertTrue(Grading.hasFail(List.of(5, 2)));
        assertEquals(2, Grading.finalGrade(List.of(5, 2)));
        assertEquals(2, Grading.finalGrade(List.of(2, 5)));
        assertFalse(Grading.hasFail(List.of(3, 5)));
    }

    @Test
    void formatDropsZeroFractionAndUsesComma() {
        assertEquals("5", Grading.format(5));
        assertEquals("4,5", Grading.format(4.5));
        assertEquals("3,5", Grading.format(3.5));
    }

    @Test
    void levelGrades() {
        assertEquals(5, Level.EASY.gradeForAttempt(1));
        assertEquals(5, Level.HARD.gradeForAttempt(1));
        assertEquals(4, Level.HARD.gradeForAttempt(2));
        assertEquals(3, Level.HARD.gradeForAttempt(3));
        assertEquals(1, Level.EASY.attempts);
        assertEquals(3, Level.HARD.attempts);
    }

    @Test
    void themeWithoutLevelsAlwaysUsesThreeAttempts() {
        assertEquals(Level.HARD, Theme.DIVIDERS.effectiveLevel(Level.EASY));
        assertEquals(Level.EASY, Theme.OHM.effectiveLevel(Level.EASY));
        assertEquals(Level.EASY, Theme.EQUIVALENT.effectiveLevel(Level.EASY));
    }

    @Test
    void acThemeIsReady() {
        assertTrue(Theme.AC_REACTANCE.ready());
        assertTrue(Theme.OHM.ready() && Theme.EQUIVALENT.ready() && Theme.DIVIDERS.ready());
        assertEquals("4. Переменный ток. Сопротивление реактивных элементов", Theme.AC_REACTANCE.toString());
        assertEquals(2, Theme.AC_REACTANCE.tasks);                 // катушка и конденсатор
        assertTrue(Theme.AC_REACTANCE.hasLevels);
        assertEquals(7 * 60, Theme.AC_REACTANCE.timeLimitSec(Level.EASY));
        assertEquals(10 * 60, Theme.AC_REACTANCE.timeLimitSec(Level.HARD));
        assertNotNull(Theme.AC_REACTANCE.newGenerator().next(Level.EASY, new java.util.Random(1)));
    }

    @Test
    void resonanceThemeIsReady() {
        assertTrue(Theme.RESONANCE.ready());
        assertEquals("5. Переменный ток. Последовательный колебательный контур", Theme.RESONANCE.toString());
        assertEquals(2, Theme.RESONANCE.tasks);
        assertTrue(Theme.RESONANCE.hasLevels);
        assertEquals(10 * 60, Theme.RESONANCE.timeLimitSec(Level.EASY));
        assertEquals(10 * 60, Theme.RESONANCE.timeLimitSec(Level.HARD));
    }

    @Test
    void themeDescriptions() {
        assertTrue(Theme.OHM.description().endsWith("На задание — 10 минут на лёгком уровне, 15 минут на сложном."));
        assertTrue(Theme.EQUIVALENT.description().endsWith("На каждое задание — 7 минут."));
        assertTrue(Theme.DIVIDERS.description().endsWith("На каждое задание — 5 минут."));
        assertTrue(Theme.RESONANCE.description().startsWith("Два задания: в последовательном контуре"));
    }

    @Test
    void themeTimeLimits() {
        assertEquals(10 * 60, Theme.OHM.timeLimitSec(Level.EASY));
        assertEquals(15 * 60, Theme.OHM.timeLimitSec(Level.HARD));  // сложный уровень темы 1 — 15 минут
        assertEquals("10 минут", Theme.OHM.minutesText(Level.EASY));
        assertEquals("15 минут", Theme.OHM.minutesText(Level.HARD));
        assertEquals(1, Theme.OHM.tasks);                   // в теме 1 одно задание
        assertEquals(2, Theme.EQUIVALENT.tasks);
        assertEquals(2, Theme.DIVIDERS.tasks);
        assertEquals(7 * 60, Theme.EQUIVALENT.timeLimitSec(Level.EASY));
        assertEquals(7 * 60, Theme.EQUIVALENT.timeLimitSec(Level.HARD));
        assertEquals(5 * 60, Theme.DIVIDERS.timeLimitSec(Theme.DIVIDERS.effectiveLevel(Level.EASY)));
        assertEquals("5 минут", Theme.DIVIDERS.minutesText(Level.HARD));
    }
}
