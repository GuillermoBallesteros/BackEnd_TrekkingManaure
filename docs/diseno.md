# Diseño técnico: API de reservas (carrera de Manaure)

> Versión 2, pendiente de aprobación del usuario. Reemplaza el diseño anterior (copia en `docs/anterior/`). Negocio y reglas: `docs/flujo-app.md` (RN-xx). Historias: `docs/historias.md`.
> Principio: **el backend se ajusta al front**. El contrato JSON usa los tipos de `shared/reservas/tipos.ts` (nombres en inglés, valores como `'Páramo'`, `'Por revisar'`). Dominio, tablas y clases van en español sin tildes y se mapean en una capa de DTO.

Stack: Spring Boot 4.1.2, Java 25, PostgreSQL 18, Flyway, JPA (`ddl-auto=validate`, `open-in-view=false`), Bean Validation, springdoc (solo dev), JUnit 5, Mockito, Testcontainers. Dinero en `Long`/`BIGINT`. Fechas de negocio con un `Clock` en America/Bogota (un bean, fijable en pruebas); instantes en `TIMESTAMPTZ` mapeados a `OffsetDateTime`.

## 1. Alcance del cambio
- Se reemplazan las tablas de negocio anteriores (`abono`, `ficha_salud`, `persona`, `inscripcion`, `administrador`, `auditoria`, `configuracion_evento`) y su código. Los datos locales de desarrollo se pierden (no hay producción).
- Se conserva: Docker/compose/Caddy y perfil `prod` (HU-D1, HU-D2), Flyway, `Clock`, `GlobalExceptionHandler`/`NegocioException` (se amplía el catálogo), `RateLimitFilter` (se adapta), la base de la seguridad con `oauth2-resource-server` (se reescribe el flujo de sesión).
- Se elimina (sin contraparte en el front): anular inscripción, anular abono, corregir persona, resumen, exportación CSV, cotización, configuración editable completa y el abono como entidad (lo sustituye `pago`).
- Cada ruta del contrato tiene una historia en `docs/historias.md`.

## 2. Paquetes (`co.manaure.trekking`)
```
config/          Clock, CORS, Jackson, OpenAPI, propiedades (@ConfigurationProperties "app.*")
comun/error/     NegocioException(code, status, detail, campo), GlobalExceptionHandler, CodigoError
catalogo/        entidades y repositorios de catálogos (TipoDocumento, Genero, Talla, Estadia, Extra,
                 Eps, Parentesco, PaisTelefono) + CatalogoService (caché en memoria, solo lectura)
configuracion/   ConfiguracionEvento, ConfiguracionRepository, ConfiguracionService
reserva/         Inscripcion, InscripcionParticipante, InscripcionExtra, HotelPrevio, repositorios,
                 ReservaService (crear), ConsultaReservaService (lista admin), DisponibilidadService
persona/         Persona, FichaSalud, ContactoEmergencia, ConsentimientoPersona, repositorios
pago/            Pago, PagoRepository, PagoService (revisar, registrar manual), AutorizacionMenorService
regla/           reglas puras sin Spring: ValidadorPersona, ValidadorReserva, ValidadorTelefono,
                 ValidadorDocumento, ValidadorHotel, CalculadoraPrecios, EstadoInscripcion,
                 Fechas, VentanaHotel (port de shared/reservas/utils)
cifrado/         CifradoSaludConverter (AttributeConverter), ClaveCifrado (versiones)
seguridad/       SecurityConfig, JwtService, AuthController, AuthService, RefreshTokenService,
                 IntentosLoginService, RateLimitFilter, CabecerasSeguridadFilter,
                 ProblemAuthenticationEntryPoint, ProblemAccessDeniedHandler
administrador/   Administrador, AdministradorRepository, AdministradorInicialRunner
auditoria/       Auditoria, AuditoriaRepository, AuditoriaService
api/             controladores (PublicoController, AdminController, AuthController) y dto/ (records
                 con los nombres del front) + mapeadores dominio -> JSON
```
Las reglas de `regla/` no dependen de JPA ni de Spring: reciben datos y devuelven el primer error (`Optional<ErrorRegla>` con `code`, `detail`, `campo`), como las funciones de `validaciones.ts`. Se prueban con las mismas tablas de casos del front.

## 3. Esquema de base de datos
Convenciones: tablas en `snake_case` singular; claves naturales de catálogo en texto (el mismo valor que usa el front, con tildes si las lleva); `BIGINT GENERATED ALWAYS AS IDENTITY` para ids internos; dinero `BIGINT`; todos los `CHECK` se nombran (`ck_tabla_regla`) para traducir violaciones. Las reglas del front se aplican en servicio; las restricciones de la base son la segunda barrera.

### 3.1 Catálogos (V10)
Sembrados con los valores de `shared/reservas/data/catalogos.ts`; el servicio valida cada valor entrante contra ellos y las FK impiden valores huérfanos.

| Tabla | Columnas |
|---|---|
| `tipo_documento` | `clave VARCHAR(20) PK` (CC, CE, TI, RC, Pasaporte, PPT); `patron VARCHAR(40) NOT NULL` (regex sin anclas, p. ej. `[0-9]{6,10}`); `ayuda VARCHAR(60) NOT NULL`; `edad_min SMALLINT NULL`; `edad_max SMALLINT NULL`; `orden SMALLINT NOT NULL` |
| `genero` | `clave VARCHAR(30) PK` (Femenino, Masculino, Otro, Prefiero no decirlo); `orden` |
| `talla` | `clave VARCHAR(5) PK` (XS, S, M, L, XL, XXL, XXXL); `orden` |
| `estadia` | `clave VARCHAR(30) PK` (Páramo, Hotel en Manaure, Regreso a casa); `ocupa_cupo BOOLEAN NOT NULL` (solo Páramo = true); `orden` |
| `extra` | `clave VARCHAR(30) PK` (cuatrimoto, parapente, parapenteAcompanante); `etiqueta VARCHAR(60) NOT NULL`; `requiere_acompanante BOOLEAN NOT NULL` (solo parapenteAcompanante); `orden` |
| `eps` | `clave VARCHAR(60) PK` (25 valores); `pide_nombre BOOLEAN NOT NULL` (solo Otra); `orden` |
| `parentesco` | `clave VARCHAR(40) PK` (11 valores); `es_responsable_legal BOOLEAN NOT NULL` (Madre, Padre, Tutor legal o acudiente); `orden` |
| `pais_telefono` | `iso CHAR(2) PK`; `nombre VARCHAR(40)`; `indicativo VARCHAR(4)`; `ejemplo VARCHAR(20)`; `movil_largos SMALLINT[] NOT NULL`; `movil_inicios VARCHAR(3)[] NOT NULL`; `fijo_largos SMALLINT[] NULL`; `fijo_inicios VARCHAR(3)[] NULL`; `orden`; CHECK `(fijo_largos IS NULL) = (fijo_inicios IS NULL)` |

### 3.2 administrador y sesion_refresh (V11)
**administrador**
| Columna | Tipo | Restricción |
|---|---|---|
| id | BIGINT IDENTITY PK | |
| correo | VARCHAR(254) NOT NULL | UNIQUE; CHECK `correo = lower(correo)` |
| clave_hash | VARCHAR(100) NOT NULL | BCrypt costo 12 |
| activo | BOOLEAN NOT NULL DEFAULT true | |
| intentos_fallidos | INT NOT NULL DEFAULT 0 | CHECK >= 0 |
| bloqueado_hasta | TIMESTAMPTZ NULL | |
| ultimo_acceso_en | TIMESTAMPTZ NULL | |
| creado_en | TIMESTAMPTZ NOT NULL DEFAULT now() | |

**sesion_refresh** (un registro por token emitido; el token en claro nunca se guarda)
| Columna | Tipo | Restricción |
|---|---|---|
| id | BIGINT IDENTITY PK | |
| administrador_id | BIGINT NOT NULL | FK, índice |
| familia | UUID NOT NULL | índice; agrupa la cadena de rotaciones de un inicio de sesión |
| token_hash | CHAR(64) NOT NULL | UNIQUE; SHA-256 hexadecimal del token |
| recordada | BOOLEAN NOT NULL | |
| creado_en | TIMESTAMPTZ NOT NULL DEFAULT now() | |
| expira_en | TIMESTAMPTZ NOT NULL | índice (limpieza) |
| usado_en | TIMESTAMPTZ NULL | se llena al rotar |
| revocado_en | TIMESTAMPTZ NULL | logout, reutilización o administrador inactivo |
| reemplazado_por | BIGINT NULL | FK a sesion_refresh |

