package dev.casiebarie.inosso.jachtseizoen;

import dev.casiebarie.inosso.enums.Channels;
import dev.casiebarie.inosso.utils.ReplyOperation;
import dev.casiebarie.inosso.utils.Utils;
import dev.casiebarie.inosso.utils.WebhookManager;
import dev.casiebarie.inosso.utils.logging.Logger;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static dev.casiebarie.inosso.Main.jda;
import static dev.casiebarie.inosso.enums.Variables.*;
import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class GuildManager {
	int gameStatus = 0; // 0=NotPlaying, 1=PlayingRunners, 2=Playing
	final String guildId;
	final Jachtseizoen jacht;
	ActionRow currentActionRow;
	String messageId, webhookName;
	MessageEmbed currentRules, currentGame;
	boolean shouldUpdate = false, isPaused = false;
	long startTime, inGameTime, totalPausedTime, pauseStartTime, nextLookTime, remainingLookCooldown, lastUpdate = 0;
	public GuildManager(Jachtseizoen jacht, @NotNull Guild guild) {
		this.jacht = jacht;
		this.guildId = guild.getId();
	}

	protected void onCommand(@NotNull ReplyOperation o) {
		Guild guild = o.e.getGuild();
		TextChannel channel = Channels.MAIN.getAsChannel(guild);
		currentRules = jacht.createRules(o.e.getMember());
		updateButtons();

		Webhook webhook = WebhookManager.getWebhook(channel, "Jachtseizoen");
		if(webhook == null) {o.sendFailed("Kan jachtseizoen bericht niet versturen!"); return;}
		webhookName = guild.getSelfMember().getEffectiveName() + " -  Jachtseizoen👮";

		webhook.sendMessageEmbeds(currentRules)
			.setUsername(webhookName)
			.setFiles(Utils.loadImage("jachtseizoen.png"), Utils.loadImage("empty.png"))
			.setComponents(currentActionRow)
		.queue(msg -> {messageId = msg.getId(); o.replyEmpty();}, o::sendFailed);
	}

	protected void onStartButton(ReplyOperation o) {
		if(gameStatus != 0) {o.sendFailed("Actie geannuleerd! Je was te snel."); return;}
		startTime = System.currentTimeMillis();
		gameStatus = 1;
		totalPausedTime = 0;
		nextLookTime = startTime + JACHT_HUNTER_WAIT_TIME;
		updateButtons();
		shouldUpdate = true;
		o.replyEmpty();
	}

	protected void onRerollButton(ReplyOperation o) {
		if(gameStatus != 0) {o.sendFailed("Actie geannuleerd! Je was te snel."); return;}
		currentRules = jacht.createRules(o.e.getMember());
		shouldUpdate = true;
		TextChannel channel = Channels.MAIN.getAsChannel(o.e.getGuild());
		Webhook webhook = WebhookManager.getWebhook(channel, "Jachtseizoen");
		new ReplyOperation(webhook, webhookName).sendSuccess("Regels zijn veranderd door " + Utils.getAsMention(o.e.getMember()) + ".");
		o.replyEmpty();
	}

	protected void onStopButton(@NotNull ReplyOperation o) {
		o.replyEmpty();
		jacht.stopPlaying(o.e.getGuild(), false);
	}

	protected void onCancelButton(@NotNull ReplyOperation o) {
		jacht.stopPlaying(o.e.getGuild(), true);
		TextChannel channel = Channels.MAIN.getAsChannel(o.e.getGuild());
		Webhook webhook = WebhookManager.getWebhook(channel, "Jachtseizoen");
		webhook.deleteMessageById(messageId).queue(msg -> o.replyEmpty(), ReplyOperation::error);
	}

	protected void stopPlaying(Guild guild) {
		gameStatus = -1;

		MessageEmbed endEmbed = new EmbedBuilder()
			.setColor(Color.RED)
			.setDescription("# :no_entry: Spel geëindigd :no_entry:\n- Totale speeltijd: `" + Utils.formatDuration(inGameTime) + "`")
			.setImage("attachment://empty.png")
		.build();

		TextChannel channel = Channels.MAIN.getAsChannel(guild);
		Webhook webhook = WebhookManager.getWebhook(channel, "Jachtseizoen");
		webhook.editMessageEmbedsById(messageId, currentRules, endEmbed).setComponents().queue(null, ReplyOperation::error);
	}

	protected void onPauseButton(ReplyOperation o) {
		long now = System.currentTimeMillis();
		if(!isPaused) {
			pauseStartTime = now;
			remainingLookCooldown = Math.max(0, nextLookTime - pauseStartTime);
			isPaused = true;
		} else {
			totalPausedTime += (now - pauseStartTime);
			nextLookTime = now + remainingLookCooldown;
			isPaused = false;
		}

		shouldUpdate = true;
		o.replyEmpty();
	}

	protected void updateMessage() {
		Guild guild = jda().getGuildById(guildId);

		long now = System.currentTimeMillis();
		if(now - lastUpdate < 500) {return;}
		lastUpdate = now;

		checkTimer(guild);
		if(gameStatus == -1) {return;}
		if(!shouldUpdate) {return;}

		TextChannel channel = Channels.MAIN.getAsChannel(guild);
		Webhook webhook = WebhookManager.getWebhook(channel, "Jachtseizoen");

		List<MessageEmbed> embeds = new ArrayList<>();
		embeds.add(currentRules);
		if(currentGame != null) {embeds.add(currentGame);}

		webhook.editMessageEmbedsById(messageId, embeds)
			.setComponents(currentActionRow)
		.queue(success -> shouldUpdate = false, error -> {
			jacht.stopPlaying(guild, false);
			ReplyOperation.error(error);
		});
	}

	private void checkTimer(Guild guild) {
		if(gameStatus == 0 || gameStatus == -1) {return;}
		long now = System.currentTimeMillis();
		inGameTime = (isPaused) ? pauseStartTime - startTime - totalPausedTime : now - startTime - totalPausedTime;
		remainingLookCooldown = (isPaused) ? remainingLookCooldown : Math.max(0, nextLookTime - now);
		if(inGameTime >= JACHT_MAX_DURATION) {jacht.stopPlaying(guild, false); return;}

		if(now >= nextLookTime) {
			sendLook(guild, gameStatus == 1);
			if(gameStatus == 1) {gameStatus = 2;}
			nextLookTime = (now + JACHT_LOOK_TIME) + 15000;
		}

		MessageEmbed newGame = jacht.gameEmbed(gameStatus, isPaused, inGameTime, remainingLookCooldown);
		shouldUpdate = !Objects.equals(currentGame, newGame);
		if(shouldUpdate) {currentGame = newGame;}
	}

	private void sendLook(Guild guild, boolean isFirst) {
		VoiceChannel voice = Channels.VOICE.getAsChannel(guild);
		Webhook webhook = WebhookManager.getWebhook(voice, "Jachtseizoen");
		Logger.debug(getLogger(), "Sending look to {}", () -> new String[] {Logger.getWebhookNameAndId(webhook)});

		MessageEmbed embed = new EmbedBuilder()
			.setColor(Color.decode("#79cfff"))
			.setDescription("# 🚨 JACHTSEIZOEN 🚨\n" + (isFirst ? "### De jager mag vertrekken!" : "### Er mag gekeken worden!"))
			.setFooter("Dit bericht wordt over 10 seconden verwijderd.")
		.build();

		webhook.sendMessage("||@here||")
			.setUsername(webhookName)
			.setEmbeds(embed)
		.queue(msg -> msg.delete().queueAfter(10, TimeUnit.SECONDS, null, ReplyOperation::error), ReplyOperation::error);
	}

	private void updateButtons() {
		currentActionRow = (gameStatus != 0) ? ActionRow.of(
			Button.primary("jachtseizoen-pause", isPaused ? "▶️ Hervat" : "⏸️ Pauzeer"),
			Button.danger("jachtseizoen-stop", "⛔ Stop")
		) : ActionRow.of(
			Button.success("jachtseizoen-start", "✅ Start"),
			Button.secondary("jachtseizoen-reroll", "🎲 Reroll"),
			Button.danger("jachtseizoen-cancel", "⛔ Annuleer")
		);
	}
}