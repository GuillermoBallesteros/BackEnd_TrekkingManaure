package co.manaure.trekking.seguridad.dto;

/** Cuerpo de {@code POST /api/v1/auth/login}. La validación la hace el servicio con los textos del front. */
public record LoginRequest(String correo, String clave, Boolean recordar) {

	/** Nunca imprime la clave. */
	@Override
	public String toString() {
		return "LoginRequest[correo=***, clave=***, recordar=" + recordar + "]";
	}

}
