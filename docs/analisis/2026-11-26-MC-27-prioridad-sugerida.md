# MC-27: prioridad sugerida

Historia: "Como personal administrativo quiero una prioridad sugerida para atender primero lo urgente".
Ticket: https://113872-micomuna.atlassian.net/browse/MC-27 (épica MC-6, Sprint 3).

## Criterios de aceptación (Jira)

1. La IA sugiere una prioridad (alta, media o baja) según la urgencia del problema.
2. La lista del panel se puede ordenar por prioridad.
3. El personal puede cambiar la prioridad sugerida.

## Lo que ya existe

Desde MC-24 la IA devuelve y guarda `prioridad_sugerida` (`ALTA`, `MEDIA`, `BAJA`) en `reclamo_sugerencia_ia`, con
el criterio del prompt: ALTA si hay riesgo para las personas o empeora rápido, MEDIA si molesta pero se puede
programar, BAJA si es menor o estético. No se muestra en ningún lado todavía.

## Decisiones (aprobadas por el alumno el 26/11/2026)

- **Prioridad efectiva.** Un reclamo tiene, en este orden:
  1. la prioridad que fijó el personal, si alguien la fijó (queda quién y cuándo); si no,
  2. la prioridad sugerida por la IA en la sugerencia vigente (la más nueva) si está `HECHA`, marcada como
     "sugerida por la IA"; si no,
  3. ninguna ("Sin prioridad").
  La sugerencia de la IA **no se copia** al reclamo: se lee de la sugerencia vigente. Así la IA solo sugiere y lo
  único que se guarda en el reclamo es lo que decidió una persona.
- **Fijar la prioridad** (criterio 3): el personal (`ADMINISTRATIVO` y `ADMIN_COMUNA`) elige Alta, Media o Baja
  en el detalle del panel. Elegir la misma que sugirió la IA cuenta como confirmarla (queda quién y cuándo).
  - Solo con el reclamo abierto (`RECIBIDO`, `ASIGNADO` o `EN_REPARACION`); cerrado da 409.
  - Si la prioridad efectiva cambia, queda una fila en el historial ("Cambió la prioridad de Media a Alta" o
    "de Sin prioridad a Alta"), sin cambiar el estado ni la fecha del último cambio de estado, igual que el cambio
    de tipo de MC-25. Confirmar la sugerida no agrega fila (la prioridad no cambia), pero deja quién y cuándo.
  - Mandar la misma prioridad que ya fijó el personal da 409 (no hay nada que cambiar).
  - No se vuelve a "sin decidir" (no lo pide la historia).
  - No manda correo.
- **Lista del panel** (criterio 2): orden por fecha (como hoy, el predeterminado) o por prioridad: Alta, Media,
  Baja y al final Sin prioridad; a igual prioridad, el más viejo primero. Cada fila muestra la prioridad efectiva
  y si es sugerida por la IA. El orden por prioridad respeta los filtros y la paginación que ya existen.
- **Agente:** ve la prioridad efectiva (sin el detalle de quién la fijó) en su lista y en el detalle, y suma la
  opción de orden "Prioridad": Alta primero; a igual prioridad, el más cerca si hay ubicación, si no el más viejo.
  (Decisión de diseño previa: "el agente puede ordenar por prioridad, tipo y distancia; se implementa con MC-20 y
  MC-27".)
- **Vecino:** no ve la prioridad.
- **Sin llamadas nuevas a la IA:** se usa lo que ya devuelve el análisis de MC-24.

## Fuera de esta historia

- Medir cuánto acierta la IA en la prioridad (se puede comparar después con lo guardado).
- Filtrar la lista por prioridad (solo se pide ordenar).
- Notificar al vecino o al agente cuando cambia la prioridad.
