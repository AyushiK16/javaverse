package com.matrix.timeline;

import com.matrix.model.ObjectInfo;
import com.matrix.model.ReferenceEdge;
import com.matrix.model.Snapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Compares two Snapshots and computes what objects, edges, and stack depth changed.
 */
public class SnapshotDiffer {

    /**
     * Computes the difference going from snapshot {@code a} to snapshot {@code b}.
     */
    public static SnapshotDiff diff(Snapshot a, Snapshot b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("Snapshots to compare must not be null");
        }

        Map<Long, ObjectInfo> objectsA = new LinkedHashMap<>();
        for (ObjectInfo obj : a.getHeap()) {
            objectsA.put(obj.getId(), obj);
        }

        Map<Long, ObjectInfo> objectsB = new LinkedHashMap<>();
        for (ObjectInfo obj : b.getHeap()) {
            objectsB.put(obj.getId(), obj);
        }

        List<Long> addedObjectIds = new ArrayList<>();
        List<Long> changedObjectIds = new ArrayList<>();
        for (Map.Entry<Long, ObjectInfo> entry : objectsB.entrySet()) {
            long id = entry.getKey();
            ObjectInfo objB = entry.getValue();
            if (!objectsA.containsKey(id)) {
                addedObjectIds.add(id);
            } else if (hasObjectChanged(objectsA.get(id), objB)) {
                changedObjectIds.add(id);
            }
        }

        List<Long> removedObjectIds = new ArrayList<>();
        for (Long id : objectsA.keySet()) {
            if (!objectsB.containsKey(id)) {
                removedObjectIds.add(id);
            }
        }

        Set<ReferenceEdge> edgeSetA = new LinkedHashSet<>(a.getEdges());
        Set<ReferenceEdge> edgeSetB = new LinkedHashSet<>(b.getEdges());

        List<ReferenceEdge> addedEdges = new ArrayList<>();
        for (ReferenceEdge edge : edgeSetB) {
            if (!edgeSetA.contains(edge)) {
                addedEdges.add(edge);
            }
        }

        List<ReferenceEdge> removedEdges = new ArrayList<>();
        for (ReferenceEdge edge : edgeSetA) {
            if (!edgeSetB.contains(edge)) {
                removedEdges.add(edge);
            }
        }

        int stackDepthFrom = a.getStack().size();
        int stackDepthTo = b.getStack().size();

        return new SnapshotDiff(
                a.getStepNumber(),
                b.getStepNumber(),
                addedObjectIds,
                removedObjectIds,
                changedObjectIds,
                addedEdges,
                removedEdges,
                stackDepthFrom,
                stackDepthTo
        );
    }

    private static boolean hasObjectChanged(ObjectInfo objA, ObjectInfo objB) {
        if (objA.getStatus() != objB.getStatus()) {
            return true;
        }
        if (!Objects.equals(objA.getClassName(), objB.getClassName())) {
            return true;
        }
        if (!Objects.equals(objA.getPrimitiveFields(), objB.getPrimitiveFields())) {
            return true;
        }
        return !Objects.equals(objA.getReferenceFields(), objB.getReferenceFields());
    }
}