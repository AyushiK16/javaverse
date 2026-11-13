package com.matrix.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** One object on the heap (or an array). */
public class ObjectInfo {
    private long id;
    private String className;
    private Map<String, String> primitiveFields = new LinkedHashMap<>();
    private Map<String, Long> referenceFields = new LinkedHashMap<>();
    private ObjectStatus status = ObjectStatus.LIVE;
    private int createdAtStep = -1;
    private int unreachableAtStep = -1;
    private int collectedAtStep = -1;

    public ObjectInfo() {
    }

    public ObjectInfo(long id, String className, Map<String, String> primitiveFields,
                      Map<String, Long> referenceFields, ObjectStatus status,
                      int createdAtStep, int unreachableAtStep, int collectedAtStep) {
        this.id = id;
        this.className = className;
        this.primitiveFields = primitiveFields == null ? new LinkedHashMap<>() : primitiveFields;
        this.referenceFields = referenceFields == null ? new LinkedHashMap<>() : referenceFields;
        this.status = status == null ? ObjectStatus.LIVE : status;
        this.createdAtStep = createdAtStep;
        this.unreachableAtStep = unreachableAtStep;
        this.collectedAtStep = collectedAtStep;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public Map<String, String> getPrimitiveFields() { return primitiveFields; }
    public void setPrimitiveFields(Map<String, String> primitiveFields) { this.primitiveFields = primitiveFields; }

    public Map<String, Long> getReferenceFields() { return referenceFields; }
    public void setReferenceFields(Map<String, Long> referenceFields) { this.referenceFields = referenceFields; }

    public ObjectStatus getStatus() { return status; }
    public void setStatus(ObjectStatus status) { this.status = status; }

    public int getCreatedAtStep() { return createdAtStep; }
    public void setCreatedAtStep(int createdAtStep) { this.createdAtStep = createdAtStep; }

    public int getUnreachableAtStep() { return unreachableAtStep; }
    public void setUnreachableAtStep(int unreachableAtStep) { this.unreachableAtStep = unreachableAtStep; }

    public int getCollectedAtStep() { return collectedAtStep; }
    public void setCollectedAtStep(int collectedAtStep) { this.collectedAtStep = collectedAtStep; }

    @Override
    public String toString() {
        return "ObjectInfo{id=" + id + ", className=" + className
                + ", primitiveFields=" + primitiveFields + ", referenceFields=" + referenceFields
                + ", status=" + status + ", createdAtStep=" + createdAtStep
                + ", unreachableAtStep=" + unreachableAtStep
                + ", collectedAtStep=" + collectedAtStep + "}";
    }
}
