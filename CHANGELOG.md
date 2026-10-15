# Changelog — WaveLink

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).
Versionado [SemVer](https://semver.org/lang/es/).

## [0.3.1] — versionCode 6 — 2026-10-15

### Corregido
- **El teléfono no reproducía nada (regresión de 0.3.0).** El `onConnect` del servicio otorgaba comandos vacíos al controlador, así que "Play" (lista, mini reproductor, notificación, controles del sistema) no hacía nada. Ahora cada controlador recibe sus comandos por defecto más el de "Sincronizar". Además, si Android Auto llega como controlador no confiable (dispositivos donde gearhead no es privilegiado) recibe igualmente los comandos completos, y "Sincronizar" ya no se expone a controladores ajenos no confiables.
- **Android Auto: sin contenido.** Las radios colgaban directamente de la raíz y Auto solo acepta carpetas navegables en el primer nivel. Ahora la raíz contiene la carpeta "Radios" y dentro están las radios.
- **Android Auto: lista vacía al arrancar.** Auto ya no recibe la biblioteca antes de que DataStore lea las radios (la carga se espera con timeout y se notifica al completarse), y se actualiza sola al agregar, editar o borrar una radio.
- **Android Auto: búsqueda en la biblioteca.** Ahora responde a la búsqueda del auto (`onSearch`/`onGetSearchResult`), con resultados paginados contra las radios guardadas.
- **"Reproducir" sin selección** en Android Auto y en los controles del sistema reproduce la radio por defecto, sin autoplay al conectar (MA-1).
- **Android Auto: búsqueda por voz y radios sin resolver.** Una búsqueda ("reproducí X en WaveLink") o un `mediaId` que todavía no estaba cargado llegaba a ExoPlayer sin URI: la radio no sonaba (`NullPointerException`). Ahora cada pedido se resuelve contra las radios (nombre exacto, luego parcial, o la radio por defecto) o se descarta. La misma resolución se usa para el intent del asistente en el teléfono, esperando el arranque en frío (antes se perdía por una carrera).
- **Radios HLS/DASH/AAC/OGG/Opus.** El MIME estaba fijo en `audio/mpeg` y las estaciones con playlist (`.m3u8`, `.mpd`…) no sonaban ni en teléfono ni en Cast. Ahora el MIME se deriva de la URL del stream.
- **Portada del "ahora suena" en teléfono, auto y notificación.** El ícono embebido tapaba la portada en vivo (Media3 prioriza `artworkData` sobre `artworkUri`); ahora la portada reemplaza al ícono y, sin portada, se conserva el ícono.
- **La notificación de medios ahora abre la app** al tocarla (`setSessionActivity`).
- **Cast:** al reanudar con una sesión activa ya no arranca la reproducción solo; al desconectar el cast sigue sonando la radio que estaba en el TV (antes volvía la anterior); y al recrear el servicio con un cast activo se detecta la sesión (evita doble audio).
- **Archivo de radios corrupto:** se recupera automáticamente (antes el flujo de datos moría en silencio hasta reiniciar). Las radios guardadas sin id o sin URL se descartan al leer.
- **UI/rendimiento:** animaciones sin recomponer por frame; las portadas se decodifican al tamaño mostrado (≤512 px) con caché por bytes (evita OOM); listas a pantalla completa (contentPadding edge-to-edge); el teclado ya no tapa los formularios (hojas con insets correctos); sin flash de "todavía no tenés radios" antes de cargar.
- **Accesibilidad:** selector de idioma con foco único y rol correcto; botón principal de reproducción con rol de botón; aviso visible cuando la URL es `http://` ("conexión sin cifrado"); validación de host al guardar; la URL se muestra sin credenciales en la tarjeta.
- **Permiso de notificaciones:** se explica el motivo antes de volver a pedirlo y, si quedó bloqueado ("no volver a preguntar"), se ofrece abrir los ajustes del sistema.
- **Seguridad de dependencias:** DataStore 1.1.1 → 1.2.1 (CVE-2024-7254, DoS al parsear datos corruptos) y Media3 1.11.0 → 1.11.1.

### Interno
- Tests: 61 → **101** (regresión de servicio: comandos de `onConnect`, browse tree en frío, MA-1, resolución de `mediaId`; repositorio con DataStore real y corrupción; búsqueda; MIME por extensión; presupuesto del cover de Cast).
- Miniatura embebida de 256 px (antes 512 px): menos memoria por ítem al listar en el auto.
- Manifest: el servicio declara también la acción `androidx.media3.session.MediaLibraryService`, `adjustResize` para el teclado y sin scrim de contraste en la barra de navegación (OLED).
- Log de diagnóstico por conexión (`pkg`, `trusted`, `auto`) para depurar Auto en campo.
- Toolchain al día: **AGP 9.4.1 + Gradle 9.8.1 + Kotlin 2.4.21** (built-in Kotlin; corrige CVE-2026-53914 del toolchain), compileSdk 37; Compose BOM 2026.09.00, Cast 22.3.1, DataStore 1.2.1, Robolectric 4.17, ktlint 14, OWASP dependency-check 10 (detekt queda en 1.23.8: la línea 2.x sigue en pre-release).
- CI: acciones de GitHub fijadas por SHA (hardening de supply chain).

### English

#### Fixed
- **The phone played nothing (0.3.0 regression).** The service's `onConnect` granted empty commands to controllers, so "Play" did nothing. Each controller now gets its default commands plus "Sync". Android Auto controllers that are not trusted (devices where gearhead is unprivileged) now also get full commands, and "Sync" is no longer exposed to untrusted third-party controllers.
- **Android Auto: no content.** Radios were direct children of the root; Auto only accepts browsable folders at the first level. The root now contains the "Radios" folder.
- **Android Auto: empty list on cold start.** The library is not answered before DataStore finishes loading (waits with timeout and notifies when loaded) and refreshes on radio changes.
- **Android Auto: library search** now responds (`onSearch`/`onGetSearchResult`) with paginated results over the saved radios.
- **"Play" without selection** loads the default radio, with no autoplay on connect (MA-1).
- **Voice search / unresolved media ids** no longer reach ExoPlayer without a URI (silent `NullPointerException`): items resolve by exact/partial name or default, or are dropped. The phone assistant intent path waits for cold start too.
- **HLS/DASH/AAC/OGG/Opus radios.** MIME was hardcoded to `audio/mpeg`; playlists didn't play on phone or Cast. MIME is now derived from the stream URL.
- **Now-playing cover** on phone, car and notification: the embedded icon no longer overrides the live cover; without cover the icon is kept.
- **Media notification** now opens the app when tapped (`setSessionActivity`).
- **Cast:** no autoplay when resuming with an active session; stopping the cast keeps the station that was playing on the TV; an active session is detected when the service is recreated.
- **Corrupt radios file** self-recovers; entries without id or URL are discarded.
- **UI/performance:** animations no longer recompose per frame; covers decode to display size (≤512 px) with a byte-sized cache (OOM-safe); edge-to-edge list padding; keyboard no longer covers forms; no empty-state flash before loading.
- **Accessibility:** single-focus language selector with proper role; play button role; visible "unencrypted connection" warning for `http://` URLs; host validation; URL shown without credentials on the card.
- **Notification permission:** the app explains why before asking again and, if it is blocked ("don't ask again"), offers to open system settings.
- **Dependency security:** DataStore 1.1.1 → 1.2.1 (CVE-2024-7254) and Media3 1.11.0 → 1.11.1.

#### Internal
- Tests: 61 → **101** (service-level regressions: `onConnect` commands, cold-start browse tree, MA-1, media id resolution; repository with real DataStore and corruption; search; per-extension MIME; Cast cover budget).
- Embedded artwork 256 px; `MediaLibraryService` action in the manifest; `adjustResize`; no navigation-bar contrast scrim on OLED; per-connection diagnostic log (`pkg`, `trusted`, `auto`).
- Toolchain up to date: **AGP 9.4.1 + Gradle 9.8.1 + Kotlin 2.4.21** (built-in Kotlin; fixes toolchain CVE-2026-53914), compileSdk 37; Compose BOM 2026.09.00, Cast 22.3.1, DataStore 1.2.1, Robolectric 4.17, ktlint 14, OWASP dependency-check 10 (detekt stays at 1.23.8: the 2.x line is still pre-release).
- CI: GitHub Actions pinned by SHA (supply-chain hardening).

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
