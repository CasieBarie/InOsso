package dev.casiebarie.inosso.utils.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import dev.casiebarie.inosso.utils.Utils;
import net.dv8tion.jda.api.EmbedBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;

public class DiscordLogger extends AppenderBase<ILoggingEvent> {
	@Override
	protected void append(@NotNull ILoggingEvent e) {
		if(!e.getLevel().isGreaterOrEqual(Level.WARN)) {return;}
		boolean isError = e.getLevel() == Level.ERROR;

		EmbedBuilder eb = new EmbedBuilder()
			.setColor(isError ? Color.RED : Color.ORANGE);

		StringBuilder builder = new StringBuilder(isError ? "# :no_entry: Error :no_entry:" : "# :warning: Warning :warning:");
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss.SSS");
		String timestamp = new Timestamp(e.getTimeStamp()).toLocalDateTime().format(formatter);
		builder.append("\n**Timestamp:** `").append(timestamp).append("`");

		builder.append("\n**Thread:** `").append(e.getThreadName()).append("`");
		builder.append("\n**Name:** `").append(e.getLoggerName()).append("`");
		builder.append("\n**Message:** `").append(e.getFormattedMessage()).append("`");

		IThrowableProxy proxy = e.getThrowableProxy();
		if(proxy != null) {builder.append("\n**Stacktrace:** ```java\n").append(formatStackTraceForEmbed(proxy)).append("\n```");}

		eb.setDescription(builder.toString());
		Utils.getCasAsUser().openPrivateChannel().queue(channel -> channel.sendMessageEmbeds(eb.build()).queue(null, null));
	}

	private @NotNull String formatStackTraceForEmbed(@Nullable IThrowableProxy proxy) {
		StringBuilder builder = new StringBuilder();
		while(proxy != null) {
			builder.append(proxy.getClassName()).append(": ").append(proxy.getMessage()).append("\n");
			for(StackTraceElementProxy ste : proxy.getStackTraceElementProxyArray()) {builder.append(ste.getSTEAsString()).append("\n");}
			proxy = proxy.getCause();
			if(proxy != null) builder.append("Caused by: ");
		} return Utils.truncate(builder.toString(), 1900);
	}
}