import java.util.List;
import java.util.stream.Collectors;

/**
 * Одно сгенерированное задание.
 *
 * @param kind    краткое описание для итога: «сложный уровень», «делитель тока»
 * @param text    условие задания
 * @param answers искомые величины (одна или две), в порядке полей ввода
 * @param hint    подсказка после неверной попытки
 * @param scheme  рисунок схемы и пути тока для анимации
 * @param chart   график, который показывается вместо схемы после верного ответа (тема 4), иначе null
 * @param chartAlways график показывается при любом исходе задания, а не только после верного ответа (тема 5)
 * @param options варианты-картинки для задания на выбор (модуляция): ответ — номер варианта с 1;
 *                пусто — обычное задание с числовыми полями
 */
record Task(String kind, String text, List<Answer> answers, String hint, Scheme scheme, Scheme chart,
            boolean chartAlways, List<Scheme> options) {

    Task {
        answers = List.copyOf(answers);
        options = List.copyOf(options);
    }

    /** Задание без графика после верного ответа. */
    Task(String kind, String text, List<Answer> answers, String hint, Scheme scheme) {
        this(kind, text, answers, hint, scheme, null);
    }

    /** Задание с графиком только после верного ответа. */
    Task(String kind, String text, List<Answer> answers, String hint, Scheme scheme, Scheme chart) {
        this(kind, text, answers, hint, scheme, chart, false);
    }

    /** Задание с графиком; chartAlways — показывать ли его и после неверного ответа. */
    Task(String kind, String text, List<Answer> answers, String hint, Scheme scheme, Scheme chart,
         boolean chartAlways) {
        this(kind, text, answers, hint, scheme, chart, chartAlways, List.of());
    }

    /** Задание на выбор: дана картинка scheme, нужно выбрать вариант из options; correct — его номер с 1. */
    static Task choice(String kind, String text, String hint, Scheme scheme, List<Scheme> options, int correct) {
        return new Task(kind, text, List.of(new Answer("Вариант", "", correct)), hint, scheme, null, false, options);
    }

    boolean isChoice() {
        return !options.isEmpty();
    }

    /** Показывать ли график после задания, решённого (solved) или нет. */
    boolean showsChart(boolean solved) {
        return chart != null && (solved || chartAlways);
    }

    /** Сколько введённых значений верны; values идут в том же порядке, что и answers. */
    int countCorrect(double[] values) {
        int right = 0;
        for (int i = 0; i < answers.size(); i++) {
            if (answers.get(i).accepts(values[i])) right++;
        }
        return right;
    }

    /** Правильные ответы для сообщений, например «I1 = 1,00 А, I2 = 2,00 А»; в задании на выбор — «вариант 3 (АМК)». */
    String correctText() {
        if (isChoice()) return "вариант " + (int) answers.get(0).value() + " (" + kind + ")";
        return answers.stream().map(Answer::text).collect(Collectors.joining(", "));
    }
}
