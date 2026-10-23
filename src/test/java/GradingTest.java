import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void themeTimeLimits() {
        assertEquals(7 * 60, Theme.OHM.timeLimitSec);
        assertEquals(7 * 60, Theme.EQUIVALENT.timeLimitSec);
        assertEquals(5 * 60, Theme.DIVIDERS.timeLimitSec);
        assertEquals("5 минут", Theme.DIVIDERS.minutesText());
    }
}
