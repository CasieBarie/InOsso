package dev.casiebarie.inosso.utils;

import dev.casiebarie.inosso.InstanceManager;
import dev.casiebarie.inosso.Main;
import dev.casiebarie.inosso.enums.Channels;
import dev.casiebarie.inosso.interfaces.ScheduledTask;
import dev.casiebarie.inosso.utils.logging.Logger;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static dev.casiebarie.inosso.Main.jda;
import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class ScheduledTaskManager extends ListenerAdapter {
	final Collection<ScheduledTask> scheduledTasksClasses;
	final Map<String, ScheduledFuture<?>> stopTasks = new ConcurrentHashMap<>(4);
	final Map<String, Map<Class<? extends ScheduledTask>, ScheduledFuture<?>>> runningTasks = new ConcurrentHashMap<>(4);
	public ScheduledTaskManager(@NotNull InstanceManager iManager, Collection<ScheduledTask> scheduledTaskClasses) {
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
		boolean hasMembers = !Channels.VOICE.getAsChannel(guild).getMembers().isEmpty();
		if(hasMembers) {startTasks(guild);
		} else {scheduleStopTasks(guild);}
	}

	private void startTasks(@NotNull Guild guild) {
		String guildId = guild.getId();
		Map<Class<? extends ScheduledTask>, ScheduledFuture<?>> guildTasks = runningTasks.computeIfAbsent(guildId, k -> new ConcurrentHashMap<>(scheduledTasksClasses.size()));
		getLogger().debug("Starting tasks for {}", Logger.getGuildNameAndId(guild));

		jda().getPresence().setStatus(OnlineStatus.ONLINE);
		jda().getPresence().setActivity(Activity.customStatus("Spelend met de bois!"));

		for(ScheduledTask taskClass : scheduledTasksClasses) {
			if(!guildTasks.containsKey(taskClass.getClass())) {
				ScheduledFuture<?> task = taskClass.startTask(guildId);
				guildTasks.put(taskClass.getClass(), task);
			}
		}

		ScheduledFuture<?> stopTask = stopTasks.remove(guildId);
		if(stopTask != null) {
			getLogger().debug("Cancelling stop for {}", Logger.getGuildNameAndId(guild));
			stopTask.cancel(false);
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

		jda().getPresence().setStatus(OnlineStatus.IDLE);
		jda().getPresence().setActivity(Activity.customStatus("Aan het slapen..."));

		if(tasks == null) {return;}
		tasks.values().forEach(task -> task.cancel(true));
		stopTasks.remove(guildId);
	}
}