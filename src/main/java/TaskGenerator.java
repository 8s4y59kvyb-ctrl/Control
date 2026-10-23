import java.util.Random;

/** Генератор заданий темы. Создаётся заново на каждый тест и может помнить предыдущие задания. */
interface TaskGenerator {
    Task next(Level level, Random rnd);
}
