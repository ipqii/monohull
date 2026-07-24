package io.monohull.entity;

/** Normalized PR webhook event across providers. */
public enum PrBuildEvent {
    OPENED,
    SYNCHRONIZE,
    CLOSED
}
