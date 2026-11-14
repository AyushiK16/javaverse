package com.matrix.timeline;

import com.matrix.model.ReferenceEdge;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds the differences between two snapshots (fromStep -> toStep)
 * so a UI or timeline tool can highlight what changed.
 */
public class SnapshotDiff {
    private int fromStep;
    private int toStep;
    private List<Long> addedObjectIds = new ArrayList<>();
    private List<Long> removedObjectIds = new ArrayList<>();
    private List<Long> changedObjectIds = new ArrayList<>();
    private List<ReferenceEdge> addedEdges = new ArrayList<>();
    private List<ReferenceEdge> removedEdges = new ArrayList<>();
    private int stackDepthFrom;
    private int stackDepthTo;

    public SnapshotDiff() {
    }

    public SnapshotDiff(int fromStep, int toStep,
                        List<Long> addedObjectIds, List<Long> removedObjectIds, List<Long> changedObjectIds,
                        List<ReferenceEdge> addedEdges, List<ReferenceEdge> removedEdges,
                        int stackDepthFrom, int stackDepthTo) {
        this.fromStep = fromStep;
        this.toStep = toStep;
        this.addedObjectIds = addedObjectIds == null ? new ArrayList<>() : addedObjectIds;
        this.removedObjectIds = removedObjectIds == null ? new ArrayList<>() : removedObjectIds;
        this.changedObjectIds = changedObjectIds == null ? new ArrayList<>() : changedObjectIds;
        this.addedEdges = addedEdges == null ? new ArrayList<>() : addedEdges;
        this.removedEdges = removedEdges == null ? new ArrayList<>() : removedEdges;
        this.stackDepthFrom = stackDepthFrom;
        this.stackDepthTo = stackDepthTo;
    }

    public int getFromStep() {
        return fromStep;
    }

    public void setFromStep(int fromStep) {
        this.fromStep = fromStep;
    }

    public int getToStep() {
        return toStep;
    }

    public void setToStep(int toStep) {
        this.toStep = toStep;
    }

    public List<Long> getAddedObjectIds() {
        return addedObjectIds;
    }

    public void setAddedObjectIds(List<Long> addedObjectIds) {
        this.addedObjectIds = addedObjectIds;
    }

    public List<Long> getRemovedObjectIds() {
        return removedObjectIds;
    }

    public void setRemovedObjectIds(List<Long> removedObjectIds) {
        this.removedObjectIds = removedObjectIds;
    }

    public List<Long> getChangedObjectIds() {
        return changedObjectIds;
    }

    public void setChangedObjectIds(List<Long> changedObjectIds) {
        this.changedObjectIds = changedObjectIds;
    }

    public List<ReferenceEdge> getAddedEdges() {
        return addedEdges;
    }

    public void setAddedEdges(List<ReferenceEdge> addedEdges) {
        this.addedEdges = addedEdges;
    }

    public List<ReferenceEdge> getRemovedEdges() {
        return removedEdges;
    }

    public void setRemovedEdges(List<ReferenceEdge> removedEdges) {
        this.removedEdges = removedEdges;
    }

    public int getStackDepthFrom() {
        return stackDepthFrom;
    }

    public void setStackDepthFrom(int stackDepthFrom) {
        this.stackDepthFrom = stackDepthFrom;
    }

    public int getStackDepthTo() {
        return stackDepthTo;
    }

    public void setStackDepthTo(int stackDepthTo) {
        this.stackDepthTo = stackDepthTo;
    }

    @Override
    public String toString() {
        return "SnapshotDiff{fromStep=" + fromStep
                + ", toStep=" + toStep
                + ", addedObjectIds=" + addedObjectIds
                + ", removedObjectIds=" + removedObjectIds
                + ", changedObjectIds=" + changedObjectIds
                + ", addedEdges=" + addedEdges
                + ", removedEdges=" + removedEdges
                + ", stackDepthFrom=" + stackDepthFrom
                + ", stackDepthTo=" + stackDepthTo + '}';
    }
}