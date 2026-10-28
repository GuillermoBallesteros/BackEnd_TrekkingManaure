-- HU-N01: se retira el modelo anterior (V1 a V8). Los datos de desarrollo se pierden; no hay produccion.
-- Se conserva la extension unaccent (V5).
DROP TABLE IF EXISTS auditoria;
DROP TABLE IF EXISTS abono;
DROP TABLE IF EXISTS ficha_salud;
DROP TABLE IF EXISTS persona;
DROP TABLE IF EXISTS inscripcion;
-- configuracion_evento referencia a administrador (V8): va primero.
DROP TABLE IF EXISTS configuracion_evento;
DROP TABLE IF EXISTS administrador;
DROP SEQUENCE IF EXISTS inscripcion_codigo_seq;
