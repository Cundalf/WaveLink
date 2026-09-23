# ESPECIFICACIÓN v2 — App Android "WaveLink"

Cliente open source, minimalista y de bajo consumo para reproducir y castear radios SUB/WAVE, con soporte de Android Auto.

> **Historial de la v2.** Esta versión incorpora los hallazgos del análisis de la v1:
> 1. **Android Auto usa `MediaLibraryService` de Media3** (el `MediaBrowserService` clásico está deprecado) para exponer la lista de radios en el auto.
> 2. **Cliente de UI desacoplado vía `MediaController`**: la pantalla no toca el `Player` directamente; envía comandos a la sesión de medios. Permite que el auto, el cast y la app compartan el mismo estado.
> 3. **`?auth=` con URL-encoding** del password (caracteres especiales y espacios).
> 4. **Cast requiere HTTPS** para streams accesibles por internet (el receiver por defecto de Google solo permite HTTP para dispositivos en LAN). Se documenta como restricción operativa.
> 5. **Permisos corregidos**: `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (target 34+), `WAKE_LOCK` y `POST_NOTIFICATIONS` (runtime en Android 13+). La v1 los omitía.
> 6. **Manejo de errores**: distingue `401` (contraseña/URL incorrecta) de fallo de red, con reintentos acotados.
> 7. **Autoplay controlado**: la radio por defecto se reproduce al abrir la app, pero **nunca** se inicia sola cuando conecta Android Auto.
> 8. **Cleartext HTTP permitido** explícitamente (muchas radios self-hosted son HTTP); el tráfico es definido por el usuario.
> 9. **Cast opcional**: si no se configura un *receiver app ID* (registro en la Cast Developer Console), el botón de cast se deshabilita sin romper la app.
> 10. **UI multiidioma** español/inglés vía recursos.

---

## 1. Objetivo

App Android **open source** (GPLv3) cuyo propósito es reproducir el stream de una o varias radios **SUB/WAVE** y **castearlo** a dispositivos Google Cast (Google TV / Chromecast / altavoces), además de ser controlable desde **Android Auto**.

- **Minimalista:** una sola pantalla principal, sin bloat.
- **Bajo consumo:** sin analytics, sin tracking, sin dependencias pesadas. Objetivo APK **< 10 MB**.
- **Publicable en Google Play** para aportar al nicho de radios self-hosted.

---

## 2. Requisitos funcionales

### Radios
- **RF1** — Agregar, editar y eliminar radios (nombre + URL del stream + contraseña opcional).
- **RF2** — Marcar **una radio como por defecto**; se reproduce automáticamente al abrir la app **en el teléfono** (nunca al conectar Android Auto).
- **RF3** — Cambiar entre radios desde una lista/menú.

### Reproducción
- **RF4** — Reproducir el stream en el teléfono (play/pause).
- **RF5** — Reproducción en **segundo plano** con notificación de medios (gestión automática de Media3).
- **RF6** — **Castear** el stream a un dispositivo Google Cast con un botón de cast (oculto si no hay receiver app ID configurado).
- **RF7** — Aparecer como app de medios en **Android Auto** con la lista de radios y play/pause.

### Seguridad
- **RF8** — Soportar la **contraseña de estación** de SUB/WAVE (ver sección 6).

### Errores y robustez
- **RF9** — Ante un fallo del stream: mensaje claro que distinga `401` ("revisá contraseña/URL") de error de red ("no se pudo conectar, reintentando"). Reintentos acotados (máx. 3 con backoff de 5 s), sin loop infinito.

### Metadata (fase 2)
- **RF10** — Mostrar "now playing" si el stream lo provee (JSON público de SUB/WAVE). Fuera del MVP.

---

## 3. Requisitos no funcionales

- **N1 — Minimalista:** una pantalla principal (reproductor + cast + lista de radios + ajustes). Sin tabs, sin login, sin bloat.
- **N2 — Bajo consumo:** sin servicios innecesarios, sin analytics, sin tracking. APK objetivo **< 10 MB**.
- **N3 — Open source:** licencia **GPLv3**. Código limpio.
- **N4 — Sin backend propio:** la app solo depende del stream configurado por el usuario.
- **N5 — Accesible:** contraste y objetivos de toque ≥ 48 dp (requisito de calidad de Play); `contentDescription` en todos los controles.
- **N6 — Permisos mínimos:** `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK` y `POST_NOTIFICATIONS` (este último solicitado en runtime, Android 13+). Nada más.
- **N7 — Multiidioma:** español (por defecto) e inglés.

---

## 4. Stack técnico

| Componente | Tecnología |
|---|---|
| Lenguaje | Kotlin 2.2 |
| UI | Jetpack Compose (Material 3), BOM |
| Reproducción | **Media3** (`androidx.media3:media3-exoplayer`) |
| Casting | **Media3 CastPlayer** (`androidx.media3:media3-cast`) + `play-services-cast-framework` |
| Sesión / Auto | `media3-session` (**MediaLibraryService** + `MediaLibrarySession`) |
| Cliente de UI | `MediaController` (mismo componente que usa Android Auto) |
| Persistencia | DataStore (Preferences, lista JSON) |
| Min SDK / Target | 26 / 36 |

---

## 5. Arquitectura

```
UI (Compose) ── MediaController (Media3 SessionToken) ──> MediaLibrarySession
                                                            ├── ExoPlayer   (local)
                                                            └── CastPlayer  (Google Cast)
