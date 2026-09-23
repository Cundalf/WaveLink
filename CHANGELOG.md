# Changelog — WaveLink

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).
Versionado [SemVer](https://semver.org/lang/es/).

## [0.3.0] — 2026-09-22

### Agregado
- **Now playing en Android Auto**: tema, artista y portada en el panel del auto, la notificación de medios y la pantalla de bloqueo (antes solo el nombre de la radio).
- **Now playing en el cast**: la TV muestra tema, artista y portada en vivo. El teléfono los empuja por un canal custom de Cast con la portada embebida, así que funciona sin CORS ni HTTPS en la API de la estación.
- **Botón "Sincronizar"** en el mini player, el reproductor expandido, Android Auto y los controles de medios del sistema: reconecta el stream para volver al vivo tras pausas o microcortes. Al castear, también recarga el stream en el TV.

### Corregido
- **Receiver de Cast: el "ahora suena" nunca funcionaba** — `DefaultMediaItemConverter` manda el `mediaId` en `contentId` y el stream en `contentUrl`; el receiver derivaba la API del `contentId` y fallaba. Ahora usa `contentUrl` (con `contentId` como fallback).
- **Nombre de la estación en el TV** ya no se pisa con el título del tema (se lee de `subtitle`).
- **Doble polling de now-playing unificado**: app y servicio comparten una única consulta a la API de la estación (antes eran 2 cada 5 s; en pausa ahora cada 30 s).
- El receiver de Cast muestra "En vivo" en vez de dejar "Sintonizando…" fijo y vuelve a su polling propio si el teléfono se desconecta.
- El stream se declara como transmisión en vivo ante Cast (`streamType=LIVE`): los receivers por defecto no muestran barra de progreso ni seek.

### Interno
- Canal custom `urn:x-cast:com.cundalf.wavelink` declarado en `customNamespaces` del receiver.
- Botón de Android Auto con `setMediaButtonPreferences` (reemplaza al deprecado `setCustomLayout`) e ícono vectorial monocromo de 24 dp.
- La metadata en vivo se actualiza con `replaceMediaItem` sin cortar el audio (ExoPlayer reutiliza el media source cuando solo cambia la metadata).
- 61 tests unitarios y de render (Robolectric/Compose).

### English

#### Added
- **Now playing in Android Auto**: track, artist and artwork in the car panel, media notification and lock screen (previously only the station name).
- **Now playing on Cast**: the TV shows track, artist and artwork live. The phone pushes them over a custom Cast channel with the artwork embedded, so it works without CORS or HTTPS on the station API.
- **"Sync" button** in the mini player, expanded player, Android Auto and system media controls: reconnects the stream to get back to the live edge after pauses or micro-cuts. While casting it also reloads the stream on the TV.

#### Fixed
- **Cast receiver: now playing never worked** — `DefaultMediaItemConverter` puts the `mediaId` in `contentId` and the stream in `contentUrl`; the receiver derived the API base from `contentId` and always failed. It now uses `contentUrl` (falling back to `contentId`).
- **Station name on the TV** is no longer overwritten by the track title (read from `subtitle`).
- **Duplicate now-playing polling unified**: app and service share a single request stream to the station API (was 2 every 5 s; 30 s while paused).
- The Cast receiver shows "Live" instead of a stuck "Tuning in…" and falls back to its own polling when the phone disconnects.
- The stream is declared as a live broadcast to Cast (`streamType=LIVE`): default receivers no longer show a seek bar.

#### Internal
- Custom channel `urn:x-cast:com.cundalf.wavelink` declared in the receiver's `customNamespaces`.
- Android Auto button via `setMediaButtonPreferences` (replaces the deprecated `setCustomLayout`) with a 24 dp monochrome vector icon.
- Live metadata updated with `replaceMediaItem` without interrupting audio (ExoPlayer reuses the media source when only metadata changes).
- 61 unit and render tests (Robolectric/Compose).

## [0.2.0] — 2026-09-13

### Agregado
- **Información en vivo configurable por radio**: switch "Información en vivo" en el editor. Si la estación publica la API de SUB/WAVE se puede elegir mostrarla u ocultarla sin cortar el audio.
- **Detección de capacidades al probar la conexión**: la app informa si la estación publica metadata (`/api/now-playing`) y si admite pedidos (`/api/request`).
- **Pedidos a la cabina**: hoja "Pedir a la cabina" en el reproductor (texto de hasta 280 caracteres + nombre opcional), envío con `POST /api/request` y seguimiento del resultado (pendiente / confirmado / cerrado / error). Se habilita por radio con el switch "Pedidos a la cabina".

### Corregido
- **Pantalla negra al castear a Google TV / Chromecast**: el `MediaItem` no declaraba MIME ni tipo, y el converter de Media3 lo interpretaba como video (`MEDIA_TYPE_MOVIE`). Ahora se declara `audio/mpeg` + `MEDIA_TYPE_RADIO_STATION`, con lo que el receiver renderiza la UI de audio correctamente.
- **Android Auto con spinner infinito**: el árbol de biblioteca no cumplía el contrato de Media3 (la raíz no era `isBrowsable`, no había `onGetItem` y se ignoraba la paginación). Ahora la raíz es navegable, las hojas reproducibles, y `onGetChildren` respeta `page`/`pageSize`.
- **Loop infinito de "Conectando…/Reconectando…"** cuando la radio no está disponible: el contador de reintentos se reseteaba en `BUFFERING`. Ahora solo se resetea al conectar (`STATE_READY`) o al cambiar de radio; tras 3 reintentos muestra el error final y queda listo para reintentar a mano.
- **"Probar conexión" no validaba la contraseña**: ahora hace una comprobación real contra el stream autenticado (`?auth=`), distingue 401/403, y ya no se cuelga con streams que no exponen la API.
- **Bienvenida con contraste incorrecto**: el título salía negro sobre fondo OLED; ahora usa el color `onBackground`.
- Recuperación de reproducción: un `play` con la radio precargada en `IDLE` (botón de Android Auto o controles de medios) ahora dispara `prepare()`.

### Cambiado
- El botón **"Probar"** quedó junto al campo **URL del stream** (antes estaba debajo de todo el formulario).
- El skin del receiver de Cast muestra el estado de espera desde el arranque, evitando una pantalla vacía si no llega estado de medios.

### Interno
- API de SUB/WAVE dividida en transporte HTTP inyectable (`HttpTransport`), metadata (`SubWaveApi`) y pedidos (`SubWaveRequests`).
- R8/minify y gates en verde: `build`, `ktlintCheck`, `detekt`, `lintDebug`.
- 49 tests unitarios y de render (Robolectric/Compose).

## [0.1.1] — versionCode 3

### Agregado
- "Ahora suena" con título, artista, portada, DJ, oyentes y "a continuación" desde la API pública de SUB/WAVE.
- Pantalla de bienvenida de primer arranque.
- Skin WaveLink para el receiver de Google Cast.
- Mensajes de error diferenciados (401 vs. red) y reintentos acotados.

## [0.1.0] — versionCode 1

### Agregado
- Versión inicial: alta/edición/borrado de radios, contraseña de estación (`?auth=`), reproducción local, Cast y Android Auto.
- Lista de radios por defecto, multiidioma (es/en) y persistencia local con DataStore.

---

## Novedades para Play Store 0.3.0 (máx. 500 caracteres)

**Español:**
```
• "Ahora suena" también en Android Auto: tema, artista y tapa.
• Lo mismo en el cast: la TV muestra tema y tapa en vivo.
• Botón "Sincronizar": volvé al vivo tras una pausa o un microcorte.
• Arreglado: en el TV nunca se veía qué estaba sonando.
• Una sola consulta a la API de la estación (menos tráfico).
```

**English:**
```
• Now playing in Android Auto too: track, artist and artwork.
• Same on Cast: the TV shows track and cover live.
• "Sync" button: get back to live after a pause or a glitch.
• Fixed: the TV never showed what was playing.
• Single poll to the station API (less traffic).
```

## Artefactos 0.3.0

| Archivo | Uso |
|---|---|
| `WaveLink-0.3.0.apk` | Instalación directa / testing (firmado con la release key) |
| `WaveLink-0.3.0.aab` | Subida a Play Console (firmado con la upload key) |

## Novedades para Play Store 0.2.0 (máx. 500 caracteres)

**Español:**
```
• Elegí por radio si mostrás la información en vivo (título, tapa, DJ).
• Pedí temas a la cabina desde la app, si la emisora lo permite.
• Casteo arreglado: se acabó la pantalla negra en Google TV/Chromecast.
• Android Auto arreglado: ya no queda el spinner de carga infinito.
• Ya no se queda "reconectando" sin fin si la radio está fuera de línea.
• "Probar conexión" ahora valida la contraseña y está junto a la URL.
```

**English:**
```
• Per-radio switch for live info (title, artwork, DJ).
• Request songs to the booth from the app when the station allows it.
• Casting fixed: no more black screen on Google TV/Chromecast.
• Android Auto fixed: no more endless loading spinner.
• No more infinite reconnecting when a station is offline.
• "Test connection" now checks the password and sits next to the URL.
```

## Artefactos 0.2.0

| Archivo | Uso |
|---|---|
| `WaveLink-0.2.0.apk` | Instalación directa / testing (firmado con la release key) |
| `WaveLink-0.2.0.aab` | Subida a Play Console (firmado con la upload key) |

Receiver ID de Cast incluido en el build: `23C9910B`.
