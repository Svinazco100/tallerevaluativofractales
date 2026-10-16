
# Taller Evaluativo: Fractales Master-Worker
# INTEGRANTES

-Santiago Vinazco Ortegon

-Carlos Pasinga

## 1. Resultados individuales: ejecución monolítica

### Integrante 1: Santiago Vinazco Ortegón
Código: A00410365

| Prueba | Resolución | Iteraciones | Región | Tiempo (s) |
|---|---|---|---|---|
| 1 | | | | |
| 2 | | | | |
| 3 | | | | |
| 4 | | | | |
| 5 | | | | |

**Evidencias de ejecución:**



### Integrante 2: Carlos Pasinga

| Prueba | Resolución | Iteraciones | Región | Tiempo (s) |
|---|---|---|---|---|
| 1 | | | | |
| 2 | | | | |
| 3 | | | | |
| 4 | | | | |
| 5 | | | | |

**Evidencias de ejecución:**





---

## 2. Resultados de procesamiento distribuido

Los Workers deben ejecutarse en equipos diferentes.

Se deben probar las configuraciones utilizadas en la fase monolítica.

| Configuración | Tiempo monolítico (s) | Tiempo distribuido (s) | Speedup |
|---|---|---|---|
| 1 | | | |
| 2 | | | |
| 3 | | | |
| 4 | | | |
| 5 | | | |



## 3. Experimentos

### Experimento 1: Estrategia de partición

Comparar franjas fijas y baldosas con 2 Workers.

Configuración indicada: 4K, 5000 iteraciones, región seahorse.

| Estrategia | Configuración | Tiempo (s) | Speedup |
|---|---|---|---|
| FixedCount | 2 franjas | | |
| FixedCount | 4 franjas | | |
| FixedCount | 16 franjas | | |
| FixedTileSize | 32x32 | | |
| FixedTileSize | 64x64 | | |
| FixedTileSize | 128x128 | | |
| FixedTileSize | 256x256 | | |

### Experimento 2: Cantidad de hilos

| Hilos | Tiempo (s) | Speedup |
|---|---|---|
| 1 | | |
| 2 | | |
| Número de núcleos físicos | | |
| 64 | | |

### Experimento 3: Escalabilidad de Workers

| Workers | Tiempo (s) | Speedup |
|---|---|---|
| 1 | | |
| 2 | | |
| 4 | | |

---

## 4. Gráficas de Speedup

### 4.1 Speedup vs. tamaño de baldosa



### 4.2 Speedup vs. cantidad de hilos



---

## 5. Cuestionario de análisis conceptual

### Pregunta 1

¿Por qué la región classic se procesa rápidamente y seahorse requiere más tiempo?

La diferencia en los tiempos de procesamiento se debe principalmente a la complejidad de los puntos que componen cada región del conjunto de Mandelbrot.

En la región classic, muchos puntos se encuentran alejados de las zonas más complejas del fractal, por lo que es posible determinar rápidamente si pertenecen o no al conjunto. Esto reduce la cantidad de iteraciones necesarias para calcular sus píxeles.

Por otro lado, la región seahorse contiene estructuras mucho más detalladas, especialmente cerca de los límites del conjunto de Mandelbrot. En estas zonas, numerosos puntos requieren una mayor cantidad de iteraciones antes de determinar su comportamiento.

Por esta razón, bajo condiciones equivalentes de resolución e iteraciones máximas, la región seahorse puede necesitar considerablemente más tiempo de procesamiento que classic.

### Pregunta 2

¿Por qué las baldosas de 32x32 generan mayor sobrecosto de comunicación que las de 64x64?

Las baldosas de 32x32 contienen menos píxeles que las de 64x64. Por lo tanto, para procesar una imagen completa se necesita generar una mayor cantidad de tareas.

Por ejemplo, una baldosa de 32x32 contiene 1024 píxeles, mientras que una de 64x64 contiene 4096. Esto significa que, para una misma imagen con dimensiones divisibles por ambos tamaños, se necesitan cuatro veces más tareas utilizando baldosas de 32x32.

Cada tarea requiere que el Worker solicite trabajo, reciba la información necesaria, realice el procesamiento y envíe los resultados al Master.

Al aumentar la cantidad de tareas, también aumenta el número de operaciones de coordinación y comunicación entre los componentes del sistema.

Aunque las baldosas pequeñas pueden mejorar la distribución de la carga, su uso también puede generar un mayor sobrecosto de comunicación. Por eso es importante encontrar un tamaño de baldosa que mantenga un equilibrio entre el procesamiento y la comunicación.

### Pregunta 3

¿Por qué las franjas fijas producen desbalance de carga y cómo lo resuelve Bag of Tasks?

Las franjas fijas pueden producir desbalance de carga porque no todas las regiones de un fractal requieren la misma cantidad de operaciones.

Algunas zonas contienen puntos que pueden calcularse rápidamente, mientras que otras tienen estructuras complejas que requieren un número mayor de iteraciones.

Cuando la imagen se divide en franjas grandes, un Worker puede recibir una sección sencilla y terminar antes que otro Worker encargado de una zona más compleja.

