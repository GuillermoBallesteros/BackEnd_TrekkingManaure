# Flujo de la aplicación: inscripciones a la carrera de Manaure

Versión 2. El backend se ajusta al front (`Front_TrekkingManaure\TrekkingManaure`, solo lectura): los formularios, reglas, precios, textos de error y estados salen de él. La versión anterior de este documento está en `docs/anterior/`.

## 0. Cómo leer este documento
- **[CONFIRMADO]**: sale del front o del cliente. Implementar tal cual.
- **[SUPUESTO]**: valor propuesto porque el front no lo define. Implementarlo configurable y reportarlo.
- **[PENDIENTE]**: no está definido. No inventar: preguntar.
- Moneda: pesos colombianos (COP), enteros, sin decimales. Zona horaria de negocio: America/Bogota. Fechas de negocio `AAAA-MM-DD`.
- Fuentes en el front: `shared/reservas/tipos.ts`, `data/catalogos.ts`, `shared/config/index.ts`, `utils/{validaciones,cotizacion,estado,cupos,fechas,hotel,telefono}.ts`, `servicios/reservasApi.ts`, `admin/src/data/pagos/motivos.ts`, `admin/src/services/auth.ts`.

## 1. Contexto [CONFIRMADO]
Evento: "Trekking Desafío Perijá 13K", Manaure (Cesar, Colombia), 28 y 29 de noviembre de 2026, 13 km, llegada al páramo. La aplicación permite inscribirse desde una página web y que la organización controle quién pagó.

**No hay pasarela de pagos ni WhatsApp en el backend.** La persona paga por la **llave Bre-B**, fuera del sistema. El backend guarda la reserva con un primer pago esperado en revisión; la encargada verifica en Bre-B que el dinero llegó y aprueba o rechaza el pago en el panel. El mensaje de WhatsApp y el envío de la autorización firmada del menor los maneja el front y la organización; el backend no los conoce.

El estado de una inscripción no se guarda: se deriva de sus pagos (sección 9).

## 2. Actores
- **Participante** (público, sin cuenta): reserva como titular y puede añadir un acompañante.
- **Administrador**: un solo usuario, rol ADMIN, con correo y clave tomados de variables de entorno [CONFIRMADO, decisión del usuario]. Es la "encargada" que verifica pagos.

## 3. Glosario
- **Reserva / inscripción**: una solicitud completa (titular, acompañante opcional, extras, hotel previo, plan de pago, pagos). Código `PER-0001`.
- **Titular**: quien reserva. Siempre compra kit (precio 350.000, que incluye inscripción y kit).
- **Acompañante**: segunda persona de la misma reserva (máximo uno). Corre, con los mismos datos que el titular. Puede comprar kit; solo entonces tiene talla.
- **Estadía**: dónde pasa la noche cada persona: `Páramo`, `Hotel en Manaure` o `Regreso a casa`. Solo `Páramo` gasta cupo del páramo.
- **Cupos**: hay dos contadores. **Carrera**: cada reserva activa gasta un cupo, con o sin acompañante. **Páramo**: cada persona (titular o acompañante) con estadía `Páramo` de una reserva activa gasta un cupo.
- **Plan de pago**: `total` (paga todo al reservar) o `apartado` (paga 50.000 ahora y el resto antes de la fecha compromiso).
- **Pago**: monto con código `PAG-0001`, concepto, fecha y revisión (`Por revisar`, `Aprobado`, `Rechazado`).
- **Fecha de cierre**: último día para inscribirse y para pagar el restante. La cambia la organización.
- **Fecha compromiso**: día elegido por el titular (plan `apartado`) para pagar el restante.
- **Hotel previo**: noches de hotel antes del evento, cobradas por noche y por persona.
- **Autorización del menor**: confirmación de que la organización recibió el documento firmado por el responsable de un titular menor de edad.

## 4. Flujo

### Participante
1. Abre la página y ve fecha del evento, precios, extras, tallas, cupos libres de la carrera y cupos libres del páramo (`GET /disponibilidad`).
2. Diligencia el formulario: datos de cada persona, salud, compromisos, consentimiento, estadía, talla (solo con kit), extras, hotel previo y plan de pago. El front calcula el total para mostrarlo; el servidor lo recalcula siempre.
3. Envía. El servidor valida con las mismas reglas del front, asigna código `PER-xxxx`, guarda la reserva y crea el primer pago en `Por revisar` (`Separación de cupo` por 50.000 o `Pago total`). Responde con la reserva completa.
4. Paga por la llave Bre-B y avisa a la organización (fuera del sistema).
5. Si el titular es menor de edad, envía la autorización firmada a la organización (fuera del sistema).

