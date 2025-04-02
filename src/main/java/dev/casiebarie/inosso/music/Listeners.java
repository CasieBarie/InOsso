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
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static dev.casiebarie.inosso.enums.Variables.ACTION_CANCELLED_MSG;
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
		getLogger().debug("ButtonInteraction with ID {} by {}", e.getComponentId(), Logger.getUserNameAndId(e.getUser()));
		e.deferReply(true).queue(null, ReplyOperation::error);

		Main.pool.execute(() -> {
			if(!Utils.isInVoice(e.getMember(), o)) {return;}
			switch(e.getButton().getId().split("_")[1]) {
			case "pause" -> pause(manager, o);
			case "skip" -> skip(manager, o);
			case "replay" -> replay(manager, o);
			case "stop" -> stop(o);
			case "viewqueue" -> music.queueViewer.viewQueue(music, e);
			case "movetoone" -> music.moveToOne(manager.scheduler, o);
			case "emptyqueue" -> emptyQueue(manager, o);
			default -> {/*IGNORED*/}}
		});
	}

	@Override
	public void onMessageReceived(@NotNull MessageReceivedEvent e) {
		if(!e.isFromGuild() || e.getAuthor().isBot()) {return;}
		Member searcher = e.getMember();
		TextChannel channel = Channels.MUSIC.getAsChannel(searcher.getGuild());

		if(!e.getGuildChannel().equals(channel)) {return;}
		getLogger().debug("Music search message received by {}", Logger.getUserNameAndId(searcher.getUser()));

		Main.pool.execute(() -> new Search(music, searcher, e.getMessage()));
	}

	@Override
	public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent e) {
		if(!e.getSelectMenu().getId().startsWith("searchmusic_")) {return;}
		new ReplyOperation(e).replyEmpty();
	}

	private void pause(@NotNull GuildMusicManager manager, ReplyOperation o) {
		if(manager.player.getPlayingTrack() == null || manager.scheduler.loopingTrack != null) {o.sendFailed(ACTION_CANCELLED_MSG); return;}
		boolean paused = manager.player.isPaused();
		manager.player.setPaused(!paused);
		o.sendSuccess("Muziek is " + (paused ? "hervat!" : "gepauzeerd!"));
	}

	private void skip(@NotNull GuildMusicManager manager, ReplyOperation o) {
		AudioPlayer player = manager.player;
		if(player.getPlayingTrack() == null) {o.sendFailed(ACTION_CANCELLED_MSG); return;}
		if(manager.scheduler.queue.isEmpty() || manager.scheduler.loopingTrack != null) {o.sendFailed(ACTION_CANCELLED_MSG); return;}
		manager.scheduler.nextTrack(true);
		o.sendSuccess("Nummer geskipt!");
	}

	private void replay(@NotNull GuildMusicManager manager, ReplyOperation o) {
		if(manager.player.getPlayingTrack() == null) {o.sendFailed(ACTION_CANCELLED_MSG); return;}
		manager.scheduler.replay(o);
	}

	private void stop(@NotNull ReplyOperation o) {
		if(!music.controllers.get(o.e.getGuild().getId()).isConnected) {o.sendFailed(ACTION_CANCELLED_MSG); return;}
		o.replyEmpty();
		music.stopMusic(o.e.getGuild(), true);
	}

	private void emptyQueue(@NotNull GuildMusicManager manager, ReplyOperation o) {
		if(manager.scheduler.queue.isEmpty()) {o.sendFailed(ACTION_CANCELLED_MSG); return;}
		manager.scheduler.queue.clear();
		o.sendSuccess("Wachtlijst leeggemaakt!");
	}
}