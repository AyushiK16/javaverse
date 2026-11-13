package com.matrix.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** One method call on the stack. */
public class StackFrameInfo {
    private String className;
    private String methodName;
    private int lineNumber;
    private Map<String, String> primitiveLocals = new LinkedHashMap<>();
    private Map<String, Long> referenceLocals = new LinkedHashMap<>();

    public StackFrameInfo() {
    }

    public StackFrameInfo(String className, String methodName, int lineNumber,
                          Map<String, String> primitiveLocals, Map<String, Long> referenceLocals) {
        this.className = className;
        this.methodName = methodName;
        this.lineNumber = lineNumber;
        this.primitiveLocals = primitiveLocals == null ? new LinkedHashMap<>() : primitiveLocals;
        this.referenceLocals = referenceLocals == null ? new LinkedHashMap<>() : referenceLocals;
    }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }

    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }

    public Map<String, String> getPrimitiveLocals() { return primitiveLocals; }
    public void setPrimitiveLocals(Map<String, String> primitiveLocals) { this.primitiveLocals = primitiveLocals; }

    public Map<String, Long> getReferenceLocals() { return referenceLocals; }
    public void setReferenceLocals(Map<String, Long> referenceLocals) { this.referenceLocals = referenceLocals; }

    @Override
    public String toString() {
        return "StackFrameInfo{className=" + className + ", methodName=" + methodName
                + ", lineNumber=" + lineNumber + ", primitiveLocals=" + primitiveLocals
                + ", referenceLocals=" + referenceLocals + "}";
    }
}
