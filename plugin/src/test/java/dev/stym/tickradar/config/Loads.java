package dev.stym.tickradar.config;

import static org.junit.jupiter.api.Assertions.fail;

final class Loads {

    private Loads() {
    }

    static <T> LoadResult.Loaded<T> loaded(LoadResult<T> result) {
        if (result instanceof LoadResult.Loaded<T> loaded) {
            return loaded;
        }
        return fail("expected a loaded result, got " + result);
    }
}
