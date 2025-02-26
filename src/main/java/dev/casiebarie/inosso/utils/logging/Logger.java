package dev.casiebarie.inosso.utils.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.Channel;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class Logger {
	public Logger() {setFilters();}

	public static org.slf4j.Logger getLogger() {
		String className = new Throwable().getStackTrace()[1].getClassName();
		return LoggerFactory.getLogger("InOsso - " + className.substring(className.lastIndexOf('.') + 1));
	}

	private void setFilters() {
		List<String> filters = new ArrayList<>(List.of(
			"dev.lavalink.youtube", "dev.lavalink.soundcloud", "dev.lavalink.http",
			"com.sedmelluq.lava.common.natives", "com.sedmelluq.lava.common.tools", "com.sedmelluq.discord.lavaplayer"
		));

		LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		ch.qos.logback.classic.Logger rootLogger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		Level rootLevel = rootLogger.getLevel();

		if(rootLevel != Level.DEBUG && rootLevel != Level.INFO) {throw new IllegalArgumentException("Log level should at least be INFO");}
		getLogger().info("Starting application with log level: {}", rootLevel.levelStr);

		for(String filter : filters) {
			ch.qos.logback.classic.Logger logger = context.getLogger(filter);
			if(rootLevel == Level.DEBUG) {logger.setLevel(Level.DEBUG);
			} else {logger.setLevel(Level.WARN);}
		}
	}

	public static void debug(org.slf4j.@NotNull Logger logger, String message, Supplier<Object[]> argsSupplier) {if(logger.isDebugEnabled()) {logger.debug(message, argsSupplier.get());}}
	public static @NotNull String getGuildNameAndId(@NotNull Guild guild) {return guild.getName() + "(" + guild.getId() + ")";}
	public static @NotNull String getUserNameAndId(@NotNull User user) {return user.getName() + "(" + user.getId() + ")";}
	public static @NotNull String getWebhookNameAndId(@NotNull Webhook webhook) {return webhook.getName() + "(" + webhook.getId() + ")";}
	public static @NotNull String getChannelNameAndId(@NotNull Channel channel) {return channel.getName() + "(" + channel.getId() + ")";}
	public static @NotNull String getRoleNameAndId(@NotNull Role role) {return role.getName() + "(" + role.getId() + ")";}
}