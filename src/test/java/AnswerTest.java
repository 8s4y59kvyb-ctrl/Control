import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверяет {@link Answer} — проверку ответа ученика: допуск 2 % (и не менее 0,01 для малых значений),
 * текстовый вид правильного ответа и разбор введённого числа (запятая/точка, пробелы, некорректный ввод).
 */
class AnswerTest {

    @Test
    void acceptsWithinTwoPercent() {
        Answer z = new Answer("Z", "Ом", 80);
        assertTrue(z.accepts(80));
        assertTrue(z.accepts(81.6));
        assertTrue(z.accepts(78.4));
        assertFalse(z.accepts(81.7));
        assertFalse(z.accepts(78.3));
    }

    @Test
    void smallValuesUseAbsoluteToleranceOfOneHundredth() {
        Answer i = new Answer("I", "А", 0.2);       // 2 % — это 0,004, меньше 0,01
        assertTrue(i.accepts(0.21));
        assertTrue(i.accepts(0.195));
        assertFalse(i.accepts(0.22));
    }

    @Test
    void textUsesCommaAndTwoDecimals() {
        assertEquals("I = 0,75 А", new Answer("I", "А", 0.75).text());
        assertEquals("Z = 38,49 Ом", new Answer("Z", "Ом", 38.4876).text());
    }

    @Test
    void parseAcceptsCommaDotAndSpaces() {
        assertEquals(1.25, Answer.parse("1,25"));
        assertEquals(1.25, Answer.parse(" 1.25 "));
        assertEquals(-3, Answer.parse("-3"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "abc", "1,2,3", "NaN", "Infinity", "-Infinity"})
    void parseRejectsNonNumbers(String input) {
        assertThrows(NumberFormatException.class, () -> Answer.parse(input));
    }
}
