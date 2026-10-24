import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Точка входа веб-версии: java -jar ElectroQuizServer.jar.
 * Настройки — переменные окружения ELECTROQUIZ_PORT, ELECTROQUIZ_DB, ELECTROQUIZ_ADMIN_PASSWORD
 * (или системные свойства electroquiz.port, electroquiz.db, electroquiz.adminPassword).
 */
public class ServerLauncher {

    public static void main(String[] args) {
        Logger log = LoggerFactory.getLogger(ServerLauncher.class);
        ServerConfig config = ServerConfig.load(System.getenv());
        ServerDatabase db = new ServerDatabase(config.dbFile());
        ServerRepository repo = new ServerRepository(db);
        ServerApp server = new ServerApp(config, repo, new ServerQuizService(repo));

        int port = server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));   // корректно закрыть соединения по Ctrl+C
        log.info("ElectroQuiz: http://localhost:{}/  база данных: {}", port, config.dbFile());
        if (config.adminEnabled()) {
            log.info("Страница преподавателя: http://localhost:{}/admin", port);
        } else {
            log.warn("Пароль преподавателя не задан (ELECTROQUIZ_ADMIN_PASSWORD) — страница результатов отключена.");
        }
    }
}