### Administrador (encargada)
6. Inicia sesión en `/admin`.
7. Ve todas las reservas con su estado derivado, pagos pendientes de revisión y alertas de salud (el panel filtra en el cliente).
8. Verifica en Bre-B y **aprueba** el pago o lo **rechaza con motivo**. Si se equivocó, lo corrige con **Deshacer** en el aviso que aparece justo después; un pago ya decidido no tiene botón para devolverlo a `Por revisar`.
9. Si el pago llegó por otra vía, **registra un pago manual** (entra aprobado).
10. Marca la **autorización del menor** cuando recibe el documento.
11. Ajusta los **cupos de la carrera**, los **cupos del páramo** y la **fecha de cierre**.

### Sistema (avisos por correo)
12. Envía por correo, con Brevo, los recordatorios del saldo, la aprobación o el rechazo de cada pago y el aviso de la víspera del evento (RN-24).

## 5. Datos por persona (titular y acompañante) [CONFIRMADO]
Mismos campos para ambos (`Person` en el front); el acompañante añade `kit` y `shirtSize`.

**Normalización (antes de validar y de guardar).** El front la aplica al escribir y al enviar; el backend siempre la repite (`Normalizador`) y la base la comprueba con CHECK (V18). Las longitudes se miden sobre el texto ya normalizado.

| Campo | Qué se hace |
|---|---|
| Todo texto | sin espacios al inicio y al final, espacios dobles a uno, Unicode NFC |
| Nombres, apellidos, nombre del contacto | MAYÚSCULAS y sin tildes (Á→A, É→E, Í→I, Ó→O, Ú→U, Ü→U y cualquier otra marca latina como è, ã, ç); la Ñ se conserva (PEÑA no es PENA); ’ pasa a ' |
| Ciudad, EPS "Otra" | sin tildes (la Ñ se conserva), con las mayúsculas que escribió la persona |
| Documento | sin puntos, espacios ni guiones, en mayúsculas |
| Teléfono | solo dígitos; si trae el indicativo (+57) delante, se quita |
| Correo | minúsculas y sin espacios |
| Detalles de salud | no se normalizan: se guardan tal como se escriben, con sus tildes |

Ejemplos: `María José` → `MARIA JOSE`; `Muñoz Peña` → `MUÑOZ PEÑA`; `Ürümqi` → `URUMQI`; `1.098.726.541` → `1098726541`; `Ana@Gmail.com` → `ana@gmail.com`; `+57 300 123 4567` → `3001234567`; `Valledupar` → `Valledupar`.

| Campo | Regla (mensajes literales en `diseno.md` §12) |
|---|---|
| Nombres, apellidos | 2 a 40 caracteres; solo A-Z y Ñ (al escribir se aceptan tildes), apóstrofe, guion y espacio; un solo separador entre palabras. Se guardan en MAYÚSCULAS y sin tildes (la Ñ se conserva) |
| Tipo de documento | CC, CE, TI, RC, Pasaporte, PPT (tabla 5.1) |
| Documento | patrón del tipo (tabla 5.1); se normaliza antes de validar: sin puntos, espacios ni guiones y en mayúsculas (1.098.726.541 pasa a 1098726541) |
| Fecha de nacimiento | AAAA-MM-DD válida, no futura, edad <= 110 |
| Género | Femenino, Masculino, Otro, Prefiero no decirlo [lista provisional en el front] |
| Teléfono | país del catálogo (tabla 5.2) y número nacional de solo dígitos (si traía el indicativo delante, se quita); debe ser celular |
| Correo | formato válido, minúsculas y sin espacios, máx. 100; sin dos puntos seguidos; se rechazan dominios mal escritos conocidos (gmial.com, etc.) |
| Ciudad | 3 a 40 caracteres; letras, Ñ, espacios, guion, apóstrofe y punto de abreviatura. Se guarda sin tildes y con las mayúsculas escritas [SUPUESTO, por confirmar] |
| EPS | una de la lista (5.3). Con `Otra` es obligatorio el nombre (3 a 40, solo letras y espacios, sin tildes); con cualquier otra el nombre debe venir nulo |
| Contacto de emergencia | nombre (5 a 80, al menos dos palabras, MAYÚSCULAS y sin tildes), teléfono (celular o fijo) y parentesco (5.4) |
| Salud | `illness`, `allergies`, `disability`: nulo, o texto de 2 a 200 caracteres sin espacios en los extremos; `exercises`: booleano obligatorio |
| Compromisos | `protectedAreas` y `environment` deben ser `true` |
| Consentimiento | `version` igual a la vigente (`TYC-2026-10-28`), `acceptedAt` con fecha válida, `imageUse` booleano (opcional para la persona, pero siempre presente) |
| Estadía | Páramo, Hotel en Manaure, Regreso a casa |

