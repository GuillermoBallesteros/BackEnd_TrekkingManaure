package co.manaure.trekking.notificacion;

/**
 * Avisos por correo de RN-24. {@code clave} es el nombre de la propiedad de su plantilla en
 * {@code app.brevo.plantillas} (p. ej. {@code pago-3-dias}).
 */
public enum TipoAviso {

	PAGO_3_DIAS("pago-3-dias"),
	PAGO_1_DIA("pago-1-dia"),
	PAGO_HOY("pago-hoy"),
	PAGO_VENCIDO("pago-vencido"),
	PAGO_APROBADO("pago-aprobado"),
	PAGO_RECHAZADO("pago-rechazado"),
	EVENTO_MANANA("evento-manana");

	private final String clave;

	TipoAviso(String clave) {
		this.clave = clave;
	}

	public String clave() {
		return clave;
	}

}
