import java.util.EnumMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ход теста: попытки, оценки, время, итог и записанный результат. */
class ServerQuizServiceTest {

    private static final long SEED = 7;

    private ServerRepository repo;
    private ServerQuizService quiz;
    private ServerTestSupport.TestClock clock;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        repo = new ServerRepository(new ServerDatabase(dir.resolve("t.db")));
        clock = new ServerTestSupport.TestClock();
        quiz = new ServerQuizService(repo, clock, ServerTestSupport.seeded(SEED));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    private Map<String, Object> start(Theme theme, Level level) {
        return quiz.start("10-52", " иванов ", theme, level);
    }

    @Test
    void firstTryOnEasyGivesFiveAndIsRecorded() {
        Map<String, Object> st = start(Theme.OHM, Level.EASY);
        String id = (String) st.get("sessionId");
        assertEquals("task", st.get("status"));
        assertEquals("10-52", st.get("group"));
        assertEquals("Иванов", st.get("surname"));
        assertEquals(1, st.get("attemptsMax"));

        assertEquals(2, st.get("tasksTotal"));                          // в теме 1 прямая и обратная задачи
        for (int n = 0; n < Theme.OHM.tasks; n++) {
            st = quiz.answer(id, ServerTestSupport.answers(Theme.OHM, Level.EASY, SEED, n));
            assertEquals("task_done", st.get("status"));
            assertEquals(5, st.get("taskGrade"));
            st = quiz.next(id);
        }
        assertEquals("finished", st.get("status"));
        assertEquals("5", map(st.get("result")).get("finalGrade"));

        List<ServerRepository.ResultRow> rows = repo.find(ServerRepository.Filter.NONE, 10, 0);
        assertEquals(1, rows.size());
        assertEquals("10-52", rows.get(0).group());
        assertEquals(5.0, rows.get(0).grade());
        assertEquals(Level.EASY, rows.get(0).level());

        // повторное «Далее» не задваивает результат
        quiz.next(id);
        assertEquals(1, repo.count(ServerRepository.Filter.NONE));
    }

    @Test
    void hardLevelGradesByAttemptAndShowsHintOnWrongAnswer() {
        Map<String, Object> st = start(Theme.OHM, Level.HARD);
        String id = (String) st.get("sessionId");
        assertEquals(3, st.get("attemptsMax"));
        assertEquals(15 * 60, st.get("timeLimitSec"));                 // сложный уровень темы 1 — 15 минут
        assertEquals(15L * 60, ((Number) st.get("secondsLeft")).longValue());
        List<String> right = ServerTestSupport.answers(Theme.OHM, Level.HARD, SEED, 0);

        st = quiz.answer(id, ServerTestSupport.wrong(right));
        assertEquals("task", st.get("status"));
        assertEquals(1, st.get("attempt"));
        assertEquals("bad", st.get("messageKind"));
        assertFalse(((String) st.get("message")).contains("Правильный ответ"));   // ответ до конца задания не раскрывается

        st = quiz.answer(id, right);
        assertEquals("task_done", st.get("status"));
        assertEquals(4, st.get("taskGrade"));                 // верно со 2-й попытки
        assertTrue(((String) st.get("message")).contains("с попытки 2"));
    }

    @Test
    void failedTaskMakesFinalGradeTwo() {
        Map<String, Object> st = start(Theme.EQUIVALENT, Level.HARD);   // нужны два задания
        String id = (String) st.get("sessionId");
        List<String> bad = ServerTestSupport.wrong(ServerTestSupport.answers(Theme.EQUIVALENT, Level.HARD, SEED, 0));
        for (int i = 0; i < 3; i++) st = quiz.answer(id, bad);
        assertEquals(2, st.get("taskGrade"));
        assertTrue(((String) st.get("message")).contains("Правильный ответ"));   // теперь можно показать
        quiz.next(id);
        st = quiz.answer(id, ServerTestSupport.answers(Theme.EQUIVALENT, Level.HARD, SEED, 1));
        assertEquals(5, st.get("taskGrade"));
        st = quiz.next(id);
        assertEquals("2", map(st.get("result")).get("finalGrade"));   // одна двойка — итог 2, а не среднее
        assertFalse((Boolean) map(st.get("result")).get("passed"));
    }