### 3.3 configuracion_evento (V12): una sola fila
| Columna | Tipo | Restricción / valor inicial |
|---|---|---|
| id | SMALLINT PK | CHECK `id = 1` |
| nombre_evento | VARCHAR(150) NOT NULL | Trekking Desafío Perijá 13K |
| fecha_evento_inicio, fecha_evento_fin | DATE NOT NULL | 2026-11-15 / 2026-11-16; CHECK inicio <= fin |
| fecha_apertura | DATE NOT NULL | 2026-10-28; CHECK < fecha_evento_inicio |
| fecha_cierre | DATE NOT NULL | 2026-11-09; CHECK >= fecha_apertura y < fecha_evento_inicio |
| cupos_paramo | INT NOT NULL | 60; CHECK >= 1 |
| precio_titular | BIGINT NOT NULL | 350000; CHECK > 0 |
| valor_apartado | BIGINT NOT NULL | 50000; CHECK > 0 y <= precio_titular |
| precio_acompanante_sin_kit | BIGINT NOT NULL | 150000 |
| precio_acompanante_con_kit | BIGINT NOT NULL | 350000 |
| precio_cuatrimoto | BIGINT NOT NULL | 150000 |
| precio_parapente | BIGINT NOT NULL | 200000 |
| precio_hotel_noche | BIGINT NOT NULL | 45000 |
| version_terminos | VARCHAR(30) NOT NULL | TYC-2026-10-28 |
| actualizado_por | BIGINT NULL | FK administrador, índice |
| actualizado_en | TIMESTAMPTZ NULL | |

Los precios >= 0 por CHECK. Solo el panel cambia `cupos_paramo` y `fecha_cierre`; precios y versión de términos se cambian por migración [SUPUESTO]. `kitCompleto` (300.000) no se guarda: es `precio_titular - valor_apartado`.

### 3.4 Personas (V13)
**persona**: identidad y contacto. Una fila por persona por inscripción (no se reutiliza entre inscripciones).
| Columna | Tipo | Restricción |
|---|---|---|
| id | BIGINT IDENTITY PK | |
| nombres, apellidos | VARCHAR(60) NOT NULL | |
| tipo_documento | VARCHAR(20) NOT NULL | FK tipo_documento |
| documento | VARCHAR(20) NOT NULL | CHECK `~ '^[A-Z0-9]{5,20}$'`; guardado en mayúsculas; índice (no único) |
| fecha_nacimiento | DATE NOT NULL | |
| genero | VARCHAR(30) NOT NULL | FK genero |
| telefono_pais | CHAR(2) NOT NULL | FK pais_telefono |
| telefono_numero | VARCHAR(15) NOT NULL | CHECK `~ '^[0-9]{7,15}$'` |
| correo | VARCHAR(254) NOT NULL | CHECK `correo = lower(correo)` |
| ciudad | VARCHAR(60) NOT NULL | |
| eps | VARCHAR(60) NOT NULL | FK eps |
| eps_otra | VARCHAR(60) NULL | CHECK `(eps = 'Otra') = (eps_otra IS NOT NULL)` |
| creado_en | TIMESTAMPTZ NOT NULL DEFAULT now() | |

**ficha_salud** (1:1, `persona_id` PK y FK; los detalles van cifrados)
| Columna | Tipo | Restricción |
|---|---|---|
| persona_id | BIGINT PK | FK persona |
| tiene_enfermedad, tiene_alergias, tiene_discapacidad | BOOLEAN NOT NULL | |
| enfermedad_cifrada, alergias_cifrada, discapacidad_cifrada | TEXT NULL | `AttributeConverter` AES-256-GCM (sección 10.4); CHECK cada bandera = (columna IS NOT NULL) |
| practica_deporte | BOOLEAN NOT NULL | |

**contacto_emergencia** (1:1): `persona_id BIGINT PK FK`; `nombre VARCHAR(120) NOT NULL`; `telefono_pais CHAR(2) NOT NULL FK pais_telefono`; `telefono_numero VARCHAR(15) NOT NULL` (CHECK dígitos); `parentesco VARCHAR(40) NOT NULL FK parentesco`.

**consentimiento_persona** (1:1): `persona_id BIGINT PK FK`; `version_terminos VARCHAR(30) NOT NULL`; `aceptado_en TIMESTAMPTZ NOT NULL`; `uso_imagen BOOLEAN NOT NULL`; `compromiso_areas_protegidas BOOLEAN NOT NULL CHECK (true)`; `compromiso_medio_ambiente BOOLEAN NOT NULL CHECK (true)`.

### 3.5 Inscripción y pagos (V14)
**inscripcion**
| Columna | Tipo | Restricción |
|---|---|---|
| id | BIGINT IDENTITY PK | |
| codigo | VARCHAR(12) NOT NULL | UNIQUE; CHECK `~ '^PER-[0-9]{4,}$'` (JSON `id`) |
| plan_pago | VARCHAR(10) NOT NULL | CHECK IN ('TOTAL','APARTADO') |
| fecha_compromiso | DATE NULL | CHECK `(plan_pago = 'APARTADO') = (fecha_compromiso IS NOT NULL)` |
| autorizacion_menor | BOOLEAN NOT NULL DEFAULT false | |
| activa | BOOLEAN NOT NULL DEFAULT true | false cuando el estado derivado es Rechazado (RN-06); la mantiene el servicio |
| precio_titular_aplicado, precio_apartado_aplicado, precio_acompanante_sin_kit_aplicado, precio_acompanante_con_kit_aplicado, precio_cuatrimoto_aplicado, precio_parapente_aplicado, precio_hotel_noche_aplicado | BIGINT NOT NULL | CHECK >= 0 (RN-03) |
| total | BIGINT NOT NULL | CHECK > 0 |
| fecha_registro | DATE NOT NULL | día de negocio en Bogotá (JSON `createdAt`) |
| creado_en | TIMESTAMPTZ NOT NULL DEFAULT now() | |

Índices: `activa`, `fecha_registro DESC`. Secuencias: `inscripcion_codigo_seq` y `pago_codigo_seq` (`START 1`); el servicio arma `PER-%04d` y `PAG-%04d` (más dígitos pasado el 9999). Un rollback deja huecos; se acepta.

**inscripcion_participante** (titular y acompañante)
| Columna | Tipo | Restricción |
|---|---|---|
| id | BIGINT IDENTITY PK | |
| inscripcion_id | BIGINT NOT NULL | FK, índice; UNIQUE (inscripcion_id, rol) (RN-01) |
| persona_id | BIGINT NOT NULL | FK, UNIQUE |
| rol | VARCHAR(12) NOT NULL | CHECK IN ('TITULAR','ACOMPANANTE') |
| estadia | VARCHAR(30) NOT NULL | FK estadia, índice |
| lleva_kit | BOOLEAN NOT NULL | CHECK `rol <> 'TITULAR' OR lleva_kit` (RN-02) |
| talla | VARCHAR(5) NULL | FK talla; CHECK `lleva_kit = (talla IS NOT NULL)` |
| documento_activo | VARCHAR(20) NULL | copia del documento mientras la inscripción esté activa; NULL si Rechazada |

Índice único `uq_participante_documento_activo` sobre `documento_activo` (los NULL no chocan). Es la barrera final de RN-06: el servicio mantiene `documento_activo` y `inscripcion.activa` en la misma transacción.

**inscripcion_extra**: `inscripcion_id BIGINT FK`, `extra VARCHAR(30) FK extra`; PK (inscripcion_id, extra).

**hotel_previo** (1:1): `inscripcion_id BIGINT PK FK`; `entrada DATE NOT NULL`; `salida DATE NOT NULL CHECK (salida > entrada)`; `con_acompanante BOOLEAN NOT NULL`.

**pago**
| Columna | Tipo | Restricción |
|---|---|---|
| id | BIGINT IDENTITY PK | |
| codigo | VARCHAR(12) NOT NULL | UNIQUE; CHECK `~ '^PAG-[0-9]{4,}$'` (JSON `id`) |
| inscripcion_id | BIGINT NOT NULL | FK, índice |
| concepto | VARCHAR(20) NOT NULL | CHECK IN ('SEPARACION_CUPO','PAGO_TOTAL','PAGO_SALDO') |
| monto | BIGINT NOT NULL | CHECK > 0 |
| fecha | DATE NOT NULL | día de negocio |
| revision | VARCHAR(12) NOT NULL | CHECK IN ('POR_REVISAR','APROBADO','RECHAZADO') |
| motivo_rechazo | VARCHAR(200) NULL | CHECK `(revision = 'RECHAZADO') = (motivo_rechazo IS NOT NULL)` |
| origen | VARCHAR(10) NOT NULL | CHECK IN ('PUBLICO','MANUAL') |
| registrado_por | BIGINT NULL | FK administrador; CHECK `(origen = 'MANUAL') = (registrado_por IS NOT NULL)` |
| revisado_por | BIGINT NULL | FK administrador, índice |
| revisado_en | TIMESTAMPTZ NULL | CHECK `(revision = 'POR_REVISAR') = (revisado_en IS NULL AND revisado_por IS NULL)` |
| en_revision_desde | TIMESTAMPTZ NOT NULL DEFAULT now() | (V17) desde cuándo está en `POR_REVISAR`: la creación del pago o la última vez que se devolvió a `POR_REVISAR` (`Pago.revisar` la actualiza). La caducidad (RN-22) parte de aquí, no de la reserva. Backfill con `creado_en` |
| creado_en | TIMESTAMPTZ NOT NULL DEFAULT now() | |

Índice parcial `(inscripcion_id) WHERE revision = 'APROBADO'` para cupos y estado, y (V17) `(en_revision_desde) WHERE revision = 'POR_REVISAR'` para la tarea de caducidad.

