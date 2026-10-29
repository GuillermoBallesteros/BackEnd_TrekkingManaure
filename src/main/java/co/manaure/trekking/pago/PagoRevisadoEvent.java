package co.manaure.trekking.pago;

/**
 * Un pago cambió de revisión (encargada, caducidad o pago manual). Se publica dentro de la transacción; los avisos
 * por correo (HU-N15) lo atienden solo después del commit.
 */
public record PagoRevisadoEvent(String codigoPago, RevisionPago revision) {
}