```

- El **único** `Player` visible para el sistema es el que expone `MediaLibrarySession`. Android Auto y la UI hablan **solo** con la sesión vía `MediaController`; nunca con el player directo.
- **Cambio local ↔ cast:** al conectar un dispositivo Cast, la sesión cambia su player de `ExoPlayer` a `CastPlayer` (misma interfaz `Player`). Al desconectar, vuelve a `ExoPlayer` restaurando el estado previo (si estaba sonando, sigue sonando en el teléfono).
- `MediaLibrarySession` expone un árbol mínimo: la raíz es navegable y cada radio es un ítem `FLAG_PLAYABLE`. Eso es lo que descubre **Android Auto**.
- `MediaSessionService`/`MediaLibraryService` mantiene la reproducción viva en background y gestiona automáticamente: notificación de medios, audio focus/ducking, `becoming noisy` y lock screen.

---

## 6. Seguridad de SUB/WAVE (auth del stream)

### Cómo funciona SUB/WAVE

- **Una sola contraseña compartida** ("station password"), sin cuentas por usuario.
- El stream se protege con **Icecast basic-auth** y vive en **`/stream.mp3`**.
- Dos formas de sintonizar:
  1. **Userinfo (basic auth):** `https://listener:PASSWORD@tu-station/stream.mp3`
  2. **Query param:** `https://tu-station/stream.mp3?auth=PASSWORD`

### Decisión de diseño

La app **siempre** construye la URL como:

```
streamUrl + "?auth=" + Uri.encode(password)   (si hay contraseña)
streamUrl                                      (si no hay contraseña)
```

**Por qué:** `?auth=PASSWORD` viaja dentro de la URL → funciona **igual** en reproducción local (`ExoPlayer`) y en casting (`CastPlayer` con el receiver por defecto). No hace falta receiver custom ni Cast Connect.

**Encodear el password** (`Uri.encode`) es obligatorio: contraseñas con `&`, `+`, `#` o espacios romperían la query string.

### Modelo de datos

```kotlin
data class Radio(
    val id: String,             // UUID estable
    val nombre: String,
    val streamUrl: String,      // base, ej: https://radio.cundalf.com.ar/stream.mp3
    val password: String?,      // opcional; si está, se agrega ?auth=… (encodeado)
    val esDefault: Boolean      // solo una puede ser true
)
```

> **Privacidad:** el password se guarda en texto plano dentro del DataStore local del dispositivo (nunca se sube a ningún servidor). En fase 2 se puede evaluar cifrado.

### Restricción operativa de Cast

El receiver por defecto de Google Cast exige **HTTPS** para streams accesibles por internet; solo acepta HTTP para dispositivos en la **misma red local**. Consecuencia práctica:
- Radio HTTP + cast a un Chromecast en la misma LAN → **funciona**.
- Radio HTTP + cast desde internet → el receiver lo bloquea; conviene exponer el stream por HTTPS (recomendación para el operador de la radio, no es bug de la app).

---

## 7. Componentes a implementar

### 7.1 `PlaybackService` (MediaLibraryService)
- `MediaLibrarySession` con callbacks:
  - `onGetLibraryRoot` → raíz navegable; `onGetChildren` → una entrada `FLAG_PLAYABLE` por radio.
  - `onAddMediaItems` → convierte cada radio en un `MediaItem` con la URL autenticada (sección 6).
  - `onPlay`/`onPrepare` → si no hay radio cargada, carga la por defecto.
- Registro de listener de errores del player para publicar el mensaje de error (RF9).

