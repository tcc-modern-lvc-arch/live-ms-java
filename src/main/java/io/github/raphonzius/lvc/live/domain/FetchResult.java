package io.github.raphonzius.lvc.live.domain;

/**
 * Outcome of a call to an upstream data source, distinguishing "the source answered" (possibly
 * with nothing) from "the source could not be reached / failed".
 *
 * <p>Circuit-breaker fallbacks used to return an empty list, which made an outage look like an
 * empty snapshot. With this type the orchestration services can report the cycle as failed.</p>
 *
 * @param <T> payload type
 */
public sealed interface FetchResult<T> {

    /** The upstream answered; {@code data} may legitimately be empty. */
    record Fetched<T>(T data) implements FetchResult<T> {
    }

    /** The upstream could not be used (circuit open, timeout, HTTP error, ...). */
    record Unavailable<T>(String reason) implements FetchResult<T> {
    }

    static <T> FetchResult<T> of(T data) {
        return new Fetched<>(data);
    }

    static <T> FetchResult<T> unavailable(String reason) {
        return new Unavailable<>(reason);
    }

    /** The fetched data, or {@code fallback} when the upstream was unavailable. */
    default T orElse(T fallback) {
        return switch (this) {
            case Fetched<T>(var data) -> data;
            case Unavailable<T> _ -> fallback;
        };
    }
}