    /** Проходит тест: правильные ответы в заданиях из right, остальные неверные. Возвращает итоговое состояние. */
    private Map<String, Object> finish(Map<String, Object> st, Theme theme, Level level, boolean... right) {
        String id = (String) st.get("sessionId");
        for (int n = 0; n < right.length; n++) {
            List<String> a = ServerTestSupport.answers(theme, level, SEED, n);
            st = quiz.answer(id, right[n] ? a : ServerTestSupport.wrong(a));
            if ("task_done".equals(st.get("status"))) st = quiz.next(id);
        }
        return st;
    }

    private Map<String, Object> startIn(String window, String surname) {
        return quiz.start("10-52", surname, Theme.EQUIVALENT, Level.EASY, window);
    }

    @Test
    void withoutCheckboxWindowChangesNothing() {
        Map<String, Object> st = finish(startIn("w1", "Иванов"), Theme.EQUIVALENT, Level.EASY, false, false);
        assertEquals("2", map(st.get("result")).get("finalGrade"));
        assertFalse((Boolean) map(st.get("result")).get("passed"));
        assertEquals(2, ((List<?>) map(st.get("result")).get("tasks")).size());
    }

    @Test
    void bonusOnlyForFirstTestInWindow() {
        quiz.settings().setBonus(true);
        Map<String, Object> first = finish(startIn("w1", "Иванов"), Theme.EQUIVALENT, Level.EASY, false, true);
        assertEquals("3", map(first.get("result")).get("finalGrade"));          // двойка за задание: итог 2 + 1
        assertTrue((Boolean) map(first.get("result")).get("passed"));
        assertEquals(List.of(), map(first.get("result")).get("tasks"));         // оценки за задания не показываем

        Map<String, Object> second = finish(startIn("w1", "Петров"), Theme.EQUIVALENT, Level.EASY, false, true);
        assertEquals("2", map(second.get("result")).get("finalGrade"));
        assertEquals(2, ((List<?>) map(second.get("result")).get("tasks")).size());

        Map<String, Object> other = finish(startIn("w2", "Сидоров"), Theme.EQUIVALENT, Level.EASY, false, false);
        assertEquals("3", map(other.get("result")).get("finalGrade"));          // новое окно: 2 + 1
    }

    @Test
    void bonusKeepsThreeAttemptsAndHidesWrongOne() {
        quiz.settings().setBonus(true);
        Map<String, Object> st = quiz.start("10-52", "Иванов", Theme.OHM, Level.HARD, "w1");
        String id = (String) st.get("sessionId");
        assertEquals(3, st.get("attemptsMax"));
        st = quiz.answer(id, ServerTestSupport.wrong(ServerTestSupport.answers(Theme.OHM, Level.HARD, SEED, 0)));
        assertEquals("task", st.get("status"));                        // то же задание: попытки остались
        assertEquals(1, st.get("taskNumber"));
        assertEquals(1, st.get("attempt"));
        assertEquals("Ответ не засчитан.", st.get("message"));          // без подсказки и без цвета
        assertNull(st.get("messageKind"));

        st = quiz.answer(id, ServerTestSupport.answers(Theme.OHM, Level.HARD, SEED, 0));   // вторая попытка — 4
        assertEquals(2, st.get("taskNumber"));
        assertEquals("Ответ принят.", st.get("message"));
        st = quiz.answer(id, ServerTestSupport.answers(Theme.OHM, Level.HARD, SEED, 1));   // первая попытка — 5
        assertEquals("finished", st.get("status"));
        assertEquals("5", map(st.get("result")).get("finalGrade"));    // среднее 4,5 + 1, но не выше 5
    }

    @Test
    void bonusAttemptsRunOutGiveTwoThenThree() {
        quiz.settings().setBonus(true);
        Map<String, Object> st = quiz.start("10-52", "Иванов", Theme.DIVIDERS, null, "w1");
        String id = (String) st.get("sessionId");
        for (int n = 0; n < 2; n++) {
            List<String> w = ServerTestSupport.wrong(ServerTestSupport.answers(Theme.DIVIDERS, null, SEED, n));
            for (int a = 0; a < 3; a++) st = quiz.answer(id, w);
        }
        assertEquals("finished", st.get("status"));
        assertEquals("3", map(st.get("result")).get("finalGrade"));
    }

