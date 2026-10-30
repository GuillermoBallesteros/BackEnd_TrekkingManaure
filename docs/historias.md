# Historias de usuario: backend de reservas (carrera de Manaure)

Versión 2: el backend se ajusta al front. Reglas, datos, precios y estados: `docs/flujo-app.md` (RN-xx). Diseño técnico, esquema y contrato JSON exacto: `docs/diseno.md` (secciones citadas como "diseño §n"). La versión anterior está en `docs/anterior/`.

- Las rutas y los nombres JSON son los del diseño §6 (tipos del front en inglés). Los `code` y estados HTTP, los del diseño §12; el `detail` es el mensaje en español del front.
- Cada historia incluye sus pruebas: una por criterio.
- Orden, una historia por sesión: HU-D1 (hecha), HU-N01 a HU-N14 y al final HU-D2 (hecha). HU-N15 (avisos por correo) se añadió después, el 2026-10-28.
- Una historia no empieza hasta que la anterior pasa `./mvnw.cmd test`. Al terminar se informa el resultado real o "no verificado".

## Resumen de cambios respecto a la versión anterior

| Historia anterior | Destino | Motivo |
|---|---|---|
| HU-D1 Entorno Docker | Se mantiene, implementada | Sin cambios de alcance. Solo la variable `ADMIN_USER` pasa a `ADMIN_EMAIL` (HU-N05) |
| HU-00 Configuración del evento | Reemplazada por HU-N03 | El front lee `evento`/`precios` y la disponibilidad del páramo |
| HU-01 Cotizar | **Eliminada** | El front calcula el total con `cotizacion.ts`; el servidor recalcula al registrar. Sin contraparte |
| HU-02 Registrar inscripción | Reemplazada por HU-N04 | Nuevo modelo: ciudad, EPS, emergencia, salud, compromisos, consentimiento, estadía, hotel, plan |
| HU-03 Login | Reemplazada por HU-N05 y HU-N06 | Correo, access de 15 min y refresh rotatorio en cookie |
| HU-04 Listar y filtrar / HU-05 Detalle | Reemplazadas por HU-N07 | El panel recibe todas las reservas completas y filtra en el cliente |
| HU-06 Registrar abono | Reemplazada por HU-N08 y HU-N09 | El pago nace "Por revisar" y se aprueba, o se registra manual |
| HU-07 Anular abono | **Eliminada** | El front no anula pagos: rechaza con motivo; "Deshacer" justo después lo regresa a "Por revisar" (HU-N08) |
| HU-08 Anular inscripción | **Eliminada** | El estado derivado "Rechazado" libera cupo y documento |
| HU-09 Corregir persona | **Eliminada** | El front no edita personas; sin contraparte |
| HU-10 Resumen | **Eliminada** | El panel calcula cifras en el cliente a partir de la lista |
| HU-11 Exportar CSV | **Eliminada** | El panel arma la exportación en el cliente (`exportar/libro`) |
| HU-12 Editar configuración | Reducida a HU-N11 (capacidad) y HU-N12 (fecha de cierre) | Es lo único editable en el panel |
| (nuevo) | HU-N01, HU-N02, HU-N10, HU-N13, HU-N14 | Esquema, reglas, autorización de menores, conexión del front, integración |
| HU-D2 Producción | Se mantiene, implementada | |
| (nuevo, 2026-10-28) | HU-N15 | Avisos por correo con Brevo (RN-24) |

---

## HU-D1 Entorno de desarrollo con Docker (implementada)
Como desarrollador quiero levantar la API y PostgreSQL con un solo comando para que todo funcione igual en mi equipo y en el servidor.

**Estado:** implementada y aprobada. No se modifica. Nota: la variable `ADMIN_USER` la reemplaza `ADMIN_EMAIL` en HU-N05, y se añaden `JWT_SECRET` (ya existente) y `HEALTH_ENCRYPTION_KEY` (HU-N01).

**Alcance:** solo el entorno; sin lógica de negocio. Incluye añadir la dependencia de Spring Boot Actuator.

Criterios:
- `Dockerfile` de varias etapas: compila con el wrapper de Maven y ejecuta con un JRE; usuario sin privilegios; versión fija de las imágenes base (nunca `latest`).
- `.dockerignore` que excluye target, .git, .env y archivos del IDE.
- `docker-compose.yml` con dos servicios: `db` (imagen `postgres:18`, la misma versión mayor que el PostgreSQL instalado en el equipo; volumen con nombre montado en `/var/lib/postgresql`, que es la ruta correcta desde la versión 18 y no `/var/lib/postgresql/data`; y healthcheck) y `api` (arranca solo cuando `db` está saludable). En este archivo la base de datos no publica puertos.
- `docker-compose.override.yml` para desarrollo: publica la base de datos solo en 127.0.0.1:5433, para usar un cliente SQL o el IDE. Se usa 5433 porque el puerto 5432 del equipo ya lo ocupa el PostgreSQL instalado en Windows. Dentro de la red de Docker la API sigue usando `db:5432`.
- Variables por `.env` (DB_NAME, DB_USER, DB_PASSWORD, ADMIN_USER, ADMIN_PASSWORD, CORS_ORIGIN). `.env.example` sin secretos y `.env` en `.gitignore`.
- `application.yml` lee todo de variables de entorno. Perfil `dev` por defecto; el perfil `prod` se completa en HU-D2.
- `docker compose up --build` deja la API respondiendo en `http://localhost:8080/actuator/health` con estado UP. Solo ese endpoint de Actuator queda expuesto.
- Flyway se ejecuta al arrancar; la aplicación arranca aunque todavía no haya migraciones de negocio.
- Los datos sobreviven a `docker compose down` (sin la opción `-v`). Se comprueba creando un dato, ejecutando `docker compose down` y `docker compose up`, y verificando que el dato sigue ahí.
- Se puede trabajar con solo la base en contenedor: `docker compose up db` más la API desde el IDE o con `.\mvnw.cmd spring-boot:run`.
- Las pruebas con Testcontainers funcionan con Docker Desktop en marcha (`.\mvnw.cmd test`).
- El README explica: requisitos (Docker Desktop con WSL2 en Windows), cómo arrancar, detener, ver logs y reiniciar la base desde cero.

