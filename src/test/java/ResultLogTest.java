import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Проверяет {@link ResultLog} — журнал результатов: записи дописываются в конец файла,
 * нумерация сквозная (продолжается после перезапуска программы), формат строки
 * «№;дата;время;имя;тема;сложность;оценка» и заголовок в начале нового файла.
 */
class ResultLogTest {

    private static final LocalDateTime WHEN = LocalDateTime.of(2026, 10, 22, 9, 5, 7);

    @Test
    void appendsLinesWithRunningNumbers(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("results.csv");
        assertEquals(1, new ResultLog(file).append("Иванов И. И.", Theme.OHM, Level.EASY, 4.5, WHEN));
        assertEquals(2, new ResultLog(file).append("  Петров П. П. ", Theme.EQUIVALENT, Level.HARD, 2, WHEN));

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(List.of(
                "﻿" + ResultLog.HEADER,
                "1;22.10.2026;09:05:07;Иванов И. И.;" + Theme.OHM.title + ";Лёгкий;4,5",
                "2;22.10.2026;09:05:07;Петров П. П.;" + Theme.EQUIVALENT.title + ";Сложный;2"), lines);
    }

    @Test
    void themeWithoutLevelsWritesDash(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("results.csv");
        new ResultLog(file).append("Иванов", Theme.DIVIDERS, Level.HARD, 5, WHEN);
        assertEquals("1;22.10.2026;09:05:07;Иванов;" + Theme.DIVIDERS.title + ";—;5",
                Files.readAllLines(file).get(1));
    }

    @Test
    void continuesNumberingFromLastRecord(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("results.csv");
        Files.writeString(file, ResultLog.HEADER + "\n41;01.09.2026;10:00:00;Сидоров;Тема;Лёгкий;5\n\n");
        assertEquals(42, new ResultLog(file).append("Кузнецов", Theme.OHM, Level.EASY, 5, WHEN));
    }

    @Test
    void semicolonInNameDoesNotBreakColumns(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("results.csv");
        new ResultLog(file).append("Иванов;Петров", Theme.OHM, Level.HARD, 3, WHEN);
        assertEquals("1;22.10.2026;09:05:07;Иванов,Петров;" + Theme.OHM.title + ";Сложный;3",
                Files.readAllLines(file).get(1));
    }
}
