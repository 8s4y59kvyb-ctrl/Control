import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Проверяет базовые формулы расчёта цепей из {@link Circuits}: эквивалентное сопротивление
 * параллельного соединения, делитель тока и делитель напряжения.
 */
class CircuitsTest {

    private static final double EPS = 1e-9;

    @Test
    void parallelOfTwoEqualsProductOverSum() {
        assertEquals(30.0 * 60 / (30 + 60), Circuits.parallel(30, 60), EPS);   // 20 Ом
    }

    @Test
    void parallelOfThree() {
        assertEquals(10, Circuits.parallel(30, 30, 30), EPS);
        assertEquals(1 / (1 / 20.0 + 1 / 40.0 + 1 / 80.0), Circuits.parallel(20, 40, 80), EPS);
    }

    @Test
    void parallelIsLessThanSmallestResistor() {
        assertEquals(true, Circuits.parallel(10, 100) < 10);
    }

    @Test
    void currentDividerSplitsInverselyToResistance() {
        // Iвх = 9 А, R1 = 20 Ом, R2 = 40 Ом: через меньшее сопротивление идёт больший ток
        double i1 = Circuits.currentShare(9, 20, 40);
        double i2 = Circuits.currentShare(9, 40, 20);
        assertEquals(6, i1, EPS);
        assertEquals(3, i2, EPS);
        assertEquals(9, i1 + i2, EPS);
        assertEquals(i1 * 20, i2 * 40, EPS);       // напряжение на параллельных ветвях одинаково
    }

    @Test
    void voltageDividerSplitsProportionallyToResistance() {
        double u1 = Circuits.voltageShare(60, 10, 20);
        double u2 = Circuits.voltageShare(60, 20, 10);
        assertEquals(20, u1, EPS);
        assertEquals(40, u2, EPS);
        assertEquals(60, u1 + u2, EPS);
    }
}
