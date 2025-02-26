package dev.casiebarie.inosso;

import dev.casiebarie.inosso.interfaces.CommandListener;
import dev.casiebarie.inosso.interfaces.Information;
import dev.casiebarie.inosso.interfaces.ScheduledTask;
import dev.casiebarie.inosso.jachtseizoen.Jachtseizoen;
import dev.casiebarie.inosso.music.Music;
import dev.casiebarie.inosso.other.*;
import dev.casiebarie.inosso.utils.*;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ClassLoader {
	public ClassLoader(Main main) {this.main = main;}
	ArrayList<Object> eventListeners = new ArrayList<>();
	ArrayList<ScheduledTask> scheduledTaskClasses = new ArrayList<>();
	Map<CommandListener, Boolean> commandListeners = new HashMap<>();
	Map<String, Information> informationClasses =  new HashMap<>();
	public void registerAsEventListener(ListenerAdapter listener) {eventListeners.add(listener);}
	public void registerAsCommandListener(CommandListener listener, boolean ephemeral) {commandListeners.put(listener, ephemeral);}
	public void registerAsInformationClass(String name, Information infoClass) {informationClasses.put(name, infoClass);}
	public void registerAsScheduledTaskClass(ScheduledTask taskClass) {scheduledTaskClasses.add(taskClass);}
	protected ArrayList<Object> getEventListeners() {return eventListeners;}
	public void shutdown() {main.shutdown();}

	// --- CLASSES ---
	public final Main main;
	public final DependencyChecker dependencyChecker = new DependencyChecker(this);
	public final WebhookManager webhookManager = new WebhookManager(this);
	public final Music music = new Music(this);

	public final Choose choose = new Choose(this);
	public final Jachtseizoen jachtseizoen = new Jachtseizoen(this);

	public final Clips clips = new Clips(this);
	public final Come come = new Come(this);
	public final Guest guest = new Guest(this);
	public final JoinManager joinManager = new JoinManager(this);
	public final Request request = new Request(this);
	public final RoleNameManager roleNameManager = new RoleNameManager(this);
	public final SendEmbed sendEmbed = new SendEmbed(this);

	public final Help help = new Help(this, informationClasses);
	public final ScheduledTaskManager scheduledTaskManager = new ScheduledTaskManager(this, scheduledTaskClasses);
	public final SetupCommand setupCommand = new SetupCommand(this);
	public final CommandManager commandManager = new CommandManager(this, commandListeners);
}