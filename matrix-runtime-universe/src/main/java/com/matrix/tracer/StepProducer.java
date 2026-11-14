
package com.matrix.tracer;

import com.matrix.model.RawStep;
import java.util.List;

public interface StepProducer {
    List<RawStep> trace(String sourceFilePath, int maxSteps) throws Exception;
}
