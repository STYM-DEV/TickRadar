package dev.stym.tickradar.config;

import java.util.List;

public sealed interface LoadResult<T> {

    record Loaded<T>(T value, List<String> warnings) implements LoadResult<T> {

        public Loaded {
            warnings = List.copyOf(warnings);
        }
    }

    record Unreadable<T>(String error) implements LoadResult<T> {
    }
}