Solo titular: `shirtSize` obligatoria (XS, S, M, L, XL, XXL, XXXL). Solo acompañante: `kit` (booleano); con kit `shirtSize` obligatoria, sin kit debe ser nula.

La hora de aceptación (`acceptedAt`) definitiva la pone el servidor al registrar [CONFIRMADO, `reservasApi.crearReserva`].

### 5.1 Documentos por tipo
| Tipo | Formato | Edad |
|---|---|---|
| CC | 8 a 10 dígitos, no empieza por 0 | 18 años o más |
| CE | 6 a 10 dígitos | sin límite |
| TI | 10 u 11 dígitos | 7 a 18 años |
| RC | 10 dígitos | hasta 7 años |
| Pasaporte | 6 a 12 letras o números | sin límite |
| PPT | 5 a 8 dígitos | sin límite |

Todos los tipos son numéricos salvo el pasaporte. La CC de 8 a 10 dígitos la fijó el cliente (cubre 54876234 y 1098726541); los rangos de CE, RC, PPT y pasaporte y las edades están "por confirmar con el cliente" [SUPUESTO]. La edad se calcula a la fecha de hoy (Bogotá).

### 5.2 Teléfonos por país (13 países) [CONFIRMADO]
Número nacional sin indicativo, solo dígitos. `largos` = cantidad de dígitos; `inicios` = prefijos permitidos. Móvil siempre; fijo solo para el contacto de emergencia.

| ISO | País | Ind. | Móvil: largos / inicios | Fijo: largos / inicios |
|---|---|---|---|---|
| CO | Colombia | 57 | 10 / 3 | 10 / 601, 602, 604, 605, 606, 607, 608 |
| VE | Venezuela | 58 | 10 / 41, 42 | 10 / 2 |
| EC | Ecuador | 593 | 9 / 9 | 8 / 2-7 |
| PE | Perú | 51 | 9 / 9 | 8 / 1, 4, 5, 6, 7, 8 |
| MX | México | 52 | 10 / 1-9 | no admite |
| US | Estados Unidos | 1 | 10 / 2-9 | no admite |
| ES | España | 34 | 9 / 6, 7 | 9 / 8, 9 |
| AR | Argentina | 54 | 10 u 11 / 1, 2, 3, 9 | no admite |
| CL | Chile | 56 | 9 / 9 | 9 / 2-7 |
| BR | Brasil | 55 | 11 / 1-9 | 10 / 1-9 |
| PA | Panamá | 507 | 8 / 6 | 7 / 2, 3, 4, 5, 7, 8, 9 |
| CR | Costa Rica | 506 | 8 / 5, 6, 7, 8 | 8 / 2, 4 |
| DO | República Dominicana | 1 | 10 / 809, 829, 849 | no admite |

País predeterminado: CO. Dos números son el mismo si coinciden `+indicativo` + número.

### 5.3 EPS
Nueva EPS, EPS Sura, EPS Sanitas, Salud Total, Compensar, Famisanar, Coosalud, Mutual SER, Cajacopi, Asmet Salud, Emssanar, Savia Salud, Capresoca, Comfenalco Valle, Capital Salud, Aliansalud, Dusakawi, Anas Wayuu, Pijaos Salud, Mallamas, Comfachocó, Comfaoriente, Salud Mía, SOS (Servicio Occidental de Salud), EPS Familiar de Colombia, AIC (Asociación Indígena del Cauca), Régimen especial o de excepción, Otra, No tengo EPS (29 valores). Contrastada con el listado del Ministerio de Salud (5 de junio de 2025) y con prensa de 2026; la lista cambia con frecuencia, por eso existe `Otra`. Una EPS nueva se agrega con una migración (`INSERT` en `eps`); ninguna se quita ni se renombra mientras haya reservas que la usen.

