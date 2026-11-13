package com.matrix.model;

/** Kinds of things that can happen between two steps. */
public enum EventType {
    FRAME_PUSHED,
    FRAME_POPPED,
    OBJECT_CREATED,
    OBJECT_UNREACHABLE,
    OBJECT_COLLECTED,
    REFERENCE_ADDED,
    REFERENCE_REMOVED,
    VALUE_CHANGED,
    GC_RUN
}
