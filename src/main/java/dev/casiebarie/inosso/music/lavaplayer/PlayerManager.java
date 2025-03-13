package dev.casiebarie.inosso.music.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoAudioSourceManager;
import dev.casiebarie.inosso.music.Music;
import net.dv8tion.jda.api.entities.Guild;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class PlayerManager {
	final Music music;
	static PlayerManager instance;
	final DefaultAudioPlayerManager audioPlayerManager;
	final Map<String, GuildMusicManager> musicManagers = new HashMap<>();
	public static PlayerManager getInstance(Music music) {if(instance == null) {instance = new PlayerManager(music);} return instance;}
	public DefaultAudioPlayerManager getAudioPlayerManager() {return audioPlayerManager;}
	static void setInstance(PlayerManager instance) {PlayerManager.instance = instance;}
	private PlayerManager(Music music) {
		setInstance(this);
		this.music = music;

		System.setProperty("lavaplayer.http.loadbalance", "true");
		System.setProperty("lavaplayer.http.userAgent", "Mozilla/5.0");
		System.setProperty("lavaplayer.track-load-timeout", "3000");
		System.setProperty("lavaplayer.non-allocating-audio-frame-buffer", "true");

		audioPlayerManager = new DefaultAudioPlayerManager();
		audioPlayerManager.registerSourceManager(new LocalAudioSourceManager());
		audioPlayerManager.registerSourceManager(SoundCloudAudioSourceManager.createDefault());
		audioPlayerManager.registerSourceManager(new BandcampAudioSourceManager());
		audioPlayerManager.registerSourceManager(new VimeoAudioSourceManager());
		audioPlayerManager.registerSourceManager(new TwitchStreamAudioSourceManager());
		audioPlayerManager.registerSourceManager(new HttpAudioSourceManager());

		getLogger().debug("Initialized player manager");
	}

	public GuildMusicManager getGuildMusicManager(@NotNull Guild guild) {
		return musicManagers.computeIfAbsent(guild.getId(), guildId -> {
			GuildMusicManager manager = new GuildMusicManager(music, guild.getId(), audioPlayerManager);
			guild.getAudioManager().setSendingHandler(manager.getHandler());
			return manager;
		});
	}
}