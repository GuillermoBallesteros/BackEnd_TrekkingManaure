# Backend Trekking Manaure

API REST (Spring Boot 4.1, Java 25) con PostgreSQL 18, todo en contenedores.

## Requisitos
- Docker Desktop con WSL2 (Windows), en marcha.
- Para trabajar desde el IDE: JDK 25 (el wrapper `mvnw.cmd` descarga Maven).

## Configuración
```powershell
Copy-Item .env.example .env
```
Edite `.env` con sus valores (el archivo no se versiona). `JWT_SECRET`: mínimo 48 caracteres aleatorios.

**Secretos.** `DB_PASSWORD`, `ADMIN_PASSWORD`, `JWT_SECRET` y `HEALTH_ENCRYPTION_KEY` vienen vacíos en `.env.example` a propósito: la API no arranca hasta rellenarlos. La API rechaza (en `prod`; en otros perfiles solo advierte en el log) un secreto que contenga "cambiar" (sin distinguir mayúsculas) o que sea uno de los ejemplos antiguos, y en `prod` exige `JWT_SECRET` de al menos 48 caracteres y `ADMIN_PASSWORD` de al menos 12. Cómo generarlos:
```bash
openssl rand -hex 32                     # DB_PASSWORD
openssl rand -base64 18                  # ADMIN_PASSWORD (24 caracteres; o: openssl rand -hex 16)
openssl rand -base64 48                  # JWT_SECRET (64 caracteres)
echo "1:$(openssl rand -base64 32)"      # HEALTH_ENCRYPTION_KEY (guárdela aparte)
```
En Windows sin `openssl`: `python -c "import secrets;print(secrets.token_urlsafe(48))"` para JWT_SECRET y `python -c "import os,base64;print('1:'+base64.b64encode(os.urandom(32)).decode())"` para HEALTH_ENCRYPTION_KEY.

`CORS_ORIGIN` (obligatorio): origen(es) del front separados por coma, p. ej. `http://localhost:5173`. La API no arranca si está vacío o contiene `*`.

`ADMIN_EMAIL` y `ADMIN_PASSWORD`: administrador inicial (solo se crea si la tabla está vacía; correo con formato válido, que se guarda en minúsculas, y clave de 8 a 128 caracteres sin espacios en los extremos). Si faltan o son inválidos, la API arranca igual y registra una advertencia sin los valores. Nunca se cambia la clave de un administrador existente. Inicio de sesión: `POST /api/v1/auth/login` con `{correo, clave, recordar}`.

`HEALTH_ENCRYPTION_KEY` (obligatoria): clave AES-256 que cifra los detalles de salud, formato `version:base64` de 32 bytes, p. ej. `1:<base64>`. La API no arranca si falta, no tiene ese formato o no decodifica a 32 bytes. Generarla:
```powershell
python -c "import os,base64;print('1:'+base64.b64encode(os.urandom(32)).decode())"
```
Guárdela aparte: sin ella los datos de salud no se pueden leer. Para rotarla, genere una clave con versión nueva (`2:...`), póngala en `HEALTH_ENCRYPTION_KEY` y deje la anterior en `HEALTH_ENCRYPTION_KEY_ANTERIORES` (lista `version:base64` separada por comas, opcional) para poder descifrar los datos ya guardados.

`COOKIE_SECURE` (por defecto `true`): atributo `Secure` de la cookie `refresh_token`. En desarrollo local sobre `http://localhost` puede ponerse en `false`.

Límite de frecuencia por IP (ventana fija, en memoria): `POST /api/v1/reservas` 10 por minuto y `POST /api/v1/auth/login` 20 cada 15 minutos; al excederlo responde 429 `DEMASIADAS_SOLICITUDES` con `Retry-After`. Configurable en `app.rate-limit.registro-por-minuto` y `app.rate-limit.login-por-15-minutos`. Cinco fallos consecutivos de una cuenta la bloquean 15 minutos (429 `INTENTOS_EXCEDIDOS`; se guarda en la base y sobrevive a un reinicio). Se usa la IP de la conexión; en el perfil `prod` (detrás de Caddy) está activo `server.forward-headers-strategy=native`, por lo que refleja la IP real del cliente.

