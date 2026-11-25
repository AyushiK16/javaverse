
package com.matrix.tracer;

import com.sun.jdi.ArrayReference;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.StringReference;
import com.sun.jdi.Value;

public class ValueFormatter {

    public String format(Value value) {
        if (value == null) {
            return "null";
        }

        if (value instanceof StringReference) {
            return "\"" + ((StringReference) value).value() + "\"";
        }

        if (value instanceof ArrayReference) {
            ArrayReference array = (ArrayReference) value;
            return array.referenceType().name() + "[length="
                    + array.length() + "]";
        }

        if (value instanceof ObjectReference) {
            ObjectReference object = (ObjectReference) value;
            return object.referenceType().name()
                    + "@" + object.uniqueID();
        }

        return value.toString();
    }

    public boolean isSimpleValue(Value value) {
        if (value == null) {
            return true;
        }

        return !(value instanceof ObjectReference)
                || value instanceof StringReference;
    }
}
