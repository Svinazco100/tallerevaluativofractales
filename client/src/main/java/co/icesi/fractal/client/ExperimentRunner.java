package co.icesi.fractal.client;

import FractalApp.*;
import co.icesi.fractal.core.FractalRegion;
import co.icesi.fractal.core.MandelbrotCore;
import com.zeroc.Ice.*;
import java.util.UUID;

/**
 * Harness automatizado para la ejecucion de la matriz experimental.
 * Ejecuta multiples pruebas variando la estrategia y parametros, y emite
 * una tabla de resultados comparativa en formato Markdown lista para el informe.
 */
public class ExperimentRunner {

    public static void main(String[] args) {
        String masterHost = (args.length >= 1) ? args[0] : "localhost";
        int masterPort = (args.length >= 2) ? Integer.parseInt(args[1]) : 10000;
        int width = (args.length >= 3) ? Integer.parseInt(args[2]) : 3840;
        int height = (args.length >= 4) ? Integer.parseInt(args[3]) : 2160;
        int maxIter = (args.length >= 5) ? Integer.parseInt(args[4]) : 5000;
        String presetName = (args.length >= 6) ? args[5] : "seahorse";

        FractalRegion region = FractalRegion.fromName(presetName);
        double xMin = region.getXMin();
        double xMax = region.getXMax();
        double yMin = region.getYMin();
        double yMax = region.getYMax();

        // Ajuste isometrico de aspecto
        if (width > 0 && height > 0) {
            double aspect = (double) width / (double) height;
            double realHeight = yMax - yMin;
            xMax = xMin + realHeight * aspect;
        }

        InitializationData initData = new InitializationData();
        initData.properties = Util.createProperties(args);
        initData.properties.setProperty("Ice.MessageSizeMax", "204800");

        try (Communicator communicator = Util.initialize(initData)) {
            String masterEndpoint = String.format("MasterService:tcp -h %s -p %d", masterHost, masterPort);
            MasterServicePrx master = MasterServicePrx.checkedCast(communicator.stringToProxy(masterEndpoint));

            if (master == null) {
                System.err.println("[ERROR] No se pudo conectar al Master.");
                return;
            }

            int p = master.getWorkerCount();
            System.out.println("=================================================");
            System.out.println("     INICIANDO MATRIZ DE EXPERIMENTACIÓN");
            System.out.println("=================================================");
            System.out.printf("Region / Preset: %s (%s)%n", region.getName(), region.getId());
            System.out.printf("Resolucion:      %dx%d | MaxIter: %d | Workers: %d%n%n", width, height, maxIter, p);

        long tSecOverride = (args.length >= 7) ? Long.parseLong(args[6]) : -1L;
        long tSec;

        if (tSecOverride > 0) {
            tSec = tSecOverride;
            System.out.printf("Utilizando T_sec precalculado: %d ms (%.2f s)%n%n", tSec, tSec / 1000.0);
        } else {
            // 1. Linea base monohilo
            System.out.println("Calculando tiempo secuencial monohilo T_sec...");
            long t0Sec = System.currentTimeMillis();
            double dx = (width > 1) ? (xMax - xMin) / (width - 1) : 0.0;
            double dy = (height > 1) ? (yMax - yMin) / (height - 1) : 0.0;

            for (int y = 0; y < height; y++) {
                double ci = yMin + y * dy;
                for (int x = 0; x < width; x++) {
                    double cr = xMin + x * dx;
                    MandelbrotCore.computePoint(cr, ci, maxIter);
                }
            }
            tSec = System.currentTimeMillis() - t0Sec;
            System.out.printf("T_sec = %d ms (%.2f s)%n%n", tSec, tSec / 1000.0);
        }

        // Casos experimentales exactos de la Tabla 1 del PDF (Seccion 5.1):
        TestCase[] testCases = {
            new TestCase("FixedCount (Franjas fijas)", "N = 2", "2 franjas", 0, 2),
            new TestCase("FixedCount (Franjas fijas)", "N = 4", "4 franjas", 0, 4),
            new TestCase("FixedCount (Franjas fijas)", "N = 16", "16 franjas", 0, 16),
            new TestCase("FixedTileSize (Grano ultrafino)", "Baldosas 32 × 32", "8,160 baldosas", 1, 32),
            new TestCase("FixedTileSize (Grano óptimo)", "Baldosas 64 × 64", "2,040 baldosas", 1, 64),
            new TestCase("FixedTileSize (Grano intermedio)", "Baldosas 128 × 128", "510 baldosas", 1, 128),
            new TestCase("FixedTileSize (Grano grueso)", "Baldosas 256 × 256", "135 baldosas", 1, 256)
        };

        System.out.println("### Tabla 1: Matriz de impacto de la granularidad sobre el rendimiento distribuido en 4K UHD");
        System.out.println("| Estrategia / Configuración | Parámetro | Tareas Creadas | T_sec (ms) | T_dist (ms) | Speedup (S) | Eficiencia (E) |");
        System.out.println("| :--- | :---: | :---: | :---: | :---: | :---: | :---: |");

        for (TestCase tc : testCases) {
            String jobId = "exp1-" + UUID.randomUUID().toString().substring(0, 8);
            RenderConfig config = new RenderConfig(
                jobId, width, height, xMin, xMax, yMin, yMax, maxIter, tc.strategyType, tc.strategyParam
            );

            long t0Dist = System.currentTimeMillis();
            master.renderFractal(config);
            long tDist = System.currentTimeMillis() - t0Dist;

            double speedup = (double) tSec / (double) Math.max(1, tDist);
            double efficiency = (p > 0) ? (speedup / p) * 100.0 : 0.0;

            System.out.printf("| %s | %s | %s | %d | %d | %.2fx | %.1f%% |%n",
                tc.name, tc.paramStr, tc.tasksStr, tSec, tDist, speedup, efficiency);
        }

        System.out.println("\n[FIN] Matriz experimental finalizada.");

        } catch (java.lang.Exception e) {
            System.err.println("[ERROR] Error en el ejecutor de experimentos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static class TestCase {
        final String name;
        final String paramStr;
        final String tasksStr;
        final int strategyType;
        final int strategyParam;

        TestCase(String name, String paramStr, String tasksStr, int strategyType, int strategyParam) {
            this.name = name;
            this.paramStr = paramStr;
            this.tasksStr = tasksStr;
            this.strategyType = strategyType;
            this.strategyParam = strategyParam;
        }
    }
}
