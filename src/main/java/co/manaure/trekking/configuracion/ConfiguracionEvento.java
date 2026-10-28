package co.manaure.trekking.configuracion;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Configuración del evento: singleton con id = 1 (la base rechaza otra fila). Dinero en pesos enteros. */
@Entity
@Table(name = "configuracion_evento")
public class ConfiguracionEvento {

	public static final short ID = 1;

	@Id
	private Short id;

	@Column(name = "nombre_evento", nullable = false)
	private String nombreEvento;

	@Column(name = "fecha_evento_inicio", nullable = false)
	private LocalDate fechaEventoInicio;

	@Column(name = "fecha_evento_fin", nullable = false)
	private LocalDate fechaEventoFin;

	@Column(name = "fecha_apertura", nullable = false)
	private LocalDate fechaApertura;

	@Column(name = "fecha_cierre", nullable = false)
	private LocalDate fechaCierre;

	@Column(name = "cupos_paramo", nullable = false)
	private int cuposParamo;

	@Column(name = "precio_titular", nullable = false)
	private long precioTitular;

	@Column(name = "valor_apartado", nullable = false)
	private long valorApartado;

	@Column(name = "precio_acompanante_sin_kit", nullable = false)
	private long precioAcompananteSinKit;

	@Column(name = "precio_acompanante_con_kit", nullable = false)
	private long precioAcompananteConKit;

	@Column(name = "precio_cuatrimoto", nullable = false)
	private long precioCuatrimoto;

	@Column(name = "precio_parapente", nullable = false)
	private long precioParapente;

	@Column(name = "precio_hotel_noche", nullable = false)
	private long precioHotelNoche;

	@Column(name = "version_terminos", nullable = false)
	private String versionTerminos;

	@Column(name = "actualizado_por")
	private Long actualizadoPor;

	@Column(name = "actualizado_en")
	private OffsetDateTime actualizadoEn;

	protected ConfiguracionEvento() {
	}

	public Short getId() { return id; }
	public String getNombreEvento() { return nombreEvento; }
	public LocalDate getFechaEventoInicio() { return fechaEventoInicio; }
	public LocalDate getFechaEventoFin() { return fechaEventoFin; }
	public LocalDate getFechaApertura() { return fechaApertura; }
	public LocalDate getFechaCierre() { return fechaCierre; }
	public int getCuposParamo() { return cuposParamo; }
	public long getPrecioTitular() { return precioTitular; }
	public long getValorApartado() { return valorApartado; }
	public long getPrecioAcompananteSinKit() { return precioAcompananteSinKit; }
	public long getPrecioAcompananteConKit() { return precioAcompananteConKit; }
	public long getPrecioCuatrimoto() { return precioCuatrimoto; }
	public long getPrecioParapente() { return precioParapente; }
	public long getPrecioHotelNoche() { return precioHotelNoche; }
	public String getVersionTerminos() { return versionTerminos; }
	public Long getActualizadoPor() { return actualizadoPor; }
	public OffsetDateTime getActualizadoEn() { return actualizadoEn; }

	/** HU-N11: cambia los cupos del páramo y deja constancia de quién y cuándo. */
	public void fijarCupos(int cupos, Long administradorId, OffsetDateTime cuando) {
		this.cuposParamo = cupos;
		this.actualizadoPor = administradorId;
		this.actualizadoEn = cuando;
	}

	/** HU-N12: cambia la fecha de cierre y deja constancia de quién y cuándo. */
	public void fijarFechaCierre(LocalDate fecha, Long administradorId, OffsetDateTime cuando) {
		this.fechaCierre = fecha;
		this.actualizadoPor = administradorId;
		this.actualizadoEn = cuando;
	}

	/** El kit completo no se guarda: es el precio del titular menos el apartado. */
	public long getKitCompleto() { return precioTitular - valorApartado; }

}
