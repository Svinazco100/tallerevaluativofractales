
package co.icesi.fractal.client;

import FractalApp.FractalException;
import FractalApp.MasterServicePrx;
import FractalApp.RenderConfig;

import co.icesi.fractal.core.FractalRegion;
import co.icesi.fractal.core.MandelbrotCore;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.InitializationData;
import com.zeroc.Ice.Util;

import java.io.IOException;
import java.util.UUID;

/**
 * Cliente del sistema distribuido de fractales.
 *
 * Funciones:
 * 1. Conectarse al Master mediante ZeroC Ice.
 * 2. Calcular la versión secuencial como referencia.
 * 3. Solicitar el procesamiento distribuido.
 * 4. Comparar los resultados píxel por píxel.
 * 5. Calcular Speedup y eficiencia.
 * 6. Guardar la imagen final.
 */
public class ClientMain {

    public static void main(String[] args) {

        // Configuración del Master
        String masterHost = args.length > 0
                ? args[0] : "localhost";

        int masterPort = args.length > 1
                ? Integer.parseInt(args[1]) : 10000;

        // Configuración de la imagen
        int width = args.length > 2
                ? Integer.parseInt(args[2]) : 3840;

        int height = args.length > 3
                ? Integer.parseInt(args[3]) : 2160;

        int maxIter = args.length > 4
                ? Integer.parseInt(args[4]) : 5000;

        // 0 = franjas fijas
        // 1 = baldosas
        int strategyType = args.length > 5
                ? Integer.parseInt(args[5]) : 1;

        int strategyParam = args.length > 6
                ? Integer.parseInt(args[6]) : 64;

        String presetName = "seahorse";
        String outputPath = "output/mandelbrot_distribuido.png";

        // Permite indicar región y ruta de salida
        if (args.length > 7) {

            if (args[7].endsWith(".png")) {
                outputPath = args[7];
            } else {
                presetName = args[7];

                if (args.length > 8) {
                    outputPath = args[8];
                }
            }
        }

        // Validación de parámetros
        if (width <= 0 || height <= 0 || maxIter <= 0) {
            System.err.println(
                    "[ERROR] Dimensiones o iteraciones inválidas."
            );
            return;
        }

        if (masterPort < 1 || masterPort > 65535) {
            System.err.println(
                    "[ERROR] Puerto del Master inválido."
            );
            return;
        }

        if (strategyType != 0 && strategyType != 1) {
            System.err.println(
                    "[ERROR] Estrategia inválida. Use 0 o 1."
            );
            return;
        }

        if (strategyParam <= 0) {
            System.err.println(
                    "[ERROR] El parámetro debe ser positivo."
            );
            return;
        }

        // Obtener región del fractal
        FractalRegion region =
                FractalRegion.fromName(presetName);

        double xMin = region.getXMin();
        double xMax = region.getXMax();
        double yMin = region.getYMin();
        double yMax = region.getYMax();

        // Ajustar la relación de aspecto
        double aspect = (double) width / height;
        double realHeight = yMax - yMin;

        xMax = xMin + realHeight * aspect;

        // Configuración de ZeroC Ice
        InitializationData initData =
                new InitializationData();

        initData.properties =
                Util.createProperties();

        // Tamaño máximo de mensajes: 200 MB
        initData.properties.setProperty(
                "Ice.MessageSizeMax",
                "204800"
        );

        try (Communicator communicator =
                     Util.initialize(initData)) {

            // Dirección del servicio Master
            String endpoint = String.format(
                    "MasterService:tcp -h %s -p %d",
                    masterHost,
                    masterPort
            );

            System.out.println(
                    "========================================"
            );
            System.out.println(
                    "       CLIENTE MASTER-WORKER"
            );
            System.out.println(
                    "========================================"
            );

            System.out.println(
                    "[CLIENT] Conectando al Master..."
            );

            MasterServicePrx master =
                    MasterServicePrx.checkedCast(
                            communicator.stringToProxy(endpoint)
                    );

            if (master == null) {
                System.err.println(
                        "[ERROR] No se encontró el servicio Master."
                );
                return;
            }

            // Consultar Workers disponibles
            int workerCount = master.getWorkerCount();

            System.out.println(
                    "[CLIENT] Conectado: " + endpoint
            );

            System.out.println(
                    "[CLIENT] Workers registrados: "
                            + workerCount
            );

            if (workerCount == 0) {
                System.err.println(
                        "[ERROR] No hay Workers registrados."
                );
                return;
            }

            // Mostrar configuración
            System.out.println(
                    "\n========== CONFIGURACIÓN =========="
            );

            System.out.println(
                    "Región: " + region.getName()
            );

            System.out.println(
                    "Resolución: " + width + "x" + height
            );

            System.out.println(
                    "Iteraciones máximas: " + maxIter
            );

            System.out.println(
                    "Estrategia: "
                            + (strategyType == 0
                            ? "FixedCount"
                            : "FixedTileSize")
            );

            System.out.println(
                    "Parámetro: " + strategyParam
            );

            // ==================================
            // 1. PROCESAMIENTO SECUENCIAL
            // ==================================

            System.out.println(
                    "\n[1/3] Iniciando procesamiento secuencial..."
            );

            int[][] localMatrix =
                    new int[height][width];

            double dx = width > 1
                    ? (xMax - xMin) / (width - 1)
                    : 0.0;

            double dy = height > 1
                    ? (yMax - yMin) / (height - 1)
                    : 0.0;

            long startSec = System.nanoTime();

            for (int y = 0; y < height; y++) {

                double ci = yMin + y * dy;

                for (int x = 0; x < width; x++) {

                    double cr = xMin + x * dx;

                    localMatrix[y][x] =
                            MandelbrotCore.computePoint(
                                    cr,
                                    ci,
                                    maxIter
                            );
                }
            }

            long endSec = System.nanoTime();

            double tSec =
                    (endSec - startSec) / 1_000_000_000.0;

            System.out.printf(
                    "[CLIENT] Tiempo secuencial: %.3f s%n",
                    tSec
            );

            // ==================================
            // 2. PROCESAMIENTO DISTRIBUIDO
            // ==================================

            System.out.println(
                    "\n[2/3] Iniciando procesamiento distribuido..."
            );

            String jobId =
                    "job-" + UUID.randomUUID();

            RenderConfig config =
                    new RenderConfig(
                            jobId,
                            width,
                            height,
                            xMin,
                            xMax,
                            yMin,
                            yMax,
                            maxIter,
                            strategyType,
                            strategyParam
                    );

            long startDist = System.nanoTime();

            int[][] distMatrix;

            try {

                distMatrix =
                        master.renderFractal(config);

            } catch (FractalException e) {

                System.err.println(
                        "[ERROR] El Master reportó: "
                                + e.reason
                );

                return;
            }

            long endDist = System.nanoTime();

            double tDist =
                    (endDist - startDist)
                            / 1_000_000_000.0;

            System.out.printf(
                    "[CLIENT] Tiempo distribuido: %.3f s%n",
                    tDist
            );

            // ==================================
            // 3. VALIDAR RESULTADOS
            // ==================================

            System.out.println(
                    "\n[3/3] Verificando resultados..."
            );

            if (distMatrix == null ||
                    distMatrix.length != height) {

                System.err.println(
                        "[ERROR] La matriz distribuida es inválida."
                );

                return;
            }

            long errorCount = 0;

            for (int y = 0; y < height; y++) {

                if (distMatrix[y] == null ||
                        distMatrix[y].length != width) {

                    System.err.println(
                            "[ERROR] Dimensiones incorrectas en fila "
                                    + y
                    );

                    return;
                }

                for (int x = 0; x < width; x++) {

                    if (localMatrix[y][x]
                            != distMatrix[y][x]) {

                        errorCount++;
                    }
                }
            }

            if (errorCount == 0) {

                System.out.println(
                        "[OK] Resultados idénticos al monolito."
                );

            } else {

                System.err.println(
                        "[ERROR] Se encontraron "
                                + errorCount
                                + " píxeles diferentes."
                );
            }

            // ==================================
            // 4. SPEEDUP Y EFICIENCIA
            // ==================================

            double speedup =
                    tDist > 0 ? tSec / tDist : 0.0;

            double efficiency =
                    (speedup / workerCount) * 100.0;

            System.out.println(
                    "\n========================================"
            );

            System.out.println(
                    "          RESULTADOS FINALES"
            );

            System.out.println(
                    "========================================"
            );

            System.out.printf(
                    "Tiempo secuencial:   %.3f s%n",
                    tSec
            );

            System.out.printf(
                    "Tiempo distribuido: %.3f s%n",
                    tDist
            );

            System.out.printf(
                    "Speedup:            %.3fx%n",
                    speedup
            );

            System.out.printf(
                    "Eficiencia:         %.2f%%%n",
                    efficiency
            );

            System.out.printf(
                    "Errores de píxeles: %d%n",
                    errorCount
            );

            System.out.println(
                    "Workers utilizados (registrados): "
                            + workerCount
            );

            System.out.println(
                    "========================================"
            );

            // ==================================
            // 5. GUARDAR IMAGEN
            // ==================================

            try {

                MandelbrotCore.saveImage(
                        distMatrix,
                        width,
                        height,
                        maxIter,
                        outputPath
                );

                System.out.println(
                        "[CLIENT] Imagen guardada: "
                                + outputPath
                );

            } catch (IOException e) {

                System.err.println(
                        "[ERROR] No se pudo guardar la imagen: "
                                + e.getMessage()
                );
            }

        } catch (java.lang.Exception e) {

            System.err.println(
                    "[ERROR] Falló el cliente: "
                            + e.getMessage()
            );

            e.printStackTrace();
        }
    }
}
