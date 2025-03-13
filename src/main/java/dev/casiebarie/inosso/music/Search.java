package dev.casiebarie.inosso.music;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.casiebarie.inosso.enums.Channels;
import dev.casiebarie.inosso.music.lavaplayer.GuildMusicManager;
import dev.casiebarie.inosso.music.lavaplayer.PlayerManager;
import dev.casiebarie.inosso.utils.ReplyOperation;
import dev.casiebarie.inosso.utils.Utils;
import dev.casiebarie.inosso.utils.WebhookManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static dev.casiebarie.inosso.Main.jda;
import static dev.casiebarie.inosso.enums.Variables.*;
import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class Search {
	int type;
	String messageId, webhookName;
	final Guild guild;
	final Music music;
	final String query;
	final Member searcher;
	final PlayerManager manager;
	final GuildMusicManager guildManager;
	final TextChannel channel;
	ReplyOperation o;
	static final String WEBHOOK_ID = "Search", NOMUSIC_PATH = "geenmuziekjes.png";
	List<AudioTrack> options = new ArrayList<>();
	public Search(Music music, Member searcher, String query) {
		this.music = music;
		this.searcher = searcher;
		this.query = query;
		this.guild = searcher.getGuild();
		channel = Channels.MUSIC.getAsChannel(guild);
		manager = PlayerManager.getInstance(music);
		guildManager = manager.getGuildMusicManager(guild);
		webhookName = guild.getSelfMember().getEffectiveName() + " |  Muziek Zoeker🔎";
		search();
	}

	private void search() {
		Webhook webhook = WebhookManager.getWebhook(channel, WEBHOOK_ID);
		if(webhook == null) {return;}
		o = new ReplyOperation(webhook, webhookName);

		if(!Utils.isInVoice(searcher, o)) {return;}
		if(guildManager.scheduler.queue.size() >= MAX_QUEUE_SIZE) {sendQueueFull(); return;}
		type = Utils.getAudioType(query);

		webhook.sendMessageEmbeds(type == 2 ? invalidUrlEmbed() : searchEmbed())
			.setUsername(webhookName)
			.setFiles(Utils.loadImage(EMPTY_IMAGE_PATH), Utils.loadImage(type == 0 ? "soundcloud.png" : type == 1 ? "web.png" : NOMUSIC_PATH))
		.queue(message -> {
			if(type == 2) {message.delete().queueAfter(5, TimeUnit.SECONDS, null, ReplyOperation::error); return;}
			messageId = message.getId();
			load();
		}, o::sendFailed);
	}

	private void load() {
		manager.getAudioPlayerManager().loadItemOrdered(guildManager, type == 0 ? "scsearch:" + query : query, new AudioLoadResultHandler() {
			@Override public void trackLoaded(AudioTrack audioTrack) {audioTrackLoaded(audioTrack);}
			@Override public void playlistLoaded(AudioPlaylist audioPlaylist) {audioPlaylistLoaded(audioPlaylist);}
			@Override public void noMatches() {editMessage(noMatchesEmbed(), NOMUSIC_PATH);}
			@Override public void loadFailed(FriendlyException ex) {editMessage(loadFailedEmbed(), NOMUSIC_PATH); getLogger().error(ex.getMessage(), ex);}
		});
	}

	private void audioTrackLoaded(@NotNull AudioTrack track) {
		getLogger().debug("Track loaded: {}", track.getInfo().title);
		music.connect(guild);
		if(!guildManager.scheduler.queueTrack(track, searcher.getId())) {editMessage(queueFullEmbed(), NOMUSIC_PATH);}
		editMessage(trackAddedEmbed(track), type == 0 ? "soundcloud.png" : "web.png");
	}

	private void audioPlaylistLoaded(AudioPlaylist playlist) {
		if(type == 0) {handleSearch(playlist); return;}
		getLogger().debug("Playlist loaded: {}", playlist.getName());
		music.connect(guild);
		int count = guildManager.scheduler.queueAll(playlist, searcher.getId());
		if(count == 0) {editMessage(queueFullEmbed(), NOMUSIC_PATH); return;}
		editMessage(playlistAddedEmbed(playlist, count), "web.png");
	}

	private void handleSearch(@NotNull AudioPlaylist playlist) {
		List<SelectOption> selectOptions = new ArrayList<>();
		for(int i = 0; i < Math.min(playlist.getTracks().size(), 5); i++) {
			AudioTrack track = playlist.getTracks().get(i);
			options.add(i, track);
			selectOptions.add(SelectOption.of(Utils.truncate(track.getInfo().title, 180), "search-option_" + i)
				.withDescription(String.format("%s (%s)", Utils.truncate(track.getInfo().author, 180), Utils.formatDuration(track.getDuration())))
				.withEmoji(Emoji.fromFormatted("🎶")));
		} selectOptions.add(SelectOption.of("Cancel", "search-cancel").withEmoji(Emoji.fromFormatted("❌")));

		EmbedBuilder eb = new EmbedBuilder()
			.setColor(Color.ORANGE)
			.setImage(EMPTY_IMAGE)
			.setThumbnail("attachment://soundcloud.png")
			.setDescription("# :question: Gevonden (denk ik)\nIk heb meerdere nummers gevonden, kies er maar een!\n\n_Gezocht door: " + Utils.getAsMention(searcher) + "_");

		WebhookManager.getWebhook(channel, WEBHOOK_ID).editMessageEmbedsById(messageId, eb.build()).setComponents(ActionRow.of(
			StringSelectMenu.create("searchmusic_" + searcher.getId())
				.setPlaceholder("Kies een lekker nummertje!")
				.setRequiredRange(1, 1)
				.addOptions(selectOptions)
			.build()))
		.queue(null, o::sendFailed);

		jda().listenOnce(StringSelectInteractionEvent.class)
			.filter(e -> e.getChannel().getId().equals(channel.getId()))
			.filter(e -> e.getMember().equals(searcher))
			.filter(e -> e.getMessage().getId().equals(messageId))
			.timeout(Duration.of(2, ChronoUnit.MINUTES), () -> WebhookManager.getWebhook(channel, WEBHOOK_ID).deleteMessageById(messageId).queue(null, ReplyOperation::error))
		.subscribe(e -> {
			String selected = e.getSelectedOptions().get(0).getValue();
			if(selected.equals("search-cancel")) {editMessage(searchStoppedEmbed(), NOMUSIC_PATH); return;}
			audioTrackLoaded(options.get(Integer.parseInt(selected.split("_")[1])));
		});
	}

	private void sendQueueFull() {
		WebhookManager.getWebhook(channel, WEBHOOK_ID).sendMessageEmbeds(queueFullEmbed())
			.setUsername(webhookName)
			.setFiles(Utils.loadImage(EMPTY_IMAGE_PATH), Utils.loadImage(NOMUSIC_PATH))
		.queue(msg -> msg.delete().queueAfter(5, TimeUnit.SECONDS, null, ReplyOperation::error), o::sendFailed);
	}

	private void editMessage(MessageEmbed embed, String image) {
		WebhookManager.getWebhook(channel, WEBHOOK_ID).editMessageEmbedsById(messageId, embed)
			.setFiles(Utils.loadImage(EMPTY_IMAGE_PATH), Utils.loadImage(image))
			.setComponents()
		.queue(msg -> msg.delete().queueAfter(5, TimeUnit.SECONDS, null, ReplyOperation::error), o::sendFailed);
	}

	private @NotNull MessageEmbed searchEmbed() {
		return new EmbedBuilder()
			.setColor(Color.decode("#00ffff"))
			.setDescription("# <a:loading:1331405235297456203> Zoeken" +
				"\nIk ben voor " + Utils.getAsMention(searcher) + " aan het zoeken naar:" +
				"\n\n`" + Utils.truncate(query, 300) + "`")
			.setImage(EMPTY_IMAGE)
			.setThumbnail(type == 0 ? "attachment://soundcloud.png" : "attachment://web.png")
			.setFooter("Heb even geduld alstublieft.")
		.build();
	}

	private @NotNull MessageEmbed trackAddedEmbed(AudioTrack track) {
		return new EmbedBuilder()
			.setColor(Color.GREEN)
			.setImage(EMPTY_IMAGE)
			.setThumbnail(type == 0 ? "attachment://soundcloud.png" : "attachment://web.png")
			.setDescription("# :tada: Toegevoegd!" +
				"\n> **" + Utils.truncate(track.getInfo().title, 180) + "**" +
				"\n> " + Utils.truncate(track.getInfo().author, 180) +
				"\n> *(" + Utils.formatDuration(track.getDuration()) + ")*" +
				"\nis toegevoegd aan de wachtrij!\n\n_Toegevoegd door: " + Utils.getAsMention(searcher) + "_")
		.build();
	}

	private @NotNull MessageEmbed playlistAddedEmbed(AudioPlaylist playlist, int count) {
		return new EmbedBuilder()
			.setColor(Color.GREEN)
			.setImage(EMPTY_IMAGE)
			.setThumbnail("attachment://web.png")
			.setDescription("# :tada: Toegevoegd!" +
				"\nIk heb `" + count + "` nummer uit de lijst `" + Utils.truncate(playlist.getName(), 300) + "` toegevoegd aan de wachtrij!" +
				"\n\n_Toegevoegd door: " + Utils.getAsMention(searcher) + "_")
		.build();
	}

	private @NotNull MessageEmbed invalidUrlEmbed() {
		return getNoMusicEmbedBuilder().setDescription("# :x: Geen audio link!" +
			"\n`" + Utils.truncate(query, 300) + "` is geen audio link." +
			"\n\n*Gezocht door " + Utils.getAsMention(searcher) + "*")
		.build();
	}

	private @NotNull MessageEmbed noMatchesEmbed() {
		return getNoMusicEmbedBuilder().setDescription("# :x: Niet gevonden" +
			"\n`" + Utils.truncate(query, 300) + "`" +
			"\nheeft helaas niets opgeleverd." +
			"\n\n*Gezocht door " + Utils.getAsMention(searcher) + "*")
		.build();
	}

	private @NotNull MessageEmbed loadFailedEmbed() {
		return getNoMusicEmbedBuilder().setDescription("# :warning: Er is iets fout gegaan!" +
			"\n`" + Utils.truncate(query, 300) + "` heeft een error veroorzaakt!" +
			"\n\nVraag aan " + Utils.getCas(guild).getAsMention() + " om in de console te kijken." +
			"\n\n*Gezocht door " + Utils.getAsMention(searcher) + "*")
		.build();
	}

	private @NotNull MessageEmbed queueFullEmbed() {
		return getNoMusicEmbedBuilder().setDescription("# :x: Wachtrij vol!" +
			"\nWacht tot een paar nummers zijn afgelopen." +
			"\n\n*Gezocht door: " + Utils.getAsMention(searcher) + "*")
		.build();
	}

	private @NotNull MessageEmbed searchStoppedEmbed() {
		return getNoMusicEmbedBuilder().setDescription("# :x: Zoeken gestopt!" +
			"\nKan gebeuren joh!" +
			"\n\n*Gezocht door: " + Utils.getAsMention(searcher) + "*")
		.build();
	}

	private @NotNull EmbedBuilder getNoMusicEmbedBuilder() {return new EmbedBuilder().setColor(Color.RED).setImage(EMPTY_IMAGE).setThumbnail("attachment://geenmuziekjes.png");}
}