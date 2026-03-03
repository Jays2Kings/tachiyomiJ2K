# Playbook técnico para entrega de features multiplataforma

Este documento define la **arquitectura objetivo** y las **reglas de contribución** para cambios compartidos entre Android y Desktop. Su propósito es reducir regresiones, separar riesgos por plataforma y acelerar rollback/release.

## 1) Definition of Done (DoD) por feature compartida

Toda feature que toque `shared/` o que requiera parity Android/Desktop debe incluir este checklist en la descripción del PR.

### 1.1 Contrato común (shared)

- [ ] Se define/actualiza contrato en `shared/src/commonMain` (interfaces, modelos, políticas).
- [ ] Se documentan invariantes del contrato (pre/post-condiciones, errores esperados, idempotencia).
- [ ] Se valida compatibilidad hacia atrás (versionado de campos, defaults seguros, degradación controlada).
- [ ] Se agregan pruebas de contrato en `shared/src/commonTest`.

### 1.2 Implementación Android/Desktop

- [ ] Android implementa el contrato sin desviar semántica funcional.
- [ ] Desktop implementa el contrato con semántica equivalente y diferencias explícitamente documentadas.
- [ ] Cualquier diferencia por capacidades de plataforma queda trazada en docs (por ejemplo: red, cookies, trust store).
- [ ] Se evita introducir lógica de negocio específica de plataforma en capas comunes.

### 1.3 Pruebas

- [ ] Pruebas unitarias en común (`commonTest`) cubren casos felices, errores y bordes.
- [ ] Pruebas por plataforma (`android*`, `desktop*`) cubren integración mínima de la implementación concreta.
- [ ] Se ejecutan checks de compilación para Android y Desktop en CI.
- [ ] Se adjunta evidencia de pruebas en el PR (salidas de comandos o pipeline).

### 1.4 Observabilidad (mínimo obligatorio)

- [ ] Se registran eventos de inicio/éxito/fallo de la feature con `platform`, `feature`, `operation`, `result`.
- [ ] Se incluyen códigos de error normalizados (`error_code`) y categoría (`network`, `io`, `validation`, `unexpected`).
- [ ] Se mide tasa de fallo por plataforma y operación para detección de regresiones.
- [ ] Se define umbral de alerta inicial (por ejemplo, +X% de errores vs baseline semanal).

---

## 2) Estrategia de rollout

### Objetivo

Lanzar primero en Desktop con control de exposición y mantener Android estable hasta validar señales operativas.

### Política de rollout

1. **Desktop opt-in / canal experimental (fase 1)**
   - Feature detrás de flag (`feature.<name>.enabled`) desactivado por defecto.
   - Habilitación manual para testers internos y canal experimental.
   - Monitoreo diario de errores y feedback cualitativo.

2. **Desktop expansión controlada (fase 2)**
   - Incrementar exposición gradual (por cohortes o porcentaje).
   - Mantener kill-switch global por plataforma.

3. **Android estable (fase paralela)**
   - Android conserva comportamiento actual mientras Desktop valida estabilidad.
   - Sólo avanzar a Android cuando Desktop cumpla criterios de salida.

4. **Android rollout progresivo (fase 3)**
   - Activar flag en Android empezando por opt-in interno.
   - Escalar a estable tras cumplir SLOs de error/latencia y no observar regresiones funcionales.

### Criterios de salida por fase

- Error rate por operación sin degradación significativa frente al baseline.
- No existencia de bugs P0/P1 abiertos asociados a la feature.
- Evidencia de rollback probado (flag OFF) sin efectos colaterales.

---

## 3) Métricas y logs de fallos por plataforma

## Esquema mínimo de evento

Registrar al menos los siguientes campos en cada evento relevante:

- `timestamp`
- `platform` (`android` | `desktop`)
- `channel` (`stable` | `experimental`)
- `feature`
- `operation`
- `result` (`success` | `failure`)
- `error_code` (si aplica)
- `error_category` (si aplica)
- `duration_ms`
- `app_version`

### KPIs recomendados

- **Failure Rate por plataforma** = fallos / total operaciones.
- **P95 de latencia por operación**.
- **Top N error_code** por versión y plataforma.
- **Ratio de rollback** (veces que se desactiva flag tras activación).

### Reglas de logging

- No registrar PII ni payloads sensibles.
- Incluir contexto suficiente para reproducibilidad (`operation`, `error_code`, `platform`, versión).
- Alinear nomenclatura de error entre Android y Desktop para dashboards comparables.

---

## 4) Playbook de reversión

Ante incidentes, aplicar estrategia de menor impacto:

1. **Mitigación inmediata con feature flag**
   - Desactivar `feature.<name>.enabled` en la(s) plataforma(s) afectada(s).
   - Confirmar caída de errores tras propagación.

2. **Contención por plataforma**
   - Si sólo falla Desktop, mantener Android sin cambios y viceversa.
   - Evitar rollback global si el incidente es localizado.

3. **Rollback de PRs independientes**
   - Revertir PR de Desktop y PR de Android de forma separada cuando sea posible.
   - Mantener intacto el contrato común salvo evidencia de problema en `shared`.

4. **Post-rollback**
   - Documentar causa raíz, ventana de impacto y señales de detección.
   - Añadir test/regla para prevenir recurrencia antes de reintentar rollout.

### Plantilla rápida de incidente

- **Feature**:
- **Plataforma afectada**:
- **Síntoma principal**:
- **Métrica que alertó**:
- **Flag aplicada (ON/OFF)**:
- **PR revertido(s)**:
- **Estado actual**:
- **Acciones preventivas**:

---

## 5) Reglas para nuevas contribuciones multiplataforma

Toda contribución nueva debe cumplir estas reglas:

1. **Contrato primero**: diseñar y validar en `shared` antes de divergencias por plataforma.
2. **Paridad semántica**: diferencias de implementación permitidas, diferencias de comportamiento sólo si están documentadas.
3. **Flags desde el inicio**: toda feature de riesgo medio/alto nace con kill-switch por plataforma.
4. **Observabilidad obligatoria**: sin métricas/logs mínimos, no se considera lista para rollout.
5. **Rollback verificable**: el PR debe explicar cómo desactivar/revertir sin downtime funcional.
6. **PRs separados por plataforma cuando reduzca riesgo**: preferir independencia para facilitar reversión selectiva.

---

## Anexo: checklist listo para copiar en PR

```markdown
## DoD Feature Multiplataforma

### Contrato común
- [ ] Contrato en `shared` actualizado
- [ ] Invariantes documentadas
- [ ] Compatibilidad hacia atrás validada
- [ ] Pruebas de contrato en `commonTest`

### Android/Desktop
- [ ] Implementación Android alineada
- [ ] Implementación Desktop alineada
- [ ] Diferencias documentadas
- [ ] Sin lógica de negocio específica en común

### Pruebas
- [ ] Unit tests comunes
- [ ] Tests de integración por plataforma
- [ ] Build/check Android + Desktop en CI
- [ ] Evidencia de ejecución en PR

### Observabilidad
- [ ] Logs de inicio/éxito/fallo con `platform` y `feature`
- [ ] `error_code` y `error_category`
- [ ] Métricas por plataforma (failure rate, latencia)
- [ ] Umbral de alerta definido

### Rollout/Reversión
- [ ] Estrategia Desktop experimental definida
- [ ] Criterio de promoción a Android definido
- [ ] Kill-switch validado
- [ ] Plan de rollback por PR independiente
```
