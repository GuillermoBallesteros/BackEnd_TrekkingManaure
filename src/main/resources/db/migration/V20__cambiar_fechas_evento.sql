-- El evento pasa del 15 y 16 al 28 y 29 de noviembre de 2026. La fecha de cierre no cambia: se ajusta desde el panel.
UPDATE configuracion_evento
SET fecha_evento_inicio = DATE '2026-11-28',
    fecha_evento_fin = DATE '2026-11-29'
WHERE id = 1;
