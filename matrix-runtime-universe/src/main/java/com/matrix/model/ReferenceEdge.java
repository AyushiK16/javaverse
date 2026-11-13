package com.matrix.model;

import java.util.Objects;

/**
 * One arrow from a "source" (stack variable, static field or object field) to a target object.
 * sourceKind is "STACK", "STATIC" or "OBJECT".
 * sourceId is the object id if OBJECT, the frame index if STACK, and -1 if STATIC.
 */
public class ReferenceEdge {
    private String sourceKind;
    private long sourceId;
    private String label;
    private long targetId;

    public ReferenceEdge() {
    }

    public ReferenceEdge(String sourceKind, long sourceId, String label, long targetId) {
        this.sourceKind = sourceKind;
        this.sourceId = sourceId;
        this.label = label;
        this.targetId = targetId;
    }

    public String getSourceKind() { return sourceKind; }
    public void setSourceKind(String sourceKind) { this.sourceKind = sourceKind; }

    public long getSourceId() { return sourceId; }
    public void setSourceId(long sourceId) { this.sourceId = sourceId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public long getTargetId() { return targetId; }
    public void setTargetId(long targetId) { this.targetId = targetId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReferenceEdge)) return false;
        ReferenceEdge other = (ReferenceEdge) o;
        return sourceId == other.sourceId
                && targetId == other.targetId
                && Objects.equals(sourceKind, other.sourceKind)
                && Objects.equals(label, other.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceKind, sourceId, label, targetId);
    }

    @Override
    public String toString() {
        return "ReferenceEdge{sourceKind=" + sourceKind + ", sourceId=" + sourceId
                + ", label=" + label + ", targetId=" + targetId + "}";
    }
}
