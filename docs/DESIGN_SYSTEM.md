# **WaveLink: Especificación Técnica del Sistema de Diseño (UI/UX)**

Documentación técnica de los componentes visuales, patrones de interacción y directrices de experiencia de usuario para el desarrollo nativo de WaveLink (Android, Android Auto, Google TV). El diseño asume la adopción de los lineamientos de Material Design vigentes en 2026, priorizando interfaces Edge-to-Edge y optimización para paneles OLED.

## **1\. Paleta de Colores (OLED Dark-First)**

El uso de un tema oscuro absoluto reduce el consumo energético en pantallas OLED y minimiza la fatiga visual en entornos automotrices (Android Auto) y de baja luminosidad (Google TV).

| Token de Color | Valor Hexadecimal | Aplicación Técnica   |
| :---- | :---- | :---- |
| Fondo Principal | \#0B0C10 | Superficie base de la aplicación (activa apagado de píxeles en OLED). |
| Superficie Elevada | \#1F2833 | Tarjetas de emisoras, diálogos modales y Bottom Sheet del reproductor. |
| Acento Primario | \#66FCF1 | Indicadores de estado activo (Play), progreso del buffer, y estado de Casting conectado. |
| Texto Primario | \#FFFFFF | Títulos de radios y metadatos críticos de la sesión de audio. |
| Texto Secundario | \#C5C6C7 | Frecuencias, descripciones de Subwave, latencia (ping) y etiquetas secundarias. |

## **2\. Tipografía y Sistema de Cuadrícula**

El sistema requiere el uso de fuentes variables para escalar dinámicamente según la densidad de píxeles (DPI) del dispositivo de destino.

> * **Familia Tipográfica:** Roboto Flex o Inter.  
> * **Escala de Texto:**  
  * Display / Títulos (Reproductor maximizado): 32sp, Bold.  
  * Títulos de Listas (Nombres de emisoras): 20sp, Semi-bold.  
  * Cuerpo (Metadatos de la pista en curso): 16sp, Regular.  
  * Metadatos Técnicos (Bitrate, Codec): 12sp, Monospace (Roboto Mono).  
> * **Sistema de Cuadrícula (Grid):**  
  * Incrementos base de 8dp.  
  * Márgenes laterales en Mobile: 16dp.  
  * Márgenes laterales en Tablet/Foldables: 24dp con diseño de paneles (List-Detail).  
  * Márgenes en TV: 56dp (Overscan safe zone).

## **3\. Arquitectura UX por Plataforma**

### **3.1. Android OS (Móvil y Foldables)**

La interacción principal debe permitir la navegación del catálogo sin interrumpir el control de la transmisión.

> * **Reproductor Persistente:** Implementación de un Bottom Sheet que reside sobre la barra de navegación del sistema. Al expandirse, debe ocupar la pantalla completa con animaciones guiadas por el gesto de retroceso predictivo (Predictive Back Gesture).  
> * **Gestión de Errores de Red:** Dada la naturaleza de las transmisiones SUB/WAVE, se debe incluir un indicador no intrusivo de "Reconectando..." en lugar de interrumpir la UI con diálogos modales cuando se pierde el buffer.

### **3.2. Android Auto**

El diseño está estrictamente condicionado por las directrices de distracción del conductor (Driver Distraction Guidelines).

> * **Áreas de Impacto Táctil:** Los controles de reproducción principales no deben ser menores a 64x64dp.  
> * **Jerarquía de Navegación:** Restringida a un nivel de profundidad. La vista principal debe presentar las emisoras "Favoritas" o "Recientes". Para explorar todo el directorio, el vehículo debe estar detenido.  
> * **Control por Voz:** Requiere implementación completa de MediaSessionCompat para procesar Intents de búsqueda y reproducción de Google Assistant.

### **3.3. Google TV (Android TV OS)**

Optimizado para la navegación a distancia mediante control remoto direccional (D-Pad).

> * **Estados de Foco (Focus States):** Crítico para la usabilidad en TV. Cualquier elemento interactivo debe incrementar su escala en un 5% al recibir foco y mostrar un borde sólido de 2dp con el Acento Primario (\#66FCF1).  
> * **Prevención de Burn-in:** Si la aplicación se mantiene en el reproductor a pantalla completa sin interacción durante 5 minutos, la interfaz debe rotar lenta y aleatoriamente las coordenadas de los elementos en pantalla para proteger los paneles OLED.

## **4\. Comportamiento del Casting (Google Cast SDK)**

La transición entre la reproducción local y el dispositivo receptor debe ser continua.

> * El botón del Cast Media Route debe estar visible persistentemente en la esquina superior derecha del Top App Bar.  
> * Al conectar exitosamente, el botón adopta el color Acento Primario, el reproductor local detiene la decodificación de audio, y la interfaz móvil cambia instantáneamente a un estado de "Control Remoto", mostrando latencia y controles de volumen del dispositivo de destino.