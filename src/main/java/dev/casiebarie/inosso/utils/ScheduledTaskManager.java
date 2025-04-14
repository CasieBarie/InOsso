package dev.casiebarie.inosso.utils;

import dev.casiebarie.inosso.InstanceManager;
import dev.casiebarie.inosso.Main;
import dev.casiebarie.inosso.enums.Channels;
import dev.casiebarie.inosso.interfaces.ScheduledTask;
import dev.casiebarie.inosso.utils.logging.Logger;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static dev.casiebarie.inosso.Main.jda;
import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class ScheduledTaskManager extends ListenerAdapter {
	final List<ScheduledTask> scheduledTasksClasses;
	final Map<String, ScheduledFuture<?>> stopTasks = new ConcurrentHashMap<>();
	final Map<String, Map<Class<? extends ScheduledTask>, ScheduledFuture<?>>> runningTasks = new ConcurrentHashMap<>();
	public ScheduledTaskManager(@NotNull InstanceManager iManager, List<ScheduledTask> scheduledTaskClasses) {
		this.scheduledTasksClasses = scheduledTaskClasses;
		iManager.registerAsEventListener(this);
	}

	@Override
	public void onGuildReady(@NotNull GuildReadyEvent e) {
		startTasks(e.getGuild());
		checkVoiceChannel(e.getGuild());
	}

	@Override
	public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent e) {checkVoiceChannel(e.getGuild());}

	private void checkVoiceChannel(Guild guild) {
		List<Member> onlineMembers = new ArrayList<>(Channels.VOICE.getAsChannel(guild).getMembers());
		if(!onlineMembers.isEmpty()) {startTasks(guild);
		} else {scheduleStopTasks(guild);}
	}

	private void startTasks(@NotNull Guild guild) {
		String guildId = guild.getId();
		runningTasks.putIfAbsent(guildId, new ConcurrentHashMap<>());
		getLogger().debug("Starting tasks for {}", Logger.getGuildNameAndId(guild));
		if(guildId.equals("844304271649538058")) {jda().getPresence().setStatus(OnlineStatus.ONLINE);}

		for(ScheduledTask taskClass : scheduledTasksClasses) {
			if(runningTasks.get(guildId).containsKey(taskClass.getClass())) {continue;}
			ScheduledFuture<?> task = taskClass.startTask(guildId);
			runningTasks.get(guildId).put(taskClass.getClass(), task);
		}

		if(stopTasks.containsKey(guildId)) {
			getLogger().debug("Cancelling stop for {}", Logger.getGuildNameAndId(guild));
			stopTasks.get(guildId).cancel(false);
			stopTasks.remove(guildId);
		}
	}

	private void scheduleStopTasks(@NotNull Guild guild) {
		getLogger().debug("Scheduling stop tasks for {}", Logger.getGuildNameAndId(guild));
		String guildId = guild.getId();
		ScheduledFuture<?> stopTask = Main.scheduledPool.schedule(Main.safeRunnable(() -> stopTasks(guild)), 30, TimeUnit.SECONDS);
		stopTasks.put(guildId, stopTask);
	}

	private void stopTasks(@NotNull Guild guild) {
		getLogger().debug("Stopping tasks for {}", Logger.getGuildNameAndId(guild));
		String guildId = guild.getId();
		Map<Class<? extends ScheduledTask>, ScheduledFuture<?>> tasks = runningTasks.remove(guildId);
		if(guildId.equals("844304271649538058")) {jda().getPresence().setStatus(OnlineStatus.IDLE);}

		if(tasks == null) {return;}
		tasks.values().forEach(task -> task.cancel(true));
		stopTasks.remove(guildId);
	}
}