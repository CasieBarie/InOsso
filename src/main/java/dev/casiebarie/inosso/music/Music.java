package dev.casiebarie.inosso.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.casiebarie.inosso.ClassLoader;
import dev.casiebarie.inosso.enums.Channels;
import dev.casiebarie.inosso.interfaces.Information;
import dev.casiebarie.inosso.music.lavaplayer.PlayerManager;
import dev.casiebarie.inosso.music.lavaplayer.TrackScheduler;
import dev.casiebarie.inosso.utils.ReplyOperation;
import dev.casiebarie.inosso.utils.Utils;
import dev.casiebarie.inosso.utils.logging.Logger;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static dev.casiebarie.inosso.enums.Variables.*;
import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class Music implements Information {
	public final QueueViewer queueViewer;
	public final Map<String, Controller> controllers = new HashMap<>();
	public Music(ClassLoader classes) {
		this.queueViewer = new QueueViewer();
		new Listeners(classes, this);
		classes.registerAsInformationClass("muziekjes", this);
	}

	public void connect(@NotNull Guild guild) {
		Controller controller = controllers.get(guild.getId());
		controller.isConnected = true;
		if(!guild.getAudioManager().isConnected()) {
			Logger.debug(getLogger(), "Connecting to guild {}", () -> new String[] {Logger.getGuildNameAndId(guild)});
			guild.getAudioManager().openAudioConnection(Channels.VOICE.getAsChannel(guild));
			controller.manager.scheduler.playStartupSound(guild);
		}
	}

	protected void stopMusic(Guild guild, boolean isButton) {
		Logger.debug(getLogger(), "Stopping music in guild {}", () -> new String[] {Logger.getGuildNameAndId(guild)});
		Controller controller = controllers.get(guild.getId());
		controller.manager.player.stopTrack();

		controller.manager.scheduler.setToNull();
		controller.manager.scheduler.queue.clear();

		guild.getAudioManager().closeAudioConnection();
		controller.isConnected = false;
		controller.forceUpdate = true;

		((TextChannel) Channels.MUSIC.getAsChannel(guild))
			.sendMessageEmbeds(new EmbedBuilder()
				.setDescription("# Muziek Gestopt" +
					(isButton ? "" : "\nIedereen is verdwenen uit de call.") +
					"\n- Muziek gestopt." +
					"\n- Wachtrij geleegd." +
					"\n- Call verlaten.")
				.setThumbnail("attachment://geenmuziekjes.png")
				.setImage(EMPTY_IMAGE)
				.setColor(Color.RED).build())
			.setFiles(Utils.loadImage("geenmuziekjes.png"), Utils.loadImage(EMPTY_IMAGE_PATH))
		.queue(msg -> msg.delete().queueAfter(5, TimeUnit.SECONDS));
	}

	protected void moveToOne(@NotNull TrackScheduler scheduler, ReplyOperation o) {
		if(scheduler.queue.size() <= 1) {o.sendFailed(ACTION_CANCELLED_MSG); return;}

		LinkedList<AudioTrack> list = new LinkedList<>(scheduler.queue);
		AudioTrack lastTrack = list.remove(list.size() - 1);
		list.add(0, lastTrack);
		scheduler.queue.clear();
		scheduler.queue.addAll(list);

		o.e.getHook().editOriginalEmbeds(
			new EmbedBuilder()
				.setDescription("# Verplaatst" +
					"\n> **" + Utils.truncate(lastTrack.getInfo().title, 180) + "**" +
					"\n> " + Utils.truncate(lastTrack.getInfo().author, 180) +
					"\n> _(" + Utils.formatDuration(lastTrack.getDuration()) + ")_" +
					"\n\nis verplaatst naar de top van de wachtrij!")
				.setColor(Color.GREEN)
				.setThumbnail("attachment://muziekjes.png")
				.setImage(EMPTY_IMAGE)
			.build()
		).setFiles(Utils.loadImage("muziekjes.png"), Utils.loadImage(EMPTY_IMAGE_PATH)).queue(success -> getLogger().debug("Song {} has been moved to #1 in guild {}", lastTrack.getInfo().title, Logger.getGuildNameAndId(o.e.getGuild())), o::sendFailed);
	}

	public void playJachtseizoen(Guild guild) {
		Logger.debug(getLogger(), "Playing Jachtseizoen sound in {}", () -> new String[] {Logger.getGuildNameAndId(guild)});
		PlayerManager.getInstance(this).getGuildMusicManager(guild).scheduler.playUnSkippableTrack(
			new AudioTrackInfo(":rotating_light: Jachtseizoen :rotating_light:", "De jacht in GTA is geopend!", 46000L, "jachtseizoen.mp3", false, "internal"),
			"audio/jachtseizoen.mp3",
			false, false
		);
	}

	public void playCall(Guild guild, @NotNull TextChannel channel) {
		Logger.debug(getLogger(), "Playing call sound in {}", () -> new String[] {Logger.getGuildNameAndId(guild)});
		PlayerManager.getInstance(this).getGuildMusicManager(guild).scheduler.playUnSkippableTrack(
			new AudioTrackInfo(":pray: Iemand wil meedoen! :pray:", "Kijk in " + channel.getAsMention() + "!", 65000L, "call.mp3", false, "internal"),
			"audio/call.mp3",
			true, true
		);
	}

	public void stopLooping(Guild guild) {
		Logger.debug(getLogger(), "Stopping looping sound in {}", () -> new String[] {Logger.getGuildNameAndId(guild)});
		PlayerManager.getInstance(this).getGuildMusicManager(guild).scheduler.stopLoopingTrack();
	}

	@Override
	public void sendInformation(@NotNull ReplyOperation o) {
		Guild guild = o.e.getGuild();
		Member self = guild.getSelfMember();

		EmbedBuilder eb = new EmbedBuilder()
			.setColor(Color.CYAN)
			.setImage(EMPTY_IMAGE)
			.setThumbnail("attachment://muziekjes.png")
			.setDescription("# :trumpet: Muziekjes Help :trumpet:\n" +
				"Muziek afspelen is heel eenvoudig! Stuur een bericht met de titel of URL van een nummer/afspeellijst in " + Channels.MUSIC.getAsMention(guild) + " en het wordt automatisch opgezocht. " +
				"Zorg ervoor dat je in " + Channels.VOICE.getAsMention(guild) + " zit! " +
				"Als de link niet wordt ondersteund, zoekt " + self.getAsMention() + " automatisch op SoundCloud en geeft je maximaal vijf opties om uit te kiezen. " +
				"Het gevonden nummer wordt direct afgespeeld of aan de wachtrij toegevoegd. De wachtrij heeft een limiet van `" + MAX_QUEUE_SIZE + "` nummers." +
				"\n### :paperclip: | Welke links kun je gebruiken?" +
				"\n**Soundcloud**\n**Bandcamp**\n**Vimeo**\n**Twitch streams**" +
				"\n**HTTP URLs** Alle links die eindigen met `.mp3`, `.flac`, `.wav`, `.mka`, `.webm`, `.mp4`, `.m4a`, `.ogg`, `.opus`, `.aac`, `.m3u`, `.pls`." +
				"\n### :control_knobs: | Hoe bedien je de muziek?" +
				"\nZodra de muziek speelt, kun je met deze knoppen de boel regelen:" +
				"\n**`⏸️ Pauzeer`** Zet de muziek even stil." +
				"\n**`🔁 Opnieuw Afspelen`** Speelt hetzelfde liedje nog een keer." +
				"\n**`⛔ Stop`** Stopt alles, leegt de wachtrij en " + self.getAsMention() + " verlaat de call. _(Dit gebeurt ook als iedereen de call verlaat.)_" +
				"\n**`📄 Wachtrij`** Stuurt de volledige wachtrij. _(Werkt niet goed op mobiele apparaten.)_" +
				"\n**`📝 Laatste naar #1`** Zet het laatst toegevoegde nummer helemaal bovenaan." +
				"\n**`📝 Wachtrij Leegmaken`** Verwijdert alle nummers uit de wachtrij."
			).setFooter("Veel plezier met luisteren!");

		o.e.getHook().sendMessageEmbeds(eb.build())
			.setFiles(Utils.loadImage("muziekjes.png"), Utils.loadImage(EMPTY_IMAGE_PATH))
		.queue(null, o::sendFailed);
	}
}