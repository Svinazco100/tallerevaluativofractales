
package co.icesi.fractal.monolith;

import FractalApp.RenderConfig;
import FractalApp.TaskTile;

import co.icesi.fractal.core.MandelbrotCore;
import co.icesi.fractal.strategy.PartitionStrategyFactory;
import co.icesi.fractal.strategy.TaskPartitionStrategy;

import java.util.List;

public class SmokeTestMain {

    public static void main(String[] args) {

        int width = 37;
        int height = 23;
        int iterations = 100;

        RenderConfig config = new RenderConfig(
                "test-local",
                width,
                height,
                -2.0,
                0.5,
                -1.25,
                1.25,
                iterations,
                0,
                4
        );

        int[][] reference = new int[height][width];

        double dx =
                (config.xMax - config.xMin) / (width - 1);

        double dy =
                (config.yMax - config.yMin) / (height - 1);

        for (int y = 0; y < height; y++) {

            for (int x = 0; x < width; x++) {

                reference[y][x] =
                        MandelbrotCore.computePoint(
                                config.xMin + x * dx,
                                config.yMin + y * dy,
                                iterations
                        );
            }
        }

        int[][] configurations = {
                {0, 2},
                {0, 4},
                {0, 16},
                {1, 8},
                {1, 16},
                {1, 32}
        };

        for (int[] test : configurations) {

            config.strategyType = test[0];
            config.strategyParam = test[1];

            TaskPartitionStrategy strategy =
                    PartitionStrategyFactory.createStrategy(
                            test[0],
                            test[1]
                    );

            List<TaskTile> tiles =
                    strategy.partition(config);

            int[][] actual = new int[height][width];
            int[][] visits = new int[height][width];

            for (TaskTile tile : tiles) {

                int[][] pixels =
                        MandelbrotCore.computeTile(tile);

                for (int y = 0; y < tile.tileHeight; y++) {

                    for (int x = 0; x < tile.tileWidth; x++) {

                        int yy = tile.startY + y;
                        int xx = tile.startX + x;

                        visits[yy][xx]++;

                        actual[yy][xx] = pixels[y][x];
                    }
                }
            }

            for (int y = 0; y < height; y++) {

                for (int x = 0; x < width; x++) {

                    if (visits[y][x] != 1 ||
                            actual[y][x] != reference[y][x]) {

                        throw new AssertionError(
                                "Error en pixel "
                                        + x + "," + y
                        );
                    }
                }
            }

            System.out.println(
                    "[OK] " + strategy.getStrategyName()
                            + " - Cero diferencias"
            );
        }

        System.out.println(
                "Todas las pruebas fueron exitosas"
        );
    }
}


