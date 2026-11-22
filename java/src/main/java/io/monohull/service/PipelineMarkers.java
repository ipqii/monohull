package io.monohull.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The container-start marker steps every pipeline runs: start-db, check-db-schema and
 * start-adm. BuildService does their work inline rather than exec'ing a command.
 *
 * <p>They are real steps in a pipeline definition, so the Pipelines page shows them and
 * they can be moved — check-db-schema after a step that restores the database, say. A
 * definition that doesn't list one (written before they existed, or with it removed) gets
 * it back in front: start-db first, check-db-schema straight after it, then start-adm. A
 * definition that does list one keeps its placement.
 */
public final class PipelineMarkers {

    public static final String START_DB = "start-db";
    public static final String CHECK_DB_SCHEMA = "check-db-schema";
    public static final String START_ADM = "start-adm";
    public static final String START_APP = "start-app";

    private PipelineMarkers() {}

    /**
     * @param steps   the pipeline's steps, in order
     * @param key     the action key of a step
     * @param marker  builds a step for a marker key that has to be added
     * @param hasDb   whether there is a DB container (markers for a missing container are dropped)
     */
    public static <T> List<T> withMarkers(List<T> steps, Function<T, String> key,
                                          Function<String, T> marker,
                                          boolean hasDb, boolean hasAdm, boolean hasApp) {
        List<T> result = new ArrayList<>();
        for (T s : steps) {
            String k = key.apply(s);
            if ((START_DB.equals(k) || CHECK_DB_SCHEMA.equals(k)) && !hasDb) continue;
            if (START_ADM.equals(k) && !hasAdm) continue;
            if (START_APP.equals(k) && !hasApp) continue;
            result.add(s);
        }
        boolean listsDb = indexOf(result, key, START_DB) >= 0;
        boolean listsSchema = indexOf(result, key, CHECK_DB_SCHEMA) >= 0;
        boolean listsAdm = indexOf(result, key, START_ADM) >= 0;
        if (hasDb && !listsDb) {
            result.add(0, marker.apply(START_DB));
        }
        if (hasDb && !listsSchema) {
            result.add(indexOf(result, key, START_DB) + 1, marker.apply(CHECK_DB_SCHEMA));
        }
        if (hasAdm && !listsAdm) {
            // After the schema check when it was added here; right after start-db otherwise
            // (a pipeline that places check-db-schema itself wants it after its own steps).
            int anchor = !listsSchema ? indexOf(result, key, CHECK_DB_SCHEMA) : indexOf(result, key, START_DB);
            result.add(anchor + 1, marker.apply(START_ADM));
        }
        return result;
    }

    /** For a saved definition, which isn't tied to an environment: assume every container. */
    public static List<String> withMarkers(List<String> actionKeys) {
        return withMarkers(actionKeys, k -> k, k -> k, true, true, true);
    }

    private static <T> int indexOf(List<T> steps, Function<T, String> key, String id) {
        for (int i = 0; i < steps.size(); i++) {
            if (id.equals(key.apply(steps.get(i)))) return i;
        }
        return -1;
    }
}
