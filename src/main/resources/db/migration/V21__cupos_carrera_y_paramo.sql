-- Cupos de la carrera (60, uno por reserva) separados de los del páramo (30 camas, una por persona).
ALTER TABLE configuracion_evento
    ADD COLUMN cupos_carrera INT NOT NULL DEFAULT 60,
    ADD CONSTRAINT ck_configuracion_cupos_carrera CHECK (cupos_carrera >= 1);

UPDATE configuracion_evento
SET cupos_carrera = 60,
    cupos_paramo = 30
WHERE id = 1;
