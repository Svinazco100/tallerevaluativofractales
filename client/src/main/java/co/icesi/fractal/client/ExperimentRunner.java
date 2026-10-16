
package co.icesi.fractal.client;

import FractalApp.*;
import co.icesi.fractal.core.FractalRegion;
import co.icesi.fractal.core.MandelbrotCore;
import com.zeroc.Ice.*;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

import java.util.concurrent.TimeUnit;

public class ExperimentRunner {

    private record Case(int strategy, int param) {}

    private static final String HEADER =
            "fecha,experimento,escenario,preset,ancho,alto,maxIter," +
                    "workers,hilosPorWorker,estrategia,parametro,tareas," +
                    "tSecMs,tDistMs,speedup,eficienciaPct,diferencias,correcto";

    public static void main(String[] args) {

        String host = args.length > 0
                ? args[0] : "localhost";

        int port = args.length > 1
                ? Integer.parseInt(args[1]) : 10000;

        int width = args.length > 2
                ? Integer.parseInt(args[2]) : 3840;

        int height = args.length > 3
                ? Integer.parseInt(args[3]) : 2160;

        int maxIter = args.length > 4
                ? Integer.parseInt(args[4]) : 5000;

        String preset = args.length > 5
                ? args[5] : "seahorse";

        String mode = args.length > 6
                ? args[6].toLowerCase(Locale.ROOT)
                : "granularidad";

        String label = args.length > 7
                ? args[7] : "4";

        String csvFile = args.length > 8
                ? args[8] : "resultados/mediciones.csv";

        int selectedStrategy = args.length > 9
                ? Integer.parseInt(args[9]) : 1;

        int selectedParam = args.length > 10
                ? Integer.parseInt(args[10]) : 64;

        int expectedWorkers = args.length > 11
                ? Integer.parseInt(args[11]) : -1;

        List<Case> cases = new ArrayList<>();

        switch (mode) {

            case "granularidad" -> {

                cases.add(new Case(0, 2));
                cases.add(new Case(0, 4));
                cases.add(new Case(0, 16));

                for (int size :
                        new int[]{32, 64, 128, 256}) {

                    cases.add(new Case(1, size));
                }
            }

            case "hilos", "workers", "contraste" -> {

                cases.add(
                        new Case(
                                selectedStrategy,
                                selectedParam
                        )
                );
            }

            default -> throw new IllegalArgumentException(
                    "Experimento desconocido: " + mode
            );
        }

        FractalRegion region =
                FractalRegion.fromName(preset);

        double xmin = region.getXMin();
        double xmax = region.getXMax();
        double ymin = region.getYMin();
        double ymax = region.getYMax();

        InitializationData initData =
                new InitializationData();

        initData.properties =
                Util.createProperties(args);

        initData.properties.setProperty(
                "Ice.MessageSizeMax", "204800");

        try (Communicator communicator =
                     Util.initialize(initData)) {

            String endpoint = String.format(
                    "MasterService:tcp -h %s -p %d",
                    host,
                    port
            );

            MasterServicePrx master =
                    MasterServicePrx.checkedCast(
                            communicator.stringToProxy(endpoint)
                    );

            if (master == null) {
                throw new IllegalStateException(
                        "No se pudo conectar al Master");
            }

            int workers = master.getWorkerCount();

            if (expectedWorkers > 0) {

                long deadline = System.nanoTime()
                        + TimeUnit.SECONDS.toNanos(120);

                while (workers != expectedWorkers &&
                        System.nanoTime() < deadline) {

                    Thread.sleep(500);

                    workers = master.getWorkerCount();
                }

                if (workers != expectedWorkers) {

                    throw new IllegalStateException(
                            "Workers esperados: " + expectedWorkers
                                    + ", registrados: " + workers
                    );
                }
            }

            if (workers == 0) {
                throw new IllegalStateException(
                        "No hay Workers registrados");
            }

            int[][] baseline =
                    new int[height][width];

            double dx = width > 1
                    ? (xmax - xmin) / (width - 1)
                    : 0;

            double dy = height > 1
                    ? (ymax - ymin) / (height - 1)
                    : 0;

            long startSec = System.nanoTime();

            for (int y = 0; y < height; y++) {

                double ci = ymin + y * dy;

                for (int x = 0; x < width; x++) {

                    double cr = xmin + x * dx;

                    baseline[y][x] =
                            MandelbrotCore.computePoint(
                                    cr, ci, maxIter
                            );
                }
            }

            double tSec = (System.nanoTime() - startSec)
                    / 1_000_000.0;

            System.out.printf(
                    Locale.US,
                    "Tiempo secuencial: %.3f ms%n",
                    tSec
            );

            for (Case test : cases) {

                int strategy = test.strategy();
                int param = test.param();

                int taskCount;

                if (strategy == 0) {

                    taskCount = Math.min(param, height);

                } else {

                    taskCount =
                            ((width + param - 1) / param)
                                    * ((height + param - 1) / param);
                }

                RenderConfig config = new RenderConfig(
                        "exp-" + UUID.randomUUID(),
                        width,
                        height,
                        xmin,
                        xmax,
                        ymin,
                        ymax,
                        maxIter,
                        strategy,
                        param
                );

                long startDist = System.nanoTime();

                int[][] distributed =
                        master.renderFractal(config);

                double tDist =
                        (System.nanoTime() - startDist)
                                / 1_000_000.0;

                long differences = 0;

                if (distributed == null ||
                        distributed.length != height) {

                    throw new IllegalStateException(
                            "Imagen recibida invalida");
                }

                for (int y = 0; y < height; y++) {

                    if (distributed[y] == null ||
                            distributed[y].length != width) {

                        throw new IllegalStateException(
                                "Fila invalida: " + y);
                    }

                    for (int x = 0; x < width; x++) {

                        if (baseline[y][x]
                                != distributed[y][x]) {

                            differences++;
                        }
                    }
                }

                double speedup =
                        tSec / Math.max(tDist, 0.001);

                double efficiency =
                        speedup / workers * 100.0;

                String kind = strategy == 0
                        ? "Franjas" : "Baldosas";

                System.out.printf(
                        Locale.US,
                        "%s %d | Tareas: %d | "
                                + "T_dist: %.2f ms | "
                                + "Speedup: %.3f | "
                                + "Eficiencia: %.2f%% | "
                                + "Diferencias: %d%n",
                        kind,
                        param,
                        taskCount,
                        tDist,
                        speedup,
                        efficiency,
                        differences
                );

                String row = String.format(
                        Locale.US,
                        "%s,%s,%s,%s,%d,%d,%d,%d,%s,%s,"
                                + "%d,%d,%.3f,%.3f,%.6f,%.3f,%d,%s",
                        LocalDateTime.now(),
                        mode,
                        label.replace(',', '_'),
                        region.getId(),
                        width,
                        height,
                        maxIter,
                        workers,
                        mode.equals("hilos") ? label : "",
                        kind,
                        param,
                        taskCount,
                        tSec,
                        tDist,
                        speedup,
                        efficiency,
                        differences,
                        differences == 0
                );

                saveCsv(csvFile, row);

                if (differences != 0) {

                    throw new IllegalStateException(
                            "Las imagenes tienen "
                                    + differences
                                    + " pixeles diferentes");
                }
            }

            System.out.println(
                    "Experimentos finalizados");

        } catch (Exception e) {

            System.err.println(
                    "Error: " + e.getMessage());

            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void saveCsv(
            String filename,
            String row) throws java.io.IOException {

        Path path = Path.of(filename);

        if (path.getParent() != null) {

            Files.createDirectories(
                    path.getParent());
        }

        if (!Files.exists(path) ||
                Files.size(path) == 0) {

            Files.writeString(
                    path,
                    HEADER + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        }

        Files.writeString(
                path,
                row + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND
        );
    }
}
