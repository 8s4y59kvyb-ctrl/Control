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
 * Ход теста на сервере: несколько заданий по теме, у каждого лимит времени и число попыток уровня,
 * оценка за задание по номеру удачной попытки, итог по {@link Grading}. Время и число заданий берутся
 * из {@link ServerSettings} и запоминаются при старте теста.
 * Правильные ответы остаются на сервере и уходят в браузер только в сообщении о завершении задания.
 * Состояние активных тестов хранится в памяти (условие и ответы случайны и не восстанавливаются),
 * в базу попадает только завершённый тест. После перезапуска сервера незавершённые тесты теряются.
 */
final class ServerQuizService {

    static final int MAX_ACTIVE = 5000;                           // защита памяти от потока «Начать»
    static final Duration IDLE_TTL = Duration.ofHours(3);         // сессии без активности забываются
    static final int GRACE_SEC = 2;                               // запас на задержку сети при проверке времени
    static final int MAX_ANSWER_LEN = 30;
    static final int CHART_SEC = 5;                               // сколько браузер показывает график после задания
    static final int MAX_WINDOW_ID_LEN = 64;
    static final int MAX_WINDOWS = 100_000;                       // защита памяти от потока выдуманных номеров окон
    static final Duration WINDOW_TTL = Duration.ofDays(1);        // номера окон забываются через сутки

    /** Состояние одного теста; все обращения — под synchronized (session). */
    private static final class Session {
        final String id = UUID.randomUUID().toString();           // 122 случайных бита: чужой id не угадать
        final long cadetId;
        final String group, surname;
        final Theme theme;
        final Level level;                // выбранный (null для темы без выбора сложности)
        final Level taskLevel;            // по которому реально решаются задания
        final ThemeRules rules;           // время и число заданий на момент старта
        final TaskGenerator generator;
        final Random rnd;
        final boolean bonus;              // итог +1 (не выше 5), результаты заданий курсанту не показываются
        final List<Integer> grades = new ArrayList<>();
        final List<String> kinds = new ArrayList<>();

        Task task;
        Map<String, Object> schemeJson;
        Map<String, Object> chartJson;    // график после задания (темы 4 и 5), иначе null
        List<Map<String, Object>> optionsJson;   // картинки-варианты задания на выбор, иначе null
        int taskNumber, attempt;
        Instant deadline;
        boolean taskDone, finished;
        int taskGrade;
        String message, messageKind;
        double finalGrade;
        long resultId;
        Instant touched;

        Session(long cadetId, String group, String surname, Theme theme, Level level, ThemeRules rules, Random rnd,
                boolean bonus) {
            this.cadetId = cadetId;
            this.group = group;
            this.surname = surname;
            this.theme = theme;
            this.level = level;
            this.taskLevel = theme.effectiveLevel(level);
            this.rules = rules;
            this.generator = theme.newGenerator(rules);
            this.rnd = rnd;
            this.bonus = bonus;
        }
    }

    private final ServerRepository repo;
    private final ServerSettings settings;
    private final Clock clock;
    private final Supplier<Random> randoms;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, Instant> usedWindows = new ConcurrentHashMap<>();   // окна браузера, где тест уже начинали

    ServerQuizService(ServerRepository repo) {
        this(repo, Clock.systemDefaultZone(), Random::new);
    }

    /** Часы и источник случайности подменяются в тестах. */
    ServerQuizService(ServerRepository repo, Clock clock, Supplier<Random> randoms) {
        this.repo = repo;
        this.settings = new ServerSettings(repo);
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
        return start(group, surname, theme, level, null);
    }

