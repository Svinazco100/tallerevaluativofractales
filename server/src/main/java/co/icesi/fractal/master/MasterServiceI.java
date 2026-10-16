
package co.icesi.fractal.master;

import FractalApp.*;
import co.icesi.fractal.strategy.PartitionStrategyFactory;
import co.icesi.fractal.strategy.TaskPartitionStrategy;
import com.zeroc.Ice.Current;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MasterServiceI implements MasterService {

    private final Map<String, WorkerObserverPrx> workers =
            new ConcurrentHashMap<>();

    private final AtomicBoolean rendering =
            new AtomicBoolean(false);

    private volatile Job activeJob;

    private static class Job {

        final String id;
        final int[][] image;
        final TaskTile[] definitions;

        final ConcurrentLinkedQueue<TaskTile> queue;
        final BitSet reported;

        final CompletableFuture<int[][]> finished =
                new CompletableFuture<>();

        final Map<String, WorkerLoad> distribution =
                new HashMap<>();

        int completeCount = 0;

        Job(RenderConfig config, List<TaskTile> tiles) {

            id = config.jobId;

            image = new int[config.height][config.width];

            definitions =
                    tiles.toArray(new TaskTile[0]);

            queue =
                    new ConcurrentLinkedQueue<>(tiles);

            reported = new BitSet(tiles.size());
        }
    }

    private static class WorkerLoad {
        int tasks = 0;
        long accumulatedComputeMs = 0;
    }

    @Override
    public int[][] renderFractal(
            RenderConfig config,
            Current current) throws FractalException {

        if (config == null ||
                config.jobId == null ||
                config.jobId.isBlank() ||
                config.width <= 0 ||
                config.height <= 0 ||
                config.maxIterations <= 0 ||
                config.strategyParam <= 0 ||
                (config.strategyType != 0 &&
                        config.strategyType != 1) ||
                !(config.xMin < config.xMax) ||
                !(config.yMin < config.yMax)) {

            throw new FractalException(
                    "Configuracion invalida");
        }

        if (workers.isEmpty()) {
            throw new FractalException(
                    "No hay Workers registrados");
        }

        if (!rendering.compareAndSet(false, true)) {
            throw new FractalException(
                    "El Master ya esta procesando otro trabajo");
        }

        try {

            TaskPartitionStrategy strategy =
                    PartitionStrategyFactory.createStrategy(
                            config.strategyType,
                            config.strategyParam
                    );

            List<TaskTile> tiles =
                    strategy.partition(config);

            if (tiles.isEmpty()) {
                throw new FractalException(
                        "No se generaron tareas");
            }

            Job job = new Job(config, tiles);

            activeJob = job;

            System.out.printf(
                    "[MASTER] Trabajo %s - %d tareas - %d Workers%n",
                    job.id,
                    tiles.size(),
                    workers.size()
            );

            long start = System.nanoTime();

            for (Map.Entry<String, WorkerObserverPrx> entry
                    : workers.entrySet()) {

                notifyWorker(
                        entry.getKey(),
                        entry.getValue(),
                        job.id
                );
            }

            try {

                int[][] result = job.finished.get(
                        60,
                        TimeUnit.MINUTES
                );

                double elapsed =
                        (System.nanoTime() - start) / 1e9;

                System.out.printf(
                        "[MASTER] Trabajo completado en %.3f segundos%n",
                        elapsed
                );

                synchronized (job) {

                    job.distribution.forEach((id, load) -> {

                        System.out.printf(
                                "[MASTER] %s: %d tareas, %d ms acumulados%n",
                                id,
                                load.tasks,
                                load.accumulatedComputeMs
                        );
                    });
                }

                return result;

            } catch (TimeoutException e) {

                throw new FractalException(
                        "Tiempo agotado esperando Workers");

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();

                throw new FractalException(
                        "Trabajo interrumpido");

            } catch (ExecutionException e) {

                throw new FractalException(
                        "Error de procesamiento: " + e.getMessage());
            }

        } finally {

            activeJob = null;
            rendering.set(false);
        }
    }

    private void notifyWorker(
            String workerId,
            WorkerObserverPrx observer,
            String jobId) {

        CompletableFuture.runAsync(() -> {

            try {

                observer.notifyTasksAvailable(jobId);

            } catch (java.lang.Exception e) {

                System.err.printf(
                        "[MASTER] No se pudo notificar a %s: %s%n",
                        workerId,
                        e.getMessage()
                );
            }
        });
    }

    @Override
    public void registerWorker(
            String workerId,
            WorkerObserverPrx observer,
            Current current) {

        if (workerId == null ||
                workerId.isBlank() ||
                observer == null) {
            return;
        }

        workers.put(workerId, observer);

        System.out.println(
                "[MASTER] Worker registrado: " + workerId
        );

        Job job = activeJob;

        if (job != null &&
                !job.finished.isDone() &&
                !job.queue.isEmpty()) {

            notifyWorker(workerId, observer, job.id);
        }
    }

    @Override
    public void unregisterWorker(
            String workerId,
            Current current) {

        workers.remove(workerId);

        System.out.println(
                "[MASTER] Worker retirado: " + workerId
        );
    }

    @Override
    public TaskTile pollTask(
            String workerId,
            Current current) {

        Job job = activeJob;

        if (job != null && !job.finished.isDone()) {

            TaskTile task = job.queue.poll();

            if (task != null) {
                return task;
            }
        }

        return new TaskTile(
                false, "", -1,
                0, 0, 0, 0,
                0, 0,
                0, 0, 0, 0, 0
        );
    }

    @Override
    public void reportResult(
            TileResult result,
            Current current) throws FractalException {

        Job job = activeJob;

        if (job == null ||
                result == null ||
                !job.id.equals(result.jobId)) {

            throw new FractalException(
                    "Resultado de un trabajo inexistente");
        }

        if (result.taskId < 0 ||
                result.taskId >= job.definitions.length ||
                result.pixels == null) {

            throw new FractalException(
                    "Resultado invalido");
        }

        TaskTile expected =
                job.definitions[result.taskId];

        if (result.startX != expected.startX ||
                result.startY != expected.startY ||
                result.tileWidth != expected.tileWidth ||
                result.tileHeight != expected.tileHeight ||
                result.pixels.length != expected.tileHeight) {

            throw new FractalException(
                    "Dimensiones incorrectas");
        }

        for (int[] row : result.pixels) {

            if (row == null ||
                    row.length != expected.tileWidth) {

                throw new FractalException(
                        "Fila de pixeles invalida");
            }
        }

        synchronized (job) {

            if (job.reported.get(result.taskId)) {
                return;
            }

            if (job.finished.isDone()) {
                return;
            }

            for (int y = 0; y < expected.tileHeight; y++) {

                System.arraycopy(
                        result.pixels[y],
                        0,
                        job.image[expected.startY + y],
                        expected.startX,
                        expected.tileWidth
                );
            }

            job.reported.set(result.taskId);

            String id = result.workerId == null
                    ? "Desconocido"
                    : result.workerId;

            WorkerLoad load =
                    job.distribution.computeIfAbsent(
                            id,
                            ignored -> new WorkerLoad()
                    );

            load.tasks++;

            load.accumulatedComputeMs +=
                    Math.max(0, result.computeTimeMs);

            int done = ++job.completeCount;

            if (done == job.definitions.length) {

                job.finished.complete(job.image);

            } else if (done % 100 == 0 || done == 1) {

                System.out.printf(
                        "[MASTER] Progreso: %d/%d%n",
                        done,
                        job.definitions.length
                );
            }
        }
    }

    @Override
    public int getWorkerCount(Current current) {
        return workers.size();
    }

    @Override
    public int getPendingTasksCount(Current current) {

        Job job = activeJob;

        return job == null ? 0 : job.queue.size();
    }
}
