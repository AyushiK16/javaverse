package com.matrix.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What the tracer saw at one step: only the stack, the objects and the static references. */
public class RawStep {
    private int stepNumber;
    private int lineNumber;
    private List<StackFrameInfo> stack = new ArrayList<>();
    private List<ObjectInfo> objects = new ArrayList<>();
    private Map<String, Long> staticReferences = new LinkedHashMap<>();
    private String newOutput = "";

    public RawStep() {
    }

    public RawStep(int stepNumber, int lineNumber, List<StackFrameInfo> stack,
                   List<ObjectInfo> objects, Map<String, Long> staticReferences, String newOutput) {
        this.stepNumber = stepNumber;
        this.lineNumber = lineNumber;
        this.stack = stack == null ? new ArrayList<>() : stack;
        this.objects = objects == null ? new ArrayList<>() : objects;
        this.staticReferences = staticReferences == null ? new LinkedHashMap<>() : staticReferences;
        this.newOutput = newOutput == null ? "" : newOutput;
    }

    public int getStepNumber() { return stepNumber; }
    public void setStepNumber(int stepNumber) { this.stepNumber = stepNumber; }

    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }

    public List<StackFrameInfo> getStack() { return stack; }
    public void setStack(List<StackFrameInfo> stack) { this.stack = stack; }

    public List<ObjectInfo> getObjects() { return objects; }
    public void setObjects(List<ObjectInfo> objects) { this.objects = objects; }

    public Map<String, Long> getStaticReferences() { return staticReferences; }
    public void setStaticReferences(Map<String, Long> staticReferences) { this.staticReferences = staticReferences; }

    public String getNewOutput() { return newOutput; }
    public void setNewOutput(String newOutput) { this.newOutput = newOutput; }

    @Override
    public String toString() {
        return "RawStep{stepNumber=" + stepNumber + ", lineNumber=" + lineNumber
                + ", stack=" + stack + ", objects=" + objects
                + ", staticReferences=" + staticReferences + ", newOutput=" + newOutput + "}";
    }
}
