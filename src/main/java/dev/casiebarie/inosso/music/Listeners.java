package dev.casiebarie.inosso.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import dev.casiebarie.inosso.ClassLoader;
import dev.casiebarie.inosso.Main;
import dev.casiebarie.inosso.enums.Channels;
import dev.casiebarie.inosso.interfaces.ScheduledTask;
import dev.casiebarie.inosso.music.lavaplayer.GuildMusicManager;
import dev.casiebarie.inosso.music.lavaplayer.PlayerManager;
import dev.casiebarie.inosso.utils.ReplyOperation;
import dev.casiebarie.inosso.utils.Utils;
import dev.casiebarie.inosso.utils.logging.Logger;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class Listeners extends ListenerAdapter implements ScheduledTask {
	final Music music;
	final ClassLoader classes;
	public Listeners(@NotNull ClassLoader classes, Music music) {
		this.classes = classes;
		this.music = music;
		classes.registerAsEventListener(this);
		classes.registerAsScheduledTaskClass(this);
	}

	@Override
	public ScheduledFuture<?> startTask(String guildId) {
		return Main.scheduledPool.scheduleAtFixedRate(Main.safeRunnable(() -> music.controllers.computeIfAbsent(guildId, id -> new Controller(music, id)).updateMessage()), 0L, Utils.jitter(100), TimeUnit.MILLISECONDS);
	}

	@Override
	public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent e) {
		Guild guild = e.getGuild();
		if(!guild.getAudioManager().isConnected()) {return;}
		List<Member> members = new ArrayList<>(Channels.VOICE.getAsChannel(guild).getMembers());
		members.remove(guild.getSelfMember());
		if(!members.isEmpty()) {return;}
		classes.jachtseizoen.stopPlaying(guild, false);
		music.stopMusic(guild, false);
	}

	@Override
	public void onButtonInteraction(@NotNull ButtonInteractionEvent e) {
		if(!e.getButton().getId().startsWith("music_") || e.getUser().isBot()) {return;}
		Guild guild = e.getGuild();
		ReplyOperation o = new ReplyOperation(e);
		GuildMusicManager manager = PlayerManager.getInstance(music).getGuildMusicManager(guild);
		Logger.debug(getLogger(), "ButtonInteraction with ID {} by {}", () -> new String[] {e.getComponentId(), Logger.getUserNameAndId(e.getUser())});
		e.deferReply(true).queue();

		Main.pool.execute(() -> {
			if(!Utils.isInVoice(e.getMember(), o)) {return;}

			switch(e.getButton().getId().split("_")[1]) {
			case "pause" -> {
				if(manager.player.getPlayingTrack() == null || manager.scheduler.loopingTrack != null) {o.sendFailed("Actie geannuleerd! Je was te snel."); return;}
				boolean paused = manager.player.isPaused();
				manager.player.setPaused(!paused);
				o.sendSuccess("Muziek is " + (paused ? "hervat!" : "gepauzeerd!"));}
			case "skip" -> {
				AudioPlayer player = manager.player;
				if(player.getPlayingTrack() == null) {o.sendFailed("Actie geannuleerd! Je was te snel."); return;}
				if(manager.scheduler.queue.isEmpty() || manager.scheduler.loopingTrack != null) {o.sendFailed("Actie geannuleerd! Je was te snel."); return;}
				manager.scheduler.nextTrack(true);
				o.sendSuccess("Nummer geskipt!");}
			case "replay" -> {
				if(manager.player.getPlayingTrack() == null) {o.sendFailed("Actie geannuleerd! Je was te snel."); return;}
				manager.scheduler.replay(o);}
			case "stop" -> {
				if(!music.controllers.get(guild.getId()).isConnected) {o.sendFailed("Actie geannuleerd! Je was te snel."); return;}
				o.replyEmpty();
				music.stopMusic(e.getGuild(), true);}
			case "viewqueue" -> music.queueViewer.viewQueue(music, e);
			case "movetoone" -> music.moveToOne(manager.scheduler, o);
			case "emptyqueue" -> {
				if(manager.scheduler.queue.isEmpty()) {o.sendFailed("Actie geannuleerd! Je was te snel."); return;}
				manager.scheduler.queue.clear();
				o.sendSuccess("Wachtlijst leeggemaakt!");}
			default -> {/*IGNORED*/}}
		});
	}
}