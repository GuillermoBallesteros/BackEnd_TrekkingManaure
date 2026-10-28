package co.manaure.trekking.regla.modelo;

/** Consentimiento: fecha-hora de aceptación (texto), versión de términos y uso de imagen. */
public record Consentimiento(String aceptadoEn, String version, Boolean usoImagen) {
}
