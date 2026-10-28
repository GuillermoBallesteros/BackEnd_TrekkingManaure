package co.manaure.trekking.reserva;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Inscripción (reserva): plan de pago, precios aplicados al registrar (RN-03), total y día de registro.
 * Dinero en pesos enteros (Long).
 */
@Entity
@Table(name = "inscripcion")
public class Inscripcion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** PER-0001; es el id del JSON. */
	@Column(name = "codigo", nullable = false, length = 12, updatable = false)
	private String codigo;

	@Enumerated(EnumType.STRING)
	@Column(name = "plan_pago", nullable = false, length = 10)
	private PlanPago planPago;

	@Column(name = "fecha_compromiso")
	private LocalDate fechaCompromiso;

	@Column(name = "autorizacion_menor", nullable = false)
	private boolean autorizacionMenor;

	@Column(name = "activa", nullable = false)
	private boolean activa = true;

	@Column(name = "precio_titular_aplicado", nullable = false)
	private long precioTitularAplicado;

	@Column(name = "precio_apartado_aplicado", nullable = false)
	private long precioApartadoAplicado;

	@Column(name = "precio_acompanante_sin_kit_aplicado", nullable = false)
	private long precioAcompananteSinKitAplicado;

	@Column(name = "precio_acompanante_con_kit_aplicado", nullable = false)
	private long precioAcompananteConKitAplicado;

	@Column(name = "precio_cuatrimoto_aplicado", nullable = false)
	private long precioCuatrimotoAplicado;

	@Column(name = "precio_parapente_aplicado", nullable = false)
	private long precioParapenteAplicado;

	@Column(name = "precio_hotel_noche_aplicado", nullable = false)
	private long precioHotelNocheAplicado;

	@Column(name = "total", nullable = false)
	private long total;

	/** Día de negocio en America/Bogota (JSON createdAt). */
	@Column(name = "fecha_registro", nullable = false)
	private LocalDate fechaRegistro;

	@Column(name = "creado_en", insertable = false, updatable = false)
	private OffsetDateTime creadoEn;

	protected Inscripcion() {
	}

	public Inscripcion(String codigo, PlanPago planPago, LocalDate fechaCompromiso, long precioTitularAplicado,
			long precioApartadoAplicado, long precioAcompananteSinKitAplicado, long precioAcompananteConKitAplicado,
			long precioCuatrimotoAplicado, long precioParapenteAplicado, long precioHotelNocheAplicado, long total,
			LocalDate fechaRegistro) {
		this.codigo = codigo;
		this.planPago = planPago;
		this.fechaCompromiso = fechaCompromiso;
		this.precioTitularAplicado = precioTitularAplicado;
		this.precioApartadoAplicado = precioApartadoAplicado;
		this.precioAcompananteSinKitAplicado = precioAcompananteSinKitAplicado;
		this.precioAcompananteConKitAplicado = precioAcompananteConKitAplicado;
		this.precioCuatrimotoAplicado = precioCuatrimotoAplicado;
		this.precioParapenteAplicado = precioParapenteAplicado;
		this.precioHotelNocheAplicado = precioHotelNocheAplicado;
		this.total = total;
		this.fechaRegistro = fechaRegistro;
	}

	/** La mantiene el servicio según el estado derivado (RN-06). */
	public void setActiva(boolean activa) { this.activa = activa; }
	public void setAutorizacionMenor(boolean autorizacionMenor) { this.autorizacionMenor = autorizacionMenor; }

	public Long getId() { return id; }
	public String getCodigo() { return codigo; }
	public PlanPago getPlanPago() { return planPago; }
	public LocalDate getFechaCompromiso() { return fechaCompromiso; }
	public boolean isAutorizacionMenor() { return autorizacionMenor; }
	public boolean isActiva() { return activa; }
	public long getPrecioTitularAplicado() { return precioTitularAplicado; }
	public long getPrecioApartadoAplicado() { return precioApartadoAplicado; }
	public long getPrecioAcompananteSinKitAplicado() { return precioAcompananteSinKitAplicado; }
	public long getPrecioAcompananteConKitAplicado() { return precioAcompananteConKitAplicado; }
	public long getPrecioCuatrimotoAplicado() { return precioCuatrimotoAplicado; }
	public long getPrecioParapenteAplicado() { return precioParapenteAplicado; }
	public long getPrecioHotelNocheAplicado() { return precioHotelNocheAplicado; }
	public long getTotal() { return total; }
	public LocalDate getFechaRegistro() { return fechaRegistro; }
	public OffsetDateTime getCreadoEn() { return creadoEn; }

}