Tope diario por IP (H4): además, `POST /api/v1/reservas` admite 20 solicitudes por IP en 24 horas (`app.rate-limit.reservas-por-ip-por-dia`, variable `RESERVAS_POR_IP_POR_DIA`); la 21 recibe 429 con `Retry-After` y se genera una alerta única (log WARN con la IP como hash corto y auditoría `ALERTA_RESERVAS_IP`).

Caducidad de reservas sin pago (H4): una tarea programada (`app.reservas.caducidad.cron`, cada 10 minutos) rechaza el pago "Por revisar" de las reservas sin pagos aprobados cuando lleva `app.reservas.caducidad.horas` (12) o más en revisión, con el motivo "No se verificó el pago dentro de 12 horas." y libera el documento. Se activa con `CADUCIDAD_RESERVAS_ACTIVA` (por defecto `true`; `CADUCIDAD_RESERVAS_HORAS` cambia el plazo). La migración V17 añade `pago.en_revision_desde` (rellenada con la fecha de creación: al desplegar, los pagos pendientes de más de 12 horas se rechazarán en la primera ejecución).

## Estado de la API
Tras la HU-N01 el esquema nuevo (V9 a V15), los catálogos, la configuración del evento y el cifrado de salud están listos, pero la API pública queda mínima: solo `POST /api/v1/auth/login` (adaptado a la tabla `administrador`, correo en minúsculas) y `GET /actuator/health`. El registro de reservas, la disponibilidad, la configuración pública y el panel de administración llegan con las historias HU-N03 a HU-N12. Las tablas anteriores se eliminaron en la V9: los datos locales de desarrollo se pierden.

## Arrancar
```powershell
docker compose up --build -d
```
Comprobar: http://localhost:8080/actuator/health debe responder `{"status":"UP"}` (único endpoint de Actuator expuesto).
En desarrollo, `docker-compose.override.yml` publica la base en `127.0.0.1:5433` (el 5432 lo usa el PostgreSQL de Windows). Dentro de Docker la API usa `db:5432`.

## Detener
```powershell
docker compose down
```
Los datos se conservan en el volumen `db_data`.

## Ver logs
```powershell
docker compose logs -f api
docker compose logs -f db
```

## Reiniciar la base desde cero
Borra todos los datos:
```powershell
docker compose down -v
docker compose up --build -d
```

## Solo la base en contenedor
```powershell
docker compose up -d db
$env:DB_NAME="..."; $env:DB_USER="..."; $env:DB_PASSWORD="..."; $env:CORS_ORIGIN="http://localhost:5173"; $env:JWT_SECRET="..."; $env:HEALTH_ENCRYPTION_KEY="..."   # mismos valores de .env
.\mvnw.cmd spring-boot:run
```
Por defecto la API local se conecta a `localhost:5433` (`DB_HOST` y `DB_PORT` lo cambian).

## Pruebas
Requieren Docker Desktop en marcha (Testcontainers con `postgres:18`):
```powershell
.\mvnw.cmd test
```

## Migraciones
Flyway ejecuta los scripts de `src/main/resources/db/migration` al arrancar. Nunca edite una migración ya aplicada.

## Producción (VPS Linux)
En producción se usa `docker-compose.prod.yml` junto al base y **sin** `docker-compose.override.yml` (ese publica la base y es solo para desarrollo). Como el override se carga solo cuando no se indica `-f`, siempre use los dos `-f` explícitos:
```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml <comando>
```
Resultado: Caddy (`caddy:2.10.2`) es lo único que publica puertos (80 y 443) y obtiene el certificado HTTPS solo; `api` y `db` solo se ven dentro de la red interna de Docker. Perfil `prod`: sin Swagger/OpenAPI, errores sin detalles internos, logs en INFO sin SQL ni datos personales o de salud. Todos los contenedores usan `restart: unless-stopped`. Volúmenes con nombre: `db_data` (datos), `caddy_data` y `caddy_config` (certificados).

### Antes de empezar
- El dominio (registro DNS tipo A/AAAA) **debe apuntar a la IP del VPS antes del primer arranque**: Caddy necesita validarlo para emitir el certificado. Los puertos 80 y 443 deben estar libres y accesibles desde Internet.

