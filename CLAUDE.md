# Proyecto: backend de inscripciones de la carrera de Manaure (Cesar, Colombia)

API REST en Spring Boot para registrar participantes, calcular su costo y controlar pagos hechos por llave Bre-B (sin pasarela). El front es un proyecto aparte: solo lectura, no lo modifiques.

## Documentos (léelos solo cuando la tarea los necesite)
- docs/flujo-app.md: flujo, datos, reglas de negocio (RN-xx), precios y estados. Fuente de verdad del negocio.
- docs/historias.md: historias HU-D1, HU-00 a HU-12 y HU-D2 con criterios de aceptación. Orden: HU-D1 primero, HU-D2 al final.
- docs/diseno.md: diseño técnico aprobado. Si no existe, todavía no se diseñó.
- Marcas en los documentos: [CONFIRMADO] implementar tal cual. [SUPUESTO] implementar configurable y reportarlo. [PENDIENTE] no inventar: preguntar.

## Stack
Spring Boot con Maven (usar ./mvnw.cmd en Windows), Java según pom.xml, PostgreSQL 18, Flyway, JPA/Hibernate, Bean Validation, springdoc-openapi, JUnit 5, Mockito y Testcontainers. Docker desde el inicio (HU-D1): todo se desarrolla y prueba contra el entorno en contenedores.

## Convenciones
- Nombres de dominio en español sin tildes (Inscripcion, Abono, Persona; tablas en snake_case; JSON en camelCase). Sufijos técnicos en inglés (Controller, Service, Repository).
- Textos para el usuario y mensajes de error en español.
- Dinero: pesos enteros (Long y BIGINT). Nunca double, float ni decimales.
- Fechas de negocio en zona America/Bogota.
- Rutas públicas bajo /api/v1/. Rutas de administración bajo /api/v1/admin/ y con autenticación.
- Errores: ProblemDetail con campo `code` estable (ver flujo §10).

## Reglas de trabajo
- Una historia por sesión. Lee solo su HU, las secciones del flujo que cite y docs/diseno.md.
- No inventes endpoints, tablas, campos ni reglas. Si algo es ambiguo, pregunta una sola cosa concreta.
- Cambios mínimos; no refactorices lo que no pertenece a la historia.
- Migraciones Flyway: archivo nuevo con el siguiente número libre; nunca editar una ya aplicada.
- ddl-auto en validate. @Transactional en la capa service.
- Nunca credenciales en el código ni en el repositorio: variables de entorno. `.env` en `.gitignore`.
- Nada de datos de salud en logs, listados ni exportaciones.
- La app corre en contenedor: sin rutas absolutas, configuración por variables de entorno. El README explica cómo levantar el entorno con docker compose.
- No ejecutes git commit ni git push. Yo hago los commits.
- Antes de dar algo por terminado, ejecuta `./mvnw.cmd test` e informa el resultado real. Si no pudiste ejecutarlo, di "no verificado".
- Responde en español, sin saludos ni elogios, de forma breve.