    /** То же; windowId — случайный номер окна браузера, по нему определяется первый тест в этом окне. */
    Map<String, Object> start(String group, String surname, Theme theme, Level level, String windowId) {
        String g = ServerInput.group(group), s = ServerInput.surname(surname);
        if (!theme.ready()) throw ServerError.badRequest("Тема в разработке: заданий пока нет.");
        if (theme.hasLevels && level == null) throw ServerError.badRequest("Выберите сложность.");
        purgeIdle();
        if (sessions.size() >= MAX_ACTIVE) throw new ServerError(503, "Сервер перегружен, попробуйте позже.");

        long cadetId = repo.upsertCadet(g, s, LocalDateTime.now(clock));
        checkRetake(cadetId, theme);
        boolean first = firstInWindow(windowId);              // отказ по паузе перед пересдачей окно не расходует
        Session ses = new Session(cadetId, g, s, theme, theme.hasLevels ? level : null, settings.rules(theme),
                randoms.get(), first && settings.bonus());
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
                ses.message = ses.task.isChoice() ? "Выберите вариант ответа. Попытка не потрачена."
                        : answers.size() > 1
                        ? "Введите оба числа, например 1,25. Попытка не потрачена."
                        : "Введите число, например 1,25. Попытка не потрачена.";
                ses.messageKind = "warn";
                return view(ses);
            }