Esto provoca que algunos Workers permanezcan inactivos mientras otros continúan procesando.

La estrategia Bag of Tasks ayuda a resolver este problema mediante la distribución dinámica del trabajo. En lugar de asignar permanentemente una parte de la imagen a cada Worker, las tareas se organizan en una cola compartida.

Cada Worker solicita una nueva tarea cuando termina la anterior. De esta manera, los equipos que procesan más rápido pueden realizar más tareas, mejorando el aprovechamiento de los recursos y reduciendo los tiempos de inactividad.

### Pregunta 4

¿Qué limitaciones presentan double, BigDecimal y la memoria RAM al escalar a resoluciones 16K o 32K?

Al trabajar con resoluciones muy altas, aparecen limitaciones relacionadas con la precisión numérica, el tiempo de procesamiento y el consumo de memoria.

El tipo de dato double permite realizar operaciones matemáticas de manera rápida, pero utiliza una precisión limitada. Esto puede generar dificultades cuando se realizan ampliaciones muy profundas del fractal, debido a que las diferencias entre coordenadas pueden ser demasiado pequeñas para representarse correctamente.

Por otra parte, BigDecimal permite trabajar con una precisión mayor, lo cual resulta útil para representar coordenadas con más detalle. Sin embargo, sus operaciones matemáticas requieren más procesamiento, lo que puede aumentar considerablemente el tiempo necesario para generar una imagen.

En cuanto a la memoria RAM, una resolución de 16K o 32K implica procesar y almacenar una cantidad muy elevada de píxeles.

Por ejemplo, una imagen de 16384x16384 píxeles necesita aproximadamente 1 GiB únicamente para almacenar cuatro bytes por píxel. Una imagen de 32768x32768 necesita aproximadamente 4 GiB bajo las mismas condiciones.

Además, el programa puede necesitar memoria adicional para tareas, resultados intermedios y otros objetos.

Por lo tanto, al aumentar la resolución es importante controlar el uso de memoria, seleccionar adecuadamente los tipos de datos y evitar almacenar información innecesaria.

### Pregunta 5

¿Qué sucede cuando se utilizan 64 hilos y cómo se aplica la ley de Amdahl a los resultados?

Utilizar una mayor cantidad de hilos permite ejecutar varias tareas de manera concurrente, pero esto no significa que el rendimiento vaya a mejorar indefinidamente.

Cuando se utilizan 64 hilos en un equipo que dispone de menos núcleos de procesamiento, los hilos deben competir por los recursos del procesador.

Esto puede provocar cambios de contexto, mayor consumo de memoria y un aumento del tiempo destinado a administrar la ejecución.

Como consecuencia, es posible llegar a un punto en el que agregar más hilos no mejore el rendimiento e incluso aumente el tiempo de procesamiento.

Este comportamiento puede explicarse mediante la ley de Amdahl, que establece que la aceleración máxima de un programa está limitada por la parte que no puede ejecutarse en paralelo.

Su fórmula es:

S(p) = 1 / ((1 - f) + f/p)

Donde:

S(p) representa la aceleración.

p representa la cantidad de unidades de procesamiento.

f representa la fracción del programa que puede paralelizarse.

En el sistema Master-Worker existen operaciones que no se benefician directamente del aumento de hilos, como ciertas actividades de coordinación, comunicación y reconstrucción de resultados.

Por esta razón, el rendimiento depende tanto de la cantidad de hilos como de la capacidad del hardware y de los costos asociados al procesamiento distribuido.

## 6. Bitácora de uso ético de inteligencia artificial

### Herramientas utilizadas

ChatGPT(OpenIA)

### Prompts clave utilizados

onsulta 1: Le pregunté cómo funcionaba la arquitectura Master-Worker y cómo se comunicaban los Workers con el Maestro.

Consulta 2: Pedí ayuda para entender cómo dividir la imagen en franjas y baldosas, y revisar si la distribución estaba bien.

Consulta 3: Consulté cómo manejar los hilos en Java y evitar problemas cuando varios Workers trabajan al mismo tiempo.

Consulta 4: Pregunté cómo calcular el speedup, la eficiencia y los tiempos de ejecución.

Consulta 5: Pedí orientación para hacer las pruebas con diferentes cantidades de Workers y tamaños de tareas.

Consulta 6: Utilicé la IA para revisar partes del código, proponer correcciones y organizar el informe.

Conclusión: La IA me sirvió para entender mejor el funcionamiento del proyecto, resolver dudas de programación y apoyar la implementación del taller.
### Justificación crítica del código incorporado

Utilicé ChatGPT para ayudarme con algunas partes del código, principalmente en la distribución de tareas, el manejo de los Workers y la medición de tiempos.

Incorporé estas mejoras para evitar errores al procesar la imagen y facilitar las pruebas de rendimiento. También agregué código para calcular el speedup, la eficiencia y comparar los resultados.

Para verificarlo, se hicieron pruebas locales de las particiones y se compararon los píxeles con el cálculo secuencial, obteniendo cero diferencias. Queda pendiente comprobar el funcionamiento completo con ZeroC Ice y realizar las pruebas de rendimiento en 4K.
