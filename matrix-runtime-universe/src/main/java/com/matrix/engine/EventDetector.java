package com.matrix.engine;

import com.matrix.model.EventType;
import com.matrix.model.ObjectInfo;
import com.matrix.model.ReferenceEdge;
import com.matrix.model.RuntimeEvent;
import com.matrix.model.Snapshot;
import com.matrix.model.StackFrameInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Works out "what just happened" by comparing the previous snapshot with the current one.
 *
 * For the very first step, pass null as the previous snapshot (everything counts as new).
 * The current snapshot must already have its stack, heap and edges filled in.
 *
 * Order of the events in one step:
 *   frames pushed/popped, objects created, references removed, references added,
 *   values changed, objects unreachable, GC run, objects collected.
 */
public class EventDetector {

    public List<RuntimeEvent> detect(Snapshot previous, Snapshot current, GcSimulator.StepResult gc) {
        int step = current.getStepNumber();

        List<StackFrameInfo> prevStack = previous == null ? new ArrayList<>() : previous.getStack();
        List<ObjectInfo> prevHeap = previous == null ? new ArrayList<>() : previous.getHeap();
        List<ReferenceEdge> prevEdges = previous == null ? new ArrayList<>() : previous.getEdges();

        List<StackFrameInfo> curStack = current.getStack();
        List<ObjectInfo> curHeap = current.getHeap();
        List<ReferenceEdge> curEdges = current.getEdges();

        Map<Long, ObjectInfo> prevById = byId(prevHeap);
        Map<Long, ObjectInfo> curById = byId(curHeap);

        List<RuntimeEvent> events = new ArrayList<>();
        addFrameEvents(events, step, prevStack, curStack);
        addCreatedEvents(events, step, curHeap, prevById);
        addRemovedReferenceEvents(events, step, prevEdges, curEdges, prevStack, curStack, prevById, curById);
        addAddedReferenceEvents(events, step, prevEdges, curEdges, curStack, prevById, curById);
        addValueChangedEvents(events, step, prevStack, curStack, prevById, curHeap);
        addGcEvents(events, step, gc, prevById, curById);
        return events;
    }

    // ---------------------------------------------------------------- frames

    private void addFrameEvents(List<RuntimeEvent> events, int step,
                                List<StackFrameInfo> prevStack, List<StackFrameInfo> curStack) {
        // pushed: frames that exist now but not before
        for (int i = prevStack.size(); i < curStack.size(); i++) {
            String name = curStack.get(i).getMethodName() + "()";
            String text = (i == 0)
                    ? "Frame " + name + " pushed"
                    : "Frame " + curStack.get(i - 1).getMethodName() + "() -> " + name + " pushed";
            events.add(new RuntimeEvent(step, EventType.FRAME_PUSHED, -1, text));
        }
        // popped: frames that existed before but not now (top frame first)
        for (int i = prevStack.size() - 1; i >= curStack.size(); i--) {
            String text = "Frame " + prevStack.get(i).getMethodName() + "() popped";
            if (i > 0) {
                text += " (back to " + prevStack.get(i - 1).getMethodName() + "())";
            }
            events.add(new RuntimeEvent(step, EventType.FRAME_POPPED, -1, text));
        }
    }

    // --------------------------------------------------------------- objects

    private void addCreatedEvents(List<RuntimeEvent> events, int step,
                                  List<ObjectInfo> curHeap, Map<Long, ObjectInfo> prevById) {
        for (ObjectInfo obj : curHeap) {
            if (!prevById.containsKey(obj.getId())) {
                events.add(new RuntimeEvent(step, EventType.OBJECT_CREATED, obj.getId(),
                        label(obj.getId(), obj.getClassName()) + " created"));
            }
        }
    }

    // ------------------------------------------------------------ references

    private void addRemovedReferenceEvents(List<RuntimeEvent> events, int step,
                                           List<ReferenceEdge> prevEdges, List<ReferenceEdge> curEdges,
                                           List<StackFrameInfo> prevStack, List<StackFrameInfo> curStack,
                                           Map<Long, ObjectInfo> prevById, Map<Long, ObjectInfo> curById) {
        Set<ReferenceEdge> curSet = new HashSet<>(curEdges);
        for (ReferenceEdge edge : prevEdges) {
            if (curSet.contains(edge)) {
                continue;
            }
            // If the source itself disappeared (object collected / frame popped), the
            // OBJECT_COLLECTED or FRAME_POPPED event already tells the story. Skip it.
            if (!sourceStillExists(edge, curStack, curById)) {
                continue;
            }
            String text = describeSource(edge, prevStack, prevById, curById)
                    + " no longer points to " + nameOf(edge.getTargetId(), curById, prevById);
            events.add(new RuntimeEvent(step, EventType.REFERENCE_REMOVED, edge.getTargetId(), text));
        }
    }

    private void addAddedReferenceEvents(List<RuntimeEvent> events, int step,
                                         List<ReferenceEdge> prevEdges, List<ReferenceEdge> curEdges,
                                         List<StackFrameInfo> curStack,
                                         Map<Long, ObjectInfo> prevById, Map<Long, ObjectInfo> curById) {
        Set<ReferenceEdge> prevSet = new HashSet<>(prevEdges);
        for (ReferenceEdge edge : curEdges) {
            if (prevSet.contains(edge)) {
                continue;
            }
            String text = describeSource(edge, curStack, curById, prevById)
                    + " now points to " + nameOf(edge.getTargetId(), curById, prevById);
            events.add(new RuntimeEvent(step, EventType.REFERENCE_ADDED, edge.getTargetId(), text));
        }
    }