### 5.4 Parentescos del contacto de emergencia
Madre, Padre, Tutor legal o acudiente, Cónyuge o pareja, Hijo o hija, Hermano o hermana, Abuelo o abuela, Tío o tía, Primo o prima, Otro familiar, Amigo o amiga. Responsable legal de un titular menor: solo Madre, Padre o Tutor legal o acudiente.

## 6. Datos de la reserva [CONFIRMADO]
- `extras`: lista sin repetidos con valores `cuatrimoto` (paseo en cuatrimoto), `parapente` (vuelo del titular), `parapenteAcompanante` (vuelo del acompañante; solo con acompañante). La cuatrimoto es una sola por reserva.
- `preEventHotel`: nulo o `{checkIn, checkOut, withCompanion}` (sección RN-13).
- `plan`: `total` o `apartado`. `commitmentDate`: obligatoria con `apartado`, nula con `total`.
- `guardianAuthorized`: lo fija la organización; nace en `false`.
- `payments`: lista de pagos. Nace con uno.

### Configuración del evento (valores iniciales = `shared/config`)
| Clave | Valor inicial |
|---|---|
| Nombre | Trekking Desafío Perijá 13K [CONFIRMADO] |
| Fechas del evento | 2026-11-28 y 2026-11-29 [CONFIRMADO] |
| Apertura de inscripciones | 2026-10-28 [CONFIRMADO] |
| Fecha de cierre inicial | 2026-11-09 [CONFIRMADO]; editable por la organización |
| Cupos de la carrera iniciales | 60 [CONFIRMADO, decisión del usuario 2026-10-29]; editables |
| Cupos del páramo iniciales | 30 cupos [CONFIRMADO, decisión del usuario 2026-10-29]; editables |
| Versión de términos | TYC-2026-10-28 [CONFIRMADO] |

## 7. Precios (COP) [CONFIRMADO]
| Concepto | Valor |
|---|---|
| Titular (inscripción 50.000 + kit 300.000) | 350.000 |
| Apartado (pago inicial del plan `apartado`) | 50.000 |
| Acompañante sin kit | 150.000 |
| Acompañante con kit | 350.000 |
| Paseo en cuatrimoto | 150.000 |
| Parapente (titular o acompañante) | 200.000 cada uno |
| Hotel previo | 45.000 por noche y por persona |

`total` = 350.000 + acompañante (150.000 sin kit, 350.000 con kit) + extras + hotel (noches x personas x 45.000).

**Ejemplo 1.** Titular solo, plan `total`: total 350.000, paga 350.000, saldo 0.
**Ejemplo 2.** Titular + acompañante sin kit + cuatrimoto, plan `apartado`: total 650.000, paga 50.000, restante 600.000.
**Ejemplo 3.** Titular + acompañante con kit + parapente + parapenteAcompanante: total 350.000 + 350.000 + 200.000 + 200.000 = 1.100.000.
**Ejemplo 4.** Titular solo con hotel previo de 2 noches: 350.000 + 2 x 1 x 45.000 = 440.000. Titular + acompañante sin kit con hotel de 2 noches y `withCompanion` verdadero: 350.000 + 150.000 + 2 x 2 x 45.000 = 680.000.

