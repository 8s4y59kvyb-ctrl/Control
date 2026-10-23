import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверяет {@link FlowPath} — анимацию движения тока по схеме: длину ломаной и точки на ней,
 * число точек пропорционально доле тока, распределение потока по ветвям лестничной схемы
 * и количество путей тока в схемах делителей.
 */
class FlowPathTest {

    private static final double EPS = 1e-9;
    private static final double[][] SQUARE = {{0, 0}, {100, 0}, {100, 100}, {0, 100}};   // длина 300

    @Test
    void lengthAndPointsAlongPolyline() {
        FlowPath p = new FlowPath(SQUARE, 1.0, 0);
        assertEquals(300, p.len, EPS);
        assertArrayEquals(new double[]{0, 0}, p.pointAt(0), EPS);
        assertArrayEquals(new double[]{50, 0}, p.pointAt(50), EPS);
        assertArrayEquals(new double[]{100, 50}, p.pointAt(150), EPS);
        assertArrayEquals(new double[]{0, 100}, p.pointAt(300), EPS);
    }

    @Test
    void dotCountProportionalToShare() {
        FlowPath full = new FlowPath(SQUARE, 1.0, 0);
        FlowPath half = new FlowPath(SQUARE, 0.5, 0);
        assertEquals(Math.round(300 / FlowPath.SPACING), full.count);
        assertEquals(Math.round(150 / FlowPath.SPACING), half.count);
        assertEquals(1, new FlowPath(SQUARE, 0.001, 0).count);      // хотя бы одна точка
    }

    @Test
    void ladderBranchesShareCurrentByConductance() {
        // R2 = 20 Ом вдвое меньше R3 = 40 Ом — по ветви R2 идёт вдвое больший ток
        LadderScheme s = new LadderScheme(30, 10, 20, 40, 50, false, false);
        List<FlowPath> paths = s.flowPaths(new Random(0));
        assertEquals(2, paths.size());
        double densityR2 = paths.get(0).count / paths.get(0).len;
        double densityR3 = paths.get(1).count / paths.get(1).len;
        assertTrue(densityR2 > densityR3);
        assertEquals(3, new LadderScheme(30, 10, 20, 40, 50, true, false).flowPaths(new Random(0)).size());
    }

    @Test
    void dividerFlowPaths() {
        assertEquals(2, new DividerScheme(true, 5, 20, 40).flowPaths(new Random(0)).size());
        assertEquals(1, new DividerScheme(false, 30, 20, 40).flowPaths(new Random(0)).size());
    }
}
