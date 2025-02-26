package dev.casiebarie.inosso.utils;

import dev.casiebarie.inosso.ClassLoader;
import dev.casiebarie.inosso.interfaces.CommandListener;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;

public class SetupCommand implements CommandListener {
	final ClassLoader classes;
	public SetupCommand(@NotNull ClassLoader classes) {
		this.classes = classes;
		classes.registerAsCommandListener(this, true);
	}

	@Override
	public CommandData getCommand() {
		return Commands.slash("setup", "Basis van setup command.").addSubcommands(
			new SubcommandData("request", String.format("%s | Genereert het verzoekbericht.", Emoji.fromFormatted("🙏"))),
			new SubcommandData("muziek", String.format("%s | Genereert het muziekbericht.", Emoji.fromFormatted("🎺")))
		);
	}

	@Override
	public void onCommand(@NotNull SlashCommandInteraction e) {
		String subcommandName = e.getSubcommandName();
		if(subcommandName.equals("request")) {classes.request.onSetupCommand(e);
		} else if(subcommandName.equals("muziek")) {classes.music.onSetupCommand(e);}
	}
}