## 8. Reglas de negocio
- **RN-01** [CONFIRMADO] Una reserva tiene un titular y cero o un acompañante. El acompañante corre y registra todos sus datos.
- **RN-02** [CONFIRMADO] El titular siempre compra kit y paga 350.000. El acompañante decide `kit`: sin kit paga 150.000 y no lleva talla; con kit paga 350.000 y la talla es obligatoria.
- **RN-03** [CONFIRMADO] El total lo calcula siempre el servidor con los precios vigentes al registrar. Esos precios quedan copiados en la reserva: cambiarlos después no altera reservas existentes [SUPUESTO sobre la copia; el front calcula siempre con los precios actuales].
- **RN-04** [CONFIRMADO, decisión del usuario 2026-10-29] Cupos: dos contadores. **Carrera** (`cupos_carrera`): cada reserva **activa** gasta uno, con o sin acompañante. **Páramo** (`cupos_paramo`): cada persona con estadía `Páramo` de una reserva **activa** gasta un cupo; Hotel en Manaure y Regreso a casa no gastan cupo del páramo. Los cupos se **apartan al crear la reserva**, aunque su pago siga `Por revisar`, y se liberan cuando la reserva deja de estar activa (Rechazada, a mano o por caducidad, RN-22).
- **RN-05** [CONFIRMADO] Al reservar, bajo el bloqueo global: si la carrera está llena se rechaza con `CARRERA_AGOTADA`; si el páramo no alcanza para las personas que lo eligen, con `SIN_CUPO_PARAMO`. Aprobar un pago de una reserva activa no vuelve a validar cupos. Solo **reactivar** una reserva Rechazada (aprobar o devolver a `Por revisar` uno de sus pagos, o registrarle un pago manual) exige que vuelvan a caber su cupo de la carrera y sus cupos del páramo; si no, `SIN_CUPO_PARA_APROBAR`. Rechazar siempre se permite.
- **RN-06** [CONFIRMADO] **Documento único entre inscripciones activas** (titular o acompañante). Una inscripción activa es la que no está en estado `Rechazado`. Un estado `Rechazado` libera sus documentos. Se compara sin espacios y en mayúsculas, solo por número (no por tipo).
- **RN-07** [CONFIRMADO] Titular y acompañante no pueden compartir documento, correo ni celular. El contacto de emergencia de uno no puede ser el otro (por celular ni por nombre). Cada contacto de emergencia debe ser otra persona (nombre distinto) y con teléfono distinto al de la persona.
- **RN-08** [CONFIRMADO] **Menores de edad permitidos.** Si el titular es menor (edad a hoy < 18), su contacto de emergencia es su responsable y su parentesco debe ser Madre, Padre o Tutor legal o acudiente. La organización marca `guardianAuthorized` al recibir la autorización firmada. **Sin esa marca no se aprueba ningún pago** de esa reserva. El front solo valida la menoría del titular [PENDIENTE: acompañante menor].
- **RN-09** [CONFIRMADO] **Fecha de cierre**: pasada la fecha de cierre (hoy > cierre) no se aceptan nuevas inscripciones. La organización la fija entre la apertura de inscripciones y la víspera del evento; puede dejarla en el pasado para cerrar antes.
- **RN-10** [CONFIRMADO] **Plan apartado y fecha compromiso**: elegible desde `max(hoy, apertura)` hasta la fecha de cierre, inclusive. Si ese primer día ya supera el cierre, el plan `apartado` no está disponible y debe elegir `total`. Con plan `total` la fecha compromiso debe ser nula.
- **RN-11** [CONFIRMADO] **Primer pago**: `apartado` genera `Separación de cupo` por `min(50.000, total)`; `total` genera `Pago total` por el total. Fecha = hoy, revisión `Por revisar`.
- **RN-12** [CONFIRMADO] **Extras**: sin repetidos; `parapenteAcompanante` solo con acompañante. Los valores deben ser del catálogo.
- **RN-13** [CONFIRMADO] **Hotel previo**: ventana de llegada desde `max(hoy, apertura)` hasta la noche anterior al primer día del evento (27 de noviembre); la salida máxima es el primer día del evento. Salida posterior a la llegada; al menos una noche. `withCompanion` solo con acompañante. Si la ventana ya cerró, no se puede pedir hotel. Cobro: noches x personas x 45.000.
- **RN-14** [CONFIRMADO] **Pagos con revisión**: todo pago nace `Por revisar` salvo el manual, que nace `Aprobado`. Solo la administradora cambia la revisión. Transiciones permitidas: cualquiera entre `Por revisar`, `Aprobado` y `Rechazado` (**deshacer una revisión** es volver a `Por revisar`). En el panel, volver a `Por revisar` solo ocurre con **Deshacer**, justo después de aprobar o rechazar; no hay botón para devolver un pago ya decidido. Rechazar exige motivo (texto no vacío); al salir de `Rechazado` el motivo se borra.
- **RN-15** [CONFIRMADO] **Aprobar un pago** (o registrar un pago manual) exige: (a) autorización del menor si aplica (RN-08); (b) si la reserva estaba Rechazada, que vuelvan a caber su cupo de la carrera y sus cupos del páramo (RN-05).
- **RN-16** [CONFIRMADO] **Pago manual**: monto entero > 0 y no mayor que el saldo (`max(0, total - pagado)`). Entra `Aprobado`. Concepto: `Pago de saldo` si ya hay algo aprobado; si no, `Pago total` si el monto cubre el total, o `Separación de cupo`. Fecha = hoy.
- **RN-17** [CONFIRMADO] **Cupos de la carrera y del páramo (capacidad)**: se fijan juntos; cada uno es entero, al menos 1 y no menos que lo ya ocupado (reservas activas para la carrera, cupos ocupados para el páramo).
- **RN-18** [SUPUESTO] Las operaciones que reactivan una inscripción `Rechazada` (devolver o aprobar un pago, pago manual) fallan con conflicto si algún documento ya lo tomó otra inscripción activa. El front no lo contempla.
- **RN-19** [CONFIRMADO + decisión del usuario] **Salud**: los datos de salud (enfermedad, alergias, discapacidad, deporte) los ve únicamente el administrador autenticado, en la lista y en el detalle (el panel calcula las alertas en el cliente). Se guardan **cifrados en la base** y nunca van en logs, auditoría, mensajes de error ni exportaciones del servidor. Esta regla relaja la versión anterior (solo en el detalle).
- **RN-20** [CONFIRMADO] Sin consentimiento y compromisos de **cada** persona no se guarda la reserva. Se guarda la versión de términos, la fecha y hora y el uso de imagen por persona.
- **RN-21** [CONFIRMADO] Si el pagado >= total, el estado es `Pagado`. El excedente no se modela: el pago manual no puede superar el saldo.
- **RN-22** [CONFIRMADO + decisión del usuario, hallazgo H4] **Caducidad a 12 horas del pago sin verificar**: una reserva que no tiene ningún pago `Aprobado` y cuyo pago lleva 12 horas o más en `Por revisar` pasa a `Rechazado` automáticamente (el pago queda `Rechazado` con el motivo "No se verificó el pago dentro de 12 horas.", la reserva inactiva y sus documentos libres, como si la encargada la rechazara). La antigüedad se mide desde que el pago entró en `Por revisar` (su creación o la última vez que la encargada lo devolvió a `Por revisar`), no desde la creación de la reserva. Una reserva con cualquier pago aprobado nunca caduca; los pagos manuales nacen aprobados y no se tocan. Si el dinero sí llegó, la encargada reactiva la reserva registrando un pago manual (RN-16, RN-18). Al caducar, la reserva libera su cupo de la carrera y sus cupos del páramo. Las horas son configurables (`app.reservas.caducidad.horas`).
- **RN-23** [CONFIRMADO + decisión del usuario, hallazgo H4] **Tope de reservas por IP**: además de 10 solicitudes por minuto, una misma IP puede hacer como máximo 20 solicitudes a `POST /api/v1/reservas` en 24 horas (cuentan todas, también las que fallan por validación). Pasado el tope responde 429 `DEMASIADAS_SOLICITUDES` con `Retry-After` y se genera una alerta única por IP y ventana (log y auditoría, sin datos personales). Cuando el documento ya está reservado, el 409 `DOCUMENTO_DUPLICADO` público añade: "Si no fuiste tú, comunícate con la organización para que la revisen." (el panel conserva el mensaje original).
- **RN-24** [CONFIRMADO, decisión del usuario 2026-10-28] **Avisos por correo (Brevo)**: el backend envía correos transaccionales con plantillas de Brevo, desde `manaureecoturisticointernacional@manaurevive.com`, sin datos de salud, documento ni EPS. Solo correo: no se envían SMS ni WhatsApp.
  - Recordatorio del saldo 3 días antes, 1 día antes y el mismo día de la fecha compromiso, a las 12:00 (Bogotá), si la inscripción está en `Saldo pendiente`.
  - Aviso de vencimiento el día siguiente a la fecha compromiso, a las 12:00, si está en `Vencido`.
  - Aprobación de un pago (revisión o pago manual) y rechazo (por la encargada o por caducidad, RN-22), en el momento y con el motivo en el rechazo. Un aviso por pago y por tipo: deshacer y repetir una revisión no lo reenvía.
  - Víspera del evento (`fechaEventoInicio` - 1), a las 12:00: a cada persona (titular y acompañante) de las inscripciones en `Pagado`.
  - Los avisos de pago van al titular. Un aviso (tipo y referencia) nunca se envía dos veces a la misma inscripción. Un fallo temporal de Brevo se reintenta en el momento (hasta 3 veces); si persiste queda como fallido y se vuelve a intentar si la tarea corre otra vez ese día.