            ses.attempt++;
            int right = ses.task.countCorrect(nums);
            if (ses.bonus) {                                  // исход не показываем, правила оценки те же
                boolean solved = right == nums.length;
                if (solved || ses.attempt >= ses.taskLevel.attempts) {
                    finishTask(ses, solved ? ses.taskLevel.gradeForAttempt(ses.attempt) : 2, null);
                    advance(ses);
                    if (!ses.finished) ses.message = "Ответ принят.";
                } else {
                    ses.message = "Ответ не засчитан.";
                    ses.messageKind = null;
                }
                return view(ses);
            }
            if (right == nums.length) {                       // попытка засчитывается, если верны все значения
                finishTask(ses, ses.taskLevel.gradeForAttempt(ses.attempt), ses.theme.gradesWholeTest()
                        ? "Верно! Это " + ses.task.correctText() + "."
                        : "Верно! " + ses.task.correctText() + ". Задание решено с попытки " + ses.attempt + ".");
            } else if (ses.attempt >= ses.taskLevel.attempts) {
                String prefix = ses.taskLevel != Level.EASY ? "Попытки закончились. "
                        : ses.theme.hasLevels ? "Неверно. В лёгком режиме одна попытка. " : "Неверно. ";
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
            advance(ses);
            return view(ses);
        }
    }

    ServerSettings settings() {
        return settings;
    }

    int activeCount() {
        return sessions.size();
    }

    // ---------- внутреннее ----------

    /** Пересдача темы — не раньше чем через паузу из настроек после окончания прошлого теста по ней. */
    private void checkRetake(long cadetId, Theme theme) {
        Duration pause = settings.retakePause();
        if (pause.isZero()) return;
        LocalDateTime last = repo.lastFinished(cadetId, theme);
        if (last == null) return;
        long left = Duration.between(LocalDateTime.now(clock), last.plus(pause)).toSeconds();
        if (left > 0) {
            throw ServerError.conflict("Пересдать эту тему можно через " + (left + 59) / 60
                    + " мин.: между попытками должно пройти " + ThemeRules.minutes((int) pause.toMinutes()) + ".");
        }
    }

    /**
     * Первый ли тест в этом окне браузера. Окно считается использованным и при снятой галочке.
     * Без номера окна и при переполнении — не первый: тест идёт по обычным правилам.
     */
    private boolean firstInWindow(String windowId) {
        if (windowId == null || windowId.isEmpty() || windowId.length() > MAX_WINDOW_ID_LEN) return false;
        if (usedWindows.size() >= MAX_WINDOWS && !usedWindows.containsKey(windowId)) return false;
        return usedWindows.putIfAbsent(windowId, Instant.now(clock)) == null;
    }

    /** После завершённого задания: следующее задание либо (после последнего) итог теста с записью в базу. */
    private void advance(Session ses) {
        if (ses.grades.size() < ses.rules.tasks()) {
            startTask(ses);
        } else {
            double grade = ses.theme.finalGrade(ses.grades);
            ses.finalGrade = ses.bonus ? Grading.withBonus(grade) : grade;
            ses.resultId = repo.finishSession(ses.id, ses.cadetId, ses.group, ses.surname, ses.theme, ses.level,
                    ses.finalGrade, ses.grades, ses.bonus, LocalDateTime.now(clock));
            ses.finished = true;
            ses.task = null;
            ses.schemeJson = null;
            ses.chartJson = null;
            ses.optionsJson = null;
        }
    }

    private void startTask(Session ses) {
        ses.taskNumber++;
        ses.attempt = 0;
        ses.taskDone = false;
        ses.message = null;
        ses.messageKind = null;
        ses.chartJson = null;
        ses.task = ses.generator.next(ses.taskLevel, ses.rnd);
        ses.schemeJson = ServerSchemeJson.of(ses.task.scheme(), ses.rnd);
        ses.optionsJson = null;
        if (ses.task.isChoice()) {                    // варианты не тратят ses.rnd: порядок случайных чисел не меняется
            ses.optionsJson = new ArrayList<>();
            for (Scheme o : ses.task.options()) ses.optionsJson.add(ServerSchemeJson.of(o, new Random(0)));
        }
        ses.deadline = Instant.now(clock).plusSeconds(ses.rules.timeLimitSec(ses.taskLevel));
        touch(ses);
    }

    private void finishTask(Session ses, int grade, String message) {
        ses.taskDone = true;
        ses.taskGrade = grade;
        ses.message = message;
        ses.messageKind = ses.bonus ? null : grade >= 3 ? "ok" : "bad";
        ses.grades.add(grade);
        ses.kinds.add(ses.task.kind());
        // оценка 3 и выше — задание решено; график не тратит ses.rnd: порядок случайных чисел не меняется
        if (!ses.bonus && ses.task.showsChart(grade >= 3)) ses.chartJson = ServerSchemeJson.of(ses.task.chart(), new Random(0));
        touch(ses);
    }

    /** Если время задания вышло, ставит 2. Возвращает true, когда задание закрыто именно этим вызовом. */
    private boolean expireIfNeeded(Session ses) {
        touch(ses);
        if (ses.finished || ses.taskDone) return false;
        if (!Instant.now(clock).isAfter(ses.deadline.plusSeconds(GRACE_SEC))) return false;
        finishTask(ses, 2, ses.bonus ? "Время вышло." : "Время вышло. Правильный ответ: " + ses.task.correctText() + ".");
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
        Instant windowLimit = Instant.now(clock).minus(WINDOW_TTL);
        usedWindows.values().removeIf(t -> t.isBefore(windowLimit));
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
        m.put("tasksTotal", ses.rules.tasks());
        if (ses.theme.gradesWholeTest()) {                    // за задание — «верно / неверно», оценка — за тест
            m.put("wholeTest", true);
            if (!ses.bonus) m.put("solved", ses.grades.stream().filter(g -> g >= 3).count());
        }
        if (ses.message != null) {
            m.put("message", ses.message);
            m.put("messageKind", ses.messageKind);
        }
        if (ses.finished) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("finalGrade", Grading.format(ses.finalGrade));
            result.put("passed", ses.finalGrade >= 3);
            List<Object> tasks = new ArrayList<>();
            for (int i = 0; !ses.bonus && i < ses.grades.size(); i++) {   // при прибавке — только итог
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
        m.put("rules", ses.theme.rules(ses.taskLevel));
        m.put("timeLimitSec", ses.rules.timeLimitSec(ses.taskLevel));
        long left = Duration.between(Instant.now(clock), ses.deadline).toMillis();
        m.put("secondsLeft", ses.taskDone ? 0 : Math.max(0, (left + 999) / 1000));
        if (ses.taskDone) {
            if (!ses.bonus) m.put("taskGrade", ses.taskGrade);
            m.put("lastTask", ses.taskNumber >= ses.rules.tasks());
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
        if (ses.optionsJson != null) {
            task.put("options", ses.optionsJson);
            // номер верного варианта — только после завершения задания, чтобы подсветить его
            if (ses.taskDone && !ses.bonus) task.put("correct", (int) ses.task.answers().get(0).value());
        }
        if (ses.taskDone && ses.chartJson != null) {
            task.put("chart", ses.chartJson);
            m.put("chartSec", CHART_SEC);
        }
        m.put("task", task);
        return m;
    }
}