    @Test
    void modulationWithBonusHidesVariantsAndRaisesByOne() {
        quiz.settings().setBonus(true);
        // {верные ответы по заданиям, ожидаемая оценка}: без галочки было бы 2, 3, 4 и 5
        Object[][] cases = {
                {new boolean[]{false, false, false, true, true}, "3"},
                {new boolean[]{false, false, true, true, true}, "4"},
                {new boolean[]{false, true, true, true, true}, "5"},
                {new boolean[]{true, true, true, true, true}, "5"},
        };
        int i = 0;
        for (Object[] c : cases) {
            Map<String, Object> st = quiz.start("10-52", "Иванов" + "а".repeat(++i), Theme.MODULATION, null, "w" + i);
            assertFalse(st.containsKey("solved"));
            st = finish(st, Theme.MODULATION, null, (boolean[]) c[0]);
            assertEquals("finished", st.get("status"));
            assertEquals(c[1], map(st.get("result")).get("finalGrade"));
            assertFalse(st.containsKey("solved"));                     // счёт верных ответов не показываем
            assertEquals(List.of(), map(st.get("result")).get("tasks"));
        }
    }

    @Test
    void modulationWithBonusTimeoutHidesCorrectVariant() {
        quiz.settings().setBonus(true);
        String id = (String) quiz.start("10-52", "Иванов", Theme.MODULATION, null, "w1").get("sessionId");
        clock.advanceSeconds(Theme.MODULATION.timeLimitSec(Level.EASY) + ServerQuizService.GRACE_SEC + 1);
        Map<String, Object> st = quiz.state(id);
        assertEquals("task_done", st.get("status"));
        assertFalse(st.containsKey("taskGrade"));
        assertFalse(map(st.get("task")).containsKey("correct"));
        assertEquals("Время вышло.", st.get("message"));
    }

    @Test
    void secondModulationInWindowShowsResultsAgain() {
        quiz.settings().setBonus(true);
        finish(quiz.start("10-52", "Иванов", Theme.MODULATION, null, "w1"), Theme.MODULATION, null,
                true, true, true, true, true);
        Map<String, Object> st = quiz.start("10-52", "Петров", Theme.MODULATION, null, "w1");
        String id = (String) st.get("sessionId");
        st = quiz.answer(id, ServerTestSupport.wrong(ServerTestSupport.answers(Theme.MODULATION, null, SEED, 0)));
        assertEquals("task_done", st.get("status"));
        assertEquals(2, st.get("taskGrade"));
        assertTrue(map(st.get("task")).containsKey("correct"));
        assertEquals(0L, st.get("solved"));
    }

    @Test
    void bonusDoesNotRaiseFive() {
        quiz.settings().setBonus(true);
        Map<String, Object> st = finish(startIn("w1", "Иванов"), Theme.EQUIVALENT, Level.EASY, true, true);
        assertEquals("5", map(st.get("result")).get("finalGrade"));
    }

    @Test
    void testStartedBeforeCheckboxStaysUsualAndUsesWindow() {
        Map<String, Object> started = startIn("w1", "Иванов");
        quiz.settings().setBonus(true);
        Map<String, Object> st = finish(started, Theme.EQUIVALENT, Level.EASY, true, false);
        assertEquals("2", map(st.get("result")).get("finalGrade"));
        st = finish(startIn("w1", "Петров"), Theme.EQUIVALENT, Level.EASY, true, false);
        assertEquals("2", map(st.get("result")).get("finalGrade"));
    }

    @Test
    void uncheckingDoesNotChangeRunningTest() {
        quiz.settings().setBonus(true);
        Map<String, Object> started = startIn("w1", "Иванов");
        quiz.settings().setBonus(false);
        Map<String, Object> st = finish(started, Theme.EQUIVALENT, Level.EASY, false, false);
        assertEquals("3", map(st.get("result")).get("finalGrade"));
    }

