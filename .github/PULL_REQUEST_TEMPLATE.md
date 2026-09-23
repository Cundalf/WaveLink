## Qué cambia

<!-- Describí el cambio y, si aplica, el issue que cierra (Closes #123). -->

## Cómo probarlo

<!-- Pasos concretos para verificar el cambio. -->

## Checklist

- [ ] `./gradlew ktlintCheck detekt lintDebug` pasa
- [ ] `./gradlew testDebugUnitTest` pasa
- [ ] Los strings nuevos están en `values/` (es) y `values-en/` (en)
- [ ] Actualicé `CHANGELOG.md` si el cambio es visible para el usuario
- [ ] No hay credenciales, tokens ni URLs con `?auth=` en el diff