`revisado_por` ya es nullable y `ck_pago_revisado` solo exige `revisado_en` en un pago revisado: el rechazo automático del sistema (`revisado_por` NULL, `revisado_en` = ahora) las cumple sin cambiarlas. Un revisor NULL solo ocurre en esa acción del sistema (`RECHAZO_AUTOMATICO` en auditoría).

### 3.6 auditoria (V15)
| Columna | Tipo | Restricción |
|---|---|---|
| id | BIGINT IDENTITY PK | |
| administrador_id | BIGINT NULL | FK, índice (NULL en intentos fallidos sin usuario y en las acciones del sistema `RECHAZO_AUTOMATICO` y `ALERTA_RESERVAS_IP`, que no tienen administrador) |
| accion | VARCHAR(40) NOT NULL | CHECK IN (INICIAR_SESION, CERRAR_SESION, SESION_REUTILIZADA, BLOQUEO_CUENTA, APROBAR_PAGO, RECHAZAR_PAGO, DEVOLVER_PAGO_A_REVISION, REGISTRAR_PAGO_MANUAL, FIJAR_AUTORIZACION_MENOR, FIJAR_CAPACIDAD, FIJAR_FECHA_CIERRE; V17 añade RECHAZO_AUTOMATICO y ALERTA_RESERVAS_IP) |
| entidad | VARCHAR(30) NOT NULL | `PAGO`, `RESERVA`, `CONFIGURACION`, `SESION` |
| entidad_ref | VARCHAR(40) NOT NULL | código (`PAG-0007`, `PER-0003`) o `config`; índice (entidad, entidad_ref) |
| detalle | VARCHAR(300) NULL | solo datos no personales: transición `POR_REVISAR>APROBADO`, monto, valor anterior y nuevo de capacidad o fecha. **Nunca** salud, nombres, documentos ni el texto libre del motivo |
| fecha | TIMESTAMPTZ NOT NULL DEFAULT now() | |

## 4. Migraciones Flyway
Las V1 a V8 ya están aplicadas y no se editan. La V9 las deja sin efecto sobre el modelo de negocio.

| Archivo | Contenido |
|---|---|
| `V9__retirar_modelo_anterior.sql` | `DROP TABLE` de `auditoria`, `abono`, `ficha_salud`, `persona`, `inscripcion`, `administrador`, `configuracion_evento` y `DROP SEQUENCE inscripcion_codigo_seq`. Se conserva la extensión `unaccent` (V5) |
| `V10__crear_catalogos.sql` | 8 catálogos con sus valores |
| `V11__crear_administrador_y_sesion_refresh.sql` | `administrador`, `sesion_refresh` |
| `V12__crear_configuracion_evento.sql` | tabla y fila inicial |
| `V13__crear_personas.sql` | `persona`, `ficha_salud`, `contacto_emergencia`, `consentimiento_persona` |
| `V14__crear_inscripcion_y_pagos.sql` | `inscripcion`, `inscripcion_participante`, `inscripcion_extra`, `hotel_previo`, `pago`, secuencias, índices |
| `V15__crear_auditoria.sql` | `auditoria` |
| `V16__crear_intento_login.sql` | `intento_login` (fallos de login por IP y correo, §10.2) |
| `V17__caducidad_pago_y_alertas.sql` | `pago.en_revision_desde` (backfill con `creado_en`, default `now()`, NOT NULL) con su índice parcial, y el CHECK `ck_auditoria_accion` ampliado con `RECHAZO_AUTOMATICO` y `ALERTA_RESERVAS_IP` (H4) |

Si una migración posterior cambia un catálogo (p. ej. nueva EPS) se agrega con `INSERT`; la app no cambia.

## 5. Cálculos (clases puras en `regla/`, con pruebas unitarias)
- **total** = `precio_titular` + (acompañante ? (kit ? `precio_acompanante_con_kit` : `precio_acompanante_sin_kit`) : 0) + Σ extras (`cuatrimoto` → `precio_cuatrimoto`; `parapente` y `parapenteAcompanante` → `precio_parapente`) + hotel (`noches * personas * precio_hotel_noche`, personas = 2 si `withCompanion` y hay acompañante, si no 1; noches = días entre entrada y salida). Usa los precios vigentes al registrar y los guarda como `*_aplicado`.
- **pago inicial**: `apartado` → concepto `SEPARACION_CUPO`, monto `min(valor_apartado, total)`; `total` → `PAGO_TOTAL`, monto `total`.
- **pagado** = Σ monto de pagos `APROBADO`. **saldo** = `max(0, total - pagado)`.
- **estado derivado** (orden): `pagado > 0 y pagado >= total` → Pagado; `pagado > 0` → (`fecha_compromiso != null y fecha_compromiso < hoy` ? Vencido : Saldo pendiente); sin pagos o con alguno `POR_REVISAR` → Por verificar; si no → Rechazado. `hoy` = fecha en Bogotá.
- **activa** = estado != Rechazado.
- **cupos ocupados** = personas con `estadia.ocupa_cupo` en inscripciones con al menos un pago `APROBADO`. **disponibles** = `max(0, cupos_paramo - ocupados)`. Consulta:
  ```sql
  SELECT count(*) FROM inscripcion_participante ip
  JOIN estadia e ON e.clave = ip.estadia AND e.ocupa_cupo
  WHERE EXISTS (SELECT 1 FROM pago p WHERE p.inscripcion_id = ip.inscripcion_id AND p.revision = 'APROBADO')
    AND ip.inscripcion_id <> :excluir   -- solo al aprobar, con :excluir = -1 en los demás casos
  ```
- **primer día elegible** = `max(hoy, fecha_apertura)`. **Ventana de hotel**: `lastCheckOut = fecha_evento_inicio`; `firstCheckIn = primer día elegible`; `lastCheckIn = lastCheckOut - 1`; disponible si `firstCheckIn <= lastCheckIn`; `firstCheckOut = firstCheckIn + 1`.
- **edad** = años cumplidos a `hoy` (no al día del evento, igual que el front). **menor** = edad < 18 con fecha válida.
- **cerrado** = `hoy > fecha_cierre`.
- **capacidad mínima** = `max(1, ocupados)`.

## 6. Contrato de la API
Todas las respuestas son JSON UTF-8. Fechas `AAAA-MM-DD`; instantes ISO-8601 UTC (`2026-10-28T15:20:11.482Z`). Los records de `api/dto` usan exactamente los nombres de abajo. Propiedades desconocidas en la entrada se ignoran; no se coaccionan tipos (un `"true"` o un número donde va un booleano da 400). Los montos y la capacidad se leen como número genérico y se validan como entero positivo (422 con el mensaje del front, no 400).

### 6.1 Tipos del contrato
`Registration` (respuesta de reserva) = campos de `Person` aplanados + los de la reserva, igual que `tipos.ts`:
```json
{
  "id": "PER-0001",
  "createdAt": "2026-10-28",
  "firstName": "María José", "lastName": "Pérez Gómez",
  "documentType": "CC", "document": "1065123456",
  "birthDate": "1990-05-14", "gender": "Femenino",
  "phone": { "country": "CO", "number": "3001234567" },
  "email": "maria@example.com", "city": "Valledupar",
  "eps": { "entity": "Nueva EPS", "other": null },
  "emergencyContact": {
    "name": "Luis Pérez",
    "phone": { "country": "CO", "number": "3157654321" },
    "relationship": "Hermano o hermana"
  },
  "health": { "illness": null, "allergies": "Penicilina", "disability": null, "exercises": true },
  "commitments": { "protectedAreas": true, "environment": true },
  "consent": { "version": "TYC-2026-10-28", "acceptedAt": "2026-10-28T15:20:11.482Z", "imageUse": true },
  "stay": "Páramo",
  "shirtSize": "M",
  "companion": null,
  "extras": ["cuatrimoto"],
  "preEventHotel": null,
  "plan": "apartado",
  "commitmentDate": "2026-11-09",
  "guardianAuthorized": false,
  "payments": [
    { "id": "PAG-0001", "concept": "Separación de cupo", "amount": 50000,
      "date": "2026-10-28", "review": "Por revisar", "rejectionReason": null }
  ],
  "total": 500000
}
```
`companion` es una `Person` aplanada más `kit` (boolean) y `shirtSize` (talla o `null`). `preEventHotel` es `{ "checkIn": "2026-11-12", "checkOut": "2026-11-14", "withCompanion": true }` o `null`. `total` es un campo **adicional** (el front calcula el suyo con `getTotal`; TypeScript lo ignora): el total guardado por el servidor con los precios aplicados [SUPUESTO].

`NewRegistration` (entrada) = `Registration` sin `id`, `createdAt`, `payments`, `guardianAuthorized` (y sin `total`, que se ignora). Todos los campos son obligatorios salvo los nulables del tipo.

### 6.2 Públicas (sin autenticación)
| Método | Ruta | Entrada | Salida | HTTP |
|---|---|---|---|---|
| GET | `/api/v1/config` | | `ConfigResponse` | 200 |
| GET | `/api/v1/disponibilidad` | | `Disponibilidad` | 200 |
| POST | `/api/v1/reservas` | `NewRegistration` | `Registration` (sin cabecera `Location`: no hay consulta pública) | 201, 400, 409, 422, 429 |