    @Test
    void bonusTestHidesOutcomeOfEveryAnswer() {
        quiz.settings().setBonus(true);
        for (boolean right : new boolean[]{true, false}) {
            String window = "w" + right;
            Map<String, Object> st = quiz.start("10-52", "Иванов" + (right ? "а" : "ов"), Theme.AC_REACTANCE, Level.EASY, window);
            List<String> a = ServerTestSupport.answers(Theme.AC_REACTANCE, Level.EASY, SEED, 0);
            st = quiz.answer((String) st.get("sessionId"), right ? a : ServerTestSupport.wrong(a));
            assertEquals("task", st.get("status"));                    // сразу следующее задание
            assertEquals(2, st.get("taskNumber"));
            assertEquals("Ответ принят.", st.get("message"));
            assertNull(st.get("messageKind"));
            assertFalse(st.containsKey("taskGrade"));
            assertFalse(map(st.get("task")).containsKey("chart"));     // график выдал бы верный ответ
        }
    }

    @Test
    void bonusTimeoutHidesGrade() {
        quiz.settings().setBonus(true);
        String id = (String) startIn("w1", "Иванов").get("sessionId");
        clock.advanceSeconds(Theme.EQUIVALENT.timeLimitSec(Level.EASY) + ServerQuizService.GRACE_SEC + 1);
        Map<String, Object> st = quiz.state(id);
        assertEquals("task_done", st.get("status"));
        assertFalse(st.containsKey("taskGrade"));
        assertEquals("Время вышло.", st.get("message"));                // правильный ответ не показываем
        assertNull(st.get("messageKind"));
    }

    @Test
    void refusedRetakeDoesNotUseWindow() {
        finish(startIn("w1", "Иванов"), Theme.EQUIVALENT, Level.EASY, true, true);
        quiz.settings().setBonus(true);
        assertThrows(ServerError.class, () -> startIn("w2", "Иванов"));   // пауза перед пересдачей
        Map<String, Object> st = finish(startIn("w2", "Петров"), Theme.EQUIVALENT, Level.EASY, false, false);
        assertEquals("3", map(st.get("result")).get("finalGrade"));
    }

    @Test
    void testWithoutWindowIdHasNoBonus() {
        quiz.settings().setBonus(true);
        Map<String, Object> st = finish(startIn(null, "Иванов"), Theme.EQUIVALENT, Level.EASY, false, false);
        assertEquals("2", map(st.get("result")).get("finalGrade"));
    }

    @Test
    void settingsResetKeepsCheckboxAndRestartRemembersIt() {
        quiz.settings().setBonus(true);
        quiz.settings().reset();
        assertTrue(quiz.settings().bonus());
        assertTrue(new ServerSettings(repo).bonus());
        quiz.settings().setBonus(false);
        assertFalse(new ServerSettings(repo).bonus());
    }

    @Test
    void invalidInputDoesNotSpendAttempt() {
        Map<String, Object> st = start(Theme.EQUIVALENT, Level.EASY);
        String id = (String) st.get("sessionId");
        st = quiz.answer(id, List.of("abc"));
        assertEquals(0, st.get("attempt"));
        assertEquals("warn", st.get("messageKind"));
        assertEquals(0, quiz.answer(id, List.of("NaN")).get("attempt"));
        assertEquals(0, quiz.answer(id, List.of("1".repeat(100))).get("attempt"));
        assertThrows(ServerError.class, () -> quiz.answer(id, List.of("1", "2")));     // не то число полей
    }

    @Test
    void twoAnswerThemeAcceptsCommaDecimalAndCountsPartial() {
        Map<String, Object> st = start(Theme.DIVIDERS, null);          // уровня нет, всегда 3 попытки
        String id = (String) st.get("sessionId");
        assertEquals(3, st.get("attemptsMax"));
        assertNull(st.get("level"));
        List<String> right = ServerTestSupport.answers(Theme.DIVIDERS, null, SEED, 0);
        st = quiz.answer(id, List.of(right.get(0), "0"));
        assertTrue(((String) st.get("message")).contains("верно введено 1 из 2"));
        st = quiz.answer(id, right.stream().map(v -> v.replace('.', ',')).toList());
        assertEquals("task_done", st.get("status"));
        assertEquals(4, st.get("taskGrade"));
    }

