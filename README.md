# WaveLink

[![CI](https://github.com/Cundalf/WaveLink/actions/workflows/ci.yml/badge.svg)](https://github.com/Cundalf/WaveLink/actions/workflows/ci.yml)
[![Security](https://github.com/Cundalf/WaveLink/actions/workflows/security.yml/badge.svg)](https://github.com/Cundalf/WaveLink/actions/workflows/security.yml)
[![License: GPL v3](https://img.shields.io/badge/license-GPLv3-blue.svg)](LICENSE)
[![minSdk 26](https://img.shields.io/badge/minSdk-26%20(Android%208.0)-3DDC84.svg)](#requisitos-para-compilar)
[![Kotlin 2.2](https://img.shields.io/badge/Kotlin-2.2-7F52FF.svg)](https://kotlinlang.org)

Cliente Android **open source** (GPLv3), minimalista y de bajo consumo para reproducir y castear radios **SUB/WAVE**, con soporte de **Android Auto**.

- Kotlin + Jetpack Compose (Material 3), UI con el design system **WaveLink** (OLED dark-first)
- Reproducción con **Media3 ExoPlayer** (background + notificación + lock screen)
- **Android Auto** vía `MediaLibraryService` (lista de radios + play/pause)
- **Google Cast** con `CastPlayer` y botón de cast nativo (`MediaRouteButton`)
- Auth de estación vía `?auth=<password>` (compatible local y cast)
- **Now playing** desde la API pública de SUB/WAVE (`/api/now-playing` + `/api/state`): track, tapa, DJ, bitrate, oyentes y "a continuación"
- Persistencia con DataStore (sin backend propio)
- Sin analytics, sin tracking. APK release < 4 MB.

> Especificación completa y decisiones de diseño: [docs/proyecto.md](docs/proyecto.md)
> Instructivo de pruebas en teléfono, Google TV (cast) y Android Auto: [docs/guia-de-pruebas.md](docs/guia-de-pruebas.md)
> Sistema de diseño (paleta OLED, tipografía, UX por plataforma): [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md)
> Novedades por versión: [CHANGELOG.md](CHANGELOG.md)
> Índice de documentación: [docs/](docs/README.md)
> Skills reutilizables de auditoría: `.opencode/skills/android-ui-ux-audit` y `.opencode/skills/android-static-analysis-security`

---

## Estructura del repositorio

```
.
├── app/                    # módulo Android (Kotlin + Compose + Media3)
├── config/detekt.yml       # reglas de análisis estático
├── docs/                   # especificación, guía de pruebas, design system
├── gradle/                 # wrapper + catálogo de versiones (libs.versions.toml)
├── receiver/               # skin del receiver de Google Cast (Cloudflare Pages)
├── store-listing/          # textos para Play Console
├── .github/                # CI, CodeQL, OWASP dependency-check, plantillas
└── .opencode/skills/       # skills de auditoría (UI/UX y seguridad)
```

---

## Gates de calidad y seguridad (antes de publicar)

```bash
export JAVA_HOME=~/local/jdk-21* && export ANDROID_HOME=$HOME/Android/Sdk
./gradlew build                # lint + lintVital + unit tests + assemble (falla si hay errores)
./gradlew ktlintCheck detekt   # formato Kotlin + análisis estático (detekt + reglas Compose)
./gradlew testDebugUnitTest    # unit tests (Robolectric)
./gradlew dependencyCheckAnalyze  # SCA de CVEs (requiere NVD_API_KEY; fallback: dependencyUpdates)
```

- **Android Lint**: `abortOnError = true`, reportes HTML/XML.
- **detekt** (config en `config/detekt.yml`, `maxIssues: 0`) + **reglas Compose** (`io.nlopez.compose.rules`).
- **ktlint** con `.editorconfig` (permite PascalCase en composables).
- **OWASP dependency-check** configurado (CVSS ≥ 7 bloquea). La base NVD necesita `NVD_API_KEY`; como fallback rápido usa `./gradlew dependencyUpdates`.
- **R8/manifest**: `CastOptionsProvider` se referencia por string en el manifest → tiene `-keep` rule en `proguard-rules.pro`.

Estos gates corren en CI en cada push y pull request
([`ci.yml`](.github/workflows/ci.yml)); además, cada semana corren CodeQL (SAST)
y OWASP dependency-check ([`security.yml`](.github/workflows/security.yml)).

## Checklist de seguridad (OWASP MASVS L1 / Play)

- Permisos mínimos: `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`, `POST_NOTIFICATIONS`.
- Backup desactivado en todas las APIs: `allowBackup=false` + `fullBackupContent=false` (API < 31) y `dataExtractionRules` que excluye todo (API 31+, incluido device-to-device transfer).
- URL validadas al guardar (solo `http`/`https`); password codificado con `Uri.encode` antes de la query string.
- La URL con `?auth=` jamás se muestra en metadatos/título (no filtra credenciales en el panel del auto).
- Sin secretos hardcodeados, sin analytics, sin WebViews ni componentes exportados innecesarios.
- Los ítems de biblioteca se marcan `isPlayable` y `onAddMediaItems` resuelve el `mediaId` → `MediaItem` completo (requisito del bridge Media1 de Android Auto).

---

## Requisitos para compilar

- JDK 17 o 21
- Android SDK con `platforms;android-36` y `build-tools;35.0.0`
- `local.properties` con `sdk.dir=<ruta al SDK>` (cada entorno lo genera; no se commitea)

## Build

```bash
# Debug (firmado con debug key)
./gradlew assembleDebug

# Release (minificado + R8; sin firmar)
./gradlew assembleRelease
```

APKs:
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release-unsigned.apk` (firmar con `apksigner` antes de instalar/distribuir)

### Configurar el botón de Cast

Google Cast exige un **receiver app ID** propio (registro en la
[Cast Developer Console](https://cast.google.com/publish), costo único ≈ USD 5).
La app ya trae el skin del receiver listo en la carpeta `receiver/` (pantalla
oscura WaveLink que se muestra en el TV con el track en vivo).

**Paso 1 — Subir el skin a Cloudflare Pages:**

1. Cloudflare → Workers & Pages → Create → Pages → **Upload assets**.
2. Arrastrá el contenido de la carpeta `receiver/` (solo el `index.html`).
3. Te da una URL tipo `https://wavelink-receiver.pages.dev` — esa es tu **Receiver Application URL**.

**Paso 2 — Registrar la app en la Cast Console:**

1. https://cast.google.com/publish → Add New Application → **Custom Receiver**.
2. Nombre: `WaveLink` · **Receiver Application URL**: tu URL de Cloudflare Pages.
3. En los detalles:
   - **Guest Mode**: NO (la app no lo implementa).
   - **Google Cast for Audio**: SÍ (castea a altavoces y dispositivos solo-audio).
   - **Android TV Application Details**: dejalo en blanco (no hay app Android TV nativa; el TV se usa vía cast).

**Paso 3 — Pasar el App ID al build:**

```bash
./gradlew assembleDebug -Psubwave.castAppId=ABCD1234
```

o editá `gradle.properties`:

```properties
subwave.castAppId=ABCD1234
```

- Sin receiver configurado, la app funciona **igual** en el teléfono y en Android Auto; solo el botón de cast no aparece.
- El `?auth=` viaja dentro de la URL, así que el casting funciona con cualquier receiver compatible con el media namespace (incluido el de arriba, sin cambios en la app).

## Notas operativas

- **HTTPS en Cast:** el receiver por defecto de Google solo reproduce streams **HTTPS** salvo que el Chromecast esté en la **misma LAN** (HTTP funciona en red local).
- **Cleartext HTTP:** la app permite streams HTTP (muchas radios self-hosted lo son). El tráfico siempre es hacia la URL que configuró el usuario.
- **Privacidad:** las contraseñas se guardan solo en el DataStore local; el backup a la nube está desactivado.

## Android Auto

- El `PlaybackService` se declara con `exported=true`, `foregroundServiceType="mediaPlayback"` y las actions `androidx.media3.session.MediaSessionService` + `android.media.browse.MediaBrowserService`.
- Metadata `com.google.android.gms.car.application` → `res/xml/automotive_app_desc.xml` (categoría `media`).
- El árbol de biblioteca expone un ítem por radio (raíz navegable → radios `FLAG_PLAYABLE`).
- El servicio **nunca arranca reproducción por sí solo** al conectarse el auto; solo responde a comandos del usuario. El autoplay de la radio por defecto ocurre únicamente al abrir la app en el teléfono.

## Contribuir

- [CONTRIBUTING.md](CONTRIBUTING.md) — setup, gates de calidad y convenciones.
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) — código de conducta de la comunidad.
- [SECURITY.md](SECURITY.md) — reporte privado de vulnerabilidades.

## Licencia

GPLv3 — ver [LICENSE](LICENSE).