---

## HU-N01 Esquema, catálogos y cifrado de salud
Como equipo técnico quiero el esquema normalizado del nuevo modelo y la retirada del anterior para construir las reglas sobre una base correcta.

**Alcance:** migraciones V9 a V15 (diseño §3 y §4), entidades y repositorios, catálogos con caché, cifrado de salud y retirada del código y las pruebas de lo eliminado (anular inscripción, abonos, corregir persona, resumen, CSV, cotización, configuración editable). Sin endpoints nuevos. Las V1 a V8 no se editan.

Criterios:
- Flyway aplica V9 a V15 sobre una base con V1 a V8 sin error; `ddl-auto=validate` arranca la app.
- V9 elimina `auditoria`, `abono`, `ficha_salud`, `persona`, `inscripcion`, `administrador`, `configuracion_evento` y `inscripcion_codigo_seq`.
- Los catálogos quedan sembrados: 6 tipos de documento con patrón y edades (CC 18+, TI 7 a 18, RC hasta 7), 4 géneros, 7 tallas, 3 estadías (solo `Páramo` con `ocupa_cupo`), 3 extras (solo `parapenteAcompanante` con `requiere_acompanante`), 25 EPS (solo `Otra` con `pide_nombre`), 11 parentescos (3 con `es_responsable_legal`) y los 13 países con sus reglas de móvil y fijo.
- `configuracion_evento` tiene una sola fila con los valores iniciales del diseño §3.3 y rechaza una segunda fila (`id = 1`).
- Un `INSERT` en `inscripcion_participante` con rol TITULAR y `lleva_kit = false` falla por restricción.
- Un participante con `lleva_kit = true` sin talla, o con `lleva_kit = false` y talla, falla por restricción.
- Dos `inscripcion_participante` con el mismo `documento_activo` fallan por `uq_participante_documento_activo`; dos con `documento_activo` nulo coexisten.
- Un pago `RECHAZADO` sin `motivo_rechazo` falla; un pago `POR_REVISAR` con `revisado_en` falla.
- `inscripcion` con plan `APARTADO` sin fecha compromiso, o `TOTAL` con fecha, falla por restricción.
- `ficha_salud` con `tiene_enfermedad = true` y detalle nulo falla por restricción.
- Las secuencias `inscripcion_codigo_seq` y `pago_codigo_seq` producen 1, 2, 3... y el servicio las formatea como `PER-0001` y `PAG-0001`.
- Guardar una `FichaSalud` con alergia "Penicilina" deja en la columna `alergias_cifrada` un valor con formato `1:<base64>` que no contiene "Penicilina"; al leerla, la entidad devuelve "Penicilina".
- El mismo texto cifrado dos veces produce valores distintos (IV aleatorio).
- Un valor cifrado con la clave de versión anterior se descifra si está en `HEALTH_ENCRYPTION_KEY_ANTERIORES`.
- La app no arranca si `HEALTH_ENCRYPTION_KEY` falta, no tiene el formato `version:base64` o no decodifica a 32 bytes.
- `FichaSalud` y su DTO tienen `toString()` sin valores (prueba con una ficha con datos).
- Los paquetes y pruebas de abonos, resumen, exportación y cotización ya no existen; `./mvnw.cmd test` pasa.
- `.env.example` incluye `HEALTH_ENCRYPTION_KEY` y `COOKIE_SECURE` sin valores reales.

---

## HU-N02 Reglas de negocio y precios (sin endpoints)
Como equipo técnico quiero portar las reglas del front a Java puro para que el servidor valide y calcule exactamente igual.

**Alcance:** paquete `regla/` (diseño §2, §5 y §8): validadores de persona, documento, teléfono por país, correo, nombres, EPS, contacto de emergencia, salud, compromisos, consentimiento, extras, hotel, plan y fechas; `CalculadoraPrecios`; `EstadoInscripcion`; cálculo de cupos y ventana de hotel. Funciones puras que devuelven el primer error con `code`, `detail` y `campo`.

