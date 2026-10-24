import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Supplier;

/** Общие помощники тестов веб-версии: детерминированные задания и управляемые часы. */
final class ServerTestSupport {

    private ServerTestSupport() {
    }

    /** Часы, которые стоят, пока тест не сдвинет их вручную. */
    static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-10-24T09:00:00Z");

        void advanceSeconds(long s) {
            now = now.plusSeconds(s);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    /** Источник случайности с фиксированным зерном: сервис выдаёт те же задания, что и {@link #answers}. */
    static Supplier<Random> seeded(long seed) {
        return () -> new Random(seed);
    }

    /**
     * Правильные ответы n-го задания (с нуля) сессии с этим зерном. Повторяет порядок обращений
     * к Random в сервисе: задание, затем пути тока схемы.
     */
    static List<String> answers(Theme theme, Level level, long seed, int n) {
        Random rnd = new Random(seed);
        TaskGenerator gen = theme.newGenerator();
        Level taskLevel = theme.effectiveLevel(level);
        Task task = null;
        for (int i = 0; i <= n; i++) {
            task = gen.next(taskLevel, rnd);
            ServerSchemeJson.of(task.scheme(), rnd);
        }
        return task.answers().stream().map(a -> String.format(Locale.US, "%.2f", a.value())).toList();
    }

    /** Заведомо неверные значения для задания. */
    static List<String> wrong(List<String> right) {
        return right.stream().map(v -> String.format(Locale.US, "%.2f", Double.parseDouble(v) + 1000)).toList();
    }
}