    @Test
    void timeoutGivesTwoAndLateAnswerIsRejected() {
        Map<String, Object> st = start(Theme.OHM, Level.EASY);
        String id = (String) st.get("sessionId");
        assertEquals((long) Theme.OHM.timeLimitSec(Level.EASY), ((Number) st.get("secondsLeft")).longValue());
        clock.advanceSeconds(Theme.OHM.timeLimitSec(Level.EASY) + 1);               // в пределах запаса на сеть
        assertEquals("task", quiz.state(id).get("status"));
        clock.advanceSeconds(ServerQuizService.GRACE_SEC + 1);

        st = quiz.answer(id, ServerTestSupport.answers(Theme.OHM, Level.EASY, SEED, 0));   // верный, но поздно
        assertEquals("task_done", st.get("status"));
        assertEquals(2, st.get("taskGrade"));
        assertTrue(((String) st.get("message")).startsWith("Время вышло"));
    }

    @Test
    void stateNeverContainsCorrectValues() {
        Map<String, Object> st = start(Theme.DIVIDERS, null);
        String json = st.toString();
        for (String v : ServerTestSupport.answers(Theme.DIVIDERS, null, SEED, 0)) {
            assertFalse(json.contains("value"), "в состоянии не должно быть поля со значением");
            assertFalse(map(st.get("task")).toString().contains("=" + v));
        }
        for (Object f : (List<?>) map(st.get("task")).get("fields")) {
            assertEquals(List.of("symbol", "unit"), List.copyOf(map(f).keySet()));
        }
        Map<String, Object> scheme = map(map(st.get("task")).get("scheme"));
        assertFalse(((List<?>) scheme.get("ops")).isEmpty());
        assertFalse(((List<?>) scheme.get("flow")).isEmpty());
    }

    @Test
    void acChartShownOnlyAfterCorrectAnswer() {
        Map<String, Object> st = start(Theme.AC_REACTANCE, Level.EASY);
        String id = (String) st.get("sessionId");
        assertEquals(2, st.get("tasksTotal"));
        assertEquals(5, ((List<?>) map(st.get("task")).get("fields")).size());  // лёгкий уровень — 5 частот
        assertFalse(map(st.get("task")).containsKey("chart"));                  // до ответа графика нет

        st = quiz.answer(id, ServerTestSupport.answers(Theme.AC_REACTANCE, Level.EASY, SEED, 0));
        assertEquals("task_done", st.get("status"));
        assertEquals(5, st.get("taskGrade"));
        Map<String, Object> chart = map(map(st.get("task")).get("chart"));
        assertFalse(((List<?>) chart.get("ops")).isEmpty());
        assertEquals(5, ((Number) st.get("chartSec")).intValue());

        st = quiz.next(id);                                                      // второе задание — неверно
        st = quiz.answer(id, ServerTestSupport.wrong(ServerTestSupport.answers(Theme.AC_REACTANCE, Level.EASY, SEED, 1)));
        assertEquals("task_done", st.get("status"));
        assertEquals(2, st.get("taskGrade"));
        assertFalse(map(st.get("task")).containsKey("chart"));
        assertFalse(st.containsKey("chartSec"));
    }

    @Test
    void resonanceChartShownAfterAnyOutcome() {
        Map<String, Object> st = start(Theme.RESONANCE, Level.EASY);
        String id = (String) st.get("sessionId");
        assertEquals(4, ((List<?>) map(st.get("task")).get("fields")).size());  // f0, ρ, Q, UL
        assertFalse(map(st.get("task")).containsKey("chart"));

        st = quiz.answer(id, ServerTestSupport.wrong(ServerTestSupport.answers(Theme.RESONANCE, Level.EASY, SEED, 0)));
        assertEquals("task_done", st.get("status"));
        assertEquals(2, st.get("taskGrade"));
        assertFalse(((List<?>) map(map(st.get("task")).get("chart")).get("ops")).isEmpty());   // и после неверного
        assertEquals(5, ((Number) st.get("chartSec")).intValue());

        st = quiz.next(id);                                                      // второе задание — время вышло
        assertFalse(map(st.get("task")).containsKey("chart"));
        clock.advanceSeconds(Theme.RESONANCE.timeLimitSec(Level.EASY) + ServerQuizService.GRACE_SEC + 1);
        st = quiz.state(id);
        assertEquals("task_done", st.get("status"));
        assertTrue(map(st.get("task")).containsKey("chart"));
    }