### Firewall (solo 22, 80 y 443)
Ejemplo con ufw (confirme que SSH queda permitido antes de activarlo):
```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status
```
Nota: Docker publica puertos saltándose ufw; por eso el compose de producción no publica nada salvo 80 y 443. No añada `ports` a `db` ni a `api`.

### Pasos en el VPS
1. Instalar Docker Engine y el plugin Compose (guía oficial: https://docs.docker.com/engine/install/).
2. Clonar el proyecto y entrar a su carpeta (`git clone <url> && cd BackEnd_TrekkingManaure`).
3. Crear el `.env` de producción: `cp .env.example .env` y editarlo (`chmod 600 .env`). Obligatorias en prod: `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `CORS_ORIGIN` (origen HTTPS del front), `JWT_SECRET`, `HEALTH_ENCRYPTION_KEY` y `DOMAIN`. Secretos fuertes:
   ```bash
   openssl rand -hex 32      # DB_PASSWORD
   openssl rand -base64 18   # ADMIN_PASSWORD (mínimo 12 caracteres en prod)
   openssl rand -base64 48   # JWT_SECRET
   echo "1:$(openssl rand -base64 32)"   # HEALTH_ENCRYPTION_KEY (guárdela aparte)
   ```
   Si una contraseña contiene caracteres especiales, use `openssl rand -hex 32` para evitar problemas de interpolación.
4. Levantar:
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
   ```
5. Ver logs: `docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f api` (también `caddy`, `db`).
6. Actualizar de versión:
   ```bash
   git pull
   docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
   ```
   Nunca use `down -v` en producción: borra la base.

### Verificación
- `curl https://{dominio}/actuator/health` responde `{"status":"UP",...}`.
- `curl -I http://{dominio}` responde 308 con `Location: https://{dominio}/`.
- `https://{dominio}/swagger-ui.html` y `/v3/api-docs` no responden contenido.
- La base no responde desde Internet; desde **otra máquina**:
  ```bash
  nmap -Pn -p 22,80,443,5432,5433,8080 {dominio}   # solo 22, 80 y 443 abiertos
  nc -zv -w 3 {dominio} 5432                         # debe fallar/expirar
  ```
- En el servidor: `docker compose -f docker-compose.yml -f docker-compose.prod.yml config` no debe mostrar `ports` en `api` ni `db`.

Prueba local del stack de producción: `DOMAIN=localhost` en el entorno (Caddy emite un certificado interno; use `curl -k`), y detener con `down` (sin `-v`).

### Copias de seguridad
`scripts/backup.sh` (bash) ejecuta `pg_dump -Fc` dentro del contenedor `db` y guarda `backups/manaure-AAAA-MM-DD-HHMM.dump`. Lee `DB_NAME` y `DB_USER` de `.env` (no usa ni imprime contraseñas), falla con mensaje claro si falta algo y conserva solo las últimas `BACKUP_KEEP` copias locales (por defecto 7).
```bash
./scripts/backup.sh
```
Programación diaria con cron (`crontab -e`, 03:00):
```
0 3 * * * cd /ruta/al/proyecto && ./scripts/backup.sh >> backups/backup.log 2>&1
```
**Las copias deben guardarse FUERA del servidor**: una copia en el mismo VPS se pierde con él. El script no envía nada por sí mismo; defina `BACKUP_UPLOAD_CMD` (en `.env` o en el entorno) con un comando que reciba la ruta del archivo en `$1`, por ejemplo `rclone copyto "$1" remoto:manaure-backups/$(basename "$1")` o `scp "$1" usuario@otro-servidor:/copias/`. Configure usted las credenciales de ese destino (rclone config, llave SSH); no van en el repositorio. Alternativa: descargar `backups/` periódicamente a otro equipo.

### Restaurar una copia
Desde la carpeta del proyecto (la restauración reemplaza los datos actuales):
```bash
C="docker compose -f docker-compose.yml -f docker-compose.prod.yml"
$C stop api
$C exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner' < backups/manaure-AAAA-MM-DD-HHMM.dump
$C start api
```
Para una base nueva y vacía basta con levantar solo `db` y ejecutar el `pg_restore` anterior; luego `up -d`. Compruebe con `/actuator/health`.
