/** Ошибка запроса с HTTP-статусом; сообщение показывается пользователю в браузере. */
final class ServerError extends RuntimeException {

    final int status;

    ServerError(int status, String message) {
        super(message);
        this.status = status;
    }

    static ServerError badRequest(String message) {
        return new ServerError(400, message);
    }

    static ServerError notFound(String message) {
        return new ServerError(404, message);
    }

    static ServerError conflict(String message) {
        return new ServerError(409, message);
    }
}