    @Test
    void modulationIsChoiceWithOneAttempt() {
        Map<String, Object> st = start(Theme.MODULATION, null);
        String id = (String) st.get("sessionId");
        assertEquals(5, st.get("tasksTotal"));
        assertEquals(1, st.get("attemptsMax"));                                 // выбор из пяти — одна попытка
        assertEquals(60, st.get("timeLimitSec"));
        Map<String, Object> task = map(st.get("task"));
        assertEquals(6, ((List<?>) task.get("options")).size());
        assertEquals(true, st.get("wholeTest"));
        assertTrue(((String) st.get("rules")).contains("3 верных — 3"));
        assertFalse(task.containsKey("correct"));                                // до ответа номер не раскрывается

        st = quiz.answer(id, List.of(""));                                       // ничего не выбрано
        assertEquals(0, st.get("attempt"));
        assertTrue(((String) st.get("message")).startsWith("Выберите вариант"));

        List<String> right = ServerTestSupport.answers(Theme.MODULATION, null, SEED, 0);
        st = quiz.answer(id, List.of(String.valueOf((int) Double.parseDouble(right.get(0)))));
        assertEquals(5, st.get("taskGrade"));
        assertEquals((int) Double.parseDouble(right.get(0)), map(st.get("task")).get("correct"));

        st = quiz.next(id);
        int correct = (int) Double.parseDouble(ServerTestSupport.answers(Theme.MODULATION, null, SEED, 1).get(0));
        st = quiz.answer(id, List.of(String.valueOf(correct % 6 + 1)));          // другой вариант — неверно
        assertEquals(2, st.get("taskGrade"));
        assertEquals("Неверно. Правильный ответ: вариант " + correct + " (" + modKind(1) + ").", st.get("message"));
        assertEquals(1L, st.get("solved"));

        for (int n = 2; n < 5; n++) {                                            // остальные три — верно
            quiz.next(id);
            String v = ServerTestSupport.answers(Theme.MODULATION, null, SEED, n).get(0);
            quiz.answer(id, List.of(String.valueOf((int) Double.parseDouble(v))));
        }
        st = quiz.next(id);
        assertEquals("finished", st.get("status"));
        assertEquals("4", map(st.get("result")).get("finalGrade"));             // одна ошибка из пяти — 4
        assertEquals(4L, st.get("solved"));
    }

    private static String modKind(int n) {
        TaskGenerator gen = Theme.MODULATION.newGenerator();
        java.util.Random rnd = new java.util.Random(SEED);
        Task t = null;
        for (int i = 0; i <= n; i++) {
            t = gen.next(Level.EASY, rnd);
            ServerSchemeJson.of(t.scheme(), rnd);
        }
        return t.kind();
    }

    @Test
    void protocolErrors() {
        assertEquals(404, assertThrows(ServerError.class, () -> quiz.state("нет")).status);
        assertEquals(404, assertThrows(ServerError.class, () -> quiz.answer(null, List.of("1"))).status);
        Map<String, Object> st = start(Theme.EQUIVALENT, Level.EASY);
        String id = (String) st.get("sessionId");
        assertEquals(409, assertThrows(ServerError.class, () -> quiz.next(id)).status);   // задание ещё не закончено
        quiz.answer(id, List.of("1"));                                                   // неверно: одна попытка
        assertEquals(409, assertThrows(ServerError.class, () -> quiz.answer(id, List.of("1"))).status);
        assertEquals(400, assertThrows(ServerError.class, () -> quiz.start("", "Иванов", Theme.OHM, Level.EASY)).status);
        assertEquals(400, assertThrows(ServerError.class, () -> quiz.start("10-52", "Иванов", Theme.OHM, null)).status);
    }

