package com.matrix.engine;

import com.matrix.model.ObjectInfo;
import com.matrix.model.ObjectStatus;
import com.matrix.model.StackFrameInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fills the "stats" of a snapshot with four numbers:
 *  - liveObjects:        how many objects in the heap are LIVE
 *  - unreachableObjects: how many objects in the heap are UNREACHABLE
 *  - collectedSoFar:     how many objects have been collected up to and including this step
 *  - stackDepth:         how many stack frames there are
 *
 * Objects that are COLLECTED in this step are counted in collectedSoFar only
 * (not as live and not as unreachable).
 */
public class StatsCalculator {

    public static final String LIVE_OBJECTS = "liveObjects";
    public static final String UNREACHABLE_OBJECTS = "unreachableObjects";
    public static final String COLLECTED_SO_FAR = "collectedSoFar";
    public static final String STACK_DEPTH = "stackDepth";

    public Map<String, Integer> calculate(List<StackFrameInfo> stack,
                                          List<ObjectInfo> heap,
                                          int collectedSoFar) {
        int live = 0;
        int unreachable = 0;
        if (heap != null) {
            for (ObjectInfo obj : heap) {
                if (obj.getStatus() == ObjectStatus.LIVE) {
                    live++;
                } else if (obj.getStatus() == ObjectStatus.UNREACHABLE) {
                    unreachable++;
                }
            }
        }

        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put(LIVE_OBJECTS, live);
        stats.put(UNREACHABLE_OBJECTS, unreachable);
        stats.put(COLLECTED_SO_FAR, collectedSoFar);
        stats.put(STACK_DEPTH, stack == null ? 0 : stack.size());
        return stats;
    }
}