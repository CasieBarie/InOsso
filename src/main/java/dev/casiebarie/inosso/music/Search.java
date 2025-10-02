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
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static dev.casiebarie.inosso.Main.jda;
import static dev.casiebarie.inosso.enums.Variables.*;
import static dev.casiebarie.inosso.enums.Variables.AudioTypes.*;
import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class Search {
	AudioTypes type;
	ReplyOperation o;
	final Guild guild;
	final Music music;
	final Member searcher;
	final TextChannel channel;
	final PlayerManager manager;
	final GuildMusicManager guildManager;
	String messageId, webhookName, imageUrl, initialQuery, finalQuery;
	static final String WEBHOOK_ID = "Search", NOMUSIC_PATH = "geenmuziekjes.png";
	public Search(Music music, @NotNull Member searcher, Message message) {
		this.music = music;
		this.searcher = searcher;
		this.guild = searcher.getGuild();
		channel = Channels.MUSIC.getAsChannel(guild);
		manager = PlayerManager.getInstance(music);
		guildManager = manager.getGuildMusicManager(guild);
		webhookName = guild.getSelfMember().getEffectiveName() + " | Muziek Zoeker🔎";
		search(message);
	}

	private void search(Message message) {
		Webhook webhook = WebhookManager.getWebhook(channel, WEBHOOK_ID);
		if(webhook == null) {return;}
		o = new ReplyOperation(webhook, webhookName);

		if(!Utils.isInVoice(searcher, o)) {deleteMessage(message, 0); return;}
		if(guildManager.scheduler.queue.size() >= MAX_QUEUE_SIZE) {sendQueueFull(); return;}

		type = Utils.getAudioType(message);
		boolean isSupported = type != UNSUPPORTED_FILE && type != UNSUPPORTED_LINK;
		imageUrl = type == SEARCH ? "soundcloud.png" : type == LINK ? "web.png" : "file.png";
		initialQuery = type == FILE || type == UNSUPPORTED_FILE ? message.getAttachments().get(0).getFileName() : message.getContentRaw();

		Message msg = webhook.sendMessageEmbeds(isSupported ? searchEmbed() : unsupportedEmbed())
			.setUsername(webhookName)
			.setFiles(Utils.loadImage(EMPTY_IMAGE_PATH), Utils.loadImage(isSupported ? imageUrl : NOMUSIC_PATH))
		.complete();

		if(!isSupported) {deleteMessage(msg, 5); deleteMessage(message, 0); return;}
		if(type == FILE) {loadFile(message);
		} else {deleteMessage(message, 0);}
		messageId = msg.getId();
		load();
	}

	private void deleteMessage(@NotNull Message message, Integer delay) {
		if(delay > 0) {message.delete().queueAfter(delay, TimeUnit.SECONDS, null, ReplyOperation::error);
		} else {message.delete().queue(null, ReplyOperation::error);}
	}

	private void load() {
		if(type == SEARCH) {finalQuery = "scsearch:" + initialQuery;
		} else if(type == LINK) {finalQuery = initialQuery;}

		manager.getAudioPlayerManager().loadItemOrdered(guildManager, finalQuery, new AudioLoadResultHandler() {
			@Override public void trackLoaded(AudioTrack audioTrack) {audioTrackLoaded(audioTrack);}
			@Override public void playlistLoaded(AudioPlaylist audioPlaylist) {audioPlaylistLoaded(audioPlaylist);}
			@Override public void noMatches() {editMessage(noMatchesEmbed(), NOMUSIC_PATH);}
			@Override public void loadFailed(FriendlyException ex) {editMessage(loadFailedEmbed(), NOMUSIC_PATH); getLogger().error(ex.getMessage(), ex);}
		});
	}

	private void loadFile(@NotNull Message message) {
		try {
			InputStream is = message.getAttachments().get(0).getProxy().download().get();
			deleteMessage(message, 0);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			int bytesRead;
			while((bytesRead = is.read(buffer)) != -1) {baos.write(buffer, 0, bytesRead);}
			byte[] fileBytes = baos.toByteArray();
			Message audioMessage = Utils.getCasAltAsUser().openPrivateChannel().complete().sendMessage("AudioTrack:").setFiles(FileUpload.fromData(fileBytes, initialQuery)).complete();
			finalQuery = audioMessage.getAttachments().get(0).getProxyUrl();
		} catch(InterruptedException | ExecutionException | IOException ex) {Thread.currentThread().interrupt(); getLogger().error(ex.getMessage(), ex);}
	}

	private void audioTrackLoaded(@NotNull AudioTrack track) {
		getLogger().debug("Track loaded: {}", track.getInfo().title);
		music.connect(guild);
		if(Utils.isSoundCloudGoPlus(track)) {editMessage(noMatchesEmbed(), NOMUSIC_PATH); return;}
		if(!guildManager.scheduler.queueTrack(track, searcher.getId())) {editMessage(queueFullEmbed(), NOMUSIC_PATH); return;}
		editMessage(trackAddedEmbed(track), imageUrl);
	}

	private void audioPlaylistLoaded(AudioPlaylist playlist) {
		if(type == SEARCH) {handleSearch(playlist); return;}
		getLogger().debug("Playlist loaded: {}", playlist.getName());
		music.connect(guild);
		int count = guildManager.scheduler.queueAll(playlist, searcher.getId());
		if(count == -1) {editMessage(noMatchesEmbed(), NOMUSIC_PATH); return;}
		if(count == 0) {editMessage(queueFullEmbed(), NOMUSIC_PATH); return;}
		editMessage(playlistAddedEmbed(playlist, count), imageUrl);
	}

	private void handleSearch(@NotNull AudioPlaylist playlist) {
		List<AudioTrack> tracks = playlist.getTracks().stream().filter(track -> !Utils.isSoundCloudGoPlus(track)).toList();
		if(tracks.isEmpty()) {editMessage(noMatchesEmbed(), NOMUSIC_PATH); return;}

		List<SelectOption> selectOptions = new ArrayList<>();
		List<AudioTrack> options = new ArrayList<>();
		for(int i = 0; i < Math.min(tracks.size(), 5); i++) {
			AudioTrack track = tracks.get(i);
			options.add(i, track);
			selectOptions.add(SelectOption.of(Utils.truncate(track.getInfo().title, 180), "search-option_" + i)
				.withDescription(String.format("%s (%s)", Utils.truncate(track.getInfo().author, 180), Utils.formatDuration(track.getDuration())))
				.withEmoji(Emoji.fromFormatted("🎶")));
		} selectOptions.add(SelectOption.of("Cancel", "search-cancel").withEmoji(Emoji.fromFormatted("❌")));

		EmbedBuilder eb = new EmbedBuilder()
			.setColor(Color.ORANGE)
			.setImage(EMPTY_IMAGE)
			.setThumbnail("attachment://" + imageUrl)
			.setDescription("# :question: Gevonden :question:\nIk heb meerdere nummers gevonden, kies er maar een!\n\n_Gezocht door: " + Utils.getAsMention(searcher) + "_");

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
		.queue(msg -> deleteMessage(msg, 5), o::sendFailed);
	}

	private void editMessage(MessageEmbed embed, String image) {
		WebhookManager.getWebhook(channel, WEBHOOK_ID).editMessageEmbedsById(messageId, embed)
			.setFiles(Utils.loadImage(EMPTY_IMAGE_PATH), Utils.loadImage(image))
			.setComponents()
		.queue(msg -> deleteMessage(msg, 5), o::sendFailed);
	}

	private @NotNull MessageEmbed searchEmbed() {
		return new EmbedBuilder()
			.setColor(Color.decode("#00ffff"))
			.setDescription("# <a:loading:1331405235297456203> Zoeken <a:loading:1331405235297456203>" +
				"\nIk ben voor " + Utils.getAsMention(searcher) + " aan het zoeken naar:" +
				"\n\n`" + Utils.truncate(initialQuery, 300) + "`")
			.setImage(EMPTY_IMAGE)
			.setThumbnail("attachment://" + imageUrl)
			.setFooter("Heb even geduld alstublieft.")
		.build();
	}

	private @NotNull MessageEmbed trackAddedEmbed(@NotNull AudioTrack track) {
		return new EmbedBuilder()
			.setColor(Color.GREEN)
			.setImage(EMPTY_IMAGE)
			.setThumbnail("attachment://" + imageUrl)
			.setDescription("# :tada: Toegevoegd! :tada:" +
				"\n> **" + Utils.truncate(track.getInfo().title, 180) + "**" +
				"\n> " + Utils.truncate(track.getInfo().author, 180) +
				"\n> *(" + Utils.formatDuration(track.getDuration()) + ")*" +
				"\nis toegevoegd aan de wachtrij!\n\n_Toegevoegd door: " + Utils.getAsMention(searcher) + "_")
		.build();
	}

	private @NotNull MessageEmbed playlistAddedEmbed(@NotNull AudioPlaylist playlist, int count) {
		return new EmbedBuilder()
			.setColor(Color.GREEN)
			.setImage(EMPTY_IMAGE)
			.setThumbnail(imageUrl)
			.setDescription("# :tada: Toegevoegd! :tada:" +
				"\nIk heb `" + count + "` nummer uit de lijst `" + Utils.truncate(playlist.getName(), 300) + "` toegevoegd aan de wachtrij!" +
				"\n\n_Toegevoegd door: " + Utils.getAsMention(searcher) + "_")
		.build();
	}

	private @NotNull EmbedBuilder getNoMusicEmbedBuilder() {return new EmbedBuilder().setColor(Color.RED).setImage(EMPTY_IMAGE).setThumbnail("attachment://geenmuziekjes.png");}
	private @NotNull MessageEmbed queueFullEmbed() {
		return getNoMusicEmbedBuilder().setDescription("# :no_entry: Wachtrij vol! :no_entry:" +
			"\nWacht tot een paar nummers zijn afgelopen." +
			"\n\n*Gezocht door: " + Utils.getAsMention(searcher) + "*")
		.build();
	}

	private @NotNull MessageEmbed noMatchesEmbed() {
		return getNoMusicEmbedBuilder().setDescription("# :no_entry: Niet gevonden! :no_entry:" +
			"\n`" + Utils.truncate(initialQuery, 300) + "`" +
			"\nheeft helaas niets opgeleverd." +
			"\n\n*Gezocht door " + Utils.getAsMention(searcher) + "*")
		.build();
	}

	private @NotNull MessageEmbed unsupportedEmbed() {
		return getNoMusicEmbedBuilder().setDescription("# :no_entry: Geen Audio " + (type == UNSUPPORTED_LINK ? "Link" : "Bestand") + "! :no_entry:" +
			"\n`" + Utils.truncate(initialQuery, 300) + "`" +
			"\nis geen audio " + (type == UNSUPPORTED_LINK ? "link" : "bestand") + "." +
			"\n\n*Gezocht door: " + Utils.getAsMention(searcher) + "*")
		.build();
	}

	private @NotNull MessageEmbed loadFailedEmbed() {
		return getNoMusicEmbedBuilder().setDescription("# :warning: Er is iets fout gegaan! :warning:" +
			"\n`" + Utils.truncate(initialQuery, 300) + "` heeft een error veroorzaakt!" +
			"\n\nVraag aan " + Utils.getAsMention(Utils.getCasAsMember(guild)) + " om in de console te kijken." +
			"\n\n*Gezocht door " + Utils.getAsMention(searcher) + "*")
		.build();
	}

	private @NotNull MessageEmbed searchStoppedEmbed() {
		return getNoMusicEmbedBuilder().setDescription("# :no_entry: Zoeken gestopt! :no_entry: " +
			"\nKan gebeuren joh!" +
			"\n\n*Gezocht door: " + Utils.getAsMention(searcher) + "*")
		.build();
	}
}