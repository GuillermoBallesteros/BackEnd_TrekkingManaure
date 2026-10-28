-- El mensaje de WhatsApp lo arma el front; el backend ya no guarda el numero.
-- El CHECK de la columna (definido en linea en V1) se elimina junto con ella.
ALTER TABLE configuracion_evento DROP COLUMN whatsapp_numero;
