package io.monohull.service;

/**
 * Translates raw Docker daemon / registry / in-container failures into a plain-English
 * cause plus the likely fix (MXF-21). Applied at the user-facing funnels — the build log
 * stream, the pipeline step log, and the HTTP error handler — so strangers running
 * Monohull unsupervised see "what happened and what to do", not an HTTP body dump.
 *
 * <p>The original docker message is kept in parentheses for supportability; full stack
 * traces stay in the server log only.
 */
public final class DockerErrors {

    private DockerErrors() {}

    /** Plain-English cause + fix for a failure reaching a user-facing funnel. Falls back
     *  to the exception's own (first-line) message when no signature matches. */
    public static String explain(Throwable t) {
        String raw = firstLine(bestMessage(t));
        String all = messageChain(t).toLowerCase();

        if (all.contains("port is already allocated") || all.contains("address already in use")) {
            return "A host port this environment needs is already in use on the Docker host. "
                + "Free the port, change the static ports on the image config, or switch the "
                + "environment to dynamic ports. (docker: " + raw + ")";
        }
        if (all.contains("is already in use by container")) {
            return "A container with this name already exists on the Docker host — usually a "
                + "leftover from a previous failed build. Rebuilding replaces leftovers "
                + "automatically now; if this keeps happening, remove it manually with "
                + "docker rm -f <name>. (docker: " + raw + ")";
        }
        if (all.contains("pull access denied") || all.contains("unauthorized")
                || all.contains("authentication required") || all.contains("no basic auth credentials")) {
            return "The registry refused the image pull (authentication). Check the credentials "
                + "under Registry — the registry URL must match the image's host — and that the "
                + "account is allowed to pull this repository. (docker: " + raw + ")";
        }
        if (all.contains("manifest unknown") || all.contains("repository does not exist")
                || all.contains("no such image")
                || (all.contains("not found") && (all.contains("manifest") || all.contains("repository") || all.contains("pull")))) {
            return "Image not found in the registry. Check the image reference and tag on the "
                + "image config (a typo in the tag is the usual cause) and that the image was "
                + "actually pushed. (docker: " + raw + ")";
        }
        if (all.contains("no space left on device")) {
            return "The Docker host is out of disk space. Free some (docker system prune, remove "
                + "unused images/volumes) and retry. (docker: " + raw + ")";
        }
        if (all.contains("no such container")) {
            return "The container no longer exists on the Docker host (removed outside Monohull?). "
                + "Re-run the pipeline to recreate what's missing, or remove and rebuild the "
                + "environment. (docker: " + raw + ")";
        }
        if (isDaemonUnreachable(t, all)) {
            return "Cannot reach the Docker daemon. Check that Docker is running on the host and "
                + "that APP_DOCKER_HOST points at it (for a containerised Monohull, that the "
                + "socket is mounted in). Until it's back, builds, teardowns and container "
                + "actions will all fail. (docker: " + raw + ")";
        }
        return raw;
    }

    /**
     * Known in-container failure signatures, checked line-by-line against action output.
     * Returns a hint for the first match, else null. Kept deliberately short and
     * high-precision — a wrong hint is worse than none.
     */
    public static String sniff(String logLine) {
        if (logLine == null) return null;
        if (logLine.contains("SQL1598N")) {
            return "DB2 license problem (SQL1598N): the DB image's DB2 license is missing or "
                + "expired. Install a valid license in the DB image (db2licm -a) and rebuild.";
        }
        if (logLine.contains("SQL0964C")) {
            return "DB2 transaction log full (SQL0964C): increase LOGFILSIZ/LOGSECOND in the DB "
                + "image, or free log space and re-run the step.";
        }
        if (logLine.contains("SQL30081N")) {
            return "DB2 communication failure (SQL30081N): the database isn't reachable from this "
                + "container — check the DB container is running and finished starting up.";
        }
        if (logLine.contains("No space left on device")) {
            return "Out of disk space on the Docker host — free some (docker system prune) and "
                + "re-run the step.";
        }
        if (logLine.contains("java.lang.OutOfMemoryError")) {
            return "The JVM ran out of memory during this action — give the container more memory "
                + "or reduce the build's heap demand, then re-run the step.";
        }
        return null;
    }

    private static boolean isDaemonUnreachable(Throwable t, String lowerChain) {
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (c instanceof java.net.ConnectException
                    || c instanceof java.net.SocketTimeoutException
                    || c instanceof java.net.UnknownHostException
                    || c instanceof java.net.SocketException) {
                return true;
            }
        }
        return lowerChain.contains("connection refused")
            || lowerChain.contains("connect timed out")
            || lowerChain.contains("connection reset")
            || lowerChain.contains("could not connect to")
            || lowerChain.contains("docker.sock")
            || lowerChain.contains("npipe");
    }

    /** The most informative message available: the deepest non-blank cause message,
     *  falling back to the top-level one, then the class name. */
    private static String bestMessage(Throwable t) {
        String best = null;
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (c.getMessage() != null && !c.getMessage().isBlank()) {
                best = c.getMessage();
            }
        }
        return best != null ? best : t.getClass().getSimpleName();
    }

    private static String messageChain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (c.getMessage() != null) sb.append(c.getMessage()).append(' ');
        }
        return sb.toString();
    }

    private static String firstLine(String s) {
        int nl = s.indexOf('\n');
        return (nl >= 0 ? s.substring(0, nl) : s).trim();
    }
}
