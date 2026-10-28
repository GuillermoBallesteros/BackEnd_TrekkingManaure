#!/usr/bin/env bash
# Copia de seguridad de la base (pg_dump formato custom) vía docker compose.
# Uso: ./scripts/backup.sh   (desde cualquier carpeta; usa la raíz del proyecto)
# Variables (en .env o entorno): DB_NAME, DB_USER (obligatorias); BACKUP_KEEP (def. 7);
# BACKUP_UPLOAD_CMD (opcional, recibe la ruta del archivo como $1).
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$RAIZ"

fallar() { echo "ERROR: $*" >&2; exit 1; }

[ -f .env ] || fallar "no existe $RAIZ/.env"

# Lee solo las claves necesarias, sin ejecutar el archivo ni imprimir valores.
leer() { grep -E "^$1=" .env | tail -n1 | cut -d= -f2- | sed -e 's/^"//' -e 's/"$//' -e "s/^'//" -e "s/'\$//" | tr -d '\r' || true; }

DB_NAME="${DB_NAME:-$(leer DB_NAME)}"
DB_USER="${DB_USER:-$(leer DB_USER)}"
BACKUP_KEEP="${BACKUP_KEEP:-$(leer BACKUP_KEEP)}"
BACKUP_KEEP="${BACKUP_KEEP:-7}"
BACKUP_UPLOAD_CMD="${BACKUP_UPLOAD_CMD:-$(leer BACKUP_UPLOAD_CMD)}"

[ -n "$DB_NAME" ] || fallar "DB_NAME no está definido en .env"
[ -n "$DB_USER" ] || fallar "DB_USER no está definido en .env"
[[ "$BACKUP_KEEP" =~ ^[1-9][0-9]*$ ]] || fallar "BACKUP_KEEP debe ser un entero positivo"
command -v docker >/dev/null || fallar "docker no está instalado"

COMPOSE=(docker compose -f docker-compose.yml -f docker-compose.prod.yml)
"${COMPOSE[@]}" ps --status running --services 2>/dev/null | grep -qx db \
  || fallar "el contenedor db no está en ejecución"

mkdir -p backups
ARCHIVO="backups/manaure-$(date +%Y-%m-%d-%H%M).dump"
TMP="$ARCHIVO.tmp"
trap 'rm -f "$TMP"' EXIT

# La clave no se pasa ni se imprime: pg_dump corre dentro del contenedor con conexión local (socket).
"${COMPOSE[@]}" exec -T db pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc > "$TMP" \
  || fallar "pg_dump falló"
[ -s "$TMP" ] || fallar "la copia quedó vacía"
mv "$TMP" "$ARCHIVO"
trap - EXIT
echo "Copia creada: $ARCHIVO"

# Conserva solo las BACKUP_KEEP más recientes.
ls -1t backups/manaure-*.dump 2>/dev/null | tail -n +"$((BACKUP_KEEP + 1))" | while read -r viejo; do
  rm -f -- "$viejo"
  echo "Eliminada copia antigua: $viejo"
done

if [ -n "$BACKUP_UPLOAD_CMD" ]; then
  echo "Enviando copia fuera del servidor..."
  bash -c "$BACKUP_UPLOAD_CMD" _ "$ARCHIVO" || fallar "BACKUP_UPLOAD_CMD falló (la copia local se conserva)"
  echo "Envío completado."
else
  echo "AVISO: BACKUP_UPLOAD_CMD no definido; la copia solo está en este servidor." >&2
fi