    @Test
    void inputIsNormalizedAndValidated() {
        assertEquals("10-52", ServerInput.group("  10-52 "));
        assertEquals("10-52", ServerInput.group("10 – 52"));                   // тире и минус — как дефис
        assertEquals("10-52", ServerInput.group("10−52"));
        assertEquals("1052", ServerInput.group("1052"));
        assertEquals("Иванов-Петров", ServerInput.surname("иванов-ПЕТРОВ"));
        assertEquals("Иванов Иван", ServerInput.surname("  иванов    иван "));
        assertEquals("Иванов Иван Иванович", ServerInput.surname("иванов иван иванович"));
        assertEquals("Ёлкин", ServerInput.surname("ёлкин"));
        for (String bad : new String[]{"", "   ", null, "=1+1", "a;b", "x".repeat(81), "Иван<script>", "12",
                "Ivanov", "Иванoв" /* латинская «o» */, "Иванов.", "О'Брайен", "-Иванов"}) {
            assertThrows(ServerError.class, () -> ServerInput.surname(bad), String.valueOf(bad));
        }
        for (String bad : new String[]{"", null, "-1", "=SUM(A1)", "К;М", "1".repeat(21),
                "КМ-21", "10-52а", "10/52", "10--52", "10-", "10 52"}) {
            assertThrows(ServerError.class, () -> ServerInput.group(bad), String.valueOf(bad));
        }
    }

