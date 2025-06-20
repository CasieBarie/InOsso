package dev.casiebarie.inosso.music.lavaplayer;

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.container.ogg.OggAudioTrack;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.NonSeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.casiebarie.inosso.enums.Channels;
import dev.casiebarie.inosso.music.Music;
import dev.casiebarie.inosso.utils.ReplyOperation;
import dev.casiebarie.inosso.utils.Utils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static dev.casiebarie.inosso.Main.jda;
import static dev.casiebarie.inosso.enums.Variables.*;
import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class TrackScheduler extends AudioEventAdapter {
	String filename;
	final Music music;
	AudioTrackInfo info;
	final String guildId;
	final AudioPlayer player;
	boolean isLooping, wasPaused;
	public AudioTrack oldTrack, loopingTrack;
	public final Queue<AudioTrack> queue = new ConcurrentLinkedQueue<>();
	public TrackScheduler(Music music, String guildId, AudioPlayer player) {
		this.music = music;
		this.guildId = guildId;
		this.player = player;
	}

	@Override
	public void onTrackStart(AudioPlayer player, @NotNull AudioTrack track) {getLogger().debug("Playing track: {}", track.getInfo().title);}

	@Override
	public void onTrackEnd(AudioPlayer player, @NotNull AudioTrack track, AudioTrackEndReason endReason) {
		if(track.getIdentifier().equals("silence.opus")) {player.playTrack(queue.poll()); return;}
		if(endReason.mayStartNext) {nextTrack(false);}
	}

	@Override
	public void onTrackException(AudioPlayer player, @NotNull AudioTrack track, @NotNull FriendlyException exception) {handleTrackError(track, "Error bij het afspelen van: `%s`", exception.getMessage());}

	@Override
	public void onTrackStuck(AudioPlayer player, @NotNull AudioTrack track, long thresholdMs) {handleTrackError(track, "Het nummer `%s` is vastegelopen en wordt geskipt.");}

	public boolean queueTrack(@NotNull AudioTrack track, String adder) {
		track.setUserData(adder);
		if(player.getPlayingTrack() == null) {player.playTrack(track); return true;}
		if(queue.size() >= MAX_QUEUE_SIZE) {return false;}
		return queue.offer(track);
	}

	public int queueAll(@NotNull AudioPlaylist playlist, String adder) {
		int count = 0;
		for(AudioTrack track : playlist.getTracks()) {
			if(Utils.isSoundCloudGoPlus(track)) {continue;}
			track.setUserData(adder);
			if(player.getPlayingTrack() == null) {player.playTrack(track);
			} else {
				if(queue.size() >= MAX_QUEUE_SIZE) {return count;}
				queue.offer(track);
			} count++;
		} return count == 0 ? -1 : count;
	}

	public void nextTrack(boolean force) {
		if(force) {
			setToNull();
			player.playTrack(queue.poll());
			player.setPaused(false);
			return;
		}

		if(isLooping) {
			loopingTrack = new Mp3AudioTrack(info, new NonSeekableInputStream(Music.class.getClassLoader().getResourceAsStream(filename)));
			loopingTrack.setUserData(jda().getGuildById(guildId).getSelfMember().getId());
			player.playTrack(loopingTrack);
			return;
		}

		if(oldTrack != null) {player.playTrack(oldTrack);
		} else {player.playTrack(queue.poll());}

		if(wasPaused) {player.setPaused(true);}
		setToNull();
	}

	public void playStartupSound(@NotNull Guild guild) {
		AudioTrackInfo newInfo = new AudioTrackInfo("Startup Sound", "InOsso", Long.MIN_VALUE, "silence.opus", false, "internal");
		AudioTrack track = new OggAudioTrack(newInfo, new NonSeekableInputStream(Music.class.getClassLoader().getResourceAsStream("audio/silence.opus")));
		track.setUserData(guild.getSelfMember().getId());
		player.playTrack(track);
	}

	public void replay(ReplyOperation o) {
		AudioTrack nowPlaying = player.getPlayingTrack();
		copyAndSaveTrack(nowPlaying, nowPlaying.getUserData().toString(), o);
	}

	public void playUnSkippableTrack(AudioTrackInfo info, String filename, boolean isLooping, boolean force) {
		if(loopingTrack != null) {
			if(force) {stopLoopingTrack();
			} else {return;}
		}

		this.info = info;
		this.filename = filename;
		this.isLooping = isLooping;

		oldTrack = null;
		loopingTrack = null;
		wasPaused = player.isPaused();

		Guild guild = jda().getGuildById(guildId);
		music.connect(guild);
		AudioTrack nowPlaying = player.getPlayingTrack();

		if(nowPlaying != null) {copyAndSaveTrack(nowPlaying, nowPlaying.getUserData().toString(), null);}
		loopingTrack = new Mp3AudioTrack(info, new NonSeekableInputStream(Music.class.getClassLoader().getResourceAsStream(filename)));
		loopingTrack.setUserData(guild.getSelfMember().getId());

		LinkedList<AudioTrack> list = new LinkedList<>(queue);
		list.add(0, loopingTrack);
		queue.clear();
		queue.addAll(list);
		if(nowPlaying != null && !nowPlaying.getIdentifier().equals("silence.opus")) {player.playTrack(queue.poll());}
		player.setPaused(false);
	}

	public void stopLoopingTrack() {
		isLooping = false;
		nextTrack(false);
	}

	public void setToNull() {
		loopingTrack = null;
		info = null;
		filename = null;
		isLooping = false;
		oldTrack = null;
		wasPaused = false;
	}

	private void handleTrackError(@NotNull AudioTrack track, String messageFormat, Object... args) {
		String trackTitle = Utils.truncate(track.getInfo().title, 180);
		getLogger().error(messageFormat, trackTitle, args);

		Optional.ofNullable(jda().getGuildById(guildId))
			.map(Channels.MUSIC::getAsChannel)
			.ifPresent(channel -> new ReplyOperation((GuildMessageChannel) channel)
			.sendFailed(String.format(messageFormat, trackTitle))
		);
	}

	private void copyAndSaveTrack(@NotNull AudioTrack track, String adder, ReplyOperation o) {
		PlayerManager manager = PlayerManager.getInstance(music);
		manager.getAudioPlayerManager().loadItemOrdered(manager.getGuildMusicManager(jda().getGuildById(guildId)), track.getInfo().uri, new AudioLoadResultHandler() {
			@Override
			public void trackLoaded(AudioTrack audioTrack) {
				audioTrack.setUserData(adder);
				if(o == null) {oldTrack = audioTrack; return;}
				player.playTrack(audioTrack);
				player.setPaused(false);
				o.e.getHook().editOriginalEmbeds(
					new EmbedBuilder()
						.setDescription("# Opnieuw afspelen" +
							"\n> **" + Utils.truncate(audioTrack.getInfo().title, 180) + "**" +
							"\n> " + Utils.truncate(audioTrack.getInfo().author, 180) +
							"\n> *(" + Utils.formatDuration(audioTrack.getDuration()) + ")*" +
							"\n\nwordt opnieuw afgespeeld!")
						.setColor(Color.GREEN)
						.setThumbnail("attachment://muziekjes.png")
						.setImage(EMPTY_IMAGE)
					.build()
				).setFiles(Utils.loadImage("muziekjes.png"), Utils.loadImage(EMPTY_IMAGE_PATH)).queue(null, o::sendFailed);
				getLogger().debug("Track loaded: {}", track.getInfo().title);
			}

			@Override public void playlistLoaded(AudioPlaylist audioPlaylist) {replayFailed();}
			@Override public void noMatches() {replayFailed();}
			@Override public void loadFailed(FriendlyException e) {replayFailed();}
			private void replayFailed() {if(o != null) {o.sendFailed("Opnieuw afspelen mislukt. Probeer het later opnieuw.");}}
		});
	}
}