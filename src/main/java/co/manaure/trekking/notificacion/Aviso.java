package co.manaure.trekking.notificacion;

import co.manaure.trekking.reserva.RolParticipante;

/**
 * Un aviso que toca enviar: su tipo, la referencia que lo hace único dentro de la inscripción y la persona que lo
 * recibe. Los avisos de pago van siempre al titular; la víspera del evento, a cada persona.
 */
public record Aviso(TipoAviso tipo, String referencia, RolParticipante destinatario) {
}
