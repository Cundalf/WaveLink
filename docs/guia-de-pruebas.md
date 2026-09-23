# GUÍA DE PRUEBAS — WaveLink (para principiantes)

Esta guía asume que nunca instalaste un APK ni usaste Android Studio. Todo
está explicado desde cero. Andá a tu ritmo, paso a paso.

---

## Parte 1: Conseguir el APK e instalarlo en tu teléfono

### 1.1 Los archivos que te dejé listos

Ya compilé la app por vos. Están en esta carpeta:

```
SUB-WAVE-Cast/                  (la carpeta se llama "SUB-WAVE-Cast",
├── SUB-WAVE-Cast.apk            en tu carpeta de usuario: /home/cunda/SUB-WAVE-Cast/)
│                                ← USÁ ESTE (chico, 3,8 MB, recomendado)
└── SUB-WAVE-Cast-debug.apk    ← no lo uses, es por si acaso
```

> "APK" es el archivo de instalación de Android, como un `.exe` de Windows.
> El de 3,8 MB ya viene firmado, así que lo podés instalar directo.

### 1.2 Pasar el APK al teléfono (elegí UNO de estos métodos)

**Opción A — WhatsApp (la más fácil):**
1. Mandate el archivo `SUB-WAVE-Cast.apk` a vos mismo por WhatsApp Web
   (Chat → "Mensaje a mi número").
2. En el teléfono, tocá el archivo recibido → **Descargar**.
3. Cuando termine, tocá el archivo → te va a pedir permiso para instalar apps
   de esa fuente → **Permitir** → **Instalar**.

**Opción B — Google Drive:**
1. En la PC: https://drive.google.com → arrastrá el archivo `SUB-WAVE-Cast.apk`.
2. En el teléfono: abrí Google Drive → tocá el archivo → **Instalar**.
3. Si pide permiso para "instalar apps desconocidas" → **Permitir**.

**Opción C — Cable USB:**
1. Conectá el teléfono a la PC con el cable de carga.
2. En el teléfono debería aparecer "Dispositivo USB conectado" → tocá y elegí
   **Transferir archivos**.
3. Copiá `SUB-WAVE-Cast.apk` desde la carpeta `SUB-WAVE-Cast` (en tu carpeta de
   usuario) a la carpeta `Descargas` del teléfono.
4. En el teléfono: abrí la app **Archivos** → `Descargas` → tocá
   `SUB-WAVE-Cast.apk` → **Instalar**.

### 1.3 Si Android te bloquea la instalación

Android pide permiso la primera vez. Cuando salga el cartel:
**Configuración → Permitir desde esta fuente → activar**. Luego volvé a tocar el APK.

---

## Parte 2: Primera prueba en el teléfono (sin complicaciones)

1. Abrí la app **WaveLink** (aparece con el ícono de radio en la pantalla).
2. Te va a pedir **permiso de notificaciones** → tocá **Permitir**
   (es para la notificación de "qué está sonando" que aparece al bloquear la pantalla).
3. Tocá el botón **"Agregar radio"** (abajo a la derecha) para agregar tu primera radio.
4. Completá:
   - **Nombre**: lo que quieras (ej: "Mi radio").
   - **URL del stream**: pegá la dirección de tu radio, ej: `https://tustation.com/stream.mp3`
     (debe empezar con `http://` o `https://`, sino la app no te deja guardar).
   - **Contraseña**: la de tu estación (si tiene). Es opcional.
5. Tocá **"Probar conexión"** para confirmar que la estación responde
   (si es una SUB/WAVE, te va a decir "Conexión OK: estación SUB/WAVE encontrada").
6. Tocá **Guardar**. La radio aparece en la lista y empieza a sonar.

### Pruebas rápidas en el teléfono

| Qué hacés | Qué debería pasar |
|---|---|
| Tocar la barra de abajo (mini reproductor) | Se abre el reproductor completo con la tapa, el track, el DJ y datos técnicos del stream. |
| Tocar play/pausa (en la barra o en el reproductor) | El ícono cambia al instante (play ↔ pausa) y dice "Reproduciendo"/"En pausa". |
| Dejar sonando un rato | Aparece el **"Ahora suena"**: título, artista, tapa, bitrate, oyentes y "A continuación" (lo trae de la API de SUB/WAVE). |
| Agregar una radio y tocar **"Probar conexión"** | Dice "Conexión OK: estación SUB/WAVE encontrada" (o el error concreto: contraseña/red). |
| **Bloquear la pantalla** | La música sigue y aparece la notificación con play/pausa. |
| Agregar una segunda radio y tocarla en la lista | Suena la segunda y su card se marca con el equalizador. |
| Girar el teléfono (rotar pantalla) | La música sigue, y si estaba pausada NO se reanuda sola. |
| Poner una contraseña equivocada | Mensaje: "Acceso denegado. Revisá la URL y la contraseña." |
| Cortar el WiFi mientras suena | Aparece "Reconectando…" discreto en ámbar y reintenta 3 veces antes de rendirse. |
| Cerrar la app deslizándola de las apps recientes | La música sigue sonando (es normal, así funciona). |

