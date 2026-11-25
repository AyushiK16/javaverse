
package com.matrix.tracer;

import com.matrix.model.RawStep;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class TracerDemo {

    public static void main(String[] args) throws Exception {

        JdiTracer tracer = new JdiTracer();

        String[] samples = {
                "Hello",
                "Objects1",
                "LinkedListDemo",
                "GarbageDemo",
                "Recursion"
        };

        Files.createDirectories(Path.of("samples-output"));

        for (String sample : samples) {

            System.out.println("\nTracing: " + sample);

            String sourcePath = "samples/" + sample + ".java";

            List<RawStep> steps = tracer.trace(sourcePath, 200);

            String outputPath =
                    "samples-output/" + sample + ".json";

            RawStepJson jsonHandler = new RawStepJson();
            jsonHandler.save(steps, outputPath);

            System.out.println("Steps recorded: " + steps.size());
            System.out.println("Saved to: " + outputPath);
        }
    }
}
