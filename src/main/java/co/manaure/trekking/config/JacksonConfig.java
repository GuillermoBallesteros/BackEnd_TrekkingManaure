package co.manaure.trekking.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * Sin coacción de tipos escalares (diseño §6): un número o un booleano donde va texto, o un texto o número donde
 * va un booleano, da 400 en lugar de convertirse en silencio.
 */
@Configuration
class JacksonConfig {

	@Bean
	JsonMapperBuilderCustomizer sinCoaccionDeEscalares() {
		return builder -> builder
				.withCoercionConfig(LogicalType.Textual,
						c -> c.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
								.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
								.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
				.withCoercionConfig(LogicalType.Boolean,
						c -> c.setCoercion(CoercionInputShape.String, CoercionAction.Fail)
								.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
								.setCoercion(CoercionInputShape.Float, CoercionAction.Fail));
	}

}