    private boolean sourceStillExists(ReferenceEdge edge, List<StackFrameInfo> curStack,
                                      Map<Long, ObjectInfo> curById) {
        if ("OBJECT".equals(edge.getSourceKind())) {
            return curById.containsKey(edge.getSourceId());
        }
        if ("STACK".equals(edge.getSourceKind())) {
            return edge.getSourceId() < curStack.size();
        }
        return true;   // STATIC
    }

    private String describeSource(ReferenceEdge edge, List<StackFrameInfo> stack,
                                  Map<Long, ObjectInfo> primary, Map<Long, ObjectInfo> fallback) {
        if ("STACK".equals(edge.getSourceKind())) {
            int index = (int) edge.getSourceId();
            String frameName = (index >= 0 && index < stack.size())
                    ? stack.get(index).getMethodName() + "()"
                    : "frame " + index;
            return "Variable " + edge.getLabel() + " in " + frameName;
        }
        if ("STATIC".equals(edge.getSourceKind())) {
            return "Static " + edge.getLabel();
        }
        return nameOf(edge.getSourceId(), primary, fallback) + "." + edge.getLabel();
    }

    // --------------------------------------------------------------- values

    private void addValueChangedEvents(List<RuntimeEvent> events, int step,
                                       List<StackFrameInfo> prevStack, List<StackFrameInfo> curStack,
                                       Map<Long, ObjectInfo> prevById, List<ObjectInfo> curHeap) {
        // fields of objects that existed before AND exist now
        for (ObjectInfo cur : curHeap) {
            ObjectInfo old = prevById.get(cur.getId());
            if (old == null) {
                continue;   // brand new object: OBJECT_CREATED is enough
            }
            String owner = label(cur.getId(), cur.getClassName());
            for (Map.Entry<String, String> field : cur.getPrimitiveFields().entrySet()) {
                String text = valueText(owner + "." + field.getKey(),
                        old.getPrimitiveFields().get(field.getKey()), field.getValue());
                if (text != null) {
                    events.add(new RuntimeEvent(step, EventType.VALUE_CHANGED, cur.getId(), text));
                }
            }
        }
        // local variables of frames that existed before (same position, same method)
        int common = Math.min(prevStack.size(), curStack.size());
        for (int i = 0; i < common; i++) {
            StackFrameInfo oldFrame = prevStack.get(i);
            StackFrameInfo curFrame = curStack.get(i);
            if (!Objects.equals(oldFrame.getClassName(), curFrame.getClassName())
                    || !Objects.equals(oldFrame.getMethodName(), curFrame.getMethodName())) {
                continue;   // a different method: treat it as a new frame
            }
            for (Map.Entry<String, String> local : curFrame.getPrimitiveLocals().entrySet()) {
                String text = valueText(curFrame.getMethodName() + "(): " + local.getKey(),
                        oldFrame.getPrimitiveLocals().get(local.getKey()), local.getValue());
                if (text != null) {
                    events.add(new RuntimeEvent(step, EventType.VALUE_CHANGED, -1, text));
                }
            }
        }
    }

    /** @return the event text, or null if the value did not change. */
    private String valueText(String what, String oldValue, String newValue) {
        if (oldValue == null) {
            return what + " set to " + newValue;
        }
        if (!oldValue.equals(newValue)) {
            return what + " changed from " + oldValue + " to " + newValue;
        }
        return null;
    }

    // -------------------------------------------------------------------- GC

    private void addGcEvents(List<RuntimeEvent> events, int step, GcSimulator.StepResult gc,
                             Map<Long, ObjectInfo> prevById, Map<Long, ObjectInfo> curById) {
        if (gc == null) {
            return;
        }
        for (Long id : gc.getNewlyUnreachableIds()) {
            events.add(new RuntimeEvent(step, EventType.OBJECT_UNREACHABLE, id,
                    nameOf(id, curById, prevById) + " became unreachable"));
        }
        if (gc.isGcRun()) {
            events.add(new RuntimeEvent(step, EventType.GC_RUN, -1,
                    "Garbage collection ran: " + gc.getCollectedIds().size() + " object(s) collected"));
        }
        for (Long id : gc.getCollectedIds()) {
            events.add(new RuntimeEvent(step, EventType.OBJECT_COLLECTED, id,
                    nameOf(id, curById, prevById) + " collected by GC"));
        }
    }

    // --------------------------------------------------------------- helpers

    private Map<Long, ObjectInfo> byId(List<ObjectInfo> heap) {
        Map<Long, ObjectInfo> map = new HashMap<>();
        for (ObjectInfo obj : heap) {
            map.put(obj.getId(), obj);
        }
        return map;
    }

    /** Short name such as "Person#3". */
    private String label(long id, String className) {
        return (className == null ? "Object" : className) + "#" + id;
    }

    private String nameOf(long id, Map<Long, ObjectInfo> primary, Map<Long, ObjectInfo> fallback) {
        ObjectInfo obj = primary.get(id);
        if (obj == null) {
            obj = fallback.get(id);
        }
        return label(id, obj == null ? null : obj.getClassName());
    }
}