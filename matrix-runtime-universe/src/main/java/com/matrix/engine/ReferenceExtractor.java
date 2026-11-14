package com.matrix.engine;

import com.matrix.model.ObjectInfo;
import com.matrix.model.ReferenceEdge;
import com.matrix.model.StackFrameInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the list of "arrows" (ReferenceEdge) for one snapshot.
 *
 * There is one arrow for:
 *  - every reference local variable in every stack frame      (kind "STACK",  sourceId = frame index)
 *  - every static reference                                   (kind "STATIC", sourceId = -1)
 *  - every reference field of every object in the heap        (kind "OBJECT", sourceId = object id)
 *
 * Order: stack arrows first, then static arrows, then object arrows (always in the order given).
 */
public class ReferenceExtractor {

    public List<ReferenceEdge> extract(List<StackFrameInfo> stack,
                                       Map<String, Long> staticReferences,
                                       List<ObjectInfo> heap) {
        List<ReferenceEdge> edges = new ArrayList<>();

        // 1) STACK arrows: frame index 0 is the bottom frame (main)
        if (stack != null) {
            for (int frameIndex = 0; frameIndex < stack.size(); frameIndex++) {
                StackFrameInfo frame = stack.get(frameIndex);
                for (Map.Entry<String, Long> local : frame.getReferenceLocals().entrySet()) {
                    if (local.getValue() != null) {
                        edges.add(new ReferenceEdge("STACK", frameIndex, local.getKey(), local.getValue()));
                    }
                }
            }
        }

        // 2) STATIC arrows: label is "ClassName.fieldName", sourceId is -1
        if (staticReferences != null) {
            for (Map.Entry<String, Long> entry : staticReferences.entrySet()) {
                if (entry.getValue() != null) {
                    edges.add(new ReferenceEdge("STATIC", -1, entry.getKey(), entry.getValue()));
                }
            }
        }

        // 3) OBJECT arrows: every reference field of every object in the heap
        if (heap != null) {
            for (ObjectInfo obj : heap) {
                for (Map.Entry<String, Long> field : obj.getReferenceFields().entrySet()) {
                    if (field.getValue() != null) {
                        edges.add(new ReferenceEdge("OBJECT", obj.getId(), field.getKey(), field.getValue()));
                    }
                }
            }
        }

        return edges;
    }
}