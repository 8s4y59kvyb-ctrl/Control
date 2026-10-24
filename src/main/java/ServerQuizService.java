import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Ход теста на сервере — те же правила, что в ElectroQuizApp: одно или два задания по теме (Theme.tasks), у каждого лимит времени
 * и число попыток уровня, оценка за задание по номеру удачной попытки, итог по {@link Grading}.
 * Правильные ответы остаются на сервере и уходят в браузер только в сообщении о завершении задания.
 * Состояние активных тестов хранится в памяти (условие и ответы случайны и не восстанавливаются),
 * в базу попадает только завершённый тест. После перезапуска сервера незавершённые тесты теряются.
 */
final class ServerQuizService {

    static final int MAX_ACTIVE = 5000;                           // защита памяти от потока «Начать»
    static final Duration IDLE_TTL = Duration.ofHours(3);         // сессии без активности забываются
    static final int GRACE_SEC = 2;                               // запас на задержку сети при проверке времени
    static final int MAX_ANSWER_LEN = 30;

    /** Состояние одного теста; все обращения — под synchronized (session). */
    private static final class Session {
        final String id = UUID.randomUUID().toString();           // 122 случайных бита: чужой id не угадать
        final long cadetId;
        final String group, surname;
        final Theme theme;
        final Level level;                // выбранный (null для темы без выбора сложности)
        final Level taskLevel;            // по которому реально решаются задания
        final TaskGenerator generator;
        final Random rnd;
        final List<Integer> grades = new ArrayList<>();
        final List<String> kinds = new ArrayList<>();

        Task task;
        Map<String, Object> schemeJson;
        int taskNumber, attempt;
        Instant deadline;
        boolean taskDone, finished;
        int taskGrade;
        String message, messageKind;
        double finalGrade;
        long resultId;
        Instant touched;

        Session(long cadetId, String group, String surname, Theme theme, Level level, Random rnd) {
            this.cadetId = cadetId;
            this.group = group;
            this.surname = surname;
            this.theme = theme;
            this.level = level;
            this.taskLevel = theme.effectiveLevel(level);
            this.generator = theme.newGenerator();
            this.rnd = rnd;
        }
    }

    private final ServerRepository repo;
    private final Clock clock;
    private final Supplier<Random> randoms;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    ServerQuizService(ServerRepository repo) {
        this(repo, Clock.systemDefaultZone(), Random::new);
    }

    /** Часы и источник случайности подменяются в тестах. */
    ServerQuizService(ServerRepository repo, Clock clock, Supplier<Random> randoms) {
        this.repo = repo;
        this.clock = clock;
        this.randoms = randoms;
    }

    /** Вход: проверяет и нормализует группу и фамилию, заносит курсанта в базу. */
    Map<String, Object> login(String group, String surname) {
        String g = ServerInput.group(group), s = ServerInput.surname(surname);
        repo.upsertCadet(g, s, LocalDateTime.now(clock));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("group", g);
        m.put("surname", s);
        return m;
    }

    /** Начинает тест: первое задание выдаётся сразу, время на него пошло. */
    Map<String, Object> start(String group, String surname, Theme theme, Level level) {
        String g = ServerInput.group(group), s = ServerInput.surname(surname);
        if (theme.hasLevels && level == null) throw ServerError.badRequest("Выберите сложность.");
        purgeIdle();
        if (sessions.size() >= MAX_ACTIVE) throw new ServerError(503, "Сервер перегружен, попробуйте позже.");

        long cadetId = repo.upsertCadet(g, s, LocalDateTime.now(clock));
        Session ses = new Session(cadetId, g, s, theme, theme.hasLevels ? level : null, randoms.get());
        synchronized (ses) {
            repo.createSession(ses.id, cadetId, theme, ses.level, LocalDateTime.now(clock));
            sessions.put(ses.id, ses);
            startTask(ses);
            return view(ses);
        }
    }

    Map<String, Object> state(String id) {
        Session ses = find(id);
        synchronized (ses) {
            expireIfNeeded(ses);
            return view(ses);
        }
    }

    /** Проверка ответа. values — введённые значения в порядке полей. */
    Map<String, Object> answer(String id, List<String> values) {
        Session ses = find(id);
        synchronized (ses) {
            if (ses.finished || ses.taskDone) throw ServerError.conflict("Задание уже завершено.");
            if (expireIfNeeded(ses)) return view(ses);

            List<Answer> answers = ses.task.answers();
            if (values == null || values.size() != answers.size()) {
                throw ServerError.badRequest("Ожидается значений: " + answers.size() + ".");
            }
            double[] nums = new double[values.size()];
            try {
                for (int i = 0; i < nums.length; i++) {
                    String v = values.get(i);
                    if (v == null || v.length() > MAX_ANSWER_LEN) throw new NumberFormatException();
                    nums[i] = Answer.parse(v);
                }
            } catch (NumberFormatException ex) {
                // как на десктопе: попытка не тратится
                ses.message = answers.size() > 1
                        ? "Введите оба числа, например 1,25. Попытка не потрачена."
                        : "Введите число, например 1,25. Попытка не потрачена.";
                ses.messageKind = "warn";
                return view(ses);
            }

            ses.attempt++;
            int right = ses.task.countCorrect(nums);
            if (right == nums.length) {                       // попытка засчитывается, если верны все значения
                finishTask(ses, ses.taskLevel.gradeForAttempt(ses.attempt),
                        "Верно! " + ses.task.correctText() + ". Задание решено с попытки " + ses.attempt + ".");
            } else if (ses.attempt >= ses.taskLevel.attempts) {
                String prefix = ses.taskLevel == Level.EASY ? "Неверно. В лёгком режиме одна попытка. "
                                                            : "Попытки закончились. ";
                finishTask(ses, 2, prefix + "Правильный ответ: " + ses.task.correctText() + ".");
            } else {
                ses.message = nums.length > 1
                        ? "Неверно: верно введено " + right + " из " + nums.length + " значений. " + ses.task.hint()
                        : "Неверно. " + ses.task.hint();
                ses.messageKind = "bad";
            }
            return view(ses);
        }
    }