`GET /api/v1/config`:
```json
{
  "evento": { "nombre": "Trekking Desafío Perijá 13K", "fechas": ["2026-11-15", "2026-11-16"],
              "fechaAperturaInscripciones": "2026-10-28", "fechaCierre": "2026-11-09" },
  "precios": { "moneda": "COP", "total": 350000, "inscripcion": 50000, "kitCompleto": 300000,
               "acompanante": 150000, "acompananteConKit": 350000, "cuatrimoto": 150000,
               "parapente": 200000, "hotelNoche": 45000 },
  "versionTerminos": "TYC-2026-10-28"
}
```
Las claves reflejan `shared/config` (`evento`, `precios`) [SUPUESTO sobre la forma; el front no la consume hoy].

`GET /api/v1/disponibilidad`:
```json
{ "capacity": 60, "occupied": 12, "available": 48, "fechaCierre": "2026-11-09", "today": "2026-10-28" }
```
`occupied` y `available` siguen RN-04. `today` es la fecha de Bogotá en el servidor.

`POST /api/v1/reservas` valida en el orden de la sección 8 y, si pasa, crea todo en una transacción (persona, ficha cifrada, contacto, consentimiento con `aceptado_en = ahora` del servidor, participantes, extras, hotel, primer pago `POR_REVISAR`). Responde 201 con la `Registration` completa (con salud, como el front la guarda).

### 6.3 Autenticación (`/api/v1/auth`)
| Método | Ruta | Entrada | Salida | HTTP |
|---|---|---|---|---|
| POST | `/login` | `{ "correo", "clave", "recordar" }` | `SesionResponse` + cookie de refresh | 200, 400, 401, 429 |
| POST | `/refresh` | cookie `refresh_token` + cabecera `X-Requested-With` | `SesionResponse` + cookie nueva | 200, 401, 403, 429 |
| POST | `/logout` | cookie + cabecera `X-Requested-With` | sin cuerpo; borra la cookie | 204, 403 |

`SesionResponse` (refleja `Sesion` de `admin/src/services/auth.ts`; tiempos en milisegundos desde epoch):
```json
{ "token": "<jwt>", "tipo": "Bearer", "correo": "admin@dominio.com", "nombre": "Admin",
  "iniciadaEn": 1793200000000, "expiraEn": 1793200900000, "sesionExpiraEn": 1793804800000,
  "recordada": true }
```
`expiraEn` es el vencimiento del access token (15 min); `sesionExpiraEn` el del refresh (7 días con `recordar`, 8 horas sin él [SUPUESTO]); `iniciadaEn` se conserva igual a lo largo de las rotaciones de la misma familia. `nombre` se deriva del correo como `nombreDesdeCorreo`.

Login valida como el front (`validarCredenciales`: correo con formato y <= 254; clave de 8 a 128 caracteres, sin espacios al inicio ni al final). Si falla, 400 `DATOS_INVALIDOS` con `detail` "Revisa los datos marcados." y `campos: { "correo": "...", "clave": "..." }` (los mismos textos del front). Credenciales inexistentes, clave mala o `activo = false`: mismo 401 `CREDENCIALES_INVALIDAS`.

Cookie de refresh: `Set-Cookie: refresh_token=<token>; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Strict; Max-Age=604800` (con `recordar`; sin él se omite `Max-Age` y es cookie de sesión). Borrado: `Max-Age=0`. El token es un valor aleatorio de 256 bits en base64url.

### 6.4 Administración (`/api/v1/admin`, requiere `Authorization: Bearer <access>` con rol ADMIN)
| Método | Ruta | Entrada | Salida | HTTP |
|---|---|---|---|---|
| GET | `/reservas` | | `AdminReservas` | 200, 401 |
| POST | `/reservas/{id}/pagos` | `{ "amount": 100000 }` | `Payment` | 201, 401, 404, 409, 422 |
| PUT | `/pagos/{id}/revision` | `{ "review": "Rechazado", "reason": "..." }` | `Registration` | 200, 400, 401, 404, 409, 422 |
| PUT | `/reservas/{id}/autorizacion-menor` | `{ "received": true }` | `Registration` | 200, 400, 401, 404 |
| PUT | `/capacidad` | `{ "capacity": 80 }` | `{ "capacity": 80 }` | 200, 401, 422 |
| PUT | `/fecha-cierre` | `{ "fechaCierre": "2026-11-10" }` | `{ "fechaCierre": "2026-11-10" }` | 200, 401, 422 |

`{id}` es el código (`PER-0001`, `PAG-0007`). Sin token válido todas responden 401 `NO_AUTENTICADO`; con token de otro rol, 403 `ACCESO_DENEGADO`.

- `GET /reservas`: `{ "registrations": [Registration...], "capacity": 60, "fechaCierre": "2026-11-09", "today": "2026-10-28" }`. Todas las reservas, activas o no, con pagos y salud, ordenadas por código descendente [SUPUESTO]. Sin paginación (volumen de cientos) [SUPUESTO]. `Cache-Control: no-store`. Sin `counters` (los asigna el servidor).
- `POST /reservas/{id}/pagos`: crea un pago **ya verificado** (`review: "Aprobado"`, `origen MANUAL`, `date` = hoy). Reglas RN-16 y RN-15. Respuesta `{ "id": "PAG-0002", "concept": "Pago de saldo", "amount": 100000, "date": "2026-10-28", "review": "Aprobado", "rejectionReason": null }`.
- `PUT /pagos/{id}/revision`: `review` es `"Aprobado"`, `"Rechazado"` o `"Por revisar"`; cualquier otro valor: 400 `REVISION_INVALIDA`. `Rechazado` exige `reason` no vacío tras recortar espacios; en los otros dos, `reason` se ignora y el motivo queda nulo. Aprobar aplica RN-15 y, si la reserva estaba Rechazada, RN-18. Idempotente si la revisión ya es la pedida. Devuelve la `Registration` actualizada.
- `PUT /reservas/{id}/autorizacion-menor`: fija `guardianAuthorized` a `received` (boolean obligatorio). Se acepta aunque el titular sea mayor de edad [SUPUESTO: el front no lo impide].
- `PUT /capacidad`: RN-17. `PUT /fecha-cierre`: `isIsoDate`, `>= fecha_apertura`, `< fecha_evento_inicio`. Ambas actualizan `configuracion_evento` (con `actualizado_por/en`) y la auditoría.

Cada operación de escritura registra una fila de auditoría (sección 11 de `flujo-app.md`).

## 7. Mapeo dominio -> JSON
| Dominio (tabla/clase) | JSON |
|---|---|
| `inscripcion.codigo` / `pago.codigo` | `id` |
| `inscripcion.fecha_registro` | `createdAt` |
| `persona.nombres` / `apellidos` | `firstName` / `lastName` |
| `persona.tipo_documento` / `documento` | `documentType` / `document` |
| `persona.fecha_nacimiento` / `genero` | `birthDate` / `gender` |
| `persona.telefono_pais` + `telefono_numero` | `phone: { country, number }` |
| `persona.correo` / `ciudad` | `email` / `city` |
| `persona.eps` / `eps_otra` | `eps: { entity, other }` |
| `contacto_emergencia` | `emergencyContact: { name, phone, relationship }` |
| `ficha_salud` (descifrada) | `health: { illness, allergies, disability, exercises }`; detalle ausente = `null` |
| `consentimiento_persona` | `consent: { version, acceptedAt, imageUse }`; los dos compromisos = `commitments: { protectedAreas, environment }` |
| `inscripcion_participante.estadia` / `talla` / `lleva_kit` | `stay` / `shirtSize` / `kit` (solo acompañante) |
| `inscripcion_extra.extra` | `extras: [..]` (orden del catálogo) |
| `hotel_previo` | `preEventHotel: { checkIn, checkOut, withCompanion }` |
| `inscripcion.plan_pago` | `plan`: `TOTAL` = `"total"`, `APARTADO` = `"apartado"` |
| `inscripcion.fecha_compromiso` | `commitmentDate` |
| `inscripcion.autorizacion_menor` | `guardianAuthorized` |
| `pago.concepto` | `concept`: `SEPARACION_CUPO` = `"Separación de cupo"`, `PAGO_TOTAL` = `"Pago total"`, `PAGO_SALDO` = `"Pago de saldo"` |
| `pago.monto` / `fecha` | `amount` / `date` |
| `pago.revision` | `review`: `POR_REVISAR` = `"Por revisar"`, `APROBADO` = `"Aprobado"`, `RECHAZADO` = `"Rechazado"` |
| `pago.motivo_rechazo` | `rejectionReason` |
| `inscripcion.total` | `total` (adicional) |

El estado derivado no viaja: el front lo calcula con `getStatus`. Los catálogos (documentos, géneros, tallas, estadías, EPS, parentescos, extras) viajan con el mismo texto que la clave de su tabla.

