# Contribuir a WaveLink

Gracias por querer sumar. WaveLink es un proyecto chico y minimalista: antes de
escribir código, revisá que la propuesta encaje con la filosofía del proyecto
(una pantalla, sin bloat, sin analytics, sin backend propio).

## Requisitos

- JDK 17 o 21
- Android SDK con `platforms;android-36` y `build-tools;35.0.0`
- `local.properties` con `sdk.dir=<ruta al SDK>` (no se commitea)

## Setup y gates de calidad

```bash
export JAVA_HOME=~/local/jdk-21* && export ANDROID_HOME=$HOME/Android/Sdk

./gradlew ktlintCheck detekt lintDebug   # formato + análisis estático + Android Lint
./gradlew testDebugUnitTest              # tests unitarios y de render (Robolectric/Compose)
./gradlew assembleDebug                  # APK de desarrollo
./gradlew build                          # todo junto (falla si algún gate falla)
```

Antes de abrir un PR, los cuatro comandos tienen que pasar. El CI corre estos
mismos gates en cada push y pull request.

## Estructura del proyecto

```
app/src/main/java/com/cundalf/wavelink/
├── data/      # modelo, persistencia (DataStore), API de SUB/WAVE, HTTP
├── playback/  # MediaLibraryService, ExoPlayer, CastPlayer, MediaSession
└── ui/        # Jetpack Compose (Material 3): pantalla, sheets, view model
```

- La UI **nunca** toca el `Player` directo: todo pasa por `MediaController`.
- Los strings van siempre en `res/values/strings.xml` (es) y
  `res/values-en/strings.xml` (en), sin texto hardcodeado.
- No se agregan dependencias sin justificarlo en el PR (el APK release debe
  seguir siendo chico).

## Convenciones de commits

Se usa [Conventional Commits](https://www.conventionalcommits.org/es/v1.0.0/):

```
feat(ui): agregar botón de sincronizar en el mini player
fix(cast): leer la API desde contentUrl
docs: actualizar guía de pruebas
chore(ci): cachear Gradle en el workflow
```

Tipos habituales: `feat`, `fix`, `docs`, `test`, `refactor`, `perf`, `build`,
`ci`, `chore`. Los mensajes pueden estar en español.

## Flujo de pull requests

1. Creá una rama descriptiva (`fix/cast-content-url`, `feat/...`).
2. Hacé commits atómicos y verificables.
3. Actualizá `CHANGELOG.md` si el cambio es visible para el usuario.
4. Abrí el PR completando la plantilla y esperá el verde del CI.

## Reportar problemas

- **Bugs y propuestas**: usá las plantillas de issue.
- **Vulnerabilidades**: no abras un issue público; seguí [SECURITY.md](SECURITY.md).

## Licencia

WaveLink se distribuye bajo **GPLv3** (ver [LICENSE](LICENSE)). Al enviar un
aporte aceptás que se publique bajo la misma licencia.
