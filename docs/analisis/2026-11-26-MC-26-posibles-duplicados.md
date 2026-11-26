# MC-26: aviso de posibles duplicados cercanos

Historia: "Como personal administrativo quiero que me avisen si ya existe un reclamo igual cerca".
Ticket: https://113872-micomuna.atlassian.net/browse/MC-26 (épica MC-6, Sprint 3).

## Criterios de aceptación (Jira)

1. Al ingresar un reclamo, el sistema busca reclamos abiertos del mismo tipo cerca de esa ubicación.
2. Si encuentra posibles duplicados, los muestra al personal administrativo.
3. El personal puede vincular el reclamo al existente o dejarlo como nuevo.

## Decisiones (aprobadas por el alumno el 26/11/2026)

- **Regla sin IA.** La detección es una regla del backend, sin llamar a ningún proveedor: no tiene costo, da siempre
  el mismo resultado y se puede probar. La IA sigue solo en la sugerencia de tipo (MC-24/MC-25).
- **Qué es un posible duplicado** de un reclamo R:
  - otro reclamo de la **misma comuna** que R (nunca de otra: aislamiento entre comunas);
  - con el **mismo tipo actual** que R (si el personal le cambia el tipo con MC-25, la búsqueda cambia con él);
  - **abierto**: `RECIBIDO`, `ASIGNADO` o `EN_REPARACION`;
  - con latitud y longitud, a **100 m o menos** de R (distancia sobre la esfera, por ejemplo haversine);
  - distinto de R.
  - Se devuelven como máximo 5, del más cercano al más lejano (a igual distancia, el más viejo primero).
- **Cuándo se calcula:** al pedir el detalle del reclamo en el panel, no se guarda la lista. Si un candidato se
  resuelve o se descarta, deja de aparecer solo. Esto cumple el criterio 1: el aviso existe desde que el reclamo
  ingresa, porque se calcula cada vez que el personal lo mira.
- **Solo se avisa si R está abierto.** En un reclamo resuelto o descartado no se buscan candidatos.
- **Vincular** (criterio 3, primera parte) reutiliza el descarte que ya existe (MC-18): motivo `DUPLICADO` con el
  número del original. La tarjeta solo abre ese descarte con el original ya elegido.
- **"Es un reclamo nuevo"** (criterio 3, segunda parte) queda registrado en el reclamo, con quién y cuándo
  (igual criterio de trazabilidad que MC-25). Después de eso el aviso no vuelve a aparecer para R, y el detalle
  muestra quién lo marcó y cuándo. No se puede deshacer (no lo pide la historia).
  - Solo con R abierto y si todavía no se marcó (si no, 409).
  - Solo personal de la comuna: `ADMINISTRATIVO` y `ADMIN_COMUNA`. El vecino y el agente no ven nada de esto.
- **Reclamo en reparación:** el descarte de MC-18 no permite descartar un reclamo `EN_REPARACION`, así que en ese
  caso la tarjeta muestra los candidatos pero bloquea "Es duplicado del n.º X" y explica el motivo; "Es un reclamo
  nuevo" sigue disponible. El candidato (el original) sí puede estar en cualquier estado abierto.
- **Asimetría aceptada:** si A se marca como nuevo, A deja de avisar, pero A puede seguir apareciendo como candidato
  de otro reclamo B cercano (B tiene su propia revisión).
- **Reclamos sin ubicación** (los viejos con solo referencia): no tienen candidatos y no son candidatos.

## Fuera de esta historia

- Marca de "posible duplicado" en la lista del panel (solo se muestra en el detalle).
- Comparar fotos o texto con la IA.
- Avisar al vecino al crear el reclamo.
