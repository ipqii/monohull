package io.monohull.service;

import java.util.List;

/**
 * Thrown by {@link BundleService#importBundle} when one or more rows on the destination
 * would be overwritten by the import and the caller didn't opt in via {@code overwrite=true}.
 * The {@link #getConflicts()} list is human-readable ("image config: Acme / EAM / MAS",
 * "pipeline: Acme", "custom action: acme-restore-db", ...).
 */
public class BundleConflictException extends RuntimeException {
    private final List<String> conflicts;

    public BundleConflictException(List<String> conflicts) {
        super("Bundle import would overwrite " + conflicts.size() + " existing row(s); set overwrite=true to proceed.");
        this.conflicts = conflicts;
    }

    public List<String> getConflicts() {
        return conflicts;
    }
}