### 7.2 Reproducción local (ExoPlayer)
- `ExoPlayer` con `MediaItem` apuntando a la URL construida (con `?auth=` si aplica), atributos de audio media.
- Reintento automático: máximo 3 intentos con backoff de 5 s. Tras agotarlos, error en pantalla.

### 7.3 Casting (CastPlayer)
- `CastOptionsProvider` lee el **receiver app ID** desde un `BuildConfig`/recurso (`CAST_APP_ID`). Si está vacío → el botón de cast queda deshabilitado (la app sigue funcionando local y en Auto).
- `CastPlayer` con `CastContext`; al conectarse un dispositivo, la sesión conmuta a `CastPlayer`; al desconectarse, vuelve a `ExoPlayer`.
- Al elegir un dispositivo desde la UI se abre el diálogo del sistema (`SessionManager.startSessionSelection()`).

### 7.4 UI (Compose, una pantalla)
- **Cabecera:** nombre de la radio actual + estado (conectando/sonando/error).
- **Transporte:** botón play/pause grande (≥ 48 dp), botón de cast.
- **Lista de radios:** cada fila con nombre, URL (truncada), insignia "por defecto", y acciones editar/eliminar/marcar como defecto.
- **Diálogo de radio:** nombre, URL, contraseña (opcional), "por defecto".

### 7.5 Persistencia
- DataStore con dos claves: lista de radios (JSON) e id de la radio por defecto. El flag `esDefault` deriva de ese id.

---

## 8. Android Auto

- Manifest con el metadata `com.google.android.gms.car.application` → `res/xml/automotive_app_desc.xml` (categoría `media`).
- `PlaybackService` declarado con `android:exported="true"`, `foregroundServiceType="mediaPlayback"` y los intent-filters de `androidx.media3.session.MediaSessionService` + `android.media.browse.MediaBrowserService`.
- `MediaLibrarySession` expone las radios como ítems reproducibles.
- **Regla de oro:** el servicio **nunca inicia reproducción por sí solo** al ser descubierto por el auto; solo responde a comandos del usuario. El autoplay por defecto se dispara únicamente desde la apertura de la app en el teléfono.
- Cumplir las **guías de calidad para autos** (controles simples, sin distracción).

---

## 9. Publicación en Google Play

- Subir a Play Console y **optar por el form factor "Android Auto"** en Advanced Settings → Form factors.
- Publicar primero en un **track de testing** (la revisión de Android Auto en producción es **bloqueante** si no cumple calidad).
- Incluir capturas y descripción. No requiere credenciales de test (el stream es público o con contraseña compartida).
- Permisos declarados coherentes con la función declarada (playback de medios).

---

## 10. Fase 2 (nice-to-have)

- Metadata "now playing" desde el JSON público de SUB/WAVE (RF10).
- Biblioteca **Navidrome/Subsonic** (URL + usuario + contraseña) con el mismo `Player`.
- Cifrado opcional de passwords en DataStore.

---

## 11. Criterios de aceptación (MVP)

1. Instalo la app, agrego mi radio (URL + contraseña), y suena en el teléfono.
2. Agrego una segunda radio y cambio entre ambas; la marcada por defecto se reproduce al abrir la app.
3. Toco el botón de cast y suena en mi Google TV (con contraseña incluida). El botón se deshabilita si no hay receiver app ID.
4. Conecto el celular al auto y la app aparece en Android Auto con la lista de radios y play/pause.
5. La reproducción sigue al bloquear la pantalla (background + notificación).
6. Con contraseña incorrecta aparece el mensaje "revisá contraseña/URL"; sin red, "no se pudo conectar".
7. El APK pesa < 10 MB y solo pide permisos de red, foreground service y notificaciones.

---

## 12. Roadmap

- **Fase 1 (MVP):** radios + auth + cast + Android Auto + UI mínima multiidioma. ✅ este repo.
- **Fase 2:** now-playing + Navidrome/Subsonic + cifrado de passwords.
- **Fase 3:** pulido, accesibilidad, publicación en Play Store con Android Auto.

---

## 13. Entregables

1. Proyecto Android en **Kotlin + Jetpack Compose** con Media3 (`exoplayer`, `cast`, `session`).
2. `Radio` + persistencia con DataStore.
3. `PlaybackService` (MediaLibraryService) + `MediaController` en UI.
4. Reproducción local con `ExoPlayer` y casting con `CastPlayer`, URL con `?auth=` (encodeada).
5. UI minimalista multiidioma.
6. Manifest configurado para Android Auto y foreground service.
7. Proyecto **compilable** (`./gradlew assembleDebug`), README con instrucciones de build, cast app ID y licencia GPLv3.