package io.monohull.service;

/**
 * The configured registry could not be queried — unreachable, rejected our credentials,
 * or answered with something that isn't a Docker Registry HTTP API V2 response.
 *
 * <p>Distinct from {@link IllegalStateException} (which the global handler maps to 409
 * "you configured something wrong") because the failure is upstream: Monohull is fine,
 * the registry is not. Mapped to 502 alongside {@code DockerException}.
 */
public class RegistryUnavailableException extends RuntimeException {

    public RegistryUnavailableException(String message) {
        super(message);
    }

    public RegistryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