Criterios:
- Total: titular solo = 350000; titular + acompañante sin kit = 500000; con kit = 700000.
- Total con cuatrimoto = +150000; con `parapente` = +200000; con `parapenteAcompanante` = +200000.
- Hotel: 2 noches, 1 persona = 90000; 2 noches con `withCompanion` y acompañante = 180000; `withCompanion` sin acompañante cuenta 1 persona.
- Ejemplo del flujo §7 nº 3 = 1100000; nº 4 (titular + acompañante sin kit + hotel 2 noches x 2) = 680000.
- Pago inicial: `apartado` = `min(50000, total)` con concepto `SEPARACION_CUPO`; `total` = total con `PAGO_TOTAL`.
- Estado derivado: sin pagos = Por verificar; un pago `POR_REVISAR` = Por verificar; todos `RECHAZADO` = Rechazado; aprobado menor al total sin fecha compromiso o con fecha >= hoy = Saldo pendiente; con fecha < hoy = Vencido; aprobado >= total = Pagado; aprobado > 0 con otro pago `POR_REVISAR` = Saldo pendiente.
- Cupos: la carrera cuenta reservas activas; el páramo cuenta personas con estadía `Páramo` de reservas activas, aunque su pago siga `POR_REVISAR` (RN-04).
- Documentos (parametrizado por los 6 tipos): CC de 5 dígitos inválida con "El documento del participante no es válido: entre 6 y 10 dígitos."; TI con persona de 6 años y RC con persona de 8 años producen `DOCUMENTO_EDAD_INCOMPATIBLE` con el texto del front; Pasaporte alfanumérico de 5 a 20 es válido.
- Teléfonos: un caso válido y uno inválido por cada uno de los 13 países; `fijo` válido solo para el contacto de emergencia en los países que lo admiten; México, Estados Unidos, Argentina y República Dominicana rechazan fijo.
- Nombres: "María José" y "D'Angelo" válidos; "Ana2" y un nombre de 61 caracteres inválidos con `NOMBRE_INVALIDO`.
- Correo: "ana@gmial.com" produce "El correo del participante parece mal escrito: ¿quisiste decir @gmail.com?".
- EPS: `Otra` sin nombre da "Escribe el nombre de la EPS del participante."; `Nueva EPS` con nombre no nulo da "Revisa la EPS del participante.".
- Contacto de emergencia: mismo nombre que la persona, mismo teléfono que la persona y parentesco fuera de catálogo dan cada uno su mensaje.
- Menor titular (17 años a hoy) con parentesco "Amigo o amiga" da `MENOR_SIN_RESPONSABLE`; con "Madre", "Padre" o "Tutor legal o acudiente" pasa.
- Acompañante: con kit sin talla y sin kit con talla dan `TALLA_INVALIDA`; mismo documento, correo o celular que el titular dan `ACOMPANANTE_REPETIDO`.
- Extras repetidos, desconocidos o `parapenteAcompanante` sin acompañante dan `EXTRA_INVALIDO`.
- Hotel: ventana hoy 2026-10-28 → llegada 2026-10-28 a 2026-11-27, salida máxima 2026-11-28; llegada 2026-11-28 da "La llegada al hotel debe estar entre el 28 de octubre y el 27 de noviembre."; salida igual a llegada, 0 noches y `withCompanion` sin acompañante dan sus mensajes.
- Plan: `apartado` con fecha < hoy, > cierre o ausente da "Elige la fecha en que pagarás el restante."; `apartado` con primer día > cierre da "Ya pasó la fecha límite para apartar; elige pagar el total."; `total` con fecha da "El pago total no lleva fecha para el restante.".
- Orden: con varios errores a la vez, se devuelve el primero según el diseño §8.
- Cada mensaje de `validaciones.ts` y `telefono.ts` listado en el diseño §12.1 tiene al menos un caso de prueba.

---

## HU-N03 Configuración y disponibilidad públicas
Como participante quiero ver los datos del evento, los precios, los cupos de la carrera y los cupos del páramo para decidir mi reserva.

**Rutas:** `GET /api/v1/config` y `GET /api/v1/disponibilidad` (públicas).
**Salida:** diseño §6.2.

Criterios:
- `GET /config` con la configuración inicial: 200 con `evento.nombre` "Trekking Desafío Perijá 13K", `evento.fechas` `["2026-11-28","2026-11-29"]`, `fechaAperturaInscripciones` "2026-10-28", `fechaCierre` "2026-11-09".
- `precios`: `total` 350000, `inscripcion` 50000, `kitCompleto` 300000, `acompanante` 150000, `acompananteConKit` 350000, `cuatrimoto` 150000, `parapente` 200000, `hotelNoche` 45000, `moneda` "COP".
- `versionTerminos` es "TYC-2026-10-28".
- `GET /disponibilidad` sin reservas: `capacity` 60, `occupied` 0, `available` 60 (carrera), `paramo` `{ capacity, occupied: 0, available }`, `fechaCierre` "2026-11-09", `today` con la fecha de Bogotá.
- Una reserva con titular en Páramo y solo un pago `Por revisar` ya aparta: `occupied` 1 y `paramo.occupied` 1.
- Con acompañante en Páramo, la reserva gasta 1 cupo de la carrera y 2 cupos; con acompañante en Hotel en Manaure, 1 y 1.
- Estadías `Hotel en Manaure` y `Regreso a casa` gastan cupo de la carrera pero no cambian `paramo.occupied`.
- Una reserva inactiva (Rechazada) no cuenta en ninguno de los dos.
- `available` = `max(0, capacity - occupied)` en cada contador; nunca negativo.
- Ambas rutas responden sin autenticación y no incluyen datos de personas ni de administradores.
- `today` usa el reloj de America/Bogota (prueba con `Clock` fijo a las 23:30 de Bogotá, ya día siguiente en UTC).

---

## HU-N04 Registro público de una reserva
Como participante quiero enviar mis datos y los de mi acompañante para quedar reservado, a la espera de que la encargada verifique mi pago.

**Ruta:** `POST /api/v1/reservas` (pública). **Entrada:** `NewRegistration` (diseño §6.1). **Salida:** 201 con `Registration` completa. **Reglas:** RN-01 a RN-13 y RN-20; orden de validación del diseño §8.

Criterios de éxito:
- Titular solo, datos válidos, plan `total`: 201; `id` "PER-0001"; `createdAt` = hoy (Bogotá); `guardianAuthorized` false; un pago `{id:"PAG-0001", concept:"Pago total", amount:350000, review:"Por revisar", rejectionReason:null}`; `total` 350000.
- Titular + acompañante sin kit + cuatrimoto, plan `apartado` con fecha compromiso válida: 201; pago `Separación de cupo` de 50000; `total` 650000.
- Con hotel previo de 2 noches con acompañante: el `total` incluye 180000.
- El total y el estado enviados por el cliente se ignoran; `consent.acceptedAt` y `companion.consent.acceptedAt` los fija el servidor.
- Se guardan los precios aplicados; un cambio de precios posterior no altera el `total` de esta reserva (RN-03).
- El código sale de la secuencia de la base de datos; dos reservas seguidas reciben `PER-0001` y `PER-0002`.
- La respuesta devuelve `health`, `phone`, `eps`, `emergencyContact`, `commitments` y `consent` con la forma del tipo `Registration`.
- Titular menor de edad con contacto "Madre": 201 (RN-08); la reserva nace con `guardianAuthorized` false.
- Una reserva con estadía Hotel en Manaure o Regreso a casa se acepta aunque los cupos del páramo estén agotados, si queda cupo en la carrera.
- La ficha de salud queda cifrada en la base (HU-N01).

