package dev.casiebarie.inosso.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.casiebarie.inosso.enums.Channels;
import dev.casiebarie.inosso.music.lavaplayer.GuildMusicManager;
import dev.casiebarie.inosso.music.lavaplayer.PlayerManager;
import dev.casiebarie.inosso.utils.ReplyOperation;
import dev.casiebarie.inosso.utils.Utils;
import dev.casiebarie.inosso.utils.WebhookManager;
import dev.casiebarie.inosso.utils.logging.Logger;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;

import static dev.casiebarie.inosso.Main.jda;
import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class Controller {
	final Music music;
	long lastUpdate = 0;
	final String guildId;
	Queue<AudioTrack> queue;
	MessageEmbed currentEmbed;
	GuildMusicManager manager;
	String controllerId = "0";
	AudioTrack nowPlayingTrack, overridenTrack;
	List<ActionRow> currentActionRows = new ArrayList<>();
	public boolean isConnected = false, forceUpdate = false;
	boolean isPaused = false, notFound = false, initializing = false;
	public Controller(Music music, String guildId) {
		this.music = music;
		this.guildId = guildId;
		Logger.debug(getLogger(), "Controller instance created for guild {}", () -> new String[] {Logger.getGuildNameAndId(jda().getGuildById(guildId))});
	}

	public void updateMessage() {
		if(initializing) {return;}
		if(notFound) {findMessage(); return;}

		Guild guild = jda().getGuildById(guildId);
		TextChannel channel = Channels.MUSIC.getAsChannel(guild);
		Webhook webhook = WebhookManager.getWebhook(channel, "Controller");
		if(webhook == null) {return;}

		long now = System.currentTimeMillis();
		if(now - lastUpdate < 500) {return;}
		lastUpdate = now;

		manager = PlayerManager.getInstance(music).getGuildMusicManager(guild);
		MessageEmbed newEmbed = musicController();
		List<ActionRow> newActionRows = getActionRows();
		if(Objects.equals(currentEmbed, newEmbed) && !forceUpdate) {return;}

		webhook.editMessageEmbedsById(controllerId, newEmbed).setComponents(newActionRows)
			.queue(success -> {
				lastUpdate = System.currentTimeMillis();
				forceUpdate = false;
				currentEmbed = newEmbed;
				currentActionRows = newActionRows;
			}, new ErrorHandler().handle(ErrorResponse.UNKNOWN_MESSAGE, error -> notFound = true).andThen(ReplyOperation::error))
		;
	}

	private @NotNull MessageEmbed musicController() {
		Guild guild = jda().getGuildById(guildId);
		nowPlayingTrack = manager.player.getPlayingTrack();
		queue = manager.scheduler.queue;
		if(queue != null) {queue.remove(nowPlayingTrack);}
		overridenTrack = manager.scheduler.oldTrack;
		isPaused = manager.player.isPaused();
		if(nowPlayingTrack != null && nowPlayingTrack.getIdentifier().equals("silence.opus")) {nowPlayingTrack = null;}

		String nowPlaying = (nowPlayingTrack == null) ? "\n`Niets`" : String.format("\n> **%s**\n> %s\n> _(%s / %s)_\n> %s\n", Utils.truncate(nowPlayingTrack.getInfo().title, 180), Utils.truncate(nowPlayingTrack.getInfo().author, 180), Utils.formatDuration(nowPlayingTrack.getPosition(), nowPlayingTrack.getDuration()), Utils.formatDuration(nowPlayingTrack.getDuration()), Utils.getAsMention(guild.getMemberById(nowPlayingTrack.getUserData().toString())));
		String overridenPlaying = (overridenTrack == null || nowPlayingTrack == null) ? "" : String.format("\n> **%s**\n> %s\n> _(%s)_\n> %s", Utils.truncate(overridenTrack.getInfo().title, 180), Utils.truncate(overridenTrack.getInfo().author, 180), Utils.formatDuration(overridenTrack.getDuration()), Utils.getAsMention(guild.getMemberById(overridenTrack.getUserData().toString())));
		String paused = isPaused ? "\n_(Gepauzeerd)_" : "";

		return new EmbedBuilder()
			.setDescription("# Muziekjes :trumpet:" +
				"\nStuur een bericht met de titel of URL van een nummer/afspeellijst om muziek toe te voegen aan de wachtrij. Als je een titel stuurt, wordt er automatisch gezocht op YouTube naar een geschikte versie." +
				"\n### Nu aan het spelen:" + paused + nowPlaying + overridenPlaying +
				"\n### Volgende:" + getNextTracks())
			.setColor(Color.CYAN)
			.setThumbnail("attachment://muziekjes.png")
			.setImage("attachment://empty.png")
		.build();
	}

	private @NotNull String getNextTracks() {
		if(queue == null || queue.isEmpty()) {return "\n`Niets`";}
		queue.remove(nowPlayingTrack);
		StringBuilder builder = new StringBuilder();
		AudioTrack track = queue.peek();
		builder.append(String.format("\n> **%s**\n> %s\n> _(%s)_\n> %s\n", Utils.truncate(track.getInfo().title, 180), Utils.truncate(track.getInfo().author, 180), Utils.formatDuration(track.getDuration()), Utils.getAsMention(jda().getGuildById(guildId).getMemberById(track.getUserData().toString()))));
		if(queue.size() > 1) {builder.append("\nen ").append(queue.size() - 1).append(" meer...");}
		return builder.toString();
	}

	private @NotNull List<ActionRow> getActionRows() {
		List<ActionRow> rows = new ArrayList<>();
		boolean isSkippable = manager.scheduler.loopingTrack == null;

		rows.add(ActionRow.of(
			Button.secondary("music_pause", (isPaused) ? "▶️ Hervat" : "⏸️ Pauzeer").withDisabled(nowPlayingTrack == null || !isSkippable),
			Button.secondary("music_skip", "⏭️ Skip").withDisabled(queue.isEmpty() || !isSkippable),
			Button.secondary("music_replay", "🔁 Opnieuw Afspelen").withDisabled(nowPlayingTrack == null || !isSkippable),
			Button.danger("music_stop", "⛔ Stop").withDisabled(!isConnected || !isSkippable)
		));

		rows.add(ActionRow.of(
			Button.secondary("music_viewqueue", "📄 Wachtrij").withDisabled(queue.isEmpty()),
			Button.secondary("music_movetoone", "📝 Laatste naar #1").withDisabled(queue.size() <= 1),
			Button.secondary("music_emptyqueue", "📝 Wachtrij Leegmaken").withDisabled(queue.isEmpty())
		));

		return rows;
	}

	private void findMessage() {
		initializing = true;
		Guild guild = jda().getGuildById(guildId);
		TextChannel channel = Channels.MUSIC.getAsChannel(guild);
		Message message = channel.getHistoryFromBeginning(3).complete().getRetrievedHistory().stream()
			.filter(Message::isWebhookMessage)
			.filter(m -> m.getButtonById("music_pause") != null).findFirst().orElse(null);
		if(message == null) {setupController(null); return;}
		initializing = false;
		controllerId = message.getId();
		notFound = false;
	}

	public void setupController(ReplyOperation o) {
		Guild guild = jda().getGuildById(guildId);
		String webhookName = jda().getGuildById(guildId).getSelfMember().getEffectiveName() + " -  Muziekjes🎺";
		TextChannel channel = Channels.MUSIC.getAsChannel(guild);
		Webhook webhook = WebhookManager.getWebhook(channel, "Controller");
		if(o == null) {o = new ReplyOperation(webhook, webhookName);}
		if(webhook == null) {o.sendFailed("Ik kan op dit moment geen nieuwe controller sturen."); return;}

		List<Message> messages = new ArrayList<>();
		ReplyOperation finalO = o;
		channel.getIterableHistory().forEachAsync(msg -> {
			messages.add(msg);
			return messages.size() < 10;
		}).thenRun(() -> channel.purgeMessages(messages)).whenComplete((success, error) -> {
			currentEmbed = musicController();
			currentActionRows = getActionRows();
			webhook.sendMessageEmbeds(currentEmbed)
				.setUsername(webhookName)
				.setComponents(currentActionRows)
				.setFiles(Utils.loadImage("muziekjes.png"), Utils.loadImage("empty.png"))
			.queue(msg -> {
				controllerId = msg.getId();
				notFound = false;
				initializing = false;
				finalO.sendSuccess("Muziek controller is gemaakt!");
				getLogger().info("A new music controller has been created");
			}, finalO::sendFailed);
		});
	}
}