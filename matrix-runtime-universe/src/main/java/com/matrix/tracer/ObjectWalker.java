
package com.matrix.tracer;

import com.matrix.model.ObjectInfo;
import com.matrix.model.ObjectStatus;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ObjectWalker {

    private final ValueFormatter formatter = new ValueFormatter();
    private final Map<Long, Integer> firstSeen = new LinkedHashMap<>();

    public List<ObjectInfo> capture(List<Value> roots, int stepNumber) {
        Map<Long, ObjectInfo> captured = new LinkedHashMap<>();
        Set<Long> visited = new HashSet<>();

        for (Value root : roots) {
            if (root instanceof ObjectReference && isTrackable((ObjectReference) root)) {
                walk((ObjectReference) root, stepNumber, captured, visited);
            }
        }

        return new ArrayList<>(captured.values());
    }

    private void walk(
            ObjectReference object,
            int stepNumber,
            Map<Long, ObjectInfo> captured,
            Set<Long> visited
    ) {
        long id = object.uniqueID();

        if (visited.contains(id) || visited.size() >= 200) {
            return;
        }

        visited.add(id);

        Map<String, String> primitiveFields = new LinkedHashMap<>();
        Map<String, Long> referenceFields = new LinkedHashMap<>();

        ObjectInfo info = new ObjectInfo(
                id,
                object.referenceType().name(),
                primitiveFields,
                referenceFields,
                ObjectStatus.LIVE,
                firstSeen.computeIfAbsent(id, ignored -> stepNumber),
                -1,
                -1
        );

        captured.put(id, info);

        if (object instanceof ArrayReference) {
            ArrayReference array = (ArrayReference) object;
            int limit = Math.min(array.length(), 200);

            for (int i = 0; i < limit; i++) {
                Value value = array.getValue(i);
                String fieldName = "[" + i + "]";
                recordValue(fieldName, value, primitiveFields,
                        referenceFields, stepNumber, captured, visited);
            }

            return;
        }

        ReferenceType type = object.referenceType();

        for (Field field : type.allFields()) {
            if (field.isStatic()) {
                continue;
            }

            Value value;
            try {
                value = object.getValue(field);
            } catch (Exception ignored) {
                continue;
            }

            recordValue(
                    field.name(),
                    value,
                    primitiveFields,
                    referenceFields,
                    stepNumber,
                    captured,
                    visited
            );
        }
    }

    private void recordValue(
            String name,
            Value value,
            Map<String, String> primitiveFields,
            Map<String, Long> referenceFields,
            int stepNumber,
            Map<Long, ObjectInfo> captured,
            Set<Long> visited
    ) {
        if (value == null) {
            return;
        }

        if (value instanceof ObjectReference) {
            ObjectReference reference = (ObjectReference) value;

            if (isTrackable(reference)) {
                referenceFields.put(name, reference.uniqueID());
                walk(reference, stepNumber, captured, visited);
            } else {
                primitiveFields.put(name, formatter.format(value));
            }
        } else {
            primitiveFields.put(name, formatter.format(value));
        }
    }

    private boolean isTrackable(ObjectReference object) {
        if (object instanceof ArrayReference) {
            return true;
        }

        String name = object.referenceType().name();

        if (name.startsWith("java.")
                || name.startsWith("javax.")
                || name.startsWith("jdk.")
                || name.startsWith("sun.")
                || name.startsWith("com.sun.")) {
            return false;
        }

        return true;
    }

    public void reset() {
    firstSeen.clear();
}

}