Criterios de error (mensaje y `code` del diseño §12.1):
- JSON mal formado, objeto obligatorio ausente o tipo incorrecto: 400 `DATOS_INVALIDOS`.
- Hoy > fecha de cierre: 409 `INSCRIPCIONES_CERRADAS` con "Las inscripciones cerraron el {d de MMMM}.".
- Un caso por cada código 422 del diseño §12.1, para titular y para acompañante: `NOMBRE_INVALIDO`, `FECHA_NACIMIENTO_INVALIDA`, `TIPO_DOCUMENTO_INVALIDO`, `DOCUMENTO_INVALIDO`, `DOCUMENTO_EDAD_INCOMPATIBLE`, `GENERO_INVALIDO`, `TELEFONO_INVALIDO`, `CORREO_INVALIDO`, `CIUDAD_INVALIDA`, `EPS_INVALIDA`, `CONTACTO_EMERGENCIA_INVALIDO`, `SALUD_INVALIDA`, `COMPROMISO_REQUERIDO`, `CONSENTIMIENTO_REQUERIDO`, `TERMINOS_DESACTUALIZADOS`, `USO_IMAGEN_INVALIDO`, `ESTADIA_INVALIDA`, `MENOR_SIN_RESPONSABLE`, `TALLA_INVALIDA`, `EXTRA_INVALIDO`, `HOTEL_INVALIDO`, `ACOMPANANTE_REPETIDO`, `PLAN_INVALIDO`, `FECHA_COMPROMISO_INVALIDA`.
- La respuesta de error indica el campo con error en `campo` (por ejemplo `companion.phone.number`).
- Documento de titular o acompañante ya presente en una inscripción activa (Por verificar, Saldo pendiente, Vencido o Pagado): 409 `DOCUMENTO_DUPLICADO` con "El documento {documento} ya tiene una reserva registrada. Si no fuiste tú, comunícate con la organización para que la revisen." (hallazgo H4, ampliado; el panel conserva el mensaje original).
- Nota H4: `POST /api/v1/reservas` tiene además un tope de 20 solicitudes por IP en 24 horas (RN-23, 429 con `Retry-After` y alerta) y las reservas sin pago aprobado cuyo pago lleva 12 horas en `Por revisar` se rechazan solas (RN-22, motivo "No se verificó el pago dentro de 12 horas."); si el dinero sí llegó, la encargada reactiva la reserva con un pago manual (HU-N09).
- Documento presente solo en una inscripción Rechazada: 201 (RN-06).
- Documento escrito en minúsculas ("ab12345") frente a otro en mayúsculas ("AB12345"): se detecta como duplicado.
- Personas en Páramo mayores que los cupos disponibles: 409 `SIN_CUPO_PARAMO`; con 0 libres, el mensaje de agotados; con 1 libre y 2 en Páramo, "Solo queda 1 cupo en el páramo y tu reserva necesita 2. ...".
- Carrera llena: 409 `CARRERA_AGOTADA` con "Ya no hay cupos disponibles para la carrera. Será en una siguiente oportunidad.".
- Reservar aparta los cupos (RN-04) bajo el bloqueo global: dos solicitudes simultáneas con documentos distintos por el último cupo del páramo, o por el último cupo de la carrera, responden 201 y 409.
- Dos solicitudes simultáneas con el mismo documento: una responde 201 y la otra 409 `DOCUMENTO_DUPLICADO`.
- `plan` `apartado` con primer día elegible > cierre: 422 `FECHA_COMPROMISO_INVALIDA` ("Ya pasó la fecha límite...").
- Una reserva rechazada por validación no deja registros parciales (transacción).
- Los datos de salud, y ningún dato personal, aparecen en los logs en un registro exitoso ni en uno fallido (`OutputCaptureExtension`).
- Más de 10 solicitudes por minuto desde la misma IP: 429 `DEMASIADAS_SOLICITUDES` con `Retry-After`.

---

## HU-N05 Administrador inicial e inicio de sesión
Como administradora quiero iniciar sesión con mi correo y clave para usar el panel.

**Ruta:** `POST /api/v1/auth/login` `{correo, clave, recordar}` → `SesionResponse` + cookie `refresh_token` (diseño §6.3 y §10). Incluye el `ApplicationRunner` del administrador, el JWT de 15 minutos y el control de acceso a `/api/v1/admin/**`.

