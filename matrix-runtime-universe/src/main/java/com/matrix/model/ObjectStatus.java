package com.matrix.model;

/** Life-cycle state of an object on the heap. */
public enum ObjectStatus {
    LIVE,
    UNREACHABLE,
    COLLECTED
}
