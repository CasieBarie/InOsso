package dev.casiebarie.inosso.utils;

import dev.casiebarie.inosso.ClassLoader;
import dev.casiebarie.inosso.Main;
import dev.casiebarie.inosso.interfaces.CommandListener;
import dev.casiebarie.inosso.utils.logging.Logger;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.context.UserContextInteraction;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class CommandManager extends ListenerAdapter {
	final ClassLoader classes;
	Map<CommandData, CommandListener> commandListeners = new HashMap<>();
	Map<CommandListener, Boolean> needsPoolMap = new HashMap<>();
	public CommandManager(@NotNull ClassLoader classes, @NotNull Map<CommandListener, Boolean> commandListeners) {
		this.classes = classes;
		classes.registerAsEventListener(this);
		needsPoolMap = commandListeners;
		commandListeners.keySet().forEach(listener -> this.commandListeners.put(listener.getCommand(), listener));
	}

	@Override
	public void onReady(ReadyEvent e) {runConsole();}

	@Override
	public void onGuildReady(@NotNull GuildReadyEvent e) {
		List<CommandData> guildCMDS = new ArrayList<>(commandListeners.keySet());
		e.getGuild().updateCommands().addCommands(guildCMDS).queue(
			success -> getLogger().info("Updated commands for: {}", e.getGuild().getName()),
			error -> getLogger().error("Didn't update commands for: {}", e.getGuild().getName(), error)
		);
	}

	@Override
	public void onGenericCommandInteraction(@NotNull GenericCommandInteractionEvent e) {
		User user = e.getUser();
		Logger.debug(getLogger(), "Received command {} from {}", () -> new String[] {e.getCommandString(), Logger.getUserNameAndId(user)});
		if(user.isBot()) {return;}
		ReplyOperation o = new ReplyOperation(e);
		if(!e.isFromGuild()) {o.sendNotAllowed("Je kan alleen commands gebruiken in de InOsso server."); return;}

		CommandData commandData = commandListeners.keySet().stream().filter(data -> data.getName().equals(e.getName())).findFirst().orElse(null);
		if(commandData == null) {o.sendNotAllowed("Ik herken dit command niet!"); return;}
		CommandListener listener = commandListeners.get(commandData);

		if(e.getCommandType().equals(Command.Type.SLASH)) {
			SlashCommandInteraction event = (SlashCommandInteraction) e;
			e.deferReply(needsPoolMap.get(listener)).queue();
			Main.pool.execute(() -> listener.onCommand(event));
		} else {
			UserContextInteraction event = (UserContextInteraction) e;
			e.deferReply(needsPoolMap.get(listener)).queue();
			Main.pool.execute(() -> listener.onCommand(event));
		}
	}

	private void runConsole() {
		Thread thread = new Thread(() -> {
			Scanner scanner = new Scanner(System.in);
			while(scanner.hasNextLine()) {
				String input = scanner.nextLine();
				if(input.equals("shutdown")) {classes.shutdown();
				} else {getLogger().warn("Unknown command: {}", input);}
			}
		});
		thread.setName("InOsso Console");
		thread.start();
		getLogger().debug("Console started");
	}
}