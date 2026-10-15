---
name: wavelink-android-auto
description: Reglas expertas de Android Auto + Media3 específicas de WaveLink (SubWave). Use SIEMPRE que se toque PlaybackService, LibraryTree, RadioMedia, el browse tree, la sesión de medios, Cast, AndroidManifest (automotive_app_desc), o cuando se depure reproducción/contenido en Android Auto (DHU, auto real) o rechazos de Google Play por Auto. Palabras clave: onConnect, MediaLibraryService, onGetChildren, notifyChildrenChanged, MA-1 autoplay, PROJECTION_NOT_STARTED, onPlayFromSearch, onPlaybackResumption.
---

# WaveLink en Android Auto (Media3 + Google Play)

Basado en la documentación oficial (developer.android.com/training/cars/media, androidx.media3)
y en el incidente real de rechazo de Play de la versión 0.3.0 (versionCode 5).
Causas: comando deshabilitados por `onConnect` mal implementado (regresión 0.3.0), raíz del
browse tree con ítems reproducibles (no BROWSEABLE), y ítems sin URI pasados a ExoPlayer.

## Arquitectura que NO se debe romper

- `PlaybackService : MediaLibraryService` (`playback/PlaybackService.kt`) es el único dueño del
  player y de la sesión. La UI usa `PlaybackClient` (MediaController).
- `LibraryTree` (`playback/LibraryTree.kt`) construye el árbol: **raíz** → carpeta `stations`
  → radios. Los IDs de las radios son estables (`radio:<id>`).
- `automotive_app_desc.xml` se declara como meta-data `com.google.android.gms.car.application`
  en el manifest. El servicio declara las DOS acciones:
  `androidx.media3.session.MediaSessionService` y `android.media.browse.MediaBrowserService`.

## Reglas duras (violarlas = rechazo en Play o app rota en el auto)

1. **`onConnect`**: implementarlo con `AcceptedResultBuilder(session, controller).build()`
   (defaults correctos por controlador). NUNCA envolver el resultado de `super.onConnect` sin
   propagar el flag de "no implementado": si se crea un `ConnectionResult` a mano con
   `SessionCommands.EMPTY`/`Player.Commands.EMPTY`, el controlador no puede llamar NADA
   (síntoma en logs: `Controller isn't allowed to call command= 31/2/1` con 31=SET_MEDIA_ITEM,
   2=PREPARE, 1=PLAY_PAUSE, y el play del auto no hace nada). Incluir
   `SESSION_COMMAND_RESYNC` para que el auto pueda resincronizar estado al reconectar.

2. **Ningún ítem sin URI puede llegar al player.** Todos los caminos que agregan medios
   (`COMMAND_SET_MEDIA_ITEM`, `onAddMediaItems`, `onSetMediaItems`, `onPlayFromSearch`,
   `onPlaybackResumption`) deben resolver el `mediaId` a través de `LibraryTree.resolve()`:
   si no resuelve, devolver lista vacía/`SessionError` (nunca un `MediaItem` sin `LocalConfiguration`).
   Un ítem sin URI produce `checkNotNull` en `DefaultMediaSourceFactory` → la radio no suena
   (el error queda atrapado en un listener de Guava como SEVERE) y en reviews se ve como "crash"/"no carga".

3. **Raíz del browse tree: solo hijos BROWSEABLE (carpetas).** Android Auto usa la raíz para
   elegir tabs; si los hijos directos de la raíz son reproducibles, la pantalla queda vacía o
   "no puede cargar contenido". La raíz expone la carpeta `stations` (RadioBrowser/
   `browsableRootId`), y las radios van un nivel adentro. Verificar además que el hint
   `KEY_ROOT_CHILDREN_SUPPORTED_FLAGS` incluya `FLAG_BROWSEABLE` (Media3 lo maneja solo si la
   raíz está bien armada).

4. **Carga inicial**: el repositorio es asíncrono (DataStore). Esperar la carga
   (`whenLoaded`/`awaitLoaded` con timeout) ANTES de responder `onGetChildren` de la raíz,
   y llamar `notifyChildrenChanged(stations, null)` cuando cambian las radios. Sin esto el auto
   abre en frío y ve una lista vacía para siempre.

5. **MA-1 (prohibido autoplay)**: al conectar NO se reproduce nada. Para que el botón "Play"
   genérico del auto funcione, precargar la radio por defecto en la cola SIN reproducir
   (`setMediaItems(..., playWhenReady=false)` o reaccionar en `onPlay` si la cola está vacía).
   El estado inicial del PlaybackState debe ser `STATE_NONE`/`STATE_PAUSED`, nunca `PLAYING`.

6. **Metadata segura**: título/artista visibles en el auto. NUNCA meter URLs con credenciales
   (`?auth=...`) en título/URI visible. El artwork debe ser chico (≤512px; acá 256px) para no
   saturar el canal del auto.

7. **Búsqueda por voz** (`onPlayFromSearch`): resolver por nombre (exacto, luego parcial), y si
   no hay match devolver error/vacío. No confiar en el `mediaId` que manda el auto.

8. **Cast**: `CastBridge` cambia el player de la sesión. Al desconectar, restaurar el player
   local y el estado previo; los retries de reconexión deben cancelar el job anterior.

## Cómo verificar (en este proyecto)

```bash
export JAVA_HOME=~/local/jdk-21* ANDROID_HOME=$HOME/Android/Sdk
./gradlew test :app:lintDebug detekt ktlintCheck   # gates mínimos
./gradlew :app:installDebug                        # instalar en el teléfono
# DHU (requiere servidor de unidad principal en AA del teléfono + modo Developer):
adb forward tcp:5277 tcp:5277
$ANDROID_HOME/extras/google/auto/desktop-head-unit --usb RZGYC1EMSTH   # o sin --usb (túnel TCP)
```

- Logs útiles en el teléfono: `adb logcat | grep -iE 'gearhead|GH\.|CAR\.SERVICE|ConnLogger|FDC'`.
- Logs de qué comandos puede el auto: `adb logcat | grep -i MCImplBase` (y `MediaSessionImpl`).
- Comando rsync/estado: `adb shell dumpsys media_session` (buscar `com.cundalf.wavelink`).

### Diagnóstico rápido

| Síntoma en logs | Causa | Fix |
|---|---|---|
| `Controller isn't allowed to call command= 31/2/1` | `onConnect` devuelve comandos vacíos | AcceptedResultBuilder con defaults |
| `PROJECTION_NOT_STARTED`, `FRAMER_READ_END_OF_STREAM_NO_DATA` | Problema de entorno AA/DHU (no de la app) | Revisar servidor de unidad principal + modo Developer |
| Pantalla "no puede cargar contenido" en el auto | Raíz sin hijos BROWSEABLE o lista vacía en frío | Regla 3 y 4 |
| La radio no suena y no hay crash | Ítem sin URI (NPE tragada por Guava) | Regla 2 |
| Play del auto no hace nada | Cola vacía sin `onPlay`/precarga | Regla 5 |

## Fuentes

- https://developer.android.com/training/cars/media (calidad MA-1..MA-6)
- https://developer.android.com/training/cars/testing (DHU)
- https://developer.android.com/media/media3/session/background-playback
- https://developer.android.com/reference/androidx/media3/session/MediaLibraryService
