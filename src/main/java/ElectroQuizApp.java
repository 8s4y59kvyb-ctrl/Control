import javafx.animation.Animation;
import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Контроль знаний по электротехнике — окно программы.
 * Курсанту показывается цепь, нужно найти ток источника I (или другую величину по теме).
 * Каждый тест состоит из двух заданий; итоговая оценка — среднее за два, при любой двойке итог 2.
 * На выполнение каждого задания отводится время темы; если время вышло — оценка 2.
 * Отсчёт времени начинается после ввода фамилии и нажатия «Начать тестирование».
 *
 * Темы и их задания описаны в {@link Theme}, уровни сложности — в {@link Level},
 * схемы рисуют реализации {@link Scheme}; этот класс отвечает только за интерфейс и ход теста.
 */
public class ElectroQuizApp extends Application {

    private static final int MAX_DOTS = 3;
    private static final int WARN_SEC = 60;
    private static final int TASKS_PER_TEST = 2;   // в каждой теме курсант решает два задания

    private final Random rnd = new Random();
    private final ResultLog resultLog = ResultLog.inDocuments();    // журнал итоговых оценок

    // состояние тестирования
    private boolean started = false;               // нажата «Начать тестирование»
    private Level level = Level.HARD;              // выбранный в шапке уровень
    private Level taskLevel = Level.HARD;          // уровень текущего задания
    private Theme theme = Theme.OHM;               // выбранная тема
    private TaskGenerator generator;               // генератор заданий текущего теста
    private Task task;                             // текущее задание
    private int attempt = 0;
    private int taskNumber = 0;
    private final List<Integer> taskGrades = new ArrayList<>();    // оценки за задания теста
    private final List<String> taskNames = new ArrayList<>();      // описание каждого задания для итога
    private int secondsLeft = theme.timeLimitSec;
    private boolean taskFinished = false;
    private boolean solved = false;
    private Timeline timer;

    // элементы интерфейса
    private final TextField nameField = new TextField();
    private final ToggleGroup levelGroup = new ToggleGroup();
    private final ToggleButton easyBtn = new ToggleButton("Лёгкий · 1 попытка");
    private final ToggleButton hardBtn = new ToggleButton("Сложный · 3 попытки");
    private final Button startBtn = new Button("Начать тестирование");
    private final Label startHint = new Label();
    private final ComboBox<Theme> themeBox = new ComboBox<>();
    private final Label themeLabel = new Label();
    private final Label answerSymbol = new Label("I =");
    private final Label answerUnit = new Label("А");
    private final TextField answerField2 = new TextField();     // второе значение (тема 3)
    private final Label answerSymbol2 = new Label("I2 =");
    private final Label answerUnit2 = new Label("А");
    private HBox answerGroup2;
    private Label levelLabelNode;                               // «Сложность:» (скрывается в теме 3)
    private HBox levelBoxNode;                                  // переключатель сложности
    private VBox startCard;
    private VBox workBox;
    private VBox resultCard;                                    // итог теста
    private final Label resultInfo = new Label();
    private final Label resultGrade = new Label();
    private final Label resultNote = new Label();
    private final Label taskTitle = new Label();
    private final Label taskText = new Label();
    private final TextField answerField = new TextField();
    private final Button checkBtn = new Button("Проверить");
    private final Button nextBtn = new Button("Следующее задание");
    private final Button newTestBtn = new Button("Новый тест");
    private final Label feedback = new Label();
    private final Label timeValue = new Label();
    private final Label timeNote = new Label();
    private final ProgressBar timeBar = new ProgressBar(1);
    private final Label gradeValue = new Label("—");
    private final Label gradeCaption = new Label("оценка за задание");
    private final Label rulesLabel = new Label();
    private final Circle[] dots = new Circle[MAX_DOTS];
    private final Label attemptsLabel = new Label();
    private final Canvas canvas = new Canvas(600, 300);

