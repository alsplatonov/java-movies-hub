package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public abstract class BaseHttpHandler implements HttpHandler {
    protected static final String CT_JSON = "application/json; charset=UTF-8";

    // Метод для отправки JSON с телом
    protected void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    // Метод для отправки ответа без тела с кодом 204
    protected void sendNoContent(HttpExchange ex) throws IOException {
        // Устанавливаем заголовок Content-Type
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        // Отправляем ответ без тела
        ex.sendResponseHeaders(204, -1);
    }
}
