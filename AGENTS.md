# AGENTS.md — WaveLink (SubWave)

App Android de radios por streaming (ex "SubWave Cast"). Reproduce radios definidas por el
usuario en teléfono, **Android Auto** y **Google Cast**. Nombre de app: WaveLink,
`applicationId com.cundalf.wavelink`.

## Stack

- Kotlin 2.2.0 + Jetpack Compose (Material 3, BOM 2026.06.00), AGP 8.9.2, Gradle wrapper.
- Media3 **1.11.0** (exoplayer + session + cast), play-services-cast-framework 22.1.0,
  MediaRouter 1.7.0.
- DataStore Preferences (persistencia), minSdk 26 / target y compileSdk 36.
- Sin DI framework, sin Navigation lib: 1 Activity, ViewModel + StateFlow, Composables.
- Calidad: ktlint 12.1.2, detekt 1.23.8 (+ reglas Compose), OWASP dependency-check 9.2.0,
  Android Lint (`abortOnError = true`), JUnit4 + Robolectric 4.14.1.

## Entorno local (Linux)

```bash
export JAVA_HOME=$HOME/local/jdk-21.0.12.1+1   # JDK 21 requerido
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$JAVA_HOME/bin:$PATH
```

Gradle corre **offline** por defecto en esta máquina: usar `./gradlew --offline`.
Teléfono de pruebas: Samsung SM-A566E (`RZGYC1EMSTH`). DHU en
`$ANDROID_HOME/extras/google/auto/desktop-head-unit` (v2.0; requiere
`LD_LIBRARY_PATH=/tmp/opencode/libcxx/root/usr/lib/x86_64-linux-gnu` si falta libc++).

## Comandos

| Tarea | Comando |
|---|---|
| Compilar debug | `./gradlew --offline :app:assembleDebug` |
| Instalar en teléfono | `./gradlew --offline :app:installDebug` |
| Unit tests (rápido) | `./gradlew --offline test` |
| Gates completos | `./gradlew --offline :app:lintDebug detekt ktlintCheck test` |
| Release (R8) | `./gradlew --offline :app:assembleRelease` |
| AAB para Play | `./gradlew --offline :app:bundleRelease` |
| Dependencias nuevas | `./gradlew --offline dependencyUpdates` |
| CVEs dependencias | `./gradlew dependencyCheckAnalyze` (requiere red/NVD_API_KEY) |

Salidas: APKs en `app/build/outputs/apk/…`, AAB en `app/build/outputs/bundle/release/`.

## Estructura

- `app/src/main/java/com/cundalf/wavelink/`
  - `playback/` — PlaybackService (MediaLibraryService), LibraryTree (browse tree),
    RadioMedia (metadata/artwork), CastBridge/CastNowPlaying, eventos y retry policy.
  - `data/` — Radio, RadioRepository, RadioJson, SettingsRepository, SubWaveApi (requests),
    HttpTransport, LanguageStore.
  - `ui/` — toda la UI Compose (SubWaveCastApp, PlayerViewModel, PlayerComponents, sheets…).
- `docs/` — proyecto.md (spec), guia-de-pruebas.md, DESIGN_SYSTEM.md, android-auto-media3.md.
- `config/detekt.yml` — configuración de detekt.
- `CHANGELOG.md` — cambiar SIEMPRE al modificar comportamiento visible.
- `store-listing/TEXTOS-PLAY.md` — textos de la ficha de Play.

## Flujo de trabajo para agentes

1. **Entender**: usar subagente `explore` para mapear antes de tocar código.
2. **Cargar skills relevantes** (herramienta `skill`):
   - Tocar playback/Auto/sesión/Cast → `wavelink-android-auto` (obligatoria) + skills
     `media3-*` de `.opencode/skills/media-pack/`.
   - UI Compose → `android-ui-ux-audit`, `jetpack-compose-performance`, `edge-to-edge`.
   - Tests → `testing-setup`, `media3-test-utils-robolectric`.
   - Antes de publicar → `android-static-analysis-security`, `r8-analyzer`,
     `play-policy-insights`, `android-permissions-security`, `android-intent-security`.
3. **Docs de librerías**: usar el MCP `context7` (p. ej. library `/androidx/media3` o
   `/websites/developer_android`). No inventar APIs de memoria.
4. **Dispositivo**: usar `adb -s RZGYC1EMSTH` o el MCP `mobile-mcp` para tocar la pantalla,
   instalar APKs y sacar capturas. Android Auto se prueba con DHU o auto real.
5. **Revisión**: subagentes `media-reviewer` (solo lectura, Media3/Auto) y `android-qa`
   (tests + gates). Auditar en paralelo cuando el cambio toca varias capas.
6. **Cerrar**: correr los gates completos y actualizar CHANGELOG/docs. No afirmar que algo
   pasa sin el output del comando que lo demuestra.

## Definition of Done

- `./gradlew --offline :app:lintDebug detekt ktlintCheck test` en verde.
- Si se toca comportamiento: tests nuevos que fallan sin el fix.
- `CHANGELOG.md` actualizado; `versionCode`/`versionName` bump si va a Play
  (Play no acepta reusar un versionCode ya rechazado).
- Si se toca Auto/Cast: verificación explícita (DHU, auto real o al menos
  `dumpsys media_session` en el teléfono).

## Reglas de oro del dominio

- Android Auto + Media3: leer la skill `wavelink-android-auto` **antes** de tocar
  `PlaybackService`/`LibraryTree`. Contiene las causas del rechazo de Play de 0.3.0.
- MA-1: nunca autoplay al conectar el auto; el play genérico debe funcionar con la cola
  precargada sin reproducir.
- Ningún `MediaItem` sin URI debe llegar al player (resolver siempre vía `LibraryTree`).
- Las URLs de streaming pueden ser `http://` (radios self-hosted); es una decisión
  consciente documentada. Nunca exponer credenciales de la URL en metadata/logs.
- Los secretos (keystores, contraseñas) nunca se commitean. Firma de release fuera del repo.

## Notas de entorno / gotchas

- El teléfono tiene instalada la app **de Play** (firma distinta a debug): instalar una
  build local requiere desinstalar (se pierden las radios guardadas) o subir a pista interna.
- DHU puede fallar con AA reciente (`PROJECTION_NOT_STARTED`) aunque la app esté bien:
  verificar en auto real antes de culpar a la app.
- `pkill -f "desktop-head-unit"` se auto-mata (matchea el propio shell): usar
  `pkill -f 'desktop-head-unit$'`.
- Las builds locales de gradle requieren `--offline` (no hay red garantizada en el sandbox).
- MCPs configurados (`context7`, `mobile-mcp`) se cargan al iniciar opencode: reiniciar
  después de cambiar `opencode.json`. Para límites más altos de Context7, crear API key en
  context7.com/dashboard y agregar el header `Authorization: Bearer {env:CONTEXT7_API_KEY}`.