    // анимация тока: поверх схемы лежит прозрачный слой, на нём рисуются движущиеся точки
    private static final double FLOW_SPEED = 70;     // скорость точек, пикселей в секунду
    private final Canvas overlay = new Canvas(600, 300);
    private final CheckBox flowCheck = new CheckBox("Показывать движение тока");
    private final List<FlowPath> flowPaths = new ArrayList<>();
    private AnimationTimer flowTimer;
    private long flowStartNs = 0;

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");
        root.setTop(buildHeader());
        root.setCenter(buildCenter());
        root.setRight(buildSidePanel());

        Scene scene = new Scene(root, 1140, 720);
        scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());

        // иконка окна и панели задач (файл icon.png лежит в src/main/resources)
        java.io.InputStream iconStream = getClass().getResourceAsStream("icon.png");
        if (iconStream != null) stage.getIcons().add(new Image(iconStream));

        stage.setTitle("Электротехника — контроль знаний");
        stage.setScene(scene);
        stage.setMinWidth(1040);
        stage.setMinHeight(680);
        stage.setOnCloseRequest(ev -> {
            stopTimer();
            if (flowTimer != null) flowTimer.stop();
        });
        stage.show();

        showIdleState();
        nameField.requestFocus();
    }

    // ---------- Верхняя панель ----------
    private HBox buildHeader() {
        Label title = new Label("⚡ Электротехника");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("Контроль знаний · расчёт электрических цепей");
        subtitle.getStyleClass().add("app-subtitle");
        VBox titles = new VBox(2, title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // переключатель уровня сложности
        Label levelLabel = new Label("Сложность:");
        levelLabel.getStyleClass().add("header-muted");
        easyBtn.setToggleGroup(levelGroup);
        hardBtn.setToggleGroup(levelGroup);
        easyBtn.getStyleClass().addAll("level-btn", "level-left");
        hardBtn.getStyleClass().addAll("level-btn", "level-right");
        hardBtn.setSelected(true);
        levelGroup.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null) {            // нельзя снять выбор
                old.setSelected(true);
                return;
            }
            level = (now == easyBtn) ? Level.EASY : Level.HARD;
            if (!started) {               // до старта сразу показываем правила выбранного уровня
                taskLevel = theme.effectiveLevel(level);
                showIdleState();
            }                             // после старта переключатель заблокирован до конца теста
        });
        HBox levelBox = new HBox(0, easyBtn, hardBtn);
        levelBox.setAlignment(Pos.CENTER_LEFT);
        levelLabelNode = levelLabel;
        levelBoxNode = levelBox;

        Label who = new Label("Курсант:");
        who.getStyleClass().add("header-muted");
        nameField.setPromptText("Фамилия И. О.");
        nameField.setPrefWidth(200);
        // только русские буквы; пробел, точка и дефис — для инициалов и двойных фамилий
        nameField.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().matches("[А-Яа-яЁё .-]*") ? change : null));
        nameField.setOnAction(ev -> onStart());

        HBox header = new HBox(14, titles, spacer, levelLabel, levelBox, who, nameField);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 24, 14, 24));
        header.getStyleClass().add("header");
        return header;
    }

    // ---------- Центральная часть: стартовая карточка / рабочая область ----------
    private StackPane buildCenter() {
        // --- стартовая карточка ---
        Label startTitle = new Label("Контроль знаний по электротехнике");
        startTitle.getStyleClass().add("start-title");
        Label startText = new Label(
                "Введите фамилию в поле «Курсант» (вверху справа), выберите тему и сложность и нажмите "
                        + "«Начать тестирование». На задание отводится 7 минут, а в теме «Делители тока "
                        + "и напряжения» (в ней нет выбора сложности, два задания) — 5 минут на каждое. "
                        + "Время пойдёт сразу после нажатия кнопки.");
        startText.setWrapText(true);
        startText.getStyleClass().add("task-text");
        // меню выбора темы
        Label themeCaption = new Label("Тема:");
        themeCaption.getStyleClass().add("card-title");
        themeBox.getItems().addAll(Theme.values());
        themeBox.setValue(theme);
        themeBox.setMaxWidth(Double.MAX_VALUE);
        themeBox.getStyleClass().add("theme-combo");
        themeBox.valueProperty().addListener((obs, old, now) -> {
            if (now == null) return;
            theme = now;
            startHint.setText("");
            taskLevel = theme.effectiveLevel(level);
            updateLevelControls();                 // в теме 3 выбор сложности скрыт
            if (!started) showIdleState();         // показываем время и правила выбранной темы
        });
        VBox themeRow = new VBox(6, themeCaption, themeBox);

        startBtn.getStyleClass().addAll("primary-btn", "start-btn");
        startBtn.setOnAction(ev -> onStart());
        startHint.setWrapText(true);
        startHint.getStyleClass().addAll("feedback", "fb-warn");

        startCard = new VBox(16, startTitle, startText, themeRow, startBtn, startHint);
        startCard.getStyleClass().add("card");
        startCard.setMaxHeight(Region.USE_PREF_SIZE);

        // --- рабочая область ---
        taskTitle.getStyleClass().add("card-title");
        themeLabel.getStyleClass().add("muted");
        VBox titleBox = new VBox(2, taskTitle, themeLabel);
        flowCheck.setSelected(false);          // по умолчанию анимация выключена
        flowCheck.getStyleClass().add("flow-check");
        Region headSpacer = new Region();
        HBox.setHgrow(headSpacer, Priority.ALWAYS);
        HBox cardHead = new HBox(10, titleBox, headSpacer, flowCheck);
        cardHead.setAlignment(Pos.CENTER_LEFT);

        overlay.setMouseTransparent(true);
        StackPane circuitPane = new StackPane(canvas, overlay);
        circuitPane.setAlignment(Pos.TOP_LEFT);
        circuitPane.setMaxWidth(Region.USE_PREF_SIZE);
        VBox circuitCard = new VBox(10, cardHead, circuitPane);
        circuitCard.getStyleClass().add("card");

        taskText.setWrapText(true);
        taskText.getStyleClass().add("task-text");

        answerField.setPromptText("Например, 1,25");
        answerField.setPrefWidth(200);
        answerField.setOnAction(ev -> checkAnswer());
        answerUnit.getStyleClass().add("unit");

        checkBtn.getStyleClass().add("primary-btn");
        checkBtn.setOnAction(ev -> checkAnswer());
        nextBtn.getStyleClass().add("secondary-btn");
        nextBtn.setOnAction(ev -> onNext());
        nextBtn.setDisable(true);

        // второе поле ответа: показывается, когда в задании два значения (тема 3: I1 и I2 либо U1 и U2)
        answerField2.setPromptText("Например, 1,25");
        answerField2.setPrefWidth(120);
        answerField2.setOnAction(ev -> checkAnswer());
        answerUnit2.getStyleClass().add("unit");
        answerGroup2 = new HBox(8, answerSymbol2, answerField2, answerUnit2);
        answerGroup2.setAlignment(Pos.CENTER_LEFT);
        answerGroup2.setVisible(false);
        answerGroup2.setManaged(false);

        HBox inputRow = new HBox(10, answerSymbol, answerField, answerUnit, answerGroup2, checkBtn, nextBtn);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        feedback.setWrapText(true);
        feedback.getStyleClass().add("feedback");

        VBox taskCard = new VBox(12, taskText, inputRow, feedback);
        taskCard.getStyleClass().add("card");

        workBox = new VBox(16, circuitCard, taskCard);
        workBox.setVisible(false);
        workBox.setManaged(false);

        // --- итоговая карточка (после двух заданий) ---
        Label resultTitle = new Label("Тестирование завершено");
        resultTitle.getStyleClass().add("start-title");
        resultInfo.setWrapText(true);
        resultInfo.getStyleClass().add("task-text");
        resultGrade.getStyleClass().add("grade-value");
        Label resultCaption = new Label("итоговая оценка");
        resultCaption.getStyleClass().add("muted");
        VBox resultGradeBox = new VBox(0, resultGrade, resultCaption);
        resultNote.setWrapText(true);
        resultNote.getStyleClass().add("muted");
        newTestBtn.getStyleClass().add("primary-btn");
        newTestBtn.setOnAction(ev -> onNewTest());
        resultCard = new VBox(14, resultTitle, resultInfo, resultGradeBox, resultNote, newTestBtn);
        resultCard.getStyleClass().add("card");
        resultCard.setMaxHeight(Region.USE_PREF_SIZE);
        resultCard.setVisible(false);
        resultCard.setManaged(false);

        StackPane center = new StackPane(workBox, startCard, resultCard);
        StackPane.setAlignment(startCard, Pos.TOP_CENTER);
        StackPane.setAlignment(resultCard, Pos.TOP_CENTER);
        center.setPadding(new Insets(20, 12, 20, 24));
        return center;
    }

    // ---------- Правая панель: время, попытки, оценка ----------
    private VBox buildSidePanel() {
        // время
        Label timeTitle = new Label("Время");
        timeTitle.getStyleClass().add("card-title");
        timeValue.getStyleClass().add("timer-value");
        timeBar.setMaxWidth(Double.MAX_VALUE);
        timeBar.getStyleClass().add("timer-bar");
        timeNote.setWrapText(true);
        timeNote.getStyleClass().add("muted");
        VBox timeBox = new VBox(8, timeTitle, timeValue, timeBar, timeNote);
        timeBox.getStyleClass().add("card");

        // попытки
        Label attemptsTitle = new Label("Попытки");
        attemptsTitle.getStyleClass().add("card-title");
        HBox dotsBox = new HBox(10);
        dotsBox.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < MAX_DOTS; i++) {
            dots[i] = new Circle(13);
            dots[i].getStyleClass().add("dot");
            dotsBox.getChildren().add(dots[i]);
        }
        attemptsLabel.getStyleClass().add("muted");
        VBox attemptsBox = new VBox(10, attemptsTitle, dotsBox, attemptsLabel);
        attemptsBox.getStyleClass().add("card");

        // оценка
        Label gradeTitle = new Label("Оценка");
        gradeTitle.getStyleClass().add("card-title");
        gradeValue.getStyleClass().add("grade-value");
        gradeCaption.getStyleClass().add("muted");
        VBox gradeNumber = new VBox(0, gradeValue, gradeCaption);
        gradeNumber.setAlignment(Pos.CENTER);
        rulesLabel.getStyleClass().add("rules");
        VBox gradeBox = new VBox(10, gradeTitle, gradeNumber, rulesLabel);
        gradeBox.getStyleClass().add("card");

        VBox side = new VBox(14, timeBox, attemptsBox, gradeBox);
        side.setPadding(new Insets(20, 24, 20, 12));
        side.setPrefWidth(300);
        return side;
    }

    // ---------- Ход теста ----------
    /** Состояние до нажатия «Начать тестирование»: таймер стоит, показаны правила выбранного уровня. */
    private void showIdleState() {
        attempt = 0;
        solved = false;
        taskFinished = false;
        secondsLeft = theme.timeLimitSec;
        updateTimerView();
        timeNote.setText("Отсчёт начнётся после нажатия «Начать тестирование»");
        rulesLabel.setText(taskLevel.rules());
        updateAttempts();
        updateLevelLock();
    }

    private void onStart() {
        if (started) return;
        if (nameField.getText().trim().isEmpty()) {
            startHint.setText("Введите фамилию, чтобы начать тестирование.");
            nameField.requestFocus();
            return;
        }
        started = true;
        generator = theme.newGenerator();
        startHint.setText("");
        startCard.setVisible(false);
        startCard.setManaged(false);
        workBox.setVisible(true);
        workBox.setManaged(true);
        startTask();
    }

    /** Запускает новое задание и обратный отсчёт времени. */
    private void startTask() {
        stopTimer();
        taskNumber++;
        taskLevel = theme.effectiveLevel(level);
        attempt = 0;
        taskFinished = false;
        solved = false;
        task = generator.next(taskLevel, rnd);

        taskTitle.setText(theme.hasLevels
                ? "Задание №" + taskNumber + " · " + taskLevel.title
                : "Задание №" + taskNumber);                       // в теме 3 уровней сложности нет
        themeLabel.setText("Тема: " + theme.title);
        taskText.setText(task.text());

        // поля ответа: одно (I или Z) либо два (тема 3: I1 и I2 либо U1 и U2)
        List<Answer> answers = task.answers();
        boolean two = answers.size() > 1;
        answerSymbol.setText(answers.get(0).symbol() + " =");
        answerUnit.setText(answers.get(0).unit());
        answerGroup2.setVisible(two);
        answerGroup2.setManaged(two);
        if (two) {
            answerSymbol2.setText(answers.get(1).symbol() + " =");
            answerUnit2.setText(answers.get(1).unit());
        }
        answerField.setPrefWidth(two ? 120 : 200);
        answerField.clear();
        answerField.setDisable(false);
        answerField2.clear();
        answerField2.setDisable(false);

        checkBtn.setDisable(false);
        nextBtn.setDisable(true);
        nextBtn.setText("Следующее задание");
        gradeValue.setText("—");
        gradeValue.getStyleClass().removeAll("grade-good", "grade-bad");
        rulesLabel.setText(taskLevel.rules());
        timeNote.setText("На задание отводится " + theme.minutesText());
        setFeedback("", null);
        updateAttempts();
        updateLevelLock();
        drawScheme(task.scheme());
        flowPaths.clear();
        flowPaths.addAll(task.scheme().flowPaths(rnd));
        flowCheck.setVisible(!flowPaths.isEmpty());    // в схеме с конденсаторами тока нет
        startFlow();

        secondsLeft = theme.timeLimitSec;
        updateTimerView();
        timer = new Timeline(new KeyFrame(Duration.seconds(1), ev -> tick()));
        timer.setCycleCount(Animation.INDEFINITE);
        timer.play();

        answerField.requestFocus();
    }

    /** Кнопка после задания: следующее задание либо (после двух заданий) итог. */
    private void onNext() {
        if (taskGrades.size() >= TASKS_PER_TEST) {
            showResult();
        } else {
            startTask();
        }
    }

    /** Итог теста: среднее за два задания; если хотя бы за одно стоит 2, итоговая оценка 2. */
    private void showResult() {
        stopTimer();
        StringBuilder sb = new StringBuilder("Курсант: " + nameField.getText().trim()
                + "\nТема: " + theme.title + "\n");
        for (int i = 0; i < taskGrades.size(); i++) {
            sb.append("\nЗадание ").append(i + 1).append(" (").append(taskNames.get(i))
              .append("): оценка ").append(taskGrades.get(i));
        }
        boolean anyTwo = Grading.hasFail(taskGrades);
        double finalGrade = Grading.finalGrade(taskGrades);

        resultInfo.setText(sb.toString());
        resultGrade.setText(Grading.format(finalGrade));
        resultGrade.getStyleClass().removeAll("grade-good", "grade-bad");
        resultGrade.getStyleClass().add(finalGrade >= 3 ? "grade-good" : "grade-bad");
        String note = anyTwo
                ? "За одно из заданий поставлена оценка 2, поэтому итоговая оценка — 2."
                : "Итоговая оценка — среднее за два задания: " + Grading.format(Grading.average(taskGrades)) + ".";
        try {
            int number = resultLog.append(nameField.getText(), theme, level, finalGrade,
                    java.time.LocalDateTime.now());
            note += "\nРезультат сохранён в журнал (запись №" + number + "): " + resultLog.file();
        } catch (java.io.IOException ex) {
            note += "\nНе удалось сохранить результат в " + resultLog.file() + ": " + ex.getMessage();
        }
        resultNote.setText(note);

        workBox.setVisible(false);
        workBox.setManaged(false);
        resultCard.setVisible(true);
        resultCard.setManaged(true);
        nameField.setDisable(true);
        easyBtn.setDisable(true);
        hardBtn.setDisable(true);
        newTestBtn.requestFocus();
    }

    /** Кнопка «Новый тест» на итоговой карточке: сбрасывает тест и возвращает к стартовой карточке. */
    private void onNewTest() {
        stopTimer();
        started = false;
        generator = null;
        task = null;
        taskNumber = 0;
        taskGrades.clear();
        taskNames.clear();
        flowPaths.clear();
        gradeValue.setText("—");
        gradeValue.getStyleClass().removeAll("grade-good", "grade-bad");
        setFeedback("", null);

        resultCard.setVisible(false);
        resultCard.setManaged(false);
        startCard.setVisible(true);
        startCard.setManaged(true);

        nameField.clear();                 // тест будет проходить следующий курсант
        taskLevel = theme.effectiveLevel(level);
        showIdleState();                   // снимает блокировку фамилии и сложности
        nameField.requestFocus();
    }

    private void tick() {
        secondsLeft--;
        updateTimerView();
        if (secondsLeft <= 0) {
            finishTask(2, "Время вышло. Правильный ответ: " + task.correctText() + ".");
        }
    }

    private void checkAnswer() {
        if (!started || taskFinished) return;
        boolean two = task.answers().size() > 1;
        double[] values;
        try {
            values = two
                    ? new double[]{Answer.parse(answerField.getText()), Answer.parse(answerField2.getText())}
                    : new double[]{Answer.parse(answerField.getText())};
        } catch (NumberFormatException ex) {
            setFeedback(two ? "Введите оба числа, например 1,25. Попытка не потрачена."
                            : "Введите число, например 1,25. Попытка не потрачена.", "warn");
            return;
        }
        attempt++;
        int right = task.countCorrect(values);
        boolean ok = right == values.length;    // попытка засчитывается, если верны все значения
        if (ok) {
            solved = true;
            int grade = taskLevel.gradeForAttempt(attempt);
            finishTask(grade, "Верно! " + task.correctText() + ". Задание решено с попытки " + attempt + ".");
        } else if (attempt >= taskLevel.attempts) {
            String prefix = (taskLevel == Level.EASY) ? "Неверно. В лёгком режиме одна попытка. "
                                                      : "Попытки закончились. ";
            finishTask(2, prefix + "Правильный ответ: " + task.correctText() + ".");
        } else {
            setFeedback(two
                    ? "Неверно: верно введено " + right + " из " + values.length + " значений. " + task.hint()
                    : "Неверно. " + task.hint(), "bad");
            answerField.selectAll();
            updateAttempts();
        }
    }

    private void finishTask(int grade, String message) {
        stopTimer();
        taskFinished = true;
        gradeValue.setText(String.valueOf(grade));
        gradeValue.getStyleClass().removeAll("grade-good", "grade-bad");
        gradeValue.getStyleClass().add(grade >= 3 ? "grade-good" : "grade-bad");
        setFeedback(message, grade >= 3 ? "ok" : "bad");
        answerField.setDisable(true);
        answerField2.setDisable(true);
        checkBtn.setDisable(true);
        nextBtn.setDisable(false);
        // запоминаем оценку за задание; после последнего задания кнопка ведёт к итогу
        taskGrades.add(grade);
        taskNames.add(task.kind());
        nextBtn.setText(taskGrades.size() >= TASKS_PER_TEST
                ? "Завершить тестирование" : "Следующее задание");
        updateAttempts();
        updateLevelLock();
    }

    /** В теме без выбора сложности переключатель не нужен — прячем его. */
    private void updateLevelControls() {
        boolean show = theme.hasLevels;
        levelLabelNode.setVisible(show);
        levelLabelNode.setManaged(show);
        levelBoxNode.setVisible(show);
        levelBoxNode.setManaged(show);
    }

    private void stopTimer() {
        if (timer != null) timer.stop();
    }

    /** Весь тест (от «Начать тестирование» до «Новый тест») менять сложность и фамилию нельзя. */
    private void updateLevelLock() {
        easyBtn.setDisable(started);
        hardBtn.setDisable(started);
        nameField.setDisable(started);
    }

    private void updateTimerView() {
        int s = Math.max(secondsLeft, 0);
        timeValue.setText(String.format("%02d:%02d", s / 60, s % 60));
        timeBar.setProgress((double) s / theme.timeLimitSec);
        boolean warn = started && !taskFinished && s <= WARN_SEC;
        timeValue.getStyleClass().remove("timer-warn");
        timeBar.getStyleClass().remove("timer-warn");
        if (warn) {
            timeValue.getStyleClass().add("timer-warn");
            timeBar.getStyleClass().add("timer-warn");
        }
    }

    private void updateAttempts() {
        for (int i = 0; i < MAX_DOTS; i++) {
            boolean shown = i < taskLevel.attempts;
            dots[i].setVisible(shown);
            dots[i].setManaged(shown);
            dots[i].getStyleClass().removeAll("dot-free", "dot-used", "dot-ok");
            if (i < attempt) {
                boolean good = solved && i == attempt - 1;
                dots[i].getStyleClass().add(good ? "dot-ok" : "dot-used");
            } else {
                dots[i].getStyleClass().add("dot-free");
            }
        }
        attemptsLabel.setText("Использовано " + attempt + " из " + taskLevel.attempts);
    }

    private void setFeedback(String text, String kind) {
        feedback.setText(text);
        feedback.getStyleClass().removeAll("fb-ok", "fb-bad", "fb-warn");
        if (kind != null) feedback.getStyleClass().add("fb-" + kind);
    }

    // ---------- Схема и анимация тока ----------
    private void drawScheme(Scheme scheme) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        g.setStroke(Color.web(Scheme.WIRE));
        g.setFill(Color.web(Scheme.WIRE));
        g.setLineWidth(2.2);
        scheme.draw(new FxPainter(g));
    }

    private void startFlow() {
        flowStartNs = 0;                 // отсчёт времени анимации начнётся с ближайшего кадра
        if (flowTimer == null) {
            flowTimer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    drawFlow(now);
                }
            };
            flowTimer.start();
        }
    }

    private void drawFlow(long now) {
        GraphicsContext g = overlay.getGraphicsContext2D();
        g.clearRect(0, 0, overlay.getWidth(), overlay.getHeight());
        if (!flowCheck.isSelected() || flowPaths.isEmpty()) return;
        if (flowStartNs == 0) flowStartNs = now;

        double shift = (now - flowStartNs) / 1e9 * FLOW_SPEED;
        g.setFill(Color.web(Scheme.BLUE));
        g.setStroke(Color.WHITE);
        g.setLineWidth(1);
        for (FlowPath p : flowPaths) {
            for (int i = 0; i < p.count; i++) {
                double s = (p.phase + i * p.spacing + shift) % p.len;
                double[] xy = p.pointAt(s);
                g.fillOval(xy[0] - 3.5, xy[1] - 3.5, 7, 7);
                g.strokeOval(xy[0] - 3.5, xy[1] - 3.5, 7, 7);
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
