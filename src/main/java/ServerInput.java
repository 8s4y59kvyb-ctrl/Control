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

    private static final Pattern GROUP = Pattern.compile("\\d+(-\\d+)*");                    // например 10-52
    private static final Pattern SURNAME = Pattern.compile("[А-ЯЁа-яё]+([ -][А-ЯЁа-яё]+)*");  // только русские буквы
    private static final Pattern DASHES = Pattern.compile("\\s*[-‐‑‒–—−]\\s*");             // тире, минус, дефис

    private ServerInput() {
    }

    /** Группа: только цифры через дефис; любое тире или минус приводится к дефису («10 – 52» → «10-52»). */
    static String group(String raw) {
        String s = DASHES.matcher(squash(raw)).replaceAll("-");
        if (s.isEmpty()) throw ServerError.badRequest("Введите номер группы.");
        if (s.length() > MAX_GROUP) {
            throw ServerError.badRequest("Номер группы слишком длинный (не больше " + MAX_GROUP + " символов).");
        }
        if (!GROUP.matcher(s).matches()) {
            throw ServerError.badRequest("Номер группы — только цифры и тире, например 10-52.");
        }
        return s;
    }

    /** Фамилия: только русские буквы; каждая часть (через пробел или дефис) с заглавной буквы, остальные строчные. */
    static String surname(String raw) {
        String s = DASHES.matcher(squash(raw)).replaceAll("-");
        if (s.isEmpty()) throw ServerError.badRequest("Введите фамилию.");
        if (s.length() > MAX_SURNAME) {
            throw ServerError.badRequest("Фамилия слишком длинная (не больше " + MAX_SURNAME + " символов).");
        }
        if (!SURNAME.matcher(s).matches()) throw ServerError.badRequest("Фамилию введите русскими буквами.");
        StringBuilder sb = new StringBuilder(s.length());
        boolean start = true;
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            sb.append(start ? Character.toUpperCase(c) : c);
            start = c == ' ' || c == '-';
        }
        return sb.toString();
    }

    private static String squash(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
    }
}
