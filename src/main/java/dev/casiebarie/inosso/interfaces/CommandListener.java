package dev.casiebarie.inosso.interfaces;

import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.context.UserContextInteraction;

public interface CommandListener {
	CommandData getCommand();
	default void onCommand(SlashCommandInteraction e) {}
	default void onCommand(UserContextInteraction e) {}
}