Criterios:
- Con `ADMIN_EMAIL` y `ADMIN_PASSWORD` válidos y la tabla vacía, al arrancar se crea un administrador con el correo en minúsculas y la clave con BCrypt costo 12.
- Si faltan o son inválidos (clave de menos de 8 caracteres): no se crea, se registra una advertencia sin el valor y la app arranca.
- Si ya existe un administrador, no se crea otro ni se cambia su clave.
- Credenciales válidas: 200 con `token` (JWT), `tipo` "Bearer", `correo`, `nombre` derivado del correo, `iniciadaEn`, `expiraEn` (ahora + 15 min en ms), `sesionExpiraEn`, `recordada`.
- El JWT contiene `sub` con el id, `email`, `roles` ["ADMIN"] y `exp` a 15 minutos.
- Con `recordar` true: `Set-Cookie: refresh_token=...; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Strict; Max-Age=604800`; con false: sin `Max-Age` y `sesionExpiraEn` a 8 horas.
- En la base solo existe el SHA-256 del refresh token (el valor en claro de la cookie no aparece en `sesion_refresh`).
- Correo inexistente, clave incorrecta o cuenta inactiva: 401 `CREDENCIALES_INVALIDAS` con el mismo mensaje en los tres casos.
- Correo inexistente tarda un tiempo comparable a una clave incorrecta (se compara contra el hash ficticio; la prueba verifica que se invoca el verificador).
- Correo con formato inválido, clave de menos de 8 o más de 128 caracteres, o con espacios en los extremos: 400 `DATOS_INVALIDOS` con "Revisa los datos marcados." y `campos` con el texto del front por campo.
- Cinco fallos consecutivos de una cuenta: el siguiente intento, incluso con la clave correcta, da 429 `INTENTOS_EXCEDIDOS` con `Retry-After` durante 15 minutos; pasado el plazo se puede entrar y el contador se reinicia.
- El bloqueo persiste tras reiniciar la aplicación (se guarda en `administrador`).
- Un acceso exitoso reinicia `intentos_fallidos`.
- Más de 20 intentos de login en 15 minutos desde una IP: 429 `DEMASIADAS_SOLICITUDES`.
- `GET /api/v1/admin/reservas` sin token, con token vencido o con firma inválida: 401 `NO_AUTENTICADO`; con token sin rol ADMIN: 403 `ACCESO_DENEGADO`.
- Las rutas públicas (`config`, `disponibilidad`, `POST /reservas`, `/auth/**`, `actuator/health`) no exigen token; cualquier otra ruta no declarada: denegada.
- Se audita `INICIAR_SESION` y `BLOQUEO_CUENTA` sin la clave ni el correo en `detalle`.
- La clave nunca aparece en logs.
- `JWT_SECRET` ausente o de menos de 32 bytes: la app no arranca.

---

## HU-N06 Refresh rotatorio, cierre de sesión y seguridad web
Como administradora quiero mantener la sesión de forma segura y cerrarla cuando termine.

**Rutas:** `POST /api/v1/auth/refresh` y `POST /api/v1/auth/logout` (requieren cookie y cabecera `X-Requested-With`). Incluye CORS con credenciales y cabeceras de seguridad (diseño §10.1 y §10.3).

