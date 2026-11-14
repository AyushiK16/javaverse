package com.matrix.engine;

import com.matrix.model.ObjectInfo;
import com.matrix.model.RawStep;
import com.matrix.model.StackFrameInfo;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Finds which objects are still "reachable" in one RawStep.
 *
 * Start points (roots): every reference local variable of every stack frame,
 * plus every static reference. From there we follow the arrows (referenceFields)
 * from object to object. Everything we can touch is reachable. Everything else is garbage.
 */
public class ReachabilityAnalyzer {

    /**
     * @return the ids of all objects in this step that can be reached from the roots.
     *         Only objects that appear in step.getObjects() are counted.
     */
    public Set<Long> findReachableIds(RawStep step) {
        Set<Long> reachable = new LinkedHashSet<>();
        if (step == null) {
            return reachable;
        }

        // Quick lookup: object id -> object
        Map<Long, ObjectInfo> objectsById = new HashMap<>();
        for (ObjectInfo obj : step.getObjects()) {
            objectsById.put(obj.getId(), obj);
        }

        // Ids we still have to look at (a to-do list)
        Deque<Long> toVisit = new ArrayDeque<>();

        // 1) Roots: locals of every frame
        for (StackFrameInfo frame : step.getStack()) {
            addAllNotNull(toVisit, frame.getReferenceLocals().values());
        }
        // 2) Roots: static references
        addAllNotNull(toVisit, step.getStaticReferences().values());

        // 3) Walk the arrows. The "reachable" set also protects us from cycles (A -> B -> A).
        while (!toVisit.isEmpty()) {
            Long id = toVisit.poll();
            ObjectInfo obj = objectsById.get(id);
            if (obj == null) {
                continue;               // we know nothing about this id, skip it
            }
            if (!reachable.add(id)) {
                continue;               // already visited, do not loop forever
            }
            addAllNotNull(toVisit, obj.getReferenceFields().values());
        }
        return reachable;
    }

    private void addAllNotNull(Deque<Long> target, Collection<Long> ids) {
        for (Long id : ids) {
            if (id != null) {
                target.add(id);
            }
        }
    }
}