import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Проверка и приведение к единому виду группы и фамилии курсанта.
 * Единый вид нужен, чтобы «иванов» и «Иванов » были одним курсантом. Белый список символов заодно
 * защищает CSV: нет «;», кавычек и ведущих «=», «+», «@», с которых Excel начинает формулу.
 */
final class ServerInput {

    static final int MAX_GROUP = 20;
    static final int MAX_SURNAME = 80;                    // вмещает полное ФИО

    private static final Pattern GROUP = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N} /-]*");
    private static final Pattern SURNAME = Pattern.compile("\\p{L}[\\p{L} .'-]*");

    private ServerInput() {
    }

    /** Группа: пробелы по краям убираются, повторные схлопываются, буквы — заглавные («км-21» → «КМ-21»). */
    static String group(String raw) {
        String s = squash(raw);
        if (s.isEmpty()) throw ServerError.badRequest("Введите номер группы.");
        if (s.length() > MAX_GROUP) {
            throw ServerError.badRequest("Номер группы слишком длинный (не больше " + MAX_GROUP + " символов).");
        }
        if (!GROUP.matcher(s).matches()) throw ServerError.badRequest("Номер группы содержит недопустимые символы.");
        return s.toUpperCase(Locale.ROOT);
    }

    /** Фамилия: каждая часть (через пробел или дефис) с заглавной буквы, остальные строчные. */
    static String surname(String raw) {
        String s = squash(raw);
        if (s.isEmpty()) throw ServerError.badRequest("Введите фамилию.");
        if (s.length() > MAX_SURNAME) {
            throw ServerError.badRequest("Фамилия слишком длинная (не больше " + MAX_SURNAME + " символов).");
        }
        if (!SURNAME.matcher(s).matches()) throw ServerError.badRequest("Фамилия содержит недопустимые символы.");
        StringBuilder sb = new StringBuilder(s.length());
        boolean start = true;
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            sb.append(start ? Character.toUpperCase(c) : c);
            start = c == ' ' || c == '-' || c == '\'' || c == '.';
        }
        return sb.toString();
    }

    private static String squash(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
    }
}