---

## Parte 3: Google TV / Chromecast (castear la radio al TV)

> **IMPORTANTE**: el botón de cast **no aparece** en el APK que te compilé,
> porque Google exige un "receiver app ID" (una clave que se registra en la
> página de Google Cast, cuesta ~USD 5 una sola vez). Sin esa clave la app
> funciona perfecto en el teléfono y en el auto, pero el botón de castear no se muestra.
>
> Cuando tengas tu clave (o si querés que te explique cómo sacarla),
> avisame y te recompilo el APK con el botón activado en 1 minuto.

### Cómo se vería la prueba cuando lo tengas

1. El teléfono y el Google TV / Chromecast en el **mismo WiFi**.
2. Reproducí una radio en el teléfono.
3. Tocá el **botón de castear** (parece un monitor con una antena).
4. Elegí tu TV de la lista → la radio suena en el TV.
5. Desconectalo → la radio vuelve a sonar en el teléfono.

> Si tu radio es `http://` (sin la "s"), castear solo funciona si el TV está
> en la misma red que el teléfono. Si es `https://` (con la "s"), funciona desde
> cualquier lado.

---

## Parte 4: Android Auto (la app en la pantalla del auto)

### 4.1 Lo más importante (una sola regla)

Cuando conectás el auto, la app **NO debe empezar a sonar sola**. Solo suena
si vos tocás play. Es un requisito obligatorio de Google, ya está así en la app.

### 4.2 Probar sin auto (opción recomendada si no tenés el auto a mano)

Necesitás el "Android Auto" instalado en el teléfono y un simulador en la PC:

1. En el teléfono: instalá la app **Android Auto** desde Play Store y abrila
   una vez (aceptá los términos).
2. En la PC: descargá el simulador "Desktop Head Unit (DHU)" desde
   https://developer.android.com/training/cars/testing/dhu
   (es un archivo `.zip`; descomprimilo y doble clic en `dhu.bat` si usás
   Windows, o `dhu` si usás Linux/Mac).
3. Conectá el teléfono a la PC con cable USB y activá **Depuración USB**
   (si no sabés qué es: Configuración → Acerca del teléfono → tocá 7 veces
   "Número de compilación" → Configuración → Opciones de desarrollador →
   activá "Depuración USB").
4. En el simulador de la PC, buscá la app entre las apps de medios.

### 4.3 Probar en el auto real

1. Conectá el teléfono al auto con un **cable USB bueno** (los cables viejos dan problemas).
2. En la pantalla del auto: Apps → sección de **Música/Audio** → buscá WaveLink.
3. Probá esto:

| Qué hacés en el auto | Qué debería pasar |
|---|---|
| Abrir la app | NO suena nada (correcto, es lo que exige Google). |
| Tocar el botón de play grande | Suena la radio que marcaste como "por defecto". |
| Abrir la lista y tocar otra radio | Suena la que tocaste. |
| Pausar / seguir | Responde al toque. |
| Desconectar el cable | La música sigue sonando en el teléfono. |

---

## Parte 5: Lista final para decir "está lista"

- [ ] Se instala y abre en el teléfono.
- [ ] Agregué mi radio con contraseña y suena.
- [ ] Suena con la pantalla bloqueada (notificación).
- [ ] Cambio entre radios.
- [ ] Aparece el mensaje correcto con contraseña mala y con red cortada.
- [ ] En el auto: aparece en la lista, no suena sola, y suena al tocar play.
- [ ] (Cuando tengas la clave de cast) Suena en el Google TV.

## Parte 6: Si algo no funciona

1. **"No se puede abrir el archivo"** → volvé a descargarlo completo por otro método (Opción A/B/C).
2. **La radio no suena** → probá primero la URL del stream en el navegador del teléfono.
   Si el navegador la reproduce, la app también debería.
3. **El botón de cast no aparece** → es normal: falta la clave de Google Cast (Parte 3).
4. Cualquier otra cosa, contame qué mensaje sale exactamente y lo miramos.