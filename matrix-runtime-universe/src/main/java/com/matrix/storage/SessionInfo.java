package com.matrix.storage;

/**
 * Metadata summary for one saved program execution run ("session").
 */
public class SessionInfo {
    private long sessionId;
    private String programName;
    private String createdAt;
    private int stepCount;

    public SessionInfo() {
    }

    public SessionInfo(long sessionId, String programName, String createdAt, int stepCount) {
        this.sessionId = sessionId;
        this.programName = programName;
        this.createdAt = createdAt;
        this.stepCount = stepCount;
    }

    public long getSessionId() {
        return sessionId;
    }

    public void setSessionId(long sessionId) {
        this.sessionId = sessionId;
    }

    public String getProgramName() {
        return programName;
    }

    public void setProgramName(String programName) {
        this.programName = programName;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public int getStepCount() {
        return stepCount;
    }

    public void setStepCount(int stepCount) {
        this.stepCount = stepCount;
    }

    @Override
    public String toString() {
        return "SessionInfo{sessionId=" + sessionId
                + ", programName='" + programName + '\''
                + ", createdAt='" + createdAt + '\''
                + ", stepCount=" + stepCount + '}';
    }
}