## 9. Estados derivados [CONFIRMADO]
Se calculan al consultar; no se guardan. `pagado` = suma de pagos `Aprobado`. `hoy` = fecha en Bogotá.

| Estado | Condición (se evalúa en este orden) |
|---|---|
| Pagado | `pagado > 0` y `pagado >= total` |
| Vencido | `pagado > 0` y `pagado < total`, con fecha compromiso y `fecha compromiso < hoy` |
| Saldo pendiente | `pagado > 0` y `pagado < total`, sin fecha compromiso o con `fecha compromiso >= hoy` |
| Por verificar | `pagado = 0` y (sin pagos o algún pago `Por revisar`) |
| Rechazado | `pagado = 0`, con pagos y ninguno `Por revisar` |

Saldo = `max(0, total - pagado)`. Una inscripción `Rechazado` no es activa (RN-06).

## 10. Motivos de rechazo de pago [CONFIRMADO]
Opciones del panel: "El pago no aparece en la llave Bre-B", "El monto recibido no coincide con lo esperado", "La persona canceló la reserva" y "Otro motivo". El servidor guarda el texto recibido sin restringirlo a la lista (máx. 200 caracteres [SUPUESTO]) y solo exige que no esté vacío.

## 11. Seguridad y privacidad
- HTTPS en producción (Caddy). CORS con origen exacto y credenciales; nunca `*`.
- Rutas públicas: `/api/v1/config`, `/api/v1/disponibilidad`, `POST /api/v1/reservas` y `/api/v1/auth/**`. Todo `/api/v1/admin/**` exige sesión de administrador.
- Sesión: access token de 15 minutos y refresh token rotatorio en cookie `HttpOnly; Secure; SameSite=Strict`. "Recordar" = 7 días; sin recordar, 8 horas [SUPUESTO, la duración que usa el front hoy].
- Clave con BCrypt (costo 12). Bloqueo por intentos fallidos y límite por IP. Un solo administrador creado desde `ADMIN_EMAIL` y `ADMIN_PASSWORD`, sin valores por defecto en el código.
- Cada acción del administrador queda en auditoría (quién, qué, cuándo), sin datos de salud ni personales.
- Datos personales: Ley 1581 de 2012. El texto legal de los términos lo entrega la organización [PENDIENTE]; este documento no es asesoría legal. Política de retención y borrado [PENDIENTE].

