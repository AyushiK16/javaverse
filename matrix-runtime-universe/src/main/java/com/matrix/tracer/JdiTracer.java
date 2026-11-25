
package com.matrix.tracer;

import com.matrix.model.RawStep;
import com.matrix.model.StackFrameInfo;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.Field;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.StepRequest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JdiTracer implements StepProducer {

    private final SourceCompiler compiler = new SourceCompiler();
    private final TargetLauncher launcher = new TargetLauncher();
    private final ValueFormatter formatter = new ValueFormatter();
    private final ObjectWalker objectWalker = new ObjectWalker();

    @Override
    public List<RawStep> trace(String sourceFilePath, int maxSteps)
            throws Exception {

        if (maxSteps <= 0) {
            return new ArrayList<>();
        }

        objectWalker.reset();

        Path classesDirectory = compiler.compile(sourceFilePath);
        String fileName = Path.of(sourceFilePath).getFileName().toString();
        String className = fileName.substring(0, fileName.lastIndexOf('.'));

        VirtualMachine vm = launcher.launch(classesDirectory, className);
        List<RawStep> steps = new ArrayList<>();

        try {
            ThreadReference mainThread = findMainThread(vm);

            if (mainThread == null) {
                throw new IllegalStateException("Could not find the main thread.");
            }

            StepRequest stepRequest = vm.eventRequestManager()
                    .createStepRequest(
                            mainThread,
                            StepRequest.STEP_LINE,
                            StepRequest.STEP_INTO
                    );

            stepRequest.addClassExclusionFilter("java.*");
            stepRequest.addClassExclusionFilter("javax.*");
            stepRequest.addClassExclusionFilter("jdk.*");
            stepRequest.addClassExclusionFilter("sun.*");
            stepRequest.addClassExclusionFilter("com.sun.*");
            stepRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
            stepRequest.enable();

            EventQueue queue = vm.eventQueue();
            boolean finished = false;

            vm.resume();

            while (!finished && steps.size() < maxSteps) {
                EventSet eventSet = queue.remove();

                for (Event event : eventSet) {
                    if (event instanceof StepEvent) {
                        StepEvent stepEvent = (StepEvent) event;
                        stepRequest.disable();

                        RawStep rawStep = captureStep(
                                stepEvent.thread(),
                                vm,
                                steps.size()
                        );

                        steps.add(rawStep);

                        if (steps.size() < maxSteps) {
                            stepRequest.enable();
                        }
                    } else if (event instanceof VMDeathEvent
                            || event instanceof VMDisconnectEvent) {
                        finished = true;
                    }
                }

                eventSet.resume();
            }

            return steps;

        } finally {
            try {
                vm.dispose();
            } catch (Exception ignored) {
                // The target VM may already have disconnected.
            }
        }
    }

    private ThreadReference findMainThread(VirtualMachine vm) {
        for (ThreadReference thread : vm.allThreads()) {
            if ("main".equals(thread.name())) {
                return thread;
            }
        }

        List<ThreadReference> threads = vm.allThreads();
        return threads.isEmpty() ? null : threads.get(0);
    }

    private RawStep captureStep(
            ThreadReference thread,
            VirtualMachine vm,
            int stepNumber
    ) throws Exception {

        List<StackFrame> frames = new ArrayList<>(thread.frames());
        List<StackFrameInfo> stack = new ArrayList<>();
        List<Value> roots = new ArrayList<>();
        Map<String, Long> staticReferences = new LinkedHashMap<>();

        int lineNumber = -1;

        // JDI gives frames from top to bottom.
        // Reverse them so index 0 is main (the bottom frame).
        Collections.reverse(frames);

        for (StackFrame frame : frames) {
            String className = frame.location().declaringType().name();
            String methodName = frame.location().method().name();
            int frameLine = frame.location().lineNumber();

            if (frameLine >= 0) {
                lineNumber = frameLine;
            }

            Map<String, String> primitiveLocals = new LinkedHashMap<>();
            Map<String, Long> referenceLocals = new LinkedHashMap<>();

            try {
                for (LocalVariable variable : frame.visibleVariables()) {
                    Value value = frame.getValue(variable);
                    String name = variable.name();

                    if (value instanceof ObjectReference
                            && !formatter.isSimpleValue(value)) {

                        ObjectReference object = (ObjectReference) value;
                        referenceLocals.put(name, object.uniqueID());
                        roots.add(value);

                    } else {
                        primitiveLocals.put(name, formatter.format(value));
                    }
                }
            } catch (com.sun.jdi.AbsentInformationException ignored) {
                // Some frames may not have local-variable debug information.
            }

            stack.add(new StackFrameInfo(
                    className,
                    methodName,
                    frameLine,
                    primitiveLocals,
                    referenceLocals
            ));
        }

        // Also inspect static references from user classes.
        for (ReferenceType type : vm.allClasses()) {
            String name = type.name();

            if (isLibraryClass(name)) {
                continue;
            }

            for (Field field : type.allFields()) {
                if (!field.isStatic()) {
                    continue;
                }

                try {
                    Value value = type.getValue(field);

                    if (value instanceof ObjectReference
                            && !formatter.isSimpleValue(value)) {

                        ObjectReference object = (ObjectReference) value;
                        staticReferences.put(
                                name + "." + field.name(),
                                object.uniqueID()
                        );
                        roots.add(value);
                    }
                } catch (Exception ignored) {
                    // Skip fields that cannot be read.
                }
            }
        }

        ObjectWalker walker = objectWalker;
        List<com.matrix.model.ObjectInfo> objects =
                walker.capture(roots, stepNumber);

        return new RawStep(
                stepNumber,
                lineNumber,
                stack,
                objects,
                staticReferences,
                ""
        );
    }

    private boolean isLibraryClass(String name) {
        return name.startsWith("java.")
                || name.startsWith("javax.")
                || name.startsWith("jdk.")
                || name.startsWith("sun.")
                || name.startsWith("com.sun.");
    }
}
