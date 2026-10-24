import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Выгрузка журнала в CSV в формате {@link ResultLog} (UTF-8 с BOM, разделитель «;» — для Excel)
 * плюс столбец «Группа» после времени. «№» — номер записи в базе: он не меняется от фильтров.
 */
final class ServerCsv {

    static final String HEADER = ResultLog.HEADER.replace("Имя", "Группа;Имя");

    private ServerCsv() {
    }

    static byte[] render(List<ServerRepository.ResultRow> rows) {
        StringBuilder sb = new StringBuilder("﻿").append(HEADER).append("\r\n");
        for (ServerRepository.ResultRow r : rows) {
            sb.append(r.id()).append(';')
              .append(ResultLog.DATE.format(r.when())).append(';')
              .append(ResultLog.TIME.format(r.when())).append(';')
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