## 8. Orden de validación de `POST /reservas`
Port de `validateNewRegistration`; devuelve el **primer** error. Los números son el orden de ejecución.
1. Estructura (400 `DATOS_INVALIDOS`): JSON válido; objetos obligatorios presentes (`phone`, `eps`, `emergencyContact`, `health`, `commitments`, `consent`, `extras`, `plan`); tipos correctos (texto, booleano, lista).
2. Cierre: `hoy > fecha_cierre` → 409 `INSCRIPCIONES_CERRADAS`.
3. Titular (`validatePerson`, `quien = participante`), en este orden: nombres y apellidos; fecha de nacimiento (válida, no futura, edad <= 110); documento (tipo del catálogo, patrón del tipo, edad mínima y máxima del tipo); género; teléfono (país del catálogo, solo dígitos, celular válido del país); correo (formato, dominios errados); ciudad; EPS (en catálogo; con `Otra` nombre 3 a 60; si no, nulo); contacto de emergencia (nombre de 5 a 120 con al menos dos palabras; distinto del nombre de la persona; teléfono celular o fijo válido; teléfono distinto al de la persona; parentesco en catálogo); salud (detalles de 2 a 200 caracteres sin espacios en los extremos o nulos; `exercises` booleano); compromisos (`protectedAreas`, luego `environment`); consentimiento (`acceptedAt` fecha válida, versión vigente, `imageUse` booleano); estadía en catálogo.
4. Titular menor: parentesco del contacto debe ser responsable legal → `MENOR_SIN_RESPONSABLE`.
5. Talla del titular en catálogo → `TALLA_INVALIDA`.
6. Extras: todos en catálogo; sin repetidos; `parapenteAcompanante` solo con acompañante.
7. Hotel previo (si viene): ventana disponible; fechas válidas; llegada dentro de la ventana; salida > llegada; salida <= `lastCheckOut`; al menos una noche; `withCompanion` solo con acompañante.
8. Acompañante (si viene): `validatePerson` con `quien = acompañante`; con `kit` talla en catálogo; sin `kit` talla nula. Luego `validateDistinct`: documento, correo, celular distintos; contacto de emergencia de cada uno no es el otro (por celular y por nombre), en el orden del front.
9. Plan: en catálogo; `apartado` con primer día elegible <= cierre y fecha compromiso entre primer día y cierre; `total` con fecha nula.
10. Documentos duplicados (bajo bloqueo): titular y luego acompañante contra inscripciones activas (RN-06) → 409 `DOCUMENTO_DUPLICADO`.
11. Cupo del páramo (bajo bloqueo): personas con estadía `Páramo` frente a disponibles → 409 `SIN_CUPO_PARAMO`.

Normalización previa: el documento se valida tal como llega y se guarda en mayúsculas; el correo se pasa a minúsculas antes de validar [SUPUESTO]; no se recortan espacios (los patrones los rechazan, como en el front). Los valores de catálogo se reciben como texto, nunca como `enum`, para devolver el mensaje del front y no un 400 genérico.

## 9. Concurrencia
- **Bloqueo global (`configuracion_evento`)**: `SELECT ... FROM configuracion_evento WHERE id = 1 FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`). Lo toman, en este orden de adquisición y siempre primero: crear reserva, aprobar un pago, devolver un pago a "Por revisar" o rechazarlo cuando la inscripción estaba Rechazada, pago manual y cambio de capacidad. Serializa la comprobación de **cupo** y de **documento duplicado** (volumen bajo). `jakarta.persistence.lock.timeout` = 5 s [SUPUESTO] → 409 `RECURSO_OCUPADO` si vence.
- **Bloqueo de la inscripción**: toda operación sobre pagos o autorización toma `FOR UPDATE` de la fila de `inscripcion` (`findByCodigoForUpdate`) **después** del bloqueo global. Dentro: recalcula pagado, estado derivado, `activa` y `documento_activo` y los guarda en la misma transacción. El pago se resuelve por su código a su inscripción antes de bloquear.
- **Orden fijo**: configuración y luego inscripción; sin interbloqueos.
- **Documento**: la comprobación en servicio da el mensaje; `uq_participante_documento_activo` respalda ante cualquier carrera residual; la violación se traduce a 409 `DOCUMENTO_DUPLICADO` (por nombre de restricción, sin valores).
- **Caducidad (H4, RN-22)**: `CaducidadReservasService` (tarea `@Scheduled` `app.reservas.caducidad.cron`, cada 10 min, activable con `app.reservas.caducidad.activa`) lee por lotes (paginación por id, `app.reservas.caducidad.lote`) las reservas con un pago `POR_REVISAR` con `en_revision_desde <= ahora - horas` y sin pagos aprobados, **sin bloqueo**, y por cada una llama a `RevisionPagoService.rechazarPorCaducidad`, una transacción por reserva que toma el mismo orden (configuración, luego inscripción) y **vuelve a comprobar** pagos aprobados y antigüedad bajo bloqueo. Reutiliza `aplicar` (estado derivado, `activa` y `documento_activo` coherentes). Un fallo de una reserva se registra (solo el código y la clase de la excepción) y no frena a las demás. Frente a una aprobación simultánea, el bloqueo serializa: si gana la tarea, la aprobación reactiva la reserva (RN-18); si gana la aprobación, la tarea no hace nada. Nunca queda un pago aprobado en una reserva inactiva.
- **Cupo**: ocupados se recalculan bajo el bloqueo global, nunca se guardan.
- `@Transactional` en servicios; `readOnly = true` en consultas.

## 10. Seguridad

### 10.1 Sesión
- **Access token**: JWT HS256 (`NimbusJwtEncoder` / `NimbusJwtDecoder.withSecretKey`), 15 minutos. Claims: `sub` = id del administrador, `email`, `roles` = `["ADMIN"]`, `jti`. Secreto `JWT_SECRET` (>= 32 bytes); sin él la app no arranca. El front lo guarda **solo en memoria** (no en `localStorage`).
- **Refresh token**: 256 bits aleatorios (`SecureRandom`), en cookie. En base solo se guarda `SHA-256(token)` hexadecimal en `sesion_refresh`. Un solo uso:
  1. `/refresh` calcula el hash y busca el registro. Si no existe, está vencido o revocado: 401 `SESION_INVALIDA` y borra la cookie.
  2. Si ya fue usado (`usado_en != null`) es una **reutilización**: se revoca toda la familia, se audita `SESION_REUTILIZADA` y se responde 401 `SESION_INVALIDA`.
  3. Si es válido: marca `usado_en`, emite un token nuevo de la misma familia (`reemplazado_por`), con la misma `recordada` y `expira_en` = el original (la expiración no se extiende al rotar: 7 días desde el login con `recordar`, 8 horas sin él), y un access nuevo.
  - `/logout` revoca la familia del token presentado (si lo hay) y borra la cookie; el access sigue válido hasta su vencimiento (máx. 15 min) [aceptado].
  - Una tarea programada borra `sesion_refresh` vencidos hace más de 30 días.
- **Cookie**: `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`. `Secure` se controla con `COOKIE_SECURE` (por defecto `true`; los navegadores aceptan `Secure` en `localhost`).
- **CSRF**: sin cookie de sesión en rutas de datos (Bearer en cabecera). Las dos rutas que usan la cookie, `/auth/refresh` y `/auth/logout`, quedan cubiertas por `SameSite=Strict` y por la cabecera obligatoria `X-Requested-With` (cualquier valor no vacío; su ausencia da 403 `CABECERA_REQUERIDA`). Una cabecera personalizada obliga a un preflight CORS que solo pasa para el origen permitido. `csrf` de Spring queda desactivado.
- **Requisito de despliegue**: el front y la API deben ser *same-site* (mismo dominio registrable, p. ej. `app.dominio.com` y `api.dominio.com`); con otro sitio, `SameSite=Strict` no enviaría la cookie. En desarrollo, `localhost:5173` y `localhost:8080` lo son.

