---
name: android-static-analysis-security
description: Ejecuta y aplica análisis estático y de seguridad a la app Android WaveLink. Use cuando se quiera detectar bugs, correr gates de calidad antes de publicar, auditar vulnerabilidades (SAST/SCA) o revisar contra OWASP MASVS. Herramientas: Android Lint, detekt (+ reglas Compose), ktlint, OWASP dependency-check, dependencyUpdates, y checklist MASVS v2.1 (STORAGE, CRYPTO, AUTH, NETWORK, PLATFORM, CODE, PRIVACY).
---

# Análisis estático y seguridad de la app Android

## 1. Gates de calidad (ejecutar y dejar en verde antes de publicar)

```bash
export JAVA_HOME=~/local/jdk-21* && export ANDROID_HOME=$HOME/Android/Sdk
./gradlew lintDebug            # Android Lint (errores abortan el build)
./gradlew detekt               # análisis estático Kotlin + reglas Compose
./gradlew ktlintCheck          # formato/estilo Kotlin
./gradlew test                 # unit tests
./gradlew :app:assembleRelease # release minificado (R8)
```

- `lint { abortOnError = true; checkDependencies = false; xmlReport = true; htmlReport = true }` en `app/build.gradle.kts`.
- detekt con `maxIssues` bajo y config en `config/detekt.yml`; reglas Compose vía `detektPlugins("io.nlopez.compose.rules:detekt:...")`.
- **R8/manifest**: todo componente referenciado por string en el manifest (p. ej. un `OptionsProvider` del Cast) necesita una `-keep` rule o R8 lo elimina. Componentes declarados en `<service>/<activity>` se conservan solos.

## 2. Vulnerabilidades de dependencias (SCA)

- **OWASP dependency-check** (plugin `org.owasp.dependencycheck`): `./gradlew dependencyCheckAnalyze`. La base NVD puede requerir `NVD_API_KEY` (configurable) y descarga grande; si no corre, usar fallback:
- **dependencias desactualizadas**: `./gradlew dependencyUpdates` (plugin `com.github.ben-manes.versions`) → subir a versiones estables recientes.
- Regla práctica: dependencias de AndroidX en versiones estables recientes (no betas), AGP/Kotlin compatibles con la versión de Gradle (tabla oficial AGP↔Gradle).
- Confirmar CVEs conocidos de las dependencias clave por websearch antes de publicar.

## 3. Checklist OWASP MASVS v2.1 (perfil MAS-L1)

### MASVS-STORAGE (datos en reposo)
- No guardar secretos en texto plano salvo justificación y documentado (p. ej. password de estación en DataStore local).
- `android:allowBackup="false"` si hay datos sensibles (evita volcado a la nube del sistema).

### MASVS-NETWORK (datos en tránsito)
- Todo tráfico a internet debe ser TLS/HTTPS. **Cleartext**: si la app permite HTTP (radios self-hosted), es una decisión explícita: `android:usesCleartextTraffic="true"` global o `networkSecurityConfig` acotado, documentado en README.
- No volcar credenciales en la URL visible (metadata del auto): el `?auth=` solo en la URL de reproducción interna, nunca en metadatos/título.

### MASVS-PLATFORM (interacción con la plataforma)
- Componentes exportados solo si es requisito (el servicio de medios DEBE ser `exported=true` para Android Auto). Sin `exported` innecesarios.
- Sin WebViews, sin ContentProviders expuestos, sin IPC inseguro (esta app no los tiene).

### MASVS-CODE
- Validar y sanear entradas de usuario: URLs deben tener scheme `http`/`https` validado al guardar; passwords codificados con `Uri.encode` antes de meterlos en la query string.
- Dependencias actualizadas (ver SCA).
- `minSdk`/`targetSdk` razonables; `targetSdk` = el último de Play.
- Sin secretos hardcodeados (API keys, passwords) en el código.

### MASVS-PRIVACY
- Permisos mínimos: solo los declarados para la función (INTERNET, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, WAKE_LOCK, POST_NOTIFICATIONS).
- Sin analytics/tracking; `POST_NOTIFICATIONS` pedido en runtime.

## 4. Bugs típicos a revisar en Media3

- Retries que se apilan: cancelar el job anterior antes de programar uno nuevo.
- Players no liberados (siempre `release()` en `onDestroy`).
- Cambiar el player de la sesión (`session.setPlayer`) al conectar/desconectar Cast, y restaurar el estado previo.
- `MediaController.releaseFuture` al salir para no filtrar el controlador.
- Estado de error no limpiado: limpiar al pedir play.