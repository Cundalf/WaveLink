---
name: android-ui-ux-audit
description: Audita la UI/UX de la app Android WaveLink (Compose/Material 3) contra las mejores prácticas oficiales de Google. Use cuando se quiera mejorar la interfaz, la accesibilidad, el cumplimiento de las guías de calidad de Android Auto/Google Play o revisar UX antes de publicar. Cubre: Material 3, objetivos de toque de 48dp, contentDescription/semantics, contraste, guías de apps de medios para autos (incl. MA-1 no-autoplay) y mejores prácticas de Compose.
---

# Auditoría de UI/UX para apps Android (Material 3 + Compose + Android Auto)

Checklist basado en la documentación oficial de Google (developer.android.com y m3.material.io). Úsalo para auditar **antes** de considerar una pantalla "terminada" o de publicar.

## 1. Material 3 y diseño

- Usar componentes de Material 3 (`material3`) con `MaterialTheme`, no colores hardcodeados por pantalla.
- `ColorScheme` coherente: `primary`, `onPrimary`, `primaryContainer`, `error`, etc. **Contraste AA**: texto sobre fondo ≥ 4.5:1; texto grande ≥ 3:1. Verificar también el esquema dark.
- **Color dinámico (Material You)**: en Android 12+ (`Build.VERSION.SDK_INT >= 31`) usar `dynamicLightColorScheme`/`dynamicDarkColorScheme` con fallback propio; envolver en `runCatching` porque puede lanzar en algunos dispositivos.
- Tipografía con jerarquía (`headlineSmall`, `titleMedium`, `bodyMedium`...), no estilos ad-hoc.
- Estados visibles: loading, vacío, error, contenido. Un estado vacío debe explicar **qué hacer** (p. ej. botón "Agregar radio").
- El estado del reproductor debe ser legible de un vistazo: título, estado (reproduciendo/conectando/pausado), error con acción de reintentar.

## 2. Accesibilidad (requisito de calidad de Play)

- **Objetivos de toque ≥ 48x48dp** para todo elemento interactivo. `IconButton`, `FilledIconButton`, `Button` ya lo cumplen; elementos custom usar `Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)`.
- **Todo `Icon` que represente una acción lleva `contentDescription`** (string localizado). Solo es `null` si es puramente decorativo (p. ej. el icono dentro de un `Button` ya rotulado, o un separador).
- `Text` no necesita contentDescription (TalkBack lo lee solo).
- Semántica: `Role.Button`, `Role.Switch`, `selected` para estados; `heading` para títulos de sección; `stateDescription` para estados custom.
- No duplicar etiquetas: si un contenedor ya describe la acción, marcar los iconos internos decorativos con `null`.
- Contraste y tamaños correctos (guías de Play y de autos: `VD-*`).

## 3. Guías de calidad de apps de medios en autos (Android Auto / AAOS)

Del "Car app quality" (categoría Media):

- **MA-1 — Prohibido el autoplay**: la app **no debe** iniciar reproducción al abrir/arrancar ni al conectarse el auto; solo tras una acción del usuario. En teléfono, si hay autoplay, **detectar modo car** (`UiModeManager.currentModeType == Configuration.UI_MODE_TYPE_CAR`) y saltarlo.
- Integrar `MediaSession`/Media3 y exponer la biblioteca vía `MediaLibraryService`/`MediaBrowserService`.
- `onPlay` (play genérico sin ítem seleccionado) debe poder reproducir contenido por defecto: en Media3, precargar la radio por defecto en la cola al iniciar la sesión **sin reproducir** (`session.player.setMediaItems(...)`), así el play del auto funciona y MA-1 se cumple.
- **Metadata de cada ítem**: `MediaItem.setMediaMetadata` con `title` al menos. **Nunca** exponer URLs con credenciales (`?auth=...`) en metadata/título visible (fuga de password en la pantalla del auto).
- Estados iniciales del PlaybackState: `STATE_NONE`/`STATE_STOPPED`/`STATE_PAUSED`, nunca reproducir al conectar.
- Contraste de iconos/colores para el panel del auto (iconos vectoriales monocromos).

## 4. Buenas prácticas de Compose (para detekt-compose y revisión)

- `Modifier` como **primer parámetro opcional** de los composables públicos; nunca reusar el mismo `Modifier` en varios nodos.
- No crear `mutableStateOf`/`remember` con parámetros mutables; no leer `mutableState` en campos/structs (usar `StateFlow`+`collectAsStateWithLifecycle`).
- `rememberSaveable` para estado que debe sobrevivir recreación (campos de formularios).
- `collectAsStateWithLifecycle` (no `collectAsState`) para flows de UI.
- Estados de recomposición: `remember`/`derivedStateOf` correctos; nada de lógica pesada dentro del cuerpo del composable.
- Toda duración/animación con temas (`M3` tokens), no valores mágicos sueltos.

## 5. Checklist final

- [ ] Todos los controles ≥ 48dp
- [ ] Todo icono de acción con contentDescription localizado
- [ ] Esquemas light/dark con contraste ≥ 4.5:1 y color dinámico en 12+
- [ ] Estados loading/empty/error claros, con acción
- [ ] No autoplay en modo car (MA-1)
- [ ] Metadata de medios con title, sin credenciales en texto visible
- [ ] Strings localizados (es/en) sin hardcode
- [ ] Pantalla única minimalista, sin bloat