package dev.casiebarie.inosso.music;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.casiebarie.inosso.ClassLoader;
import dev.casiebarie.inosso.Main;
import dev.casiebarie.inosso.enums.Channels;
import dev.casiebarie.inosso.music.lavaplayer.PlayerManager;
import dev.casiebarie.inosso.utils.ReplyOperation;
import dev.casiebarie.inosso.utils.Utils;
import dev.casiebarie.inosso.utils.WebhookManager;
import dev.casiebarie.inosso.utils.logging.Logger;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static dev.casiebarie.inosso.enums.Variables.EMPTY_IMAGE;
import static dev.casiebarie.inosso.enums.Variables.EMPTY_IMAGE_PATH;
import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class Search extends ListenerAdapter {
	final Music music;
	static final String WEBHOOK_ID = "Search";
	Map<String, String> messageIds = new HashMap<>();
	Map<String, List<AudioTrack>> searchOptions = new HashMap<>();
	public Search(@NotNull ClassLoader classes, Music music) {
		this.music = music;
		classes.registerAsEventListener(this);
	}

	@Override
	public void onMessageReceived(@NotNull MessageReceivedEvent e) {
		if(!e.isFromGuild() || e.getAuthor().isBot()) {return;}
		Guild guild = e.getGuild();
		Member sender = e.getMember();
		String webhookName = guild.getSelfMember().getEffectiveName() + " |  Muziek Zoeker🔎";
		TextChannel channel = Channels.MUSIC.getAsChannel(guild);

		if(!e.getGuildChannel().equals(channel)) {return;}
		Logger.debug(getLogger(), "Music search message received by {}", () -> new String[] {Logger.getUserNameAndId(sender.getUser())});
		e.getMessage().delete().queue(null, ReplyOperation::error);

		Main.pool.execute(() -> {
			Webhook webhook = WebhookManager.getWebhook(channel, WEBHOOK_ID);
			if(webhook == null) {return;}
			ReplyOperation o = new ReplyOperation(webhook, webhookName);

			if(!Utils.isInVoice(sender, o)) {return;}
			if(messageIds.containsKey(sender.getId())) {o.sendNotAllowed("Je bent al aan het zoeken!"); return;}

			String msg = e.getMessage().getContentRaw();
			boolean isYoutube = !Utils.isAudioUrl(msg);

			EmbedBuilder eb = new EmbedBuilder()
				.setColor(Color.decode("#00ffff"))
				.setDescription("# <a:loading:1331405235297456203> Zoeken\nIk ben voor " + Utils.getAsMention(sender) + " aan het zoeken naar:\n\n`" + Utils.truncate(msg, 300) + "`")
				.setThumbnail(isYoutube ? "attachment://youtube.png" : "attachment://web.png")
				.setImage(EMPTY_IMAGE)
				.setFooter("Heb even geduld alstublieft.");

			webhook.sendMessageEmbeds(eb.build())
				.setUsername(webhookName)
				.setFiles(Utils.loadImage(EMPTY_IMAGE_PATH), Utils.loadImage(isYoutube ? "youtube.png" : "web.png"))
			.queue(message -> {
				messageIds.put(sender.getId(), message.getId());
				PlayerManager.getInstance(music).getGuildMusicManager(guild).scheduler.load(o, sender, msg, isYoutube);
			}, o::sendFailed);
		});
	}

	@Override
	public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent e) {
		Member member = e.getMember();
		String selectionId = e.getSelectMenu().getId();
		if(!selectionId.startsWith("searchmusic_")) {return;}
		Logger.debug(getLogger(), "StringSelectInteraction with ID {} by {}", () -> new String[] {selectionId, Logger.getUserNameAndId(member.getUser())});
		e.deferReply(true).queue(null, ReplyOperation::error);

		Main.pool.execute(() -> {
			ReplyOperation o = new ReplyOperation(e);
			if(!selectionId.split("_")[1].equals(member.getId())) {o.sendNotAllowed("Alleen de zoeker mag hierop reageren!"); return;}
			String selected = e.getSelectedOptions().get(0).getValue();
			if(selected.equals("search-cancel")) {e.getMessage().delete().queue(success -> o.sendSuccess("Zoeken gestopt, kan gebeuren joh!"), o::sendFailed); return;}

			int index = Integer.parseInt(selected.split("_")[1]);
			if(!searchOptions.containsKey(member.getId())) {o.sendFailed("Ik kan de opties niet meer vinden! Probeer het opnieuw."); return;}

			AudioTrack track = searchOptions.remove(member.getId()).get(index);
			trackLoaded(o, member, track, true);
		});
	}

	public void trackLoaded(ReplyOperation o, @NotNull Member searcher, @NotNull AudioTrack track, boolean isYoutube) {
		getLogger().debug("Track loaded: {}", track.getInfo().title);
		Guild guild = searcher.getGuild();
		music.connect(guild);
		boolean added = PlayerManager.getInstance(music).getGuildMusicManager(guild).scheduler.queueTrack(track, searcher.getId());
		if(!added) {queueFull(o, searcher); return;}

		EmbedBuilder eb = new EmbedBuilder()
			.setColor(Color.GREEN)
			.setImage(EMPTY_IMAGE)
			.setThumbnail(isYoutube ? "attachment://youtube.png" : "attachment://web.png")
			.setDescription("# :tada: Toegevoegd!" +
				"\n> **" + Utils.truncate(track.getInfo().title, 180) + "**" +
				"\n> " + Utils.truncate(track.getInfo().author, 180) +
				"\n> *(" + Utils.formatDuration(track.getDuration()) + ")*" +
				"\nis toegevoegd aan de wachtrij!\n\n_Toegevoegd door: " + Utils.getAsMention(searcher) + "_");

		TextChannel channel = Channels.MUSIC.getAsChannel(guild);
		String msgId = messageIds.remove(searcher.getId());
		WebhookManager.getWebhook(channel, WEBHOOK_ID).editMessageEmbedsById(msgId, eb.build())
			.setComponents()
			.setFiles(Utils.loadImage(EMPTY_IMAGE_PATH), Utils.loadImage(isYoutube ? "youtube.png" : "web.png"))
		.queue(
			msg -> {
				o.replyEmpty();
				msg.delete().queueAfter(5, TimeUnit.SECONDS, null, o::sendFailed);
			}, o::sendFailed
		);
	}

	public void playListFound(ReplyOperation o, Member searcher, AudioPlaylist playlist, boolean isYoutube) {
		if(!isYoutube) {playlistLoaded(o, searcher, playlist); return;}

		Guild guild = searcher.getGuild();
		TextChannel channel = Channels.MUSIC.getAsChannel(guild);
		String msgId = messageIds.get(searcher.getId());

		List<SelectOption> selectOptions = new ArrayList<>();
		List<AudioTrack> tracks = new ArrayList<>();
		for(int i = 0; i < Math.min(playlist.getTracks().size(), 5); i++) {
			AudioTrack track = playlist.getTracks().get(i);
			tracks.add(i, track);
			selectOptions.add(SelectOption.of(Utils.truncate(track.getInfo().title, 180), "search-option_" + i)
				.withDescription(String.format("%s (%s)", Utils.truncate(track.getInfo().author, 180), Utils.formatDuration(track.getDuration())))
				.withEmoji(Emoji.fromFormatted("🎶")));
		} selectOptions.add(SelectOption.of("Cancel", "search-cancel").withEmoji(Emoji.fromFormatted("❌")));
		searchOptions.put(searcher.getId(), tracks);

		EmbedBuilder eb = new EmbedBuilder()
			.setColor(Color.ORANGE)
			.setImage(EMPTY_IMAGE)
			.setThumbnail("attachment://youtube.png")
			.setDescription("# :question: Gevonden (denk ik)\nIk heb meerdere nummers gevonden, kies er maar een!\n\n_Gezocht door: " + Utils.getAsMention(searcher) + "_");

		WebhookManager.getWebhook(channel, WEBHOOK_ID).editMessageEmbedsById(msgId, eb.build()).setComponents(ActionRow.of(
			StringSelectMenu.create("searchmusic_" + searcher.getId())
				.setPlaceholder("Kies een lekker nummertje!")
				.setRequiredRange(1, 1)
				.addOptions(selectOptions)
			.build()))
		.queue(msg -> msg.delete().queueAfter(2, TimeUnit.MINUTES, success -> messageIds.remove(searcher.getId()), ReplyOperation::error), o::sendFailed);
	}

	private void playlistLoaded(ReplyOperation o, @NotNull Member searcher, @NotNull AudioPlaylist playlist) {
		getLogger().debug("Playlist loaded: {}", playlist.getName());
		Guild guild = searcher.getGuild();
		TextChannel channel = Channels.MUSIC.getAsChannel(guild);
		String msgId = messageIds.remove(searcher.getId());

		music.connect(guild);
		int count = PlayerManager.getInstance(music).getGuildMusicManager(guild).scheduler.queueAll(playlist, searcher.getId());
		if(count == 0) {queueFull(o, searcher); return;}

		EmbedBuilder eb = new EmbedBuilder()
			.setColor(Color.GREEN)
			.setImage(EMPTY_IMAGE)
			.setThumbnail("attachment://web.png")
			.setDescription("# :tada: Toegevoegd!\nIk heb `" + count + "` nummer uit de lijst `" + Utils.truncate(playlist.getName(), 300) + "` toegevoegd aan de wachtrij!\n\n_Toegevoegd door: " + Utils.getAsMention(searcher) + "_");

		WebhookManager.getWebhook(channel, WEBHOOK_ID).editMessageEmbedsById(msgId, eb.build()).queue(
			msg -> {
				o.replyEmpty();
				msg.delete().queueAfter(5, TimeUnit.SECONDS, null, o::sendFailed);
			}, o::sendFailed
		);
	}

	public void noMatches(@NotNull ReplyOperation o, @NotNull Member searcher, String url) {
		getLogger().debug("No matches found on the url: {}", url);
		Guild guild = searcher.getGuild();
		TextChannel channel = Channels.MUSIC.getAsChannel(guild);
		String msgId = messageIds.remove(searcher.getId());

		EmbedBuilder eb = new EmbedBuilder()
			.setColor(Color.RED)
			.setImage(EMPTY_IMAGE)
			.setThumbnail("attachment://geenmuziekjes.png")
			.setDescription("# :x: Niet gevonden\n`" + Utils.truncate(url, 300) + "`\nheeft helaas niets opgeleverd.\n\n_Gezocht door " + Utils.getAsMention(searcher) + "_")
			.setFooter("Probeer het opnieuw.");

		WebhookManager.getWebhook(channel, WEBHOOK_ID).editMessageEmbedsById(msgId, eb.build())
			.setFiles(Utils.loadImage(EMPTY_IMAGE_PATH), Utils.loadImage("geenmuziekjes.png"))
		.queue(
			msg -> {
				o.replyEmpty();
				msg.delete().queueAfter(5, TimeUnit.SECONDS, null, o::sendFailed);
			}, o::sendFailed
		);
	}

	public void loadFailed(@NotNull ReplyOperation o, @NotNull Member searcher, String url, FriendlyException ex) {
		Guild guild = searcher.getGuild();
		TextChannel channel = Channels.MUSIC.getAsChannel(guild);
		String msgId = messageIds.remove(searcher.getId());
		getLogger().error(ex.getMessage(), ex);

		EmbedBuilder eb = new EmbedBuilder()
			.setColor(Color.RED)
			.setImage(EMPTY_IMAGE)
			.setThumbnail("attachment://geenmuziekjes.png")
			.setDescription("# :warning: Er is iets fout gegaan!\n`" + Utils.truncate(url, 300) + "` heeft een error veroorzaakt!\n\nVraag aan " + Utils.getCas(guild).getAsMention() + " om in de console te kijken.\n\n_Gezocht door " + Utils.getAsMention(searcher) + "_")
			.setFooter("Probeer het later opnieuw.");

		WebhookManager.getWebhook(channel, WEBHOOK_ID).editMessageEmbedsById(msgId, eb.build())
			.setFiles(Utils.loadImage(EMPTY_IMAGE_PATH), Utils.loadImage("geenmuziekjes.png"))
		.queue(
			msg -> {
				o.replyEmpty();
				msg.delete().queueAfter(5, TimeUnit.SECONDS, null, o::sendFailed);
			}, o::sendFailed
		);
	}

	public void queueFull(@NotNull ReplyOperation o, @NotNull Member searcher) {
		Guild guild = searcher.getGuild();
		TextChannel channel = Channels.MUSIC.getAsChannel(guild);
		String msgId = messageIds.remove(searcher.getId());

		EmbedBuilder eb = new EmbedBuilder()
			.setColor(Color.RED)
			.setImage(EMPTY_IMAGE)
			.setThumbnail("attachment://geenmuziekjes.png")
			.setDescription("# :x: Wachtrij vol!\nWacht tot een paar nummers zijn afgelopen.\n\n_Gezocht door: " + Utils.getAsMention(searcher) + "_");

		WebhookManager.getWebhook(channel, WEBHOOK_ID).editMessageEmbedsById(msgId, eb.build())
			.setFiles(Utils.loadImage(EMPTY_IMAGE_PATH), Utils.loadImage("geenmuziekjes.png"))
		.queue(
			msg -> {
				o.replyEmpty();
				msg.delete().queueAfter(5, TimeUnit.SECONDS, null, o::sendFailed);
			}, o::sendFailed
		);
	}
}