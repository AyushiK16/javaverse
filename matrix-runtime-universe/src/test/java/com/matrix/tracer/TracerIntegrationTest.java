
package com.matrix.tracer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.matrix.model.RawStep;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TracerIntegrationTest {

    private final JdiTracer tracer = new JdiTracer();
    private final Gson gson = new Gson();

    private JsonArray traceSample(String name) throws Exception {
        List<RawStep> steps = tracer.trace(
                "samples/" + name + ".java", 200
        );

        return gson.toJsonTree(steps).getAsJsonArray();
    }

    @Test
    void helloShouldCaptureMainStack() throws Exception {
        JsonArray steps = traceSample("Hello");

        assertFalse(steps.isEmpty());

        JsonObject first = steps.get(0).getAsJsonObject();
        JsonArray stack = first.getAsJsonArray("stack");

        assertEquals(1, stack.size());
        assertEquals(
                "main",
                stack.get(0).getAsJsonObject()
                        .get("methodName").getAsString()
        );
    }

    @Test
    void recursionShouldCaptureMultipleStackFrames() throws Exception {
        JsonArray steps = traceSample("Recursion");

        int maximumDepth = 0;

        for (JsonElement element : steps) {
            int depth = element.getAsJsonObject()
                    .getAsJsonArray("stack").size();

            maximumDepth = Math.max(maximumDepth, depth);
        }

        assertTrue(maximumDepth >= 5,
                "Recursion should produce at least 5 stack frames");
    }

    @Test
    void linkedListShouldCaptureFourConnectedNodes() throws Exception {
        JsonArray steps = traceSample("LinkedListDemo");

        int maximumNodes = 0;
        int maximumReferences = 0;

        for (JsonElement element : steps) {
            JsonArray objects = element.getAsJsonObject()
                    .getAsJsonArray("objects");

            int nodeCount = 0;
            int referenceCount = 0;

            for (JsonElement objectElement : objects) {
                JsonObject object = objectElement.getAsJsonObject();

                if (object.get("className").getAsString()
                        .equals("Node")) {

                    nodeCount++;

                    referenceCount += object
                            .getAsJsonObject("referenceFields")
                            .size();
                }
            }

            maximumNodes = Math.max(maximumNodes, nodeCount);
            maximumReferences = Math.max(
                    maximumReferences, referenceCount
            );
        }

        assertEquals(4, maximumNodes,
                "All four Node objects should be captured");

        assertTrue(maximumReferences >= 3,
                "The linked list should contain at least 3 next references");
    }

    @Test
    void garbageDemoShouldStopShowingUnreachableObjects()
            throws Exception {

        JsonArray steps = traceSample("GarbageDemo");

        boolean objectWasSeen = false;
        int finalObjectCount = 0;

        for (JsonElement element : steps) {
            JsonArray objects = element.getAsJsonObject()
                    .getAsJsonArray("objects");

            int count = 0;

            for (JsonElement objectElement : objects) {
                String className = objectElement.getAsJsonObject()
                        .get("className").getAsString();

                if (className.equals("GarbageObject")) {
                    count++;
                }
            }

            if (count > 0) {
                objectWasSeen = true;
            }

            finalObjectCount = count;
        }

        assertTrue(objectWasSeen,
                "GarbageObjects should initially be captured");

        assertEquals(0, finalObjectCount,
                "Unreachable objects should disappear from later snapshots");
    }

    @Test
    void stepNumbersShouldBeContinuous() throws Exception {

        String[] samples = {
                "Hello",
                "Objects1",
                "LinkedListDemo",
                "GarbageDemo",
                "Recursion"
        };

        for (String sample : samples) {
            JsonArray steps = traceSample(sample);

            for (int i = 0; i < steps.size(); i++) {
                int actualStep = steps.get(i).getAsJsonObject()
                        .get("stepNumber").getAsInt();

                assertEquals(i, actualStep,
                        sample + " has incorrect step numbering");
            }
        }
    }
}
