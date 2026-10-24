import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Слой данных веб-версии на временном файле SQLite: схема, курсанты, запись и отбор результатов. */
class ServerRepositoryTest {

    private static final LocalDateTime T1 = LocalDateTime.of(2026, 10, 20, 9, 0, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 10, 21, 23, 59, 59);
    private static final LocalDateTime T3 = LocalDateTime.of(2026, 10, 22, 0, 0, 0);

    private Path file;
    private ServerRepository repo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        file = dir.resolve("sub").resolve("test.db");          // папки создаются сами
        repo = new ServerRepository(new ServerDatabase(file));
    }

    private long save(String id, String group, String surname, Theme theme, Level level, double grade, LocalDateTime when) {
        long cadet = repo.upsertCadet(group, surname, when);
        repo.createSession(id, cadet, theme, level, when);
        return repo.finishSession(id, cadet, group, surname, theme, level, grade, List.of(5, 4), when);
    }

    @Test
    void schemaIsCreatedAndReopenable() {
        assertEquals(0, repo.count(ServerRepository.Filter.NONE));
        new ServerDatabase(file);                              // повторное открытие не ломает схему
        assertEquals(0, new ServerRepository(new ServerDatabase(file)).cadetCount());
    }

    @Test
    void upsertCadetIsIdempotent() {
        long a = repo.upsertCadet("КМ-21", "Иванов", T1);
        assertEquals(a, repo.upsertCadet("КМ-21", "Иванов", T2));
        long b = repo.upsertCadet("КМ-22", "Иванов", T1);      // та же фамилия в другой группе — другой курсант
        assertEquals(2, repo.cadetCount());
        assertEquals(false, a == b);
    }

    @Test
    void savesAndReadsResultNewestFirst() {
        long first = save("s1", "КМ-21", "Иванов", Theme.OHM, Level.HARD, 4.5, T1);
        long second = save("s2", "КМ-21", "Петров", Theme.DIVIDERS, null, 5, T2);
        assertEquals(true, second > first);

        List<ServerRepository.ResultRow> rows = repo.find(ServerRepository.Filter.NONE, 10, 0);
        assertEquals(2, rows.size());
        ServerRepository.ResultRow top = rows.get(0);
        assertEquals("Петров", top.surname());
        assertEquals(Theme.DIVIDERS, top.theme());
        assertNull(top.level());                               // тема без выбора сложности
        assertEquals(T2, top.when());
        assertEquals(4.5, rows.get(1).grade());
        assertEquals(Level.HARD, rows.get(1).level());
    }

    @Test
    void filtersByGroupThemeAndInclusiveDates() {
        save("s1", "КМ-21", "Иванов", Theme.OHM, Level.EASY, 5, T1);
        save("s2", "КМ-21", "Петров", Theme.EQUIVALENT, Level.HARD, 3, T2);
        save("s3", "КМ-22", "Сидоров", Theme.OHM, Level.HARD, 2, T3);

        assertEquals(2, repo.count(new ServerRepository.Filter("КМ-21", null, null, null)));
        assertEquals(2, repo.count(new ServerRepository.Filter(null, Theme.OHM, null, null)));
        assertEquals(1, repo.count(new ServerRepository.Filter("КМ-21", Theme.OHM, null, null)));
        // «по 21.10» включает 23:59:59 того дня, но не 22.10 00:00:00
        assertEquals(2, repo.count(new ServerRepository.Filter(null, null, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21))));
        assertEquals(1, repo.count(new ServerRepository.Filter(null, null, LocalDate.of(2026, 10, 22), null)));
        assertEquals(0, repo.count(new ServerRepository.Filter("НЕТ", null, null, null)));
        assertEquals(List.of("КМ-21", "КМ-22"), repo.groups());
    }

    @Test
    void paginates() {
        for (int i = 0; i < 5; i++) save("s" + i, "КМ-21", "Иванов", Theme.OHM, Level.EASY, 5, T1.plusMinutes(i));
        assertEquals(2, repo.find(ServerRepository.Filter.NONE, 2, 0).size());
        assertEquals(1, repo.find(ServerRepository.Filter.NONE, 2, 4).size());
        assertEquals(5, repo.count(ServerRepository.Filter.NONE));
    }

    @Test
    void sessionCannotBeFinishedTwice() {
        long cadet = repo.upsertCadet("КМ-21", "Иванов", T1);
        repo.createSession("s1", cadet, Theme.OHM, Level.EASY, T1);
        assertEquals("in_progress", repo.sessionStatus("s1"));
        repo.finishSession("s1", cadet, "КМ-21", "Иванов", Theme.OHM, Level.EASY, 5, List.of(5, 5), T1);
        assertEquals("finished", repo.sessionStatus("s1"));
        assertThrows(IllegalStateException.class,
                () -> repo.finishSession("s1", cadet, "КМ-21", "Иванов", Theme.OHM, Level.EASY, 5, List.of(5, 5), T1));
        assertEquals(1, repo.count(ServerRepository.Filter.NONE));     // результат не задвоился
        assertNull(repo.sessionStatus("нет такой"));
    }

    @Test
    void sqlSpecialCharactersAreJustData() {
        // значения идут параметрами; фильтр с кавычкой ничего не ломает и ничего не находит
        save("s1", "КМ-21", "О'Брайен", Theme.OHM, Level.EASY, 5, T1);
        assertEquals(0, repo.count(new ServerRepository.Filter("x' OR '1'='1", null, null, null)));
        assertEquals("О'Брайен", repo.find(ServerRepository.Filter.NONE, 1, 0).get(0).surname());
    }
}
