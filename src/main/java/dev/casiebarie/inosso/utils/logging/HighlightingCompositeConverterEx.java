package dev.casiebarie.inosso.utils.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.color.ANSIConstants;
import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase;
import org.jetbrains.annotations.NotNull;

public class HighlightingCompositeConverterEx extends ForegroundCompositeConverterBase<ILoggingEvent> {
	@Override
	protected String getForegroundColorCode(@NotNull ILoggingEvent event) {
		Level level = event.getLevel();
		return switch(level.toInt()) {
			case Level.ERROR_INT -> ANSIConstants.BOLD + ANSIConstants.RED_FG;
			case Level.WARN_INT -> ANSIConstants.YELLOW_FG;
			case Level.INFO_INT -> ANSIConstants.CYAN_FG;
			case Level.DEBUG_INT -> ANSIConstants.WHITE_FG;
			default -> ANSIConstants.DEFAULT_FG;
		};
	}
}