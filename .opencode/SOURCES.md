# Fuentes de las skills

Estas skills se copiaron de repositorios públicos. Mantener trazabilidad al actualizar.

## Google android/skills (oficial de Google, Apache-2.0)
- Repo: https://github.com/android/skills
- Commit instalado: `5bda39f`
- Instaladas en `.opencode/skills/google/`:
  - `testing-setup` (testing/testing-setup)
  - `media3-cast-integration` (media/media3-cast-integration)
  - `jetpack-compose-performance` (jetpack-compose/jetpack-compose-performance)
  - `android-intent-security` (security/android-intent-security)
  - `android-permissions-security` (security/android-permissions-security)
  - `r8-analyzer` (performance/r8-analyzer)
  - `play-policy-insights` (play/play-policy-insights)
  - `edge-to-edge` (system/edge-to-edge)
- Actualizar: `git clone --depth 1 https://github.com/android/skills` y re-copiar la carpeta del skill.

## sunnat629/android-media-pack (comunidad, Apache-2.0)
- Repo: https://github.com/sunnat629/android-media-pack
- Commit instalado: `1d74b49`
- Instaladas en `.opencode/skills/media-pack/`:
  - `media3-android-auto-media-surface`
  - `media3-background-playback-service`
  - `media3-lifecycle-state`
  - `media3-datasources-networking`
  - `media3-live-streaming`
  - `media3-audio-playback`
  - `media3-test-utils-robolectric`
- Nota: es contenido de comunidad. Ante dudas de API, verificar contra la documentación oficial (usar el MCP Context7 o developer.android.com).

## Skills propias del proyecto
- `android-static-analysis-security`, `android-ui-ux-audit`, `wavelink-android-auto`: mantenidas en este repo, específicas de WaveLink.
