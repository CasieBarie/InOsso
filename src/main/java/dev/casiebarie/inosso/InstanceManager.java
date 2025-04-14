package dev.casiebarie.inosso;

import dev.casiebarie.inosso.interfaces.CommandListener;
import dev.casiebarie.inosso.interfaces.Information;
import dev.casiebarie.inosso.interfaces.ScheduledTask;
import dev.casiebarie.inosso.jachtseizoen.Jachtseizoen;
import dev.casiebarie.inosso.music.Music;
import dev.casiebarie.inosso.other.*;
import dev.casiebarie.inosso.utils.*;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InstanceManager {
	final Main main;
	final Map<Class<?>, Object> services = new HashMap<>();
	final List<Object> eventListeners = new ArrayList<>();
	final Map<CommandListener, Boolean> commandListeners = new HashMap<>();
	final Map<String, Information> informationClasses = new HashMap<>();
	final List<ScheduledTask> scheduledTasks = new ArrayList<>();
	public void registerAsEventListener(ListenerAdapter listener) {eventListeners.add(listener);}
	public void registerAsCommandListener(CommandListener listener, boolean ephemeral) {commandListeners.put(listener, ephemeral);}
	public void registerAsInformationClass(String name, Information infoClass) {informationClasses.put(name, infoClass);}
	public void registerAsScheduledTaskClass(ScheduledTask taskClass) {scheduledTasks.add(taskClass);}
	protected List<Object> getEventListeners() {return eventListeners;}
	public void shutdown() {main.shutdown();}

	public InstanceManager(Main main) {
		this.main = main;

		bind(DependencyChecker.class, new DependencyChecker(this));
		bind(WebhookManager.class, new WebhookManager(this));
		bind(Music.class, new Music(this));

		bind(Choose.class, new Choose(this));
		bind(Jachtseizoen.class, new Jachtseizoen(this));

		bind(Changelog.class, new Changelog(this));
		bind(Clips.class, new Clips(this));
		bind(Come.class, new Come(this));
		bind(Guest.class, new Guest(this));
		bind(JoinManager.class, new JoinManager(this));
		bind(MessageDeleter.class, new MessageDeleter(this));
		bind(Request.class, new Request(this));
		bind(RoleNameManager.class, new RoleNameManager(this));
		bind(SendEmbed.class, new SendEmbed(this));

		bind(Help.class, new Help(this, informationClasses));
		bind(ScheduledTaskManager.class, new ScheduledTaskManager(this, scheduledTasks));
		bind(CommandManager.class, new CommandManager(this, commandListeners));
	}

	public <T> void bind(Class<T> clazz, T instance) {services.put(clazz, instance);}
	public <T> T get(@NotNull Class<T> clazz) {return clazz.cast(services.get(clazz));}
}