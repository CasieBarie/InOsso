package dev.casiebarie.inosso.music.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import dev.casiebarie.inosso.music.Music;
import org.jetbrains.annotations.NotNull;

public class GuildMusicManager {
	public final AudioPlayer player;
	public final TrackScheduler scheduler;
	final AudioPlayerSendHandler handler;
	public GuildMusicManager(Music music, String guildId, @NotNull AudioPlayerManager manager) {
		player = manager.createPlayer();
		scheduler = new TrackScheduler(music, guildId, player);
		player.addListener(scheduler);
		handler = new AudioPlayerSendHandler(player);
	} public AudioPlayerSendHandler getHandler() {return handler;}
}