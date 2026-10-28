package co.manaure.trekking.seguridad;

import java.util.Locale;

import org.springframework.web.util.UrlPathHelper;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Ruta de la solicitud normalizada para decisiones de seguridad (límite de frecuencia, cabeceras): sin contexto,
 * decodificada (%6c = l), sin parámetros de ruta (;jsessionid), sin barras repetidas ni final, en minúsculas.
 * Así las variantes de una misma ruta reciben el mismo trato que la ruta canónica.
 */
final class RutaSolicitud {

	private static final UrlPathHelper HELPER = new UrlPathHelper();

	private RutaSolicitud() {
	}

	static String normalizada(HttpServletRequest request) {
		String ruta;
		try {
			ruta = HELPER.getPathWithinApplication(request);
		}
		catch (RuntimeException ex) {
			ruta = request.getRequestURI();
		}
		return normalizar(ruta);
	}

	static String normalizar(String ruta) {
		if (ruta == null) {
			return "/";
		}
		StringBuilder sb = new StringBuilder();
		for (String segmento : ruta.split("/")) {
			int pc = segmento.indexOf(';');
			String limpio = pc >= 0 ? segmento.substring(0, pc) : segmento;
			if (limpio.isEmpty() || limpio.equals(".")) {
				continue;
			}
			if (limpio.equals("..")) {
				int ult = sb.lastIndexOf("/");
				sb.setLength(Math.max(ult, 0));
				continue;
			}
			sb.append('/').append(limpio);
		}
		return sb.isEmpty() ? "/" : sb.toString().toLowerCase(Locale.ROOT);
	}

}
