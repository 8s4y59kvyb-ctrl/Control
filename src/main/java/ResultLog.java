import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Журнал результатов: каждый завершённый тест дописывается в конец файла новой строкой
 * «№;дата;время;имя;тема;сложность;оценка». Нумерация сквозная — продолжается с номера последней записи.
 * Разделитель «;» и UTF-8 с BOM — чтобы файл сразу правильно открывался в Excel.
 */
final class ResultLog {

    static final String HEADER = "№;Дата;Время;Имя;Тема;Сложность;Оценка";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Path file;

    ResultLog(Path file) {
        this.file = file;
    }

    /** Файл по умолчанию: «Документы\Результаты ElectroQuiz.csv» (или домашняя папка, если «Документов» нет). */
    static ResultLog inDocuments() {
        Path home = Paths.get(System.getProperty("user.home"));
        Path docs = home.resolve("Documents");
        return new ResultLog((Files.isDirectory(docs) ? docs : home).resolve("Результаты ElectroQuiz.csv"));
    }

    Path file() {
        return file;
    }

    /** Дописывает запись и возвращает её номер. В теме без выбора сложности вместо уровня пишется «—». */
    int append(String name, Theme theme, Level level, double grade, LocalDateTime when) throws IOException {
        int number = lastNumber() + 1;
        StringBuilder sb = new StringBuilder();
        if (!Files.exists(file) || Files.size(file) == 0) {
            sb.append('﻿').append(HEADER).append(System.lineSeparator());
        }
        sb.append(number).append(';')
          .append(DATE.format(when)).append(';')
          .append(TIME.format(when)).append(';')
          .append(name.replace(';', ',').trim()).append(';')        // «;» в имени сломал бы столбцы
          .append(theme.title).append(';')
          .append(theme.hasLevels ? level.title : "—").append(';')
          .append(Grading.format(grade))
          .append(System.lineSeparator());
        Files.writeString(file, sb, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return number;
    }

    /** Номер последней записи в файле; 0, если файла нет или записей ещё не было. */
    private int lastNumber() throws IOException {
        if (!Files.exists(file)) return 0;
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = lines.size() - 1; i >= 0; i--) {
            String first = lines.get(i).split(";", 2)[0].trim();
            if (first.isEmpty()) continue;
            try {
                return Integer.parseInt(first);
            } catch (NumberFormatException ex) {
                return 0;                                  // дошли до заголовка
            }
        }
        return 0;
    }
}