    /** Следующее задание либо (после последнего) итог теста; на завершённом тесте повторно безопасен. */
    Map<String, Object> next(String id) {
        Session ses = find(id);
        synchronized (ses) {
            if (ses.finished) return view(ses);
            expireIfNeeded(ses);
            if (!ses.taskDone) throw ServerError.conflict("Сначала завершите текущее задание.");
            if (ses.grades.size() < ses.theme.tasks) {
                startTask(ses);
            } else {
                ses.finalGrade = Grading.finalGrade(ses.grades);
                ses.resultId = repo.finishSession(ses.id, ses.cadetId, ses.group, ses.surname, ses.theme, ses.level,
                        ses.finalGrade, ses.grades, LocalDateTime.now(clock));
                ses.finished = true;
                ses.task = null;
                ses.schemeJson = null;
            }
            return view(ses);
        }
    }

    int activeCount() {
        return sessions.size();
    }

    // ---------- внутреннее ----------

    private void startTask(Session ses) {
        ses.taskNumber++;
        ses.attempt = 0;
        ses.taskDone = false;
        ses.message = null;
        ses.messageKind = null;
        ses.task = ses.generator.next(ses.taskLevel, ses.rnd);
        ses.schemeJson = ServerSchemeJson.of(ses.task.scheme(), ses.rnd);
        ses.deadline = Instant.now(clock).plusSeconds(ses.theme.timeLimitSec(ses.taskLevel));
        touch(ses);
    }

    private void finishTask(Session ses, int grade, String message) {
        ses.taskDone = true;
        ses.taskGrade = grade;
        ses.message = message;
        ses.messageKind = grade >= 3 ? "ok" : "bad";
        ses.grades.add(grade);
        ses.kinds.add(ses.task.kind());
        touch(ses);
    }

    /** Если время задания вышло, ставит 2. Возвращает true, когда задание закрыто именно этим вызовом. */
    private boolean expireIfNeeded(Session ses) {
        touch(ses);
        if (ses.finished || ses.taskDone) return false;
        if (!Instant.now(clock).isAfter(ses.deadline.plusSeconds(GRACE_SEC))) return false;
        finishTask(ses, 2, "Время вышло. Правильный ответ: " + ses.task.correctText() + ".");
        return true;
    }

    private void touch(Session ses) {
        ses.touched = Instant.now(clock);
    }

    private Session find(String id) {
        Session ses = id == null ? null : sessions.get(id);
        if (ses == null) throw ServerError.notFound("Тест не найден или устарел. Начните заново.");
        return ses;
    }

    private void purgeIdle() {
        Instant limit = Instant.now(clock).minus(IDLE_TTL);
        for (Iterator<Session> it = sessions.values().iterator(); it.hasNext(); ) {
            Session s = it.next();
            if (s.touched != null && s.touched.isBefore(limit)) it.remove();
        }
    }

    private Map<String, Object> view(Session ses) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sessionId", ses.id);
        m.put("status", ses.finished ? "finished" : ses.taskDone ? "task_done" : "task");
        m.put("group", ses.group);
        m.put("surname", ses.surname);
        m.put("theme", ses.theme.title);
        m.put("hasLevels", ses.theme.hasLevels);
        m.put("level", ses.level == null ? null : ses.level.title);
        m.put("tasksTotal", ses.theme.tasks);
        if (ses.message != null) {
            m.put("message", ses.message);
            m.put("messageKind", ses.messageKind);
        }
        if (ses.finished) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("finalGrade", Grading.format(ses.finalGrade));
            result.put("passed", ses.finalGrade >= 3);
            List<Object> tasks = new ArrayList<>();
            for (int i = 0; i < ses.grades.size(); i++) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("number", i + 1);
                t.put("kind", ses.kinds.get(i));
                t.put("grade", ses.grades.get(i));
                tasks.add(t);
            }
            result.put("tasks", tasks);
            result.put("resultId", ses.resultId);
            m.put("result", result);
            return m;
        }

        m.put("taskNumber", ses.taskNumber);
        m.put("attempt", ses.attempt);
        m.put("attemptsMax", ses.taskLevel.attempts);
        m.put("rules", ses.taskLevel.rules());
        m.put("timeLimitSec", ses.theme.timeLimitSec(ses.taskLevel));
        long left = Duration.between(Instant.now(clock), ses.deadline).toMillis();
        m.put("secondsLeft", ses.taskDone ? 0 : Math.max(0, (left + 999) / 1000));
        if (ses.taskDone) {
            m.put("taskGrade", ses.taskGrade);
            m.put("lastTask", ses.taskNumber >= ses.theme.tasks);
        }

        Map<String, Object> task = new LinkedHashMap<>();
        task.put("text", ses.task.text());
        List<Object> fields = new ArrayList<>();
        for (Answer a : ses.task.answers()) {                 // только обозначение и единица, значение не отдаётся
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("symbol", a.symbol());
            f.put("unit", a.unit());
            fields.add(f);
        }
        task.put("fields", fields);
        task.put("scheme", ses.schemeJson);
        m.put("task", task);
        return m;
    }
}
