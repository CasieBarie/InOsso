package dev.casiebarie.inosso.utils;

import dev.casiebarie.inosso.ClassLoader;
import dev.casiebarie.inosso.Main;
import dev.casiebarie.inosso.utils.logging.Logger;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.attribute.IWebhookContainer;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static dev.casiebarie.inosso.Main.jda;
import static dev.casiebarie.inosso.Main.safeRunnable;
import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class WebhookManager extends ListenerAdapter {
	static Map<String, Webhook> webhooks = new HashMap<>();
	boolean isStarted = false;
	static boolean isInitialized = false;
	static void setIsInitialized() {WebhookManager.isInitialized = true;}
	public WebhookManager(@NotNull ClassLoader classes) {classes.registerAsEventListener(this);}

	@Override
	public void onGuildReady(GuildReadyEvent e) {
		if(isStarted) {return;}
		isStarted = true;
		Main.scheduledPool.scheduleAtFixedRate(safeRunnable(() -> {
			getLogger().debug("Updating webhooks");
			jda().getGuilds().forEach(guild -> {
				try {guild.retrieveWebhooks().complete().stream()
					.filter(webhook -> webhook.getOwner().getId().equals(guild.getSelfMember().getId()))
					.forEach(webhook -> webhooks.put(webhook.getChannel().getId() + "|" + webhook.getName(), webhook));
				} catch(ErrorResponseException ignored) {/*IGNORED*/}
			}); setIsInitialized();
		}), 0, 10, TimeUnit.MINUTES);
	}

	public static @Nullable Webhook getWebhook(@NotNull IWebhookContainer channel, String name) {
		String id = channel.getId() + "|InOsso-" + name;
		if(!isInitialized) {return null;}
		if(webhooks.containsKey(id)) {return webhooks.get(id);
		} else {return createWebhook(channel, name);}
	}

	private static @Nullable Webhook createWebhook(IWebhookContainer channel, String name) {
		InputStream is = WebhookManager.class.getClassLoader().getResourceAsStream("images/inosso.png");
		try {
			Webhook webhook = channel.createWebhook("InOsso-" + name).setAvatar(Icon.from(is)).complete();
			webhooks.put(channel.getId() + "|" + webhook.getName(), webhook);
			getLogger().debug("Created webhook {} in {}", Logger.getWebhookNameAndId(webhook), Logger.getGuildNameAndId(webhook.getGuild()));
			return webhook;
		} catch(ErrorResponseException ignored) {return null;
		} catch(Exception ex) {getLogger().error("Failed to create webhook", ex); return null;}
	}
}