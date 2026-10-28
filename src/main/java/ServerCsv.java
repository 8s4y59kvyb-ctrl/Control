import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Выгрузка журнала в CSV (UTF-8 с BOM, разделитель «;» — для Excel).
 * «№» — номер записи в базе: он не меняется от фильтров.
 */
final class ServerCsv {

    static final String HEADER = "№;Дата;Время;Группа;Имя;Тема;Сложность;Оценка";
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private ServerCsv() {
    }

    static byte[] render(List<ServerRepository.ResultRow> rows) {
        StringBuilder sb = new StringBuilder("﻿").append(HEADER).append("\r\n");
        for (ServerRepository.ResultRow r : rows) {
            sb.append(r.id()).append(';')
              .append(DATE.format(r.when())).append(';')
              .append(TIME.format(r.when())).append(';')
              .append(cell(r.group())).append(';')
              .append(cell(r.surname())).append(';')
              .append(r.theme().title).append(';')
              .append(r.theme().hasLevels && r.level() != null ? r.level().title : "—").append(';')
              .append(Grading.format(r.grade()))
              .append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** «;» и переводы строк ломают столбцы (ввод и так проверяется, это вторая линия защиты). */
    private static String cell(String s) {
        return s.replace(';', ',').replace('\r', ' ').replace('\n', ' ').trim();
    }
}