### 10.2 Contraseñas, bloqueo y límites
- **BCrypt costo 12** (`BCryptPasswordEncoder(12)`). Si el correo no existe, se compara contra un **hash ficticio** del mismo costo, calculado al arrancar, para igualar el tiempo.
- **Bloqueo por intentos, por pareja (IP, correo)** [SUPUESTO, 5 fallos y 15 min de la versión anterior]. Tabla `intento_login` (V16): `ip`, `correo_hash` (SHA-256 del correo normalizado; no se guardan correos), `fallos`, `ultimo_fallo_en`, `bloqueado_hasta`, única por (`ip`, `correo_hash`). Cada fallo se cuenta igual **exista o no la cuenta** (o esté inactiva: se compara contra el hash ficticio, mismo tiempo y misma respuesta 401). Al quinto fallo se bloquea **solo esa pareja** 15 min: otra IP con la clave correcta entra, y un anónimo ya no puede dejar fuera al administrador. Pareja bloqueada: 429 `INTENTOS_EXCEDIDOS` con `Retry-After`, **sin verificar la clave** pero tras ejecutar una comparación BCrypt contra el hash ficticio (tiempo comparable); aparece igual para correos existentes e inexistentes, así que no revela nada. Un login correcto borra la fila de la pareja. Fallos viejos (último hace más de 15 min) o un bloqueo vencido reinician el contador. Persistido: sobrevive a reinicios. Las columnas `administrador.intentos_fallidos` y `bloqueado_hasta` quedan sin uso.
- **Tope global por cuenta** (`app.seguridad.tope-global-fallos`, 50): si la suma de fallos de todas las IP para un mismo correo en la ventana de 15 min llega al tope, solo se **audita** `BLOQUEO_CUENTA` (`entidad_ref` `LOGIN-GLOBAL`, sin correo ni clave). No se rechaza ninguna clave correcta ni se retrasa la respuesta (un `sleep` ataría hilos y sería un vector de DoS): el administrador legítimo nunca queda fuera. El bloqueo de pareja se audita con `entidad_ref` `LOGIN-PAR`. La tarea programada de limpieza borra a diario las filas de `intento_login` con último fallo de más de 1 día.
- **Límite por IP** (`RateLimitFilter`, ventana fija en memoria, `request.getRemoteAddr()`; tras el proxy `server.forward-headers-strategy=native`). La decisión usa la **ruta normalizada** (`RutaSolicitud`: decodificada, sin `;params`, sin barras repetidas ni final, minúsculas), de modo que `/auth/%6cogin`, `/auth/login/`, `/AUTH/LOGIN` o `;jsessionid` reciben el mismo límite que la ruta normal. `POST /auth/login` 20 por 15 min; todo otro `POST` bajo `/api/v1/auth/` (`refresh`, `logout` y rutas desconocidas) comparte el límite de 60 por minuto; `POST /reservas` 10 por minuto [SUPUESTO; el 10/min es el valor anterior]. 429 `DEMASIADAS_SOLICITUDES` con `Retry-After`. Configurables en `app.rate-limit.*`. Con `StrictHttpFirewall`, las variantes con `%2f`, `%3b`, `//` literal, etc. se rechazan con 400 antes de llegar al filtro.
- **Tope diario de reservas por IP (H4, RN-23)** (`TopeReservasPorIp`, usado por `RateLimitFilter`): 20 solicitudes a `POST /reservas` por IP en una ventana fija de 24 horas que empieza con la primera solicitud (`app.rate-limit.reservas-por-ip-por-dia`, 20), contando todas, también las que fallan por validación o por el límite por minuto, que se conserva. La IP sale de la misma fuente (`getRemoteAddr()`, con `forward-headers-strategy=native` en prod) y la ruta normalizada. Al pasar el tope: 429 `DEMASIADAS_SOLICITUDES` con `Retry-After` en segundos hasta el fin de la ventana y el mismo texto genérico (sin eco). **Alerta única por IP y ventana**: una línea WARN "ALERTA: la IP <hash> superó 20 solicitudes de reserva en 24 horas" y una fila de auditoría `ALERTA_RESERVAS_IP` (`administrador_id` NULL, entidad `RESERVA`, `entidad_ref` `IP-<hash>`, detalle `limite=20/24h`). Como las IP no se registran en otros logs, se usa un hash corto (8 hex de SHA-256) y no la IP. Estado en memoria con el `Clock` inyectable; las entradas vencidas se limpian como máximo cada 10 minutos (no en cada solicitud). Si falla la auditoría, el 429 se responde igual.
- **Tope del cuerpo** (`LimiteCuerpoFilter`, `app.limites.max-cuerpo-bytes`, 64 KB = 65536): 413 `CUERPO_DEMASIADO_GRANDE` ("La solicitud es demasiado grande.", sin eco). Con `Content-Length` mayor se rechaza sin leer; sin él se cuenta mientras se lee. Caddy aplica el mismo `request_body { max_size 64KB }` en prod. Bean Validation limita la entrada: `extras` máx. 3 elementos, textos libres máx. 500 y códigos/identificadores máx. 100 caracteres (400 `DATOS_INVALIDOS`; son topes holgados para que un valor apenas excedido siga dando el error de regla 422). Los validadores de persona comprueban la longitud **antes** de aplicar expresiones regulares.
- **Secretos** (`ValidadorSecretos`): `JWT_SECRET`, `ADMIN_PASSWORD`, `HEALTH_ENCRYPTION_KEY` y `DB_PASSWORD` no pueden contener "cambiar" (sin distinguir mayúsculas) ni ser los valores de ejemplo antiguos del `.env.example`. En `prod` impide el arranque y además exige `JWT_SECRET` >= 48 caracteres y `ADMIN_PASSWORD` presente y >= 12; en otros perfiles solo registra una advertencia fuerte. El mensaje nombra la variable, nunca el valor. El `.env.example` trae los secretos vacíos.
- **Administrador inicial**: `ApplicationRunner` crea el administrador si la tabla está vacía y existen `ADMIN_EMAIL` y `ADMIN_PASSWORD` válidos (clave de 8 a 128 caracteres sin espacios en los extremos). Si faltan o son inválidos: WARN sin el valor y no crea nada. Sin valores por defecto. El correo se guarda en minúsculas.

