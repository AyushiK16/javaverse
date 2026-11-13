package com.matrix.model;

/** Something that happened at a step. objectId is -1 if the event is not about one object. */
public class RuntimeEvent {
    private int stepNumber;
    private EventType type;
    private long objectId = -1;
    private String description;

    public RuntimeEvent() {
    }

    public RuntimeEvent(int stepNumber, EventType type, long objectId, String description) {
        this.stepNumber = stepNumber;
        this.type = type;
        this.objectId = objectId;
        this.description = description;
    }

    public int getStepNumber() { return stepNumber; }
    public void setStepNumber(int stepNumber) { this.stepNumber = stepNumber; }

    public EventType getType() { return type; }
    public void setType(EventType type) { this.type = type; }

    public long getObjectId() { return objectId; }
    public void setObjectId(long objectId) { this.objectId = objectId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String toString() {
        return "RuntimeEvent{stepNumber=" + stepNumber + ", type=" + type
                + ", objectId=" + objectId + ", description=" + description + "}";
    }
}