    @Test
    void manyCadetsAtOnceDoNotInterfere() throws Exception {
        ServerQuizService svc = new ServerQuizService(repo);            // настоящие часы и случайность
        int n = 24;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> jobs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                final int k = i;
                jobs.add(pool.submit(() -> {
                    Map<String, Object> st = svc.start("10-5" + (k % 3), "Курсант" + "абвгдежзик".charAt(k / 10) + "абвгдежзик".charAt(k % 10),
                            Theme.EQUIVALENT, Level.EASY);
                    String id = (String) st.get("sessionId");
                    for (int t = 0; t < Theme.EQUIVALENT.tasks; t++) {
                        svc.answer(id, List.of("0"));                   // неверно -> 2, одна попытка
                        st = svc.next(id);
                    }
                    return (String) st.get("status");
                }));
            }
            for (Future<String> job : jobs) assertEquals("finished", job.get());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(n, repo.count(ServerRepository.Filter.NONE));
        assertEquals(n, svc.activeCount());
    }

    /** Доводит тест до конца, пропуская время каждого задания. */
    private void finishByTimeout(String id, Theme theme, Level level) {
        for (int n = 0; n < theme.tasks; n++) {
            clock.advanceSeconds(theme.timeLimitSec(level) + ServerQuizService.GRACE_SEC + 1);
            assertEquals("task_done", quiz.state(id).get("status"));  // время вышло — задание закрыто
            quiz.next(id);
        }
        assertEquals("finished", quiz.state(id).get("status"));
    }

    @Test
    void retakeOfSameThemeOnlyAfterPause() {
        finishByTimeout((String) start(Theme.OHM, Level.EASY).get("sessionId"), Theme.OHM, Level.EASY);

        ServerError e = assertThrows(ServerError.class, () -> start(Theme.OHM, Level.HARD));
        assertEquals(409, e.status);
        assertTrue(e.getMessage().contains("15 мин."), e.getMessage());
        assertEquals("task", start(Theme.DIVIDERS, null).get("status"));   // другую тему — сразу

        clock.advanceSeconds(ServerSettings.DEFAULT_RETAKE_MIN * 60 - 60);
        assertThrows(ServerError.class, () -> start(Theme.OHM, Level.EASY));
        clock.advanceSeconds(60);
        assertEquals("task", start(Theme.OHM, Level.EASY).get("status"));
    }

    @Test
    void sameCadetWithTwoTabsGetsIndependentSessions() {
        String a = (String) start(Theme.EQUIVALENT, Level.EASY).get("sessionId");
        String b = (String) start(Theme.EQUIVALENT, Level.EASY).get("sessionId");
        assertFalse(a.equals(b));
        quiz.answer(a, List.of("0"));
        assertEquals("task", quiz.state(b).get("status"));
        assertEquals(1, repo.cadetCount());
    }

    @Test
    void settingsApplyToNewTestsOnly() {
        String running = (String) start(Theme.EQUIVALENT, Level.EASY).get("sessionId");
        Map<Theme, ThemeRules> rules = new EnumMap<>(Theme.class);
        rules.put(Theme.EQUIVALENT, new ThemeRules(3 * 60, 4 * 60, 3));
        quiz.settings().save(rules, 0);

        Map<String, Object> st = start(Theme.EQUIVALENT, Level.HARD);
        assertEquals(3, st.get("tasksTotal"));
        assertEquals(4 * 60, st.get("timeLimitSec"));
        assertTrue(((String) map(st.get("task")).get("text")).endsWith("отводится 4 минуты."));
        Map<String, Object> old = quiz.state(running);              // начатый тест — по прежним правилам
        assertEquals(2, old.get("tasksTotal"));
        assertEquals(7 * 60, old.get("timeLimitSec"));

        // сохранённое переживает перезапуск сервера, сброс возвращает значения из кода
        ServerSettings again = new ServerSettings(repo);
        assertEquals(new ThemeRules(3 * 60, 4 * 60, 3), again.rules(Theme.EQUIVALENT));
        assertEquals(Theme.OHM.defaults(), again.rules(Theme.OHM));
        assertEquals(0, again.values().retakeMin());
        again.reset();
        assertEquals(Theme.EQUIVALENT.defaults(), new ServerSettings(repo).rules(Theme.EQUIVALENT));
        assertEquals(ServerSettings.DEFAULT_RETAKE_MIN, new ServerSettings(repo).values().retakeMin());
    }

    @Test
    void onlyChangedValuesAreStored() {
        ServerSettings s = quiz.settings();
        Map<Theme, ThemeRules> rules = new EnumMap<>(Theme.class);
        rules.put(Theme.OHM, new ThemeRules(3 * 60, 15 * 60, 1));
        rules.put(Theme.DIVIDERS, new ThemeRules(6 * 60, 6 * 60, 2));
        s.save(rules, 20);
        assertEquals(Map.of(Theme.OHM, new ThemeRules(3 * 60, 15 * 60, 1), Theme.DIVIDERS, new ThemeRules(6 * 60, 6 * 60, 2)),
                repo.themeRules());
        assertEquals("20", repo.setting("retake_min"));

        // вернули значения по умолчанию — строки удаляются, тема снова следует за кодом
        rules.put(Theme.OHM, Theme.OHM.defaults());
        s.save(rules, ServerSettings.DEFAULT_RETAKE_MIN);
        assertEquals(Map.of(Theme.DIVIDERS, new ThemeRules(6 * 60, 6 * 60, 2)), repo.themeRules());
        assertNull(repo.setting("retake_min"));
        assertEquals(Theme.OHM.defaults(), s.rules(Theme.OHM));
        assertEquals(ServerSettings.DEFAULT_RETAKE_MIN, s.values().retakeMin());
    }

    @Test
    void zeroRetakePauseAllowsImmediateRetake() {
        quiz.settings().save(Map.of(), 0);
        finishByTimeout((String) start(Theme.OHM, Level.EASY).get("sessionId"), Theme.OHM, Level.EASY);
        assertEquals("task", start(Theme.OHM, Level.EASY).get("status"));
    }

    @Test
    void invalidSettingsAreRejected() {
        ServerSettings s = quiz.settings();
        assertThrows(ServerError.class, () -> s.save(Map.of(Theme.OHM, new ThemeRules(0, 60, 1)), 15));
        assertThrows(ServerError.class, () -> s.save(Map.of(Theme.OHM, new ThemeRules(61 * 60, 60, 1)), 15));
        assertThrows(ServerError.class, () -> s.save(Map.of(Theme.OHM, new ThemeRules(90, 60, 1)), 15));
        assertThrows(ServerError.class, () -> s.save(Map.of(Theme.OHM, new ThemeRules(60, 60, 0)), 15));
        assertThrows(ServerError.class, () -> s.save(Map.of(Theme.OHM, new ThemeRules(60, 60, 6)), 15));
        assertThrows(ServerError.class, () -> s.save(Map.of(), -1));
        assertThrows(ServerError.class, () -> s.save(Map.of(), ServerSettings.MAX_RETAKE_MIN + 1));
        assertEquals(Theme.OHM.defaults(), s.rules(Theme.OHM));      // ничего не записалось

        // в теме без выбора сложности время одно — берётся время сложного уровня
        s.save(Map.of(Theme.DIVIDERS, new ThemeRules(60, 8 * 60, 2)), 15);
        assertEquals(new ThemeRules(8 * 60, 8 * 60, 2), s.rules(Theme.DIVIDERS));
    }
}
