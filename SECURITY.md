# Política de seguridad

## Versiones soportadas

| Versión | Soporte |
| --- | --- |
| 0.3.x (versionCode 5) | Sí |
| < 0.3 | No |

## Reportar una vulnerabilidad

**No abras un issue público.** Usá el reporte privado de GitHub:

1. Entrá a https://github.com/Cundalf/WaveLink/security/advisories/new
2. Describí el problema con el mayor detalle posible:
   - versión de WaveLink y dispositivo/Android,
   - pasos para reproducirlo o PoC,
   - impacto estimado (fuga de credenciales, ejecución de código, etc.),
   - si querés, una propuesta de mitigación.

También podés escribir a los maintainers por GitHub si preferís coordinar antes
de cargar el advisory.

### Qué esperar

- Confirmación de recepción en un plazo razonable (proyecto mantenido a tiempo
  parcial, sin SLA).
- Coordinación para publicar un fix y el advisory conjunto antes de divulgarlo.

## Alcance

Entran en alcance:

- La app Android (`app/`): manejo de la contraseña de estación, URL de streams,
  persistencia, componentes exportados y configuración de red.
- El receiver de Cast (`receiver/`): XSS en la metadata de la estación, fugas de
  info en el TV.

Fuera de alcance:

- Bugs de la estación de radio o de su API.
- Comportamiento de Google Cast/Android Auto ajenos a este código.
- Falta de HTTPS en el stream configurado por el usuario (limitación documentada;
  el usuario define la URL).

## Prácticas del proyecto

- Permisos mínimos y `allowBackup=false` (con `dataExtractionRules`).
- Sin secretos hardcodeados ni analytics/tracking.
- Gates automáticos: Android Lint, detekt, ktlint, tests y OWASP
  dependency-check (semanal en CI).
- `?auth=` nunca se muestra en metadatos visibles (notificación, auto, TV).

Gracias por ayudar a que WaveLink sea más seguro.
