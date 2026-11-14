package com.matrix.engine;

import com.matrix.model.ObjectInfo;
import com.matrix.model.RawStep;
import com.matrix.model.StackFrameInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

/** Small helpers that build fake RawSteps by hand for tests. */
public class TestStepFactory {

    /** An object with no fields yet. */
    public static ObjectInfo obj(long id, String className) {
        ObjectInfo o = new ObjectInfo();
        o.setId(id);
        o.setClassName(className);
        return o;
    }

    /** Adds a reference field (an arrow to another object). Returns the same object. */
    public static ObjectInfo withRef(ObjectInfo o, String fieldName, long targetId) {
        o.getReferenceFields().put(fieldName, targetId);
        return o;
    }

    /** Adds a primitive field such as age=20. Returns the same object. */
    public static ObjectInfo withValue(ObjectInfo o, String fieldName, String value) {
        o.getPrimitiveFields().put(fieldName, value);
        return o;
    }

    /** A stack frame with no variables. */
    public static StackFrameInfo frame(String methodName) {
        StackFrameInfo f = new StackFrameInfo();
        f.setClassName("Main");
        f.setMethodName(methodName);
        f.setPrimitiveLocals(new LinkedHashMap<>());
        f.setReferenceLocals(new LinkedHashMap<>());
        return f;
    }

    /** Adds a reference local variable (for example p -> object 1). Returns the same frame. */
    public static StackFrameInfo withLocalRef(StackFrameInfo f, String varName, long targetId) {
        f.getReferenceLocals().put(varName, targetId);
        return f;
    }

    /** Adds a primitive local variable. Returns the same frame. */
    public static StackFrameInfo withLocalValue(StackFrameInfo f, String varName, String value) {
        f.getPrimitiveLocals().put(varName, value);
        return f;
    }

    /** A step with the given frames (bottom first) and objects. No statics, no output. */
    public static RawStep step(int stepNumber, List<StackFrameInfo> stack, ObjectInfo... objects) {
        return new RawStep(stepNumber, stepNumber + 1,
                new ArrayList<>(stack),
                new ArrayList<>(Arrays.asList(objects)),
                new LinkedHashMap<>(),
                "");
    }

    /** Adds a static reference (for example "Main.cache" -> 5). Returns the same step. */
    public static RawStep withStatic(RawStep s, String name, long targetId) {
        s.getStaticReferences().put(name, targetId);
        return s;
    }

    /** Shortcut: a list with the given frames. */
    public static List<StackFrameInfo> frames(StackFrameInfo... frames) {
        return new ArrayList<>(Arrays.asList(frames));
    }
}