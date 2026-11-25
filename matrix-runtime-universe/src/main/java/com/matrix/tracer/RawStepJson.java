
package com.matrix.tracer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import com.matrix.model.RawStep;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class RawStepJson {

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public void save(List<RawStep> steps, String filePath)
            throws IOException {

        Path path = Path.of(filePath);

        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        Files.writeString(path, gson.toJson(steps));
    }

    public List<RawStep> load(String filePath)
            throws IOException {

        Path path = Path.of(filePath);

        String json = Files.readString(path);

        Type listType =
                new TypeToken<List<RawStep>>() {}.getType();

        List<RawStep> steps = gson.fromJson(json, listType);

        return steps == null ? List.of() : steps;
    }
}
