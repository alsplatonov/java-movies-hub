package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.internal.bind.util.ISO8601Utils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Year;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MoviesApiTest {

    // Базовая часть URL
    private static final String BASE = "http://localhost:8080";

    private static MoviesServer server;
    private static HttpClient client;
    private static MoviesStore store;  // добавляем поле store
    private static final Gson gson = new Gson();

    @BeforeAll
    static void beforeAll() {
        // создаём хранилище и сервер
        store = new MoviesStore();
        server = new MoviesServer(store, 8080);
        server.start();

        // HTTP-клиент
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @BeforeEach
    void beforeEach() {
        // Очистка хранилища перед каждым тестом
        store.clear();
    }

    @AfterAll
    static void afterAll() {
        if (server != null) {
            server.stop();
        }
    }

    //GET /movies
    //возвращает пустой список
    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = resp.body().trim();
        assertEquals("[]", body, "Для пустого хранилища должен возвращаться []");
    }

    //GET /movies

    // возвращает список с ранее добавленными фильмами
    @Test
    void getMovies_whenMoviesExist_returnsMoviesList() throws Exception {
        // добавляем фильмы напрямую в хранилище
        store.add(new Movie(1, "Inception", 2010));
        store.add(new Movie(2, "Interstellar", 2014));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());

        String body = resp.body();
        assertTrue(body.contains("Inception"));
        assertTrue(body.contains("Interstellar"));
    }

    //POST /movies

    //добавляет фильм при корректных данных
    @Test
    void postMovie_withValidData_addsMovie() throws Exception {
        String body = """
                {
                  "title": "Inception",
                  "year": 2010
                }
                """;

        HttpResponse<String> resp = post(body, "application/json");

        assertEquals(201, resp.statusCode(), "POST /movies должен вернуть 201");

        String contentType =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentType);

        assertTrue(resp.body().contains("Inception"));
        assertTrue(resp.body().contains("2010"));
    }

    //возвращает ошибку при пустом title
    @Test
    void postMovie_withEmptyTitle_returns422() throws Exception {
        String body = """
                {
                  "title": "",
                  "year": 2010
                }
                """;

        HttpResponse<String> resp = post(body, "application/json");
        assertEquals(422, resp.statusCode());
        assertTrue(resp.body().contains("название не должно быть пустым"));
    }

    //возвращает ошибку при слишком длинном title (> 100 символов)
    @Test
    void postMovie_withTooLongTitle_returns422() throws Exception {
        String longTitle = "A".repeat(101);

        String body = """
                {
                  "title": "%s",
                  "year": 2010
                }
                """.formatted(longTitle);

        HttpResponse<String> resp = post(body, "application/json");

        assertEquals(422, resp.statusCode());
        assertTrue(resp.body().contains("название не должно быть длиннее 100"));
    }

    //возвращает ошибку при неверном year (меньше 1888 или больше текущего года + 1)
    @Test
    void postMovie_withTooSmallYear_returns422() throws Exception {
        String body = """
                {
                  "title": "Sample",
                  "year": 1753
                }
                """;

        HttpResponse<String> resp = post(body, "application/json");
        assertEquals(422, resp.statusCode());
        assertTrue(resp.body().contains("год должен быть между 1888 и " + (Year.now().getValue() + 1)));
    }

    //возвращает ошибку при неправильном Content-Type
    @Test
    void postMovie_withWrongContentType_returns415() throws Exception {
        String body = """
                {
                  "title": "Inception",
                  "year": 2010
                }
                """;

        HttpResponse<String> resp = post(body, "text/plain");
        assertEquals(415, resp.statusCode());
    }

    //возвращает ошибку при некорректном JSON
    @Test
    void postMovie_withInvalidJson_returns400() throws Exception {
        String body = """
                {
                  "title": "Inception",
                  "year":
                }
                """;

        HttpResponse<String> resp = post(body, "application/json");

        assertEquals(400, resp.statusCode());
    }

    //GET /movies/{id}

    //возвращает фильм по существующему id
    @Test
    void getMovieById_whenExists_returnsMovie() throws Exception {
        // подготовка данных
        store.add(new Movie(1, "Inception", 2010));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/1"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "GET /movies/{id} должен вернуть 200");

        String contentType =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentType);

        assertTrue(resp.body().contains("\"id\":1"));
        assertTrue(resp.body().contains("Inception"));
        assertTrue(resp.body().contains("\"year\":2010"));
    }

    //возвращает ошибку, если фильм не найден
    @Test
    void getMovieById_whenNotFound_returns404() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/100"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, resp.statusCode());

        assertTrue(resp.body().contains("Фильм не найден"));
    }

    //возвращает ошибку, если id не число
    @Test
    void getMovieById_whenIdIsNotNumber_returns400() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/w"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());

        assertTrue(resp.body().contains("Некорректный ID"));
    }

    //DELETE /movies/{id}

    //удаляет фильм по существующему id
    @Test
    void deleteMovieById_whenExists_deletesMovie() throws Exception {
        // подготовка данных
        store.add(new Movie(1, "Inception", 2010));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/1"))
                .DELETE()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(204, resp.statusCode(), "DELETE /movies/{id} должен вернуть 204");

        // фильм удалён
        assertTrue(store.isEmpty(), "Фильм должен быть удалён из хранилища");
    }

    //возвращает ошибку, если фильм не найден
    @Test
    void deleteMovieById_whenNotFound_returns404() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/100"))
                .DELETE()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, resp.statusCode());

        assertTrue(resp.body().contains("Фильм не найден"));
    }

    //возвращает ошибку, если id не число
    @Test
    void deleteMovieById_whenIdIsNotNumber_returns400() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/w"))
                .DELETE()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());

        assertTrue(resp.body().contains("Некорректный ID"));
    }

    //GET /movies?year=YYYY

    //возвращает пустой список, если фильмов с таким годом нет
    @Test
    void getMoviesByYear_whenNoMoviesByYear_returnsEmptyList() throws Exception {
        store.add(new Movie(1, "Inception", 2010));
        store.add(new Movie(2, "Interstellar", 2014));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2025"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());

        String body = resp.body().trim();
        assertEquals("[]", body, "Если фильмов нет, должен вернуться пустой массив");
    }

    //возвращает ошибку, если параметр year не число
    @Test
    void getMoviesByYear_whenYearIsNotNumber_returns400() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=wwww"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());
        assertTrue(
                resp.body().contains("Некорректный параметр запроса — year"),
                "Должно вернуться сообщение об ошибке"
        );
    }

    //возвращает фильмы указанного года
    @Test
    void getMoviesByYear_whenMoviesExist_returnsFilteredList() throws Exception {
        // подготовка данных
        store.add(new Movie(1, "Inception", 2010));
        store.add(new Movie(2, "Interstellar", 2014));
        store.add(new Movie(3, "Shutter Island", 2010));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2010"))
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // статус
        assertEquals(200, resp.statusCode());

        // Content-Type
        String contentType =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentType);

        // парсим ответ с помощью ListOfMoviesTypeToken
        List<Movie> movies = gson.fromJson(
                resp.body(),
                new ListOfMoviesTypeToken().getType()
        );
        System.out.println(resp.body());
        assertEquals(2, movies.size(), "Должно вернуться 2 фильма за 2010 год");

        for (Movie movie : movies) {
            assertEquals(2010, movie.getYear(), "Все фильмы должны быть 2010 года");
        }
    }


    //вспомогательный класс для post запроса
    private HttpResponse<String> post(String body, String contentType) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }

        HttpRequest request = builder.build();

        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

}