Criterios:
- `/refresh` con cookie válida y `X-Requested-With: XMLHttpRequest`: 200 con un access nuevo y una cookie nueva distinta de la anterior.
- El refresh anterior queda marcado como usado y enlazado al nuevo (`reemplazado_por`); misma familia; `iniciadaEn` y `sesionExpiraEn` no se extienden.
- Usar un refresh ya usado: 401 `SESION_INVALIDA`, se revocan **todos** los tokens de la familia (el último refresh emitido ya no sirve) y se audita `SESION_REUTILIZADA`.
- Refresh vencido, inexistente o ausente: 401 `SESION_INVALIDA` y se borra la cookie.
- `/refresh` sin la cabecera `X-Requested-With`: 403 `CABECERA_REQUERIDA`; igual para `/logout`.
- `/logout` con cookie válida: 204, se revoca la familia, `Set-Cookie` con `Max-Age=0`; usar después ese refresh da 401.
- `/logout` sin cookie: 204 (idempotente).
- Tras `/logout` o un refresh reutilizado el access ya emitido vale hasta su vencimiento (máximo 15 minutos).
- Administrador con `activo = false`: su refresh da 401 `SESION_INVALIDA`.
- CORS: desde `Origin: http://localhost:5173` (`CORS_ORIGIN`) responde `Access-Control-Allow-Origin` exacto y `Access-Control-Allow-Credentials: true`; desde otro origen no se concede; un preflight con `X-Requested-With` y `Authorization` se acepta solo para el origen permitido.
- La app no arranca si `CORS_ORIGIN` está vacío o contiene `*`.
- Las respuestas incluyen `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `X-Frame-Options: DENY` y una `Content-Security-Policy` restrictiva; `/admin/**` y `/auth/**` llevan `Cache-Control: no-store`.
- Refresh limitado a 60 por minuto por IP: el exceso da 429 `DEMASIADAS_SOLICITUDES`.
- Los refresh vencidos hace más de 30 días se borran por la tarea programada.

---

## HU-N07 Lista de reservas del panel
Como administradora quiero ver todas las reservas con sus pagos y datos para dar seguimiento.

**Ruta:** `GET /api/v1/admin/reservas` → `{ registrations, capacity, occupied, paramo, fechaCierre, today }` (diseño §6.4).

Criterios:
- Sin token: 401 `NO_AUTENTICADO`.
- Devuelve todas las reservas (activas y Rechazadas), cada una como `Registration` completa: persona, acompañante, extras, hotel, plan, `commitmentDate`, `guardianAuthorized`, `payments` con `review` y `rejectionReason`, y `total`.
- Con dos reservas creadas: orden por `id` descendente.
- `health` (enfermedad, alergias, discapacidad y deporte) llega descifrado, con `null` en los detalles vacíos (RN-19 relajada).
- `capacity` (carrera), `paramo.capacity` y `fechaCierre` salen de la configuración; `occupied` y `paramo.occupied` se calculan como en `GET /disponibilidad`; `today` es la fecha de Bogotá.
- La respuesta cumple el tipo `Registration` del front (prueba de contrato con campos y tipos; ver HU-N14).
- Un participante sin acompañante da `companion: null`; sin hotel, `preEventHotel: null`.
- Los valores de catálogo viajan con el texto del front (`"Páramo"`, `"Hotel en Manaure"`, `"Tutor legal o acudiente"`).
- La consulta no hace N+1: con 50 reservas, el número de sentencias SQL es constante (prueba con contador de sentencias).
- Lleva `Cache-Control: no-store`.
- Los datos de salud no aparecen en logs ni en la auditoría.

---

## HU-N08 Revisión de pagos
Como administradora quiero aprobar o rechazar un pago para reflejar lo que verifiqué en Bre-B, y deshacer esa decisión justo después si me equivoqué.

El panel no tiene botón "Devolver a revisión" para pagos ya decididos: la transición a `Por revisar` solo la usa "Deshacer", en el aviso que sigue a cada decisión.

**Ruta:** `PUT /api/v1/admin/pagos/{id}/revision` `{ review, reason }` → `Registration`. **Reglas:** RN-04, RN-05, RN-06, RN-14, RN-15, RN-18.

Criterios de éxito:
- Aprobar un pago `Por revisar`: 200; el pago queda `Aprobado` con `rejectionReason` null; el estado derivado de la reserva deja de ser Por verificar.
- Pago de 350000 aprobado en una reserva de 350000: estado derivado Pagado (el front lo calcula; el servidor guarda `activa` true).
- Pago de 50000 aprobado en una de 650000: Saldo pendiente.
- Aprobar el pago de una reserva activa no cambia `GET /disponibilidad`: el cupo del páramo ya se apartó al reservar.
- Rechazar con `reason` "El pago no aparece en la llave Bre-B": 200; `review` "Rechazado", `rejectionReason` con el texto sin espacios sobrantes.
- Rechazar el único pago deja la reserva Rechazada (`activa` false) y libera el documento de sus personas: una nueva reserva con ese documento da 201.
- Volver un pago `Aprobado` a `Por revisar`: 200; `rejectionReason` null; la reserva sigue activa y conserva sus cupos.
- Volver un pago `Rechazado` a `Por revisar`: 200; el motivo se borra; la reserva vuelve a `activa` true.
- Aprobar un pago ya aprobado es idempotente: 200 sin cambios.
- Cada transición se audita (`APROBAR_PAGO`, `RECHAZAR_PAGO`, `DEVOLVER_PAGO_A_REVISION`) con el código del pago y la transición, sin el texto del motivo.
- Se guardan `revisado_por` y `revisado_en`; al volver a `Por revisar` se borran.

Criterios de error:
- Sin token: 401 `NO_AUTENTICADO`.
- `{id}` inexistente: 404 `PAGO_NO_ENCONTRADO` con "El pago ya no existe.".
- `review` ausente o distinto de los tres valores: 400 `REVISION_INVALIDA`.
- `Rechazado` sin `reason`, vacío o solo espacios: 422 `MOTIVO_REQUERIDO` con "Indica el motivo del rechazo.".
- Aprobar con titular menor de edad y `guardianAuthorized` false: 422 `AUTORIZACION_MENOR_PENDIENTE` y el pago no cambia.
- Reactivar (aprobar o devolver a `Por revisar`) una reserva Rechazada con 2 personas en Páramo y 1 cupo libre: 409 `SIN_CUPO_PARA_APROBAR` con "No hay cupos en el páramo para reactivar esta inscripción (2 en páramo, 1 libres). ...".
- Reactivar una reserva Rechazada con la carrera llena: 409 `SIN_CUPO_PARA_APROBAR` con "Ya no hay cupos en la carrera para reactivar esta inscripción. ...".
- Aprobar un pago de una reserva activa no vuelve a validar cupos (RN-05).
- Aprobar o devolver a revisión un pago de una reserva Rechazada cuyo documento ya tiene otra inscripción activa: 409 `DOCUMENTO_DUPLICADO` (RN-18).
- Rechazar, o devolver a `Por revisar` un pago de una reserva activa, nunca se bloquea por menor ni por cupo.
- Dos reactivaciones simultáneas de reservas Rechazadas por el último cupo del páramo: una responde 200 y la otra 409 `SIN_CUPO_PARA_APROBAR`.
- Dos revisiones simultáneas del mismo pago no dejan el estado de la reserva inconsistente (`activa` y `documento_activo` coinciden con el estado derivado).

---

## HU-N09 Pago manual
Como administradora quiero registrar un pago que ya verifiqué para que cuente de inmediato.

**Ruta:** `POST /api/v1/admin/reservas/{id}/pagos` `{ amount }` → 201 `Payment`. **Reglas:** RN-15, RN-16.

Criterios:
- Reserva de 350000 sin pagos aprobados y `amount` 350000: 201; `concept` "Pago total"; `review` "Aprobado"; `date` = hoy; `rejectionReason` null; `id` `PAG-xxxx` de la secuencia.
- Reserva de 650000 sin aprobados y `amount` 50000: concepto "Separación de cupo".
- Con 50000 ya aprobados y `amount` 600000: concepto "Pago de saldo"; la reserva queda Pagado.
- Se guardan `origen` MANUAL, `registrado_por` y `revisado_por`; se audita `REGISTRAR_PAGO_MANUAL` con el código y el monto.
- Si el pago reactiva una reserva Rechazada, su cupo de la carrera y sus cupos del páramo se validan como en la aprobación.
- Sin token: 401. Reserva inexistente: 404 `RESERVA_NO_ENCONTRADA` con "La inscripción ya no existe.".
- `amount` ausente, decimal, cero o negativo: 422 `MONTO_INVALIDO` con "Escribe un monto válido.".
- `amount` mayor que `max(0, total - pagado)`: 422 `MONTO_SUPERA_SALDO` con "El monto supera el saldo pendiente.". Con la reserva ya Pagado, cualquier monto lo supera.
- Titular menor sin autorización: 422 `AUTORIZACION_MENOR_PENDIENTE`.
- Reactivar una reserva Rechazada sin cupo libre en el páramo: 409 `SIN_CUPO_PARA_APROBAR`.
- Reserva Rechazada cuyo documento ya tomó otra inscripción activa: 409 `DOCUMENTO_DUPLICADO` (RN-18); si no, el pago manual la reactiva (`activa` true).
- Dos pagos manuales simultáneos que juntos superan el saldo: uno responde 201 y el otro 422 `MONTO_SUPERA_SALDO`.

---

## HU-N10 Autorización del menor
Como administradora quiero marcar que recibí la autorización firmada del responsable de un titular menor de edad.

**Ruta:** `PUT /api/v1/admin/reservas/{id}/autorizacion-menor` `{ received }` → `Registration`. **Regla:** RN-08.

Criterios:
- `received` true: 200 con `guardianAuthorized` true; luego aprobar un pago de esa reserva (HU-N08) funciona.
- `received` false: 200 con `guardianAuthorized` false; aprobar de nuevo da 422 `AUTORIZACION_MENOR_PENDIENTE`.
- Marcarla no aprueba ni cambia ningún pago.
- Se acepta sobre un titular mayor de edad sin error [SUPUESTO, diseño §16 nº 12].
- `received` ausente o no booleano: 400 `DATOS_INVALIDOS`.
- Reserva inexistente: 404 `RESERVA_NO_ENCONTRADA`.
- Sin token: 401.
- Se audita `FIJAR_AUTORIZACION_MENOR` con el código de la reserva y el valor.
- Persiste: la lista (HU-N07) devuelve el valor guardado.

---

## HU-N11 Capacidad del páramo
Como administradora quiero cambiar los cupos de la carrera y los cupos del páramo para ajustarlos a la logística.

**Ruta:** `PUT /api/v1/admin/capacidad` `{ capacity, paramo }` → `{ capacity, paramo }` (`capacity` = carrera). **Regla:** RN-17.

Criterios:
- `{ capacity: 70, paramo: 80 }` con 6 reservas activas y 12 cupos ocupados: 200; `GET /disponibilidad` muestra `capacity` 70, `available` 64, `paramo.capacity` 80 y `paramo.available` 68.
- Cada valor igual a lo ocupado: 200.
- `paramo` menor que los cupos ocupados (10 con 12): 422 `CAPACIDAD_INVALIDA` con "Los cupos del páramo no pueden ser menos que los 12 cupos ya ocupados." y `campo` `paramo`.
- `capacity` menor que las reservas activas (5 con 6): 422 `CAPACIDAD_INVALIDA` con "Los cupos de la carrera no pueden ser menos que las 6 reservas activas." y `campo` `capacity`.
- Sin ocupados y 0 o negativo: 422 con "Debe haber al menos 1 cupo en la carrera." o "Debe haber al menos 1 cupo en el páramo.".
- Decimal (10.5), ausente, nulo o texto: 422 con "Los cupos de la carrera deben ser un número entero." o "Los cupos del páramo deben ser un número entero." (se valida primero `capacity`).
- Se audita `FIJAR_CAPACIDAD` con "carrera anterior=60, nuevo=70; paramo anterior=60, nuevo=80".
- Los ocupados se calculan bajo el bloqueo de configuración: una reserva simultánea no permite dejar la capacidad por debajo de lo ocupado.
- Se guardan `actualizado_por` y `actualizado_en`; se audita `FIJAR_CAPACIDAD` con valor anterior y nuevo.
- Sin token: 401.

---

## HU-N12 Fecha de cierre
Como administradora quiero cambiar la fecha de cierre para extender o adelantar las inscripciones y el plazo del restante.

**Ruta:** `PUT /api/v1/admin/fecha-cierre` `{ fechaCierre }` → `{ fechaCierre }`. **Regla:** RN-09, RN-10.

Criterios:
- `fechaCierre` "2026-11-12": 200; `GET /config` y `GET /disponibilidad` devuelven la nueva fecha.
- Fecha igual a la apertura (2026-10-28): 200. Fecha en el pasado respecto de hoy pero >= apertura: 200 (cierra antes; las nuevas reservas dan 409 `INSCRIPCIONES_CERRADAS`).
- Fecha anterior a la apertura: 422 `FECHA_CIERRE_INVALIDA` con "La fecha de cierre no puede ser anterior a la apertura de inscripciones.".
- Fecha igual o posterior al primer día del evento (2026-11-28): 422 con "La fecha de cierre debe ser antes del evento.".
- Fecha con formato inválido ("12/11/2026") o inexistente ("2026-02-30"), o ausente: 422 con "Elige una fecha de cierre válida.".
- Tras moverla, `POST /reservas` con `apartado` acepta fechas compromiso hasta la nueva fecha y rechaza las posteriores.
- Se guardan `actualizado_por` y `actualizado_en`; se audita `FIJAR_FECHA_CIERRE` con valor anterior y nuevo.
- Sin token: 401.

---

## HU-N13 Conexión del front (integración)
Como equipo quiero que el front use la API real sin cambiar sus pantallas ni su lógica.

**Alcance:** únicamente la capa de conexión del front (diseño §14). El front es de solo lectura para esta tarea; el cambio lo hace quien tenga permiso sobre ese repositorio. Esta historia lista y verifica los cambios; no modifica código del front desde el backend.

Criterios:
- Cliente HTTP con `credentials: 'include'`, base `VITE_API_URL`, `Authorization` desde la sesión en memoria, `X-Requested-With` en `/auth/refresh` y `/auth/logout`, un solo refresh en vuelo y reintento único tras 401.
- `reservasApi.ts`: las seis funciones llaman a sus endpoints y devuelven `Result` con `error` igual al `detail` de ProblemDetail; sin latencia simulada.
- `repositorio.ts`: estado desde la API, refresco al enfocar la pestaña y cada 15 s; sin `localStorage` de reservas.
- `admin/src/services/auth.ts`: login, restauración con `/auth/refresh` al cargar y logout contra la API; token solo en memoria.
- `.env.example` del front con `VITE_API_URL=http://localhost:8080`.
- `useDisponibilidad` usa `occupied` de `GET /disponibilidad` (campo opcional `occupied` en `ReservasState`).
- Un `NewRegistration` generado por `buildNewRegistration` se acepta (201) y la respuesta se almacena sin errores de tipo.
- El mensaje que muestra el formulario ante cada error coincide con el `detail` del servidor.
- El front compila (`npm run build`) y sus pruebas existentes pasan.

---

## HU-N14 Integración y endurecimiento
Como equipo quiero comprobar de extremo a extremo el sistema y cerrar los riesgos de seguridad antes del despliegue.

Criterios:
- `docker compose up --build -d` levanta backend y base; el front corre en Vite (5173) con `CORS_ORIGIN=http://localhost:5173`; el puerto 8080 del front no entra en conflicto (README lo aclara).
- Recorrido manual verificado: reservar desde el formulario; el contador de cupos sube al reservar y baja al rechazar; login en `/admin`; aprobar, rechazar con motivo y deshacer; pago manual; marcar autorización de un menor; cambiar capacidad y fecha de cierre; recargar y comprobar que persiste; cerrar sesión y comprobar 401.
- Pruebas de contrato con cargas JSON del front para `Registration`, `Payment` y `SesionResponse`.
- En la base, `ficha_salud` aparece cifrada y las cookies de refresh salen `HttpOnly`.
- Ningún dato de salud en los logs de la API durante el recorrido.
- Revisión de seguridad (`/security-review`) sin hallazgos altos abiertos; los medios quedan documentados.
- Se adaptan las pruebas de CORS, rate limit, perfil `prod` y Actuator (no se pierden).
- El README documenta las variables nuevas (`ADMIN_EMAIL`, `HEALTH_ENCRYPTION_KEY`, `COOKIE_SECURE`), cómo generarlas y la rotación de la clave de cifrado.
- Se informa lo que no se pudo verificar.

---

## HU-N15 Avisos por correo con Brevo
Como organización quiero que el sistema avise por correo cuándo pagar el saldo, si un pago se aprobó o se rechazó y que el evento es mañana, para no depender de mensajes manuales.

**Alcance:** paquete `notificacion/` (diseño §2 y §18), migración V19 (`envio_aviso`), cliente HTTP de Brevo, tarea diaria a las 12:00 (Bogotá) y aviso inmediato tras aprobar o rechazar un pago. Sin endpoints nuevos. **Regla:** RN-24.

Criterios de éxito:
- Con `BREVO_ACTIVO=false` (por defecto) no se envía nada ni se registran envíos, y la API arranca sin `BREVO_API_KEY`.
- Reserva `apartado` con fecha compromiso 9-nov y separación aprobada: la tarea del 6-nov envía `PAGO_3_DIAS`, la del 8-nov `PAGO_1_DIA`, la del 9-nov `PAGO_HOY` y la del 10-nov `PAGO_VENCIDO`. Ejecutarla dos veces el mismo día no reenvía.
- Con la separación todavía `Por revisar`, la tarea no envía recordatorios del saldo.
- Con el saldo ya pagado, no se envía ningún recordatorio del saldo.
- Aprobar un pago (revisión o pago manual) envía `PAGO_APROBADO` al titular. Rechazarlo envía `PAGO_RECHAZADO` con el motivo. La caducidad (RN-22) envía `PAGO_RECHAZADO` con su motivo.
- Deshacer y repetir la misma revisión de un pago no reenvía el aviso.
- La víspera de `fechaEventoInicio`, la tarea envía `EVENTO_MANANA` al titular y al acompañante de cada inscripción `Pagado`.
- Los parámetros de la plantilla llevan el nombre con mayúscula inicial, el código de la reserva, los montos en pesos y las fechas en español. Nunca documento, EPS ni salud.
- Si la API arranca después de las 12:00, la tarea corre en ese momento y envía los avisos del día que falten.
- Un tipo sin plantilla configurada (id 0) se omite con una advertencia sin datos personales.

Criterios de error:
- Con `BREVO_ACTIVO=true` y sin `BREVO_API_KEY`, la API no arranca y el error nombra la variable, nunca un valor.
- Una revisión que falla (p. ej. 422 por menor sin autorización) no envía correo.
- Brevo responde 429 o 5xx: se reintenta hasta 3 veces; si persiste, el envío queda `FALLIDO` y no frena los demás.
- Brevo responde otro 4xx: el envío queda `FALLIDO` sin reintentar.
- Ningún log incluye correos, nombres, documentos ni datos de salud.

---

## HU-D2 Despliegue en producción (implementada)
Como equipo técnico quiero publicar la aplicación en el servidor de forma segura.

**Estado:** implementada. No se modifica en esta versión; HU-N14 ajusta solo lo que el nuevo modelo exige (variables y cookie `Secure`).

No empezar hasta que HU-D1 y HU-N01 a HU-N14 estén aprobadas.

Criterios:
- `docker-compose.prod.yml` (se combina con el base) que añade un proxy inverso con HTTPS automático para el dominio indicado en una variable de entorno. La API y la base de datos solo son accesibles dentro de la red interna de Docker.
- Perfil `prod`: documentación de la API (springdoc) desactivada, errores sin detalles internos, logs sin datos personales ni de salud, origen CORS tomado de la variable de entorno.
- Contenedores con reinicio automático.
- Script de copia de seguridad de la base (pg_dump) con la fecha en el nombre del archivo, y guía para restaurarla. Las copias se guardan fuera del servidor.
- El README documenta el firewall (solo puertos 22, 80 y 443) y los pasos en el VPS: instalar Docker, clonar el proyecto, crear el `.env` de producción, levantar con `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d`, actualizar de versión y ver logs.
- Verificación: `https://{dominio}/actuator/health` responde UP; HTTP redirige a HTTPS; el puerto de la base de datos no responde desde Internet.