## 12. Convenciones de error
Formato ProblemDetail con campo `code` estable en mayúsculas y `detail` en español **idéntico al mensaje del front** (el front lo muestra como `Result.error`). Detalle completo en `docs/diseno.md` §12.

| HTTP | Cuándo |
|---|---|
| 400 | JSON mal formado, objeto o campo estructural faltante, tipo incorrecto |
| 401 | Sin sesión, sesión vencida, credenciales inválidas, refresh inválido |
| 403 | Sin permiso; falta la cabecera `X-Requested-With` en `/auth/refresh` y `/auth/logout` |
| 404 | La reserva o el pago no existe |
| 409 | Conflicto con el estado: inscripciones cerradas, documento ya registrado, carrera agotada, sin cupo del páramo |
| 422 | Regla de negocio o validación de campo incumplida |
| 429 | Demasiados intentos |

## 13. Fuera de alcance
Pasarela o API de Bre-B/Nequi; WhatsApp o SMS desde el backend (el correo está en RN-24); devolución de dinero; anular una inscripción (el estado `Rechazado` cumple ese papel); anular o editar un pago (se rechaza; «Deshacer» lo regresa a `Por revisar` justo después de decidir); corregir los datos de una persona; modificar la reserva ya creada; resumen, tablero o exportación a CSV/Excel en el servidor (el panel los arma con la lista); cotización en el servidor; consulta pública del estado; varios eventos; varios administradores o roles; n8n.

## 14. Decisiones abiertas
Ver `docs/diseno.md` §16 (lista numerada de preguntas concretas). Las principales: menores como acompañante, reactivar una inscripción rechazada con documento tomado, dominio del front y del API (SameSite), origen de los precios (GET /config o `shared/config`), valores de bloqueo y límites, retención de datos y texto legal.