### 10.3 CORS y cabeceras
- **CORS**: `CorsConfigurationSource` con orígenes **exactos** de `CORS_ORIGIN` (lista separada por comas). `allowCredentials = true`; métodos GET, POST, PUT, OPTIONS; cabeceras `Content-Type`, `Authorization`, `X-Requested-With`; expone `Retry-After`. La app no arranca si `CORS_ORIGIN` está vacío o contiene `*` (con credenciales no se admite comodín).
- **Cabeceras** (`CabecerasSeguridadFilter`): `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `X-Frame-Options: DENY`, `Cache-Control: no-store` en `/api/v1/admin/**` y `/api/v1/auth/**` (decidido con la ruta normalizada, igual que el límite de frecuencia). Caddy (solo prod) añade `Strict-Transport-Security: max-age=31536000; includeSubDomains`, `X-Content-Type-Options`, `Referrer-Policy`, `X-Frame-Options`, `Permissions-Policy`, quita `Server` y limita el cuerpo a 64 KB.
- **Reglas de acceso**: permitAll para `GET /api/v1/config`, `GET /api/v1/disponibilidad`, `POST /api/v1/reservas`, `/api/v1/auth/**`, `GET /actuator/health` y springdoc solo en `dev`. `/api/v1/admin/**` exige `hasRole("ADMIN")`. Todo lo demás `denyAll`. 401 y 403 en ProblemDetail con handlers propios.
- **Entrada**: consultas solo por JPA/parámetros; el servidor nunca confía en total, estado, `guardianAuthorized`, ids ni `acceptedAt` del cliente.

### 10.4 Cifrado de salud
- **Algoritmo**: AES-256-GCM, IV aleatorio de 96 bits por valor, etiqueta de 128 bits. Valor guardado: `<version>:<base64(iv || texto cifrado || etiqueta)>`, p. ej. `1:AbC...`.
- **Clave**: `HEALTH_ENCRYPTION_KEY` = `<version>:<base64 de 32 bytes>` (p. ej. `1:...`). La versión entra en el prefijo del dato, de modo que una rotación futura es posible: `HEALTH_ENCRYPTION_KEY_ANTERIORES` (lista opcional, `version:base64` separadas por coma) permite descifrar valores viejos mientras se reescriben con la clave actual [SUPUESTO]. Sin clave válida, la app no arranca.
- **Implementación**: `CifradoSaludConverter` (`AttributeConverter<String, String>`, `autoApply = false`) en los tres campos de detalle de `FichaSalud`. Los nulos no se cifran. Al no tener acceso a la fila, el converter no usa datos asociados (AAD) [aceptado].
- Las banderas `tiene_*` y `practica_deporte` van en claro (indican que hay un dato, no cuál).
- **Alcance de RN-19 relajada**: la API devuelve la ficha descifrada solo en `GET /admin/reservas` y en las respuestas de escritura de admin y de `POST /reservas`. Nada de salud en: logs (sin `show-sql`, sin trazas de binding, `toString()` enmascarado en DTO y entidades, handler que nunca registra valores rechazados ni cuerpos), auditoría, mensajes de error ni cabeceras. Prueba obligatoria con `OutputCaptureExtension`.

## 11. Errores (ProblemDetail)
`spring.mvc.problemdetails.enabled=true`. `GlobalExceptionHandler` (extiende `ResponseEntityExceptionHandler`) agrega siempre `code` y, según el caso, `campo` (ruta del campo con error, p. ej. `companion.phone`) o `campos` (solo login). `detail` en español, **idéntico al mensaje del front**; el 500 no expone detalles. `DataIntegrityViolationException` se traduce por nombre de restricción (`uq_participante_documento_activo` → `DOCUMENTO_DUPLICADO`); otra → 500. Nunca se registra el valor rechazado.

## 12. Catálogo de errores
`{de}` = "del participante" o "del acompañante"; `{Sujeto}` = "El participante" o "El acompañante". Los textos salen de `validaciones.ts`, `telefono.ts` y `reservasApi.ts`.

### 12.1 Registro público
| HTTP | code | detail |
|---|---|---|
| 400 | DATOS_INVALIDOS | "Los datos enviados están incompletos o tienen un formato inválido." [SUPUESTO] |
| 409 | INSCRIPCIONES_CERRADAS | "Las inscripciones cerraron el {d de MMMM}." (p. ej. "9 de noviembre") |
| 422 | NOMBRE_INVALIDO | "Escribe nombres y apellidos {de} solo con letras (2 a 60 caracteres)." |
| 422 | FECHA_NACIMIENTO_INVALIDA | "Revisa la fecha de nacimiento {de}." |
| 422 | TIPO_DOCUMENTO_INVALIDO | "Elige un tipo de documento válido {de}." |
| 422 | DOCUMENTO_INVALIDO | "El documento {de} no es válido: {ayuda del tipo en minúsculas}." |
| 422 | DOCUMENTO_EDAD_INCOMPATIBLE | "La {tipo} es para personas de {n} años o más: revisa el tipo de documento o la fecha de nacimiento {de}." / "El {tipo} es para personas de hasta {n} años: revisa el tipo de documento o la fecha de nacimiento {de}." |
| 422 | GENERO_INVALIDO | "Elige el género {de}." |
| 422 | TELEFONO_INVALIDO | "Elige el país del teléfono {quien}." / "Escribe el teléfono {quien} solo con números." / "El celular {quien} no es válido para {país} (ejemplo: {ejemplo})." (para el contacto, "El teléfono ..."; `quien` = `{de}` o "del contacto de emergencia {de}") |
| 422 | CORREO_INVALIDO | "Escribe el correo electrónico {de}." / "Revisa el correo electrónico {de}." / "El correo {de} parece mal escrito: ¿quisiste decir @{dominio}?" |
| 422 | CIUDAD_INVALIDA | "Escribe la ciudad {de} solo con letras." |
| 422 | EPS_INVALIDA | "Elige la EPS {de}." / "Escribe el nombre de la EPS {de}." / "Revisa la EPS {de}." |
| 422 | CONTACTO_EMERGENCIA_INVALIDO | "Escribe nombre y apellido del contacto de emergencia {de}." / "El contacto de emergencia {de} debe ser otra persona." / "El teléfono del contacto de emergencia {de} debe ser distinto al suyo propio." / "Elige el parentesco del contacto de emergencia {de}." |
| 422 | SALUD_INVALIDA | "Describe la enfermedad {de}." / "Describe la alergia {de}." / "Describe la discapacidad {de}." / "Indica si {sujeto en minúsculas} practica deporte." |
| 422 | COMPROMISO_REQUERIDO | "{Sujeto} debe comprometerse a respetar las indicaciones y normas de las áreas protegidas." / "{Sujeto} debe comprometerse a contribuir con el cuidado del medio ambiente." |
| 422 | CONSENTIMIENTO_REQUERIDO | "Falta la aceptación de los términos y condiciones {de}." |
| 422 | TERMINOS_DESACTUALIZADOS | "Los términos cambiaron: vuelve a leerlos y acéptalos {de}." |
| 422 | USO_IMAGEN_INVALIDO | "Revisa la autorización de uso de imagen {de}." |
| 422 | ESTADIA_INVALIDA | "Elige la estadía {de}." |
| 422 | MENOR_SIN_RESPONSABLE | "El participante es menor de edad: su contacto debe ser su madre, padre o tutor legal." |
| 422 | TALLA_INVALIDA | "Elige tu talla de camisa." / "Elige la talla de camisa del acompañante." / "El acompañante sin kit no lleva talla de camisa." |
| 422 | EXTRA_INVALIDO | "Hay una actividad extra que no existe." / "Cada actividad extra se elige una sola vez." / "El parapente del acompañante solo se elige si llevas acompañante." |
| 422 | HOTEL_INVALIDO | "Ya no se puede reservar hotel antes del evento." / "Elige las fechas de llegada y salida del hotel." / "La llegada al hotel debe estar entre el {a} y el {b}." / "La salida del hotel debe ser después de la llegada." / "La salida del hotel no puede ser después del {fecha}." / "El hotel previo debe tener al menos una noche." / "Solo puedes sumar al acompañante al hotel si llevas acompañante." |
| 422 | ACOMPANANTE_REPETIDO | "El acompañante no puede tener el mismo documento que el participante." / "El acompañante debe tener un correo distinto al del participante." / "El acompañante debe tener un celular distinto al del participante." / "El contacto de emergencia del participante no puede ser el acompañante." / "El contacto de emergencia del acompañante no puede ser el participante." |
| 422 | PLAN_INVALIDO | "Elige la forma de pago." |
| 422 | FECHA_COMPROMISO_INVALIDA | "Ya pasó la fecha límite para apartar; elige pagar el total." / "Elige la fecha en que pagarás el restante." / "El pago total no lleva fecha para el restante." |
| 409 | DOCUMENTO_DUPLICADO | "El documento {documento} ya tiene una reserva registrada. Si no fuiste tú, comunícate con la organización para que la revisen." (H4; sin número de WhatsApp: el contacto vive en el front. La carrera por el índice único responde lo mismo sin el número de documento) |
| 409 | SIN_CUPO_PARAMO | "Los cupos del páramo están agotados: elige hotel en Manaure o regreso a casa." / "Solo queda {n} cupo en el páramo y tu reserva necesita {m}. Elige otra estadía para alguno de los dos." |

### 12.2 Administración
| HTTP | code | detail |
|---|---|---|
| 404 | RESERVA_NO_ENCONTRADA | "La inscripción ya no existe." |
| 404 | PAGO_NO_ENCONTRADO | "El pago ya no existe." |
| 400 | REVISION_INVALIDA | "La revisión del pago no es válida." [SUPUESTO] |
| 422 | MOTIVO_REQUERIDO | "Indica el motivo del rechazo." |
| 422 | AUTORIZACION_MENOR_PENDIENTE | "El titular es menor de edad: falta la autorización firmada por su responsable." |
| 409 | SIN_CUPO_PARA_APROBAR | "No hay cupos en el páramo para confirmar esta inscripción ({m} en páramo, {n} libres). Cambia los cupos del páramo en Gestión de cupos." |
| 409 | DOCUMENTO_DUPLICADO | (RN-18) "El documento {documento} ya tiene una reserva registrada." (para la administradora no se añade "Si no fuiste tú") |
| 422 | MONTO_INVALIDO | "Escribe un monto válido." |
| 422 | MONTO_SUPERA_SALDO | "El monto supera el saldo pendiente." |
| 400 | DATOS_INVALIDOS | (cuerpo ausente o `received` no booleano) |
| 422 | CAPACIDAD_INVALIDA | "Los cupos del páramo deben ser un número entero." / "Los cupos del páramo no pueden ser menos que los {n} ya ocupados." / "Debe haber al menos 1 cupo en el páramo." |
| 422 | FECHA_CIERRE_INVALIDA | "Elige una fecha de cierre válida." / "La fecha de cierre no puede ser anterior a la apertura de inscripciones." / "La fecha de cierre debe ser antes del evento." |

### 12.3 Autenticación y transversales
| HTTP | code | detail |
|---|---|---|
| 400 | DATOS_INVALIDOS | login: "Revisa los datos marcados." + `campos` |
| 401 | CREDENCIALES_INVALIDAS | "Correo o contraseña incorrectos." [SUPUESTO] (igual para correo inexistente, clave mala o cuenta inactiva) |
| 401 | NO_AUTENTICADO | "Inicia sesión para continuar." [SUPUESTO] |
| 401 | SESION_INVALIDA | "Tu sesión terminó. Inicia sesión de nuevo." [SUPUESTO] (refresh ausente, vencido, revocado o reutilizado) |
| 403 | ACCESO_DENEGADO | "No tienes permiso para esta acción." [SUPUESTO] |
| 403 | CABECERA_REQUERIDA | "Falta la cabecera X-Requested-With." |
| 404 | RECURSO_NO_ENCONTRADO | "El recurso no existe." |
| 413 | CUERPO_DEMASIADO_GRANDE | "La solicitud es demasiado grande." (cuerpo de más de 64 KB) |
| 409 | RECURSO_OCUPADO | "Hay otra operación en curso. Intenta de nuevo." [SUPUESTO] |
| 429 | INTENTOS_EXCEDIDOS | "Demasiados intentos. Intenta de nuevo más tarde." [SUPUESTO] (con `Retry-After`) |
| 429 | DEMASIADAS_SOLICITUDES | "Demasiadas solicitudes. Intenta de nuevo en un momento." [SUPUESTO] (con `Retry-After`; también por el tope de 20 solicitudes por IP en 24 h a `POST /reservas`, H4) |
| 500 | ERROR_INTERNO | "Ocurrió un error inesperado. Intenta de nuevo." [SUPUESTO] |

## 13. Variables de entorno
| Variable | Uso | Nota |
|---|---|---|
| DB_NAME, DB_USER, DB_PASSWORD | PostgreSQL | ya existen (HU-D1) |
| CORS_ORIGIN | orígenes exactos del front y del panel, separados por coma | dev: `http://localhost:5173` |
| JWT_SECRET | firma del access token | >= 32 bytes |
| **ADMIN_EMAIL** | correo del administrador | **reemplaza a ADMIN_USER** |
| ADMIN_PASSWORD | clave inicial | 8 a 128 caracteres |
| **HEALTH_ENCRYPTION_KEY** | clave AES-256 con versión (`1:<base64 de 32 bytes>`) | nueva; obligatoria |
| HEALTH_ENCRYPTION_KEY_ANTERIORES | claves viejas para descifrar | opcional |
| COOKIE_SECURE | atributo `Secure` de la cookie | por defecto `true` |
| APP_RATE_LIMIT_* | límites por IP | opcional; valores en §10.2 |
| RESERVAS_POR_IP_POR_DIA | `app.rate-limit.reservas-por-ip-por-dia` (H4) | por defecto 20 |
| CADUCIDAD_RESERVAS_ACTIVA, CADUCIDAD_RESERVAS_HORAS | `app.reservas.caducidad.activa` / `.horas` (H4) | por defecto `true` y 12; cron `app.reservas.caducidad.cron` (cada 10 min) |

`.env.example` se actualiza sin secretos; `.env` sigue en `.gitignore`.

## 14. Cambios necesarios en el front (solo capa de conexión)
Sin tocar pantallas ni lógica de negocio. El front sigue calculando y validando antes de enviar (mismos mensajes); el servidor repite todo y manda.
1. **Cliente HTTP** nuevo: `fetch` con `credentials: 'include'`, base `import.meta.env.VITE_API_URL`, cabecera `Authorization` desde la sesión en memoria, `X-Requested-With: XMLHttpRequest` en `/auth/refresh` y `/auth/logout`, reintento único tras 401 con un `/auth/refresh` **en vuelo único** (un solo refresh a la vez; entre pestañas, con Web Locks) y conversión de ProblemDetail a `Result` (`{ ok: false, error: detail }`). Ubicación propuesta: `shared/reservas/servicios/http.ts`, porque `reservasApi.ts` lo usan el sitio y el panel [SUPUESTO; el plan decía `frontend/src/services/api.ts`, que puede reexportarlo].
2. **`shared/reservas/servicios/reservasApi.ts`**: cada función llama a su endpoint y devuelve el mismo `Result`: `crearReserva` → `POST /reservas`; `revisarPago` → `PUT /admin/pagos/{id}/revision`; `registrarPago` → `POST /admin/reservas/{id}/pagos`; `fijarAutorizacionMenor` → `PUT .../autorizacion-menor`; `fijarCapacidad` → `PUT /admin/capacidad`; `fijarFechaCierre` → `PUT /admin/fecha-cierre`. Se elimina la latencia simulada y `restablecer` (sin contraparte).
3. **`shared/reservas/servicios/repositorio.ts`**: el estado sale de la API (`GET /admin/reservas` en el panel; `GET /disponibilidad` en el sitio público) con la misma interfaz (`read`, `subscribe`); se refresca al enfocar la pestaña, cada 15 s y tras cada escritura. Sin `localStorage` de reservas. `counters` desaparece.
4. **`admin/src/services/auth.ts`**: `iniciarSesion` → `POST /auth/login`; al cargar, `POST /auth/refresh` para restaurar la sesión; `cerrarSesion` → `POST /auth/logout`. El token vive en memoria; el resto de `Sesion` se conserva. `recordada` define la duración del refresh.
5. **`.env.example`** (sitio y panel): `VITE_API_URL=http://localhost:8080`.
6. **Disponibilidad pública**: el sitio no puede descargar todas las reservas. `ReservasState` gana el campo opcional `occupied?: number` que llena `GET /disponibilidad`; `useDisponibilidad` y `getAvailability` usan ese valor cuando no hay lista de reservas. La comprobación de documentos duplicados queda del lado del servidor (409).
7. **Textos**: `AVISO_MENOR` menciona WhatsApp; sin cambios (la autorización se envía fuera del sistema).

## 15. Pruebas
- Unitarias de `regla/` con tablas de casos tomadas de las funciones del front (cada mensaje de la sección 12 tiene un caso).
- Integración con Testcontainers: registro público feliz y cada error; cupos solo con pago aprobado; documento liberado por Rechazado; menores con y sin autorización; revisión (aprobar, rechazar con motivo, deshacer) y pago manual; capacidad y fecha de cierre; concurrencia (dos reservas por el último cupo del páramo; dos aprobaciones simultáneas; mismo documento en paralelo); autenticación completa (login, bloqueo, refresh con rotación, reutilización revoca la familia, logout, cabecera obligatoria); CORS con credenciales; rate limit; perfil `prod`; Actuator.
- Privacidad: el valor en `ficha_salud` no contiene el texto original; ningún dato de salud en los logs ni en la auditoría.
- Contrato: cargas JSON generadas del front (`buildNewRegistration`) aceptadas y respuestas que cumplen `Registration`.

## 16. Decisiones y supuestos pendientes de confirmar
Cada punto es una pregunta concreta. El diseño asume la respuesta marcada con *por defecto*.

1. **Acompañante menor de edad.** El front solo exige responsable y autorización para el *titular* menor. *Por defecto*: se permite un acompañante menor sin autorización ni responsable adicional. ¿Es correcto?
2. **Edad de "menor".** El front evalúa a la fecha de hoy, no al día del evento. *Por defecto*: hoy. ¿O al 15 de noviembre?
3. **Reactivar una inscripción Rechazada** (aprobar o devolver un pago, o registrar un pago manual) cuando otro ya tomó su documento. *Por defecto*: 409 `DOCUMENTO_DUPLICADO` (RN-18). ¿Correcto?
4. **Dominio en producción.** `SameSite=Strict` exige que el front y la API compartan dominio registrable (`app.dominio.com` y `api.dominio.com`). ¿Cuál será el dominio del sitio, del panel y de la API? ¿En qué origen corre el panel `/admin` en desarrollo (¿el mismo 5173?) para `CORS_ORIGIN`?
5. **Precios.** El front lee precios de `shared/config` (estáticos). *Por defecto*: el servidor es la fuente y `GET /config` los expone; el front conserva el estático solo como respaldo. Si cambian los precios solo en el servidor, el total que muestra el front antes de enviar podría diferir hasta que lea `GET /config`. ¿Quieres que el front lea `GET /config`?
6. **Cambio de precios, fechas del evento y versión de términos**: *por defecto* solo por migración (el panel cambia únicamente cupos y fecha de cierre). ¿Se necesita editarlos desde el panel?
7. **Valores iniciales** de la migración = `shared/config` (evento 15 y 16 de noviembre de 2026; apertura 2026-10-28; cierre 2026-11-09; 60 cupos; términos `TYC-2026-10-28`). ¿Son definitivos?
8. **Sesión sin "recordar"**: *por defecto* 8 horas de refresh. ¿O que termine al cerrar el navegador (sin `Max-Age`) con tope de 8 horas?
9. **Varias pestañas del panel**: el refresh de un solo uso con detección de reutilización cierra la sesión si dos pestañas rotan a la vez. *Por defecto*: el front serializa el refresh (Web Locks) y no hay ventana de gracia en el servidor. ¿Aceptas, o prefieres una gracia de 10 s?
10. **Bloqueo y límites**: 5 fallos / 15 min de bloqueo por cuenta; login 20 por 15 min por IP; refresh 60/min por IP; registro 10/min por IP. ¿Valores correctos?
11. **Lista de reservas sin paginación** y ordenada por código descendente (volumen de cientos). ¿Aceptas?
12. **Autorización de menor** sobre un titular mayor de edad: *por defecto* se acepta (el front no lo impide). ¿Debe responder 422?
13. **Monto aprobado distinto del esperado**: el servidor no bloquea la aprobación (el front solo lo muestra con `getExpectedAmount`). ¿Confirmas?
14. **Motivo de rechazo**: máximo 200 caracteres y texto libre (no restringido a la lista de motivos). ¿Correcto?
15. **Documento único solo por número** (sin tipo), como el front. ¿O por tipo + número?
16. **Mismo correo o celular en dos inscripciones distintas**: *por defecto* permitido (el front solo lo compara dentro de la reserva). ¿Correcto?
17. **Nombre del administrador**: *por defecto* derivado del correo (`nombreDesdeCorreo`). ¿Prefieres una variable `ADMIN_NOMBRE`?
18. **Rotación de la clave de cifrado**: se soporta con `HEALTH_ENCRYPTION_KEY_ANTERIORES`. ¿Necesitas un comando o endpoint de recifrado, o basta con el procedimiento manual en el README?
19. **Retención y borrado de datos** (Ley 1581): no hay borrado ni anonimización en esta versión. ¿Hay plazo de retención o solicitud de supresión que cubrir?
20. **Texto legal** de términos y descargo: lo entrega la organización.
21. **Cliente HTTP compartido** en `shared/reservas/servicios/http.ts` en lugar de `frontend/src/services/api.ts` (el plan aprobado decía este último). ¿Aceptas el cambio?
22. **Campos adicionales en la respuesta** (`total` en `Registration`, `today` y `fechaCierre` en las listas): aditivos, sin romper el tipo. ¿Aceptas `total`?

## 17. Decisiones del usuario sobre las dudas pendientes
- **Alcance inmediato: solo local.** No se trabaja producción ni dominios (HU-D2 ya existe y queda como está; el VPS será de Hostinger y se decide después). Primero: formulario -> API REST -> base de datos -> panel admin -> cambios del admin reflejados. En desarrollo: Vite en `http://localhost:5173` y API en `http://localhost:8080` (misma "site"; `CORS_ORIGIN=http://localhost:5173`).
- Pregunta 1: un acompañante menor se acepta con las reglas generales, igual que el front (solo el titular menor exige responsable y autorización).
- Pregunta 5/6/21/22: precios estáticos en el front (`shared/config`) con los mismos valores sembrados en la base; solo cupos y fecha de cierre se editan desde el panel.
- Resto (2-4, 7-20): se aplica la opción por defecto indicada en cada punto del §16.
