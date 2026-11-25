
package com.matrix.tracer;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class SourceCompiler {

    public Path compile(String sourceFilePath) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        if (compiler == null) {
            throw new IllegalStateException(
                    "Java compiler not found. Please use a JDK, not a JRE."
            );
        }

        Path outputDirectory = Path.of("tmp-compiled");
        Files.createDirectories(outputDirectory);

        int result = compiler.run(
                null,
                null,
                null,
                "-g",
                "-d", outputDirectory.toString(),
                sourceFilePath
        );

        if (result != 0) {
            throw new RuntimeException("Compilation failed for: " + sourceFilePath);
        }

        return outputDirectory;
    }
}
