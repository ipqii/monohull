package io.monohull.dto;

/** Result of changing a Maximo user's password on an environment's ADM container. */
public record SetPasswordResult(boolean success, String output) {}
