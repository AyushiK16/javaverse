package com.matrix.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A complete picture of memory at one step. */
public class Snapshot {
    private int stepNumber;
    private int lineNumber;
    private List<StackFrameInfo> stack = new ArrayList<>();
    private List<ObjectInfo> heap = new ArrayList<>();
    private List<ReferenceEdge> edges = new ArrayList<>();
    private Map<String, Long> staticReferences = new LinkedHashMap<>();
    private String consoleOutput = "";
    private List<RuntimeEvent> events = new ArrayList<>();
    private Map<String, Integer> stats = new LinkedHashMap<>();

    public Snapshot() {
    }

    public Snapshot(int stepNumber, int lineNumber, List<StackFrameInfo> stack,
                    List<ObjectInfo> heap, List<ReferenceEdge> edges,
                    Map<String, Long> staticReferences, String consoleOutput,
                    List<RuntimeEvent> events, Map<String, Integer> stats) {
        this.stepNumber = stepNumber;
        this.lineNumber = lineNumber;
        this.stack = stack == null ? new ArrayList<>() : stack;
        this.heap = heap == null ? new ArrayList<>() : heap;
        this.edges = edges == null ? new ArrayList<>() : edges;
        this.staticReferences = staticReferences == null ? new LinkedHashMap<>() : staticReferences;
        this.consoleOutput = consoleOutput == null ? "" : consoleOutput;
        this.events = events == null ? new ArrayList<>() : events;
        this.stats = stats == null ? new LinkedHashMap<>() : stats;
    }

    public int getStepNumber() { return stepNumber; }
    public void setStepNumber(int stepNumber) { this.stepNumber = stepNumber; }

    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }

    public List<StackFrameInfo> getStack() { return stack; }
    public void setStack(List<StackFrameInfo> stack) { this.stack = stack; }

    public List<ObjectInfo> getHeap() { return heap; }
    public void setHeap(List<ObjectInfo> heap) { this.heap = heap; }

    public List<ReferenceEdge> getEdges() { return edges; }
    public void setEdges(List<ReferenceEdge> edges) { this.edges = edges; }

    public Map<String, Long> getStaticReferences() { return staticReferences; }
    public void setStaticReferences(Map<String, Long> staticReferences) { this.staticReferences = staticReferences; }

    public String getConsoleOutput() { return consoleOutput; }
    public void setConsoleOutput(String consoleOutput) { this.consoleOutput = consoleOutput; }

    public List<RuntimeEvent> getEvents() { return events; }
    public void setEvents(List<RuntimeEvent> events) { this.events = events; }

    public Map<String, Integer> getStats() { return stats; }
    public void setStats(Map<String, Integer> stats) { this.stats = stats; }

    @Override
    public String toString() {
        return "Snapshot{stepNumber=" + stepNumber + ", lineNumber=" + lineNumber
                + ", stack=" + stack + ", heap=" + heap + ", edges=" + edges
                + ", staticReferences=" + staticReferences + ", consoleOutput=" + consoleOutput
                + ", events=" + events + ", stats=" + stats + "}";
    }
}
