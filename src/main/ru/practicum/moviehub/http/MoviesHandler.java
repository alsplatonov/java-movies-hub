package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MoviesHandler extends BaseHttpHandler {

    private final MoviesStore store;
    private final Gson gson = new Gson();

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        String[] parts = path.split("/");
        switch (method) {

            case "GET":
                String query = ex.getRequestURI().getQuery(); // получение query-параметров

                if (parts.length == 2 && parts[1].equals("movies")) {

                    if (query == null) { // GET /movies
                        handleGetAll(ex); // просто список всех фильмов
                    } else {
                        String[] queryParts = query.split("=");
                        if (queryParts.length == 2 && queryParts[0].equals("year")) { //GET /movies?year=YYYY
                            handleGetByYear(ex, queryParts[1]);
                        } else {
                            String json = gson.toJson(new ErrorResponse("Некорректный параметр запроса — 'year'"));
                            sendJson(ex, 400, json);
                        }
                    }
                } else if (parts.length == 3 && parts[1].equals("movies")) {
                    // GET /movies/{id}
                    handleGetById(ex, parts[2]);
                } else {
                    ex.sendResponseHeaders(404, -1); // Не найдено
                }
                break;

            case "POST":
                if (path.equals("/movies")) {
                    handlePost(ex);
                } else {
                    ex.sendResponseHeaders(404, -1);
                }
                break;

            case "DELETE":
                if (parts.length == 3 && parts[1].equals("movies")) {
                    handleDeleteById(ex, parts[2]);
                } else {
                    ex.sendResponseHeaders(404, -1);
                }
                break;

            default:
                ex.sendResponseHeaders(405, -1); // Метод не поддерживается
                break;
        }
    }

    // Получение всех фильмов
    private void handleGetAll(HttpExchange ex) throws IOException {
        Collection<Movie> movies = store.findAll();
        // Gson сам вернёт [] для пустой коллекции
        String json = gson.toJson(movies);
        sendJson(ex, 200, json);
    }

    // Получение фильма по ID
    private void handleGetById(HttpExchange ex, String idStr) throws IOException {
        Movie movie = getMovieById(ex, idStr);
        if (movie == null) return;

        String json = gson.toJson(movie);
        sendJson(ex, 200, json);
    }

    // Получение фильма по году
    private void handleGetByYear(HttpExchange ex, String yearStr) throws IOException {
        int year;
        try {
            year = Integer.parseInt(yearStr);
        } catch (NumberFormatException e) {
            String json = gson.toJson(new ErrorResponse("Некорректный параметр запроса — 'year'"));
            sendJson(ex, 400, json);
            return;
        }

        List<Movie> filtered = new ArrayList<>();
        for (Movie movie : store.findAll()) {
            if (movie.getYear() == year) {
                filtered.add(movie);
            }
        }

        String json = gson.toJson(filtered);
        sendJson(ex, 200, json);
    }

    //сохранить фильм
    private void handlePost(HttpExchange ex) throws IOException {
        // Проверка Content-Type
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.startsWith("application/json")) {
            ex.sendResponseHeaders(415, -1);
            return;
        }

        Movie movieRequest;
        try (InputStream inputStream = ex.getRequestBody()) {
            String body = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            movieRequest = gson.fromJson(body, Movie.class); // Десериализация в Movie
        } catch (Exception e) {
            ex.sendResponseHeaders(400, -1);
            return;
        }

        // Валидация
        String validationError = validateMovie(movieRequest);
        if (validationError != null) {
            sendJson(ex, 422, gson.toJson(new ErrorResponse(validationError)));
            return;
        }

        // Получим след. номер Id
        int nextId = 1;
        Collection<Movie> allMovies = store.findAll();
        for (Movie movie : allMovies) {
            if (movie.getId() >= nextId) {
                nextId = movie.getId() + 1;
            }
        }

        Movie movie = new Movie(nextId, movieRequest.getTitle(), movieRequest.getYear());
        store.add(movie);

        String json = gson.toJson(movie);
        sendJson(ex, 201, json);
    }

    //удалить фильм по id
    private void handleDeleteById(HttpExchange ex, String idStr) throws IOException {
        Movie movie = getMovieById(ex, idStr);
        if (movie == null) return;

        store.remove(movie.getId());
        sendNoContent(ex); // 204 No Content
    }

    // Вспомогательные методы

    // Проверка корректности фильма
    private String validateMovie(Movie movie) {
        if (movie.getTitle() == null || movie.getTitle().isBlank()) {
            return "название не должно быть пустым";
        }
        if (movie.getTitle().length() > 100) {
            return "название не должно быть длиннее 100 символов";
        }
        int currentYear = Year.now().getValue();
        if (movie.getYear() < 1888 || movie.getYear() > currentYear + 1) {
            return "год должен быть между 1888 и " + (currentYear + 1);
        }
        return null;
    }

    // Получение фильма по ID с обработкой ошибок
    private Movie getMovieById(HttpExchange ex, String idStr) throws IOException {
        int id;
        try {
            id = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            sendJson(ex, 400, gson.toJson(new ErrorResponse("Некорректный ID")));
            return null;
        }

        Movie movie = store.findById(id);
        if (movie == null) {
            sendJson(ex, 404, gson.toJson(new ErrorResponse("Фильм не найден")));
            return null;
        }

        return movie;
    }
}
