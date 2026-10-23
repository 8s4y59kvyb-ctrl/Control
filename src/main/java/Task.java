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
 */
record Task(String kind, String text, List<Answer> answers, String hint, Scheme scheme) {

    Task {
        answers = List.copyOf(answers);
    }

    /** Сколько введённых значений верны; values идут в том же порядке, что и answers. */
    int countCorrect(double[] values) {
        int right = 0;
        for (int i = 0; i < answers.size(); i++) {
            if (answers.get(i).accepts(values[i])) right++;
        }
        return right;
    }

    /** Правильные ответы для сообщений, например «I1 = 1,00 А, I2 = 2,00 А». */
    String correctText() {
        return answers.stream().map(Answer::text).collect(Collectors.joining(", "));
    }
}
