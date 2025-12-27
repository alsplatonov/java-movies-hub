package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class MoviesStore {

    private final Map<Integer, Movie> movies = new HashMap<>();

    public Collection<Movie> findAll() {
        return movies.values();
    }

    public Movie findById(int id) {
        return movies.get(id);
    }

    public void add(Movie movie) {
        movies.put(movie.getId(), movie);
    }

    public void remove(int id) {
        movies.remove(id);
    }

    public boolean isEmpty() {
        return movies.isEmpty();
    }
}
