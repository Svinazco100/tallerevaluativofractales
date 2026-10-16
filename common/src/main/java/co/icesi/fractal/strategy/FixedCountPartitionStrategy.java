package co.icesi.fractal.strategy;

import FractalApp.RenderConfig;
import FractalApp.TaskTile;
import java.util.ArrayList;
import java.util.List;

/**
 * Estrategia de particionamiento en N tareas fijas (Franjas horizontales).
 * Divide la altura total de la imagen en N bloques contiguos.
 */
public class FixedCountPartitionStrategy implements TaskPartitionStrategy {

    private final int taskCount;

    public FixedCountPartitionStrategy(int taskCount) {
        this.taskCount = Math.max(1, taskCount);
    }

    @Override
    public List<TaskTile> partition(RenderConfig config) {
        List<TaskTile> tiles = new ArrayList<>();

        int width = config.width;
        int height = config.height;
        int n = Math.min(taskCount, height);

        int baseHeight = height / n;
        int remainingRows = height % n;
        int currentY = 0;

        for (int taskId = 0; taskId < n; taskId++) {

            int currentTileHeight =
                    baseHeight + (taskId < remainingRows ? 1 : 0);

            TaskTile tile = new TaskTile(
                    true,
                    config.jobId,
                    taskId,
                    0,
                    currentY,
                    width,
                    currentTileHeight,
                    width,
                    height,
                    config.xMin,
                    config.xMax,
                    config.yMin,
                    config.yMax,
                    config.maxIterations
            );

            tiles.add(tile);
            currentY += currentTileHeight;
        }

        return tiles;
    }

    @Override
    public String getStrategyName() {
        return "FixedCountStrategy (N = " + taskCount + " franjas)";
    }
}
