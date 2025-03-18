package dev.casiebarie.inosso.other;

import dev.casiebarie.inosso.ClassLoader;
import dev.casiebarie.inosso.Main;
import dev.casiebarie.inosso.enums.Channels;
import dev.casiebarie.inosso.interfaces.Information;
import dev.casiebarie.inosso.interfaces.ScheduledTask;
import dev.casiebarie.inosso.utils.ReplyOperation;
import dev.casiebarie.inosso.utils.Utils;
import dev.casiebarie.inosso.utils.WebhookManager;
import dev.casiebarie.inosso.utils.logging.Logger;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static dev.casiebarie.inosso.Main.jda;
import static dev.casiebarie.inosso.Main.safeRunnable;
import static dev.casiebarie.inosso.enums.Variables.*;
import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class Request extends ListenerAdapter implements ScheduledTask, Information {
	final ClassLoader classes;
	static final String WEBHOOK_ID = "GateKeeper";
	Map<String, Long> cooldowns = new HashMap<>();
	Map<String, GuildRequestManager> managers = new HashMap<>();
	public Request(@NotNull ClassLoader classes) {
		this.classes = classes;
		classes.registerAsEventListener(this);
		classes.registerAsScheduledTaskClass(this);
		classes.registerAsInformationClass("gasten", this);
	}

	@Override
	public ScheduledFuture<?> startTask(String guildId) {return Main.scheduledPool.scheduleAtFixedRate(safeRunnable(() -> managers.computeIfAbsent(guildId, k -> new GuildRequestManager()).updateMessage(guildId)), 0, Utils.jitter(100), TimeUnit.MILLISECONDS);}

	@Override
	public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent e) {managers.get(e.getGuild().getId()).shouldUpdate = true;}

	@Override
	public void onButtonInteraction(@NotNull ButtonInteractionEvent e) {
		if(!e.getComponentId().startsWith("request_")) {return;}
		e.deferReply(true).queue(null, ReplyOperation::error);
		getLogger().debug("ButtonInteraction with ID {} by {}", e.getComponentId(), Logger.getUserNameAndId(e.getUser()));

		Main.pool.execute(() -> {
			switch(e.getComponentId().split("_")[1].split("-")[0]) {
			case "ask" -> sendRequest(e);
			case "deny" -> answer(e, false);
			case "approve" -> answer(e, true);
			default -> {/*IGNORED*/}}
		});
	}

	@Override
	public void sendInformation(@NotNull ReplyOperation o) {
		Guild guild = o.e.getGuild();
		EmbedBuilder eb = new EmbedBuilder()
			.setColor(Color.RED)
			.setImage(EMPTY_IMAGE)
			.setDescription("# :bust_in_silhouette: Gasten Help :bust_in_silhouette:\n" +
				"Nieuwe leden hebben alleen toegang tot de `Poort🚪` channel. " +
				"Hier zien ze een bericht met wie er in de call zit en kunnen ze op een knop drukken om te vragen of ze mogen meedoen. " +
				"\n### :pray: | Request" +
				"\nAls iemand op de request-knop drukt, wordt er een beltoon afgespeeld in " + Channels.VOICE.getAsMention(guild) + "  en verschijnt er een bericht in " + Channels.MAIN.getAsMention(guild) + ". " +
				"Hierop kun je reageren om te bepalen of diegene mee mag doen. Gasten moeten `" + REQUEST_COOLDOWN_HOURS + "` uur wachten voordat ze opnieuw een request kunnen sturen." +
				"\n### :wrench: | Gast Command" +
				"\nGasten kunnen ook handmatig toegevoegd of verwijderd worden met het `/gast` command.");
		o.e.getHook().sendMessageEmbeds(eb.build()).setFiles(Utils.loadImage(EMPTY_IMAGE_PATH)).queue(null, o::sendFailed);
	}

	private void sendRequest(@NotNull ButtonInteractionEvent e) {
		Guild guild = e.getGuild();
		Member requester = e.getMember();
		ReplyOperation o = new ReplyOperation(e);

		TextChannel channel = Channels.MAIN.getAsChannel(guild);
		List<Member> onlineMembers = new ArrayList<>(Channels.VOICE.getAsChannel(guild).getMembers());
		onlineMembers.remove(guild.getSelfMember());
		if(onlineMembers.isEmpty()) {o.sendFailed("Er is op dit moment niemand online!"); return;}

		if(cooldowns.containsKey(requester.getId()) && cooldowns.get(requester.getId()) >= System.currentTimeMillis()) {o.sendNotAllowed(String.format("Je zit op dit moment in cooldown! Je kan %s weer een request sturen.", TimeFormat.RELATIVE.format(cooldowns.get(requester.getId())))); return;}
		long time = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(REQUEST_COOLDOWN_HOURS);
		cooldowns.put(requester.getId(), time);

		classes.music.playCall(guild, channel);
		Webhook webhook = WebhookManager.getWebhook(channel, WEBHOOK_ID);
		if(webhook == null) {return;}
		webhook.sendMessageEmbeds(requestingEmbed(requester))
			.setUsername(guild.getSelfMember().getEffectiveName() + " | Poortwachter🚪")
			.setFiles(Utils.loadImage(EMPTY_IMAGE_PATH), Utils.loadAvatar(requester.getEffectiveAvatarUrl()))
			.setActionRow(
				Button.success("request_approve-" + requester.getId(), "Ja"),
				Button.danger("request_deny-" + requester.getId(), "Nee"))
		.queue(success -> o.sendSuccess("Succesfully send request!"), o::sendFailed);
	}

	private void answer(ButtonInteractionEvent e, boolean approve) {
		ReplyOperation o = new ReplyOperation(e);
		if(Utils.isGuest(e.getMember(), true)) {o.sendNotAllowed("Gasten hebben geen toestemming om hierop te reageren."); return;}

		classes.music.stopLooping(e.getGuild());
		Member requester = e.getGuild().getMemberById(e.getComponentId().split("-")[1]);
		if(Utils.isGuest(requester, true)) {o.sendFailed("Er heeft al iemand gereageerd."); return;}

		if(approve) {
			try {classes.guest.switchGuest(requester, true).get();
			} catch(InterruptedException | ExecutionException ex) {
				Thread.currentThread().interrupt();
				o.sendFailed(ex);
			}
		}

		e.getMessage().editMessageEmbeds(answeredEmbed(requester, approve)).setComponents().queue(success -> {
			o.replyEmpty();
			getLogger().debug("Switched guest role of {}", Logger.getUserNameAndId(requester.getUser()));
		}, o::sendFailed);
	}

	private @NotNull MessageEmbed requestingEmbed(@NotNull Member requester) {
		return new EmbedBuilder()
			.setDescription("# :pray: Request :pray:\n" +
				requester.getAsMention() + " wil heel graag meedoen." +
				"\n\nWil je hem toelaten?")
			.setColor(Color.decode("#e67e22"))
			.setThumbnail("attachment://avatar.png")
			.setImage(EMPTY_IMAGE)
		.build();
	}

	public static @NotNull MessageEmbed answeredEmbed(Member requester, boolean approve) {
		return new EmbedBuilder()
			.setDescription("# " + (approve ? ":white_check_mark: Toegelaten :white_check_mark:\n" + requester.getAsMention() + " is toegelaten!\n\nHeel veel plezier!" : ":x: Afgewezen :x:\n" + requester.getAsMention() + " is niet toegelaten!\n\nJammer joh..."))
			.setColor((approve) ? Color.GREEN : Color.RED)
			.setThumbnail("attachment://avatar.png")
			.setImage(EMPTY_IMAGE)
		.build();
	}

	class GuildRequestManager {
		long lastUpdate = 0;
		String messageId = "0";
		boolean shouldUpdate = true, notFound = false, initializing = false;

		private void updateMessage(String guildId) {
			Guild guild = jda().getGuildById(guildId);
			try {
				if(initializing) {return;}
				if(notFound) {findMessage(guild); return;}
				if(!shouldUpdate) {return;}

				TextChannel channel = Channels.REQUEST.getAsChannel(guild);
				Webhook webhook = WebhookManager.getWebhook(channel, WEBHOOK_ID);
				if(webhook == null) {return;}

				long now = System.currentTimeMillis();
				if(now - lastUpdate < 500) {return;}
				lastUpdate = now;

				List<Member> onlineMembers = new ArrayList<>(Channels.VOICE.getAsChannel(guild).getMembers());
				onlineMembers.remove(guild.getSelfMember());

				webhook.editMessageEmbedsById(messageId, requestEmbed(onlineMembers))
					.setActionRow(Button.primary("request_ask-ask", String.format("%s Please, let me pass!", Emoji.fromFormatted("🙏"))).withDisabled(onlineMembers.isEmpty()))
				.queue(success -> shouldUpdate = false, new ErrorHandler()
					.handle(ErrorResponse.UNKNOWN_MESSAGE, error -> notFound = true)
					.andThen(ReplyOperation::error)
				);
			} catch(RejectedExecutionException ignored) {/*IGNORED*/
			} catch(Exception ex) {getLogger().error(ex.getMessage(), ex);}
		}

		private void findMessage(Guild guild) {
			initializing = true;
			shouldUpdate = true;
			TextChannel channel = Channels.REQUEST.getAsChannel(guild);
			Message message = channel.getHistoryFromBeginning(3).complete().getRetrievedHistory().stream().filter(Message::isWebhookMessage).findFirst().orElse(null);

			if(message == null) {setupMessage(guild); return;}
			messageId = message.getId();
			notFound = false;
			initializing = false;
		}

		private @NotNull MessageEmbed requestEmbed(@NotNull List<Member> onlineMembers) {
			StringBuilder desc = new StringBuilder("# :pray: Request :pray:" +
				"\nJe hebt op dit moment geen toegang. Druk op de knop hieronder om te vragen of je mag meedoen. Dit kan alleen als er iemand online is!" +
				"\n\n**Nu online: (" + onlineMembers.size() + ")**");
			onlineMembers.forEach(member -> desc.append("\n- ").append(member.getAsMention()));
			return new EmbedBuilder()
				.setDescription(desc.toString())
				.setColor(Color.decode("#e67e22"))
				.setImage("attachment://notpass.png")
				.setFooter("Request heeft een cooldown van " + REQUEST_COOLDOWN_HOURS + " uur.")
			.build();
		}

		private void setupMessage(Guild guild) {
			TextChannel channel = Channels.REQUEST.getAsChannel(guild);
			Webhook webhook = WebhookManager.getWebhook(channel, WEBHOOK_ID);
			if(webhook == null) {return;}
			String webhookName = guild.getSelfMember().getEffectiveName() + " |  Poortwachter🚪";

			List<Member> onlineMembers = new ArrayList<>(Channels.VOICE.getAsChannel(guild).getMembers());
			onlineMembers.remove(guild.getSelfMember());

			channel.getIterableHistory().takeAsync(10).thenApply(channel::purgeMessages).whenComplete((success, error) ->
			webhook.sendMessageEmbeds(requestEmbed(onlineMembers))
				.setUsername(webhookName)
				.setActionRow(Button.primary("request_ask-ask", String.format("%s Please, let me pass!", Emoji.fromFormatted("🙏"))).withDisabled(onlineMembers.isEmpty()))
				.setFiles(Utils.loadImage("notpass.png"))
			.queue(msg -> {
				messageId = msg.getId();
				notFound = false;
				initializing = false;
				getLogger().info("A new request message has been created");
			}, ReplyOperation::error));
		}
	}
}