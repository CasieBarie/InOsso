package dev.casiebarie.inosso.utils;

import dev.casiebarie.inosso.InstanceManager;
import dev.casiebarie.inosso.interfaces.CommandListener;
import dev.casiebarie.inosso.interfaces.Information;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;

import static dev.casiebarie.inosso.Main.jda;
import static dev.casiebarie.inosso.enums.Variables.EMPTY_IMAGE;
import static dev.casiebarie.inosso.enums.Variables.EMPTY_IMAGE_PATH;

public class Help implements CommandListener, Information {
	final Map<String, Information> informations;
	public Help(@NotNull InstanceManager iManager, Map<String, Information> informations) {
		this.informations = informations;
		iManager.registerAsInformationClass("bot-info", this);
		iManager.registerAsCommandListener(this, true);
	}

	@Override
	public CommandData getCommand() {
		Collection<Command.Choice> choices = new LinkedList<>();
		informations.keySet().forEach(key -> choices.add(new Command.Choice(key, key)));
		return Commands.slash("help", String.format("%s | Ontvang hulp of informatie over een functie.", Emoji.fromFormatted("🆘"))).addOptions(
			new OptionData(OptionType.STRING, "onderwerp", "Het onderwerp", true).addChoices(choices)
		);
	}

	@Override
	public void onCommand(@NotNull SlashCommandInteraction e) {
		String topic = e.getOption("onderwerp", OptionMapping::getAsString);
		Information information = informations.get(topic);
		ReplyOperation o = new ReplyOperation(e);
		information.sendInformation(o);
	}

	@Override
	public void sendInformation(@NotNull ReplyOperation o) {
		Guild guild = o.e.getGuild();
		Member self = guild.getSelfMember();
		Member cas = Utils.getCasAsMember(guild);

		EmbedBuilder eb = new EmbedBuilder()
			.setColor(o.e.getMember().getColor())
			.setThumbnail("attachment://avatar.png")
			.setImage(EMPTY_IMAGE)
			.setFooter("Gemaakt door " + cas.getEffectiveName(), "attachment://avatar1.png");

		StringBuilder builder = new StringBuilder("# :nerd: Nerd Info :nerd:");
		builder.append("\n### :sparkles: | Algemeen")
			.append("\nNaam: `").append(self.getEffectiveName()).append("`")
			.append("\nGebruikersnaam: `").append(self.getUser().getAsTag()).append("`")
			.append("\nId: `").append(self.getId()).append("`")
			.append("\nAanmaakdatum: ").append(TimeFormat.DATE_TIME_SHORT.format(self.getUser().getTimeCreated()))
			.append("\nJoindatum: ").append(TimeFormat.DATE_TIME_SHORT.format(self.getTimeJoined()));

		builder.append("\n### :stopwatch: | Tijden")
			.append("\nPing: `").append(o.e.getTimeCreated().until(OffsetDateTime.now(), ChronoUnit.MILLIS)).append("ms`")
			.append("\nGateway Ping: `").append(jda().getGatewayPing()).append("ms`");

		builder.append("\n### :dna: | Versies")
			.append("\n**__dev.casiebarie.InOsso:__** `").append(Utils.getProperties().getProperty("version")).append("`");
		DependencyChecker.versionMap.forEach((k, v) -> {
			builder.append("\n").append(k).append(": `").append(v[0]).append("`");
			if(v[1] != null && !v[0].equals(v[1])) {builder.append(" → `").append(v[1]).append("`");}
		});

		long freeMemory = Runtime.getRuntime().freeMemory() / (1024 * 1024);
		long totalMemory = Runtime.getRuntime().totalMemory() / (1024 * 1024);
		long usedMemory = totalMemory - freeMemory;
		builder.append("\n### :desktop: | Systeem")
			.append("\nPlatform: `").append(System.getProperty("os.name")).append("`")
			.append("\nArch: `").append(System.getProperty("os.arch")).append("`")
			.append("\nUptime: `").append(Utils.getUptime()).append("`")
			.append("\nCPU Cores: `").append(Runtime.getRuntime().availableProcessors()).append("`")
			.append("\nGeheugen:")
			.append("\n　_Vrij: `").append(freeMemory).append("MB`_")
			.append("\n　_Totaal: `").append(totalMemory).append("MB`_")
			.append("\n　_Gebruikt: `").append(usedMemory).append("MB`_");

		o.e.getHook().sendMessageEmbeds(eb.setDescription(builder.toString()).build())
			.setFiles(Utils.loadAvatar(self.getEffectiveAvatarUrl()), Utils.loadAvatar(cas.getEffectiveAvatarUrl(), 1), Utils.loadImage(EMPTY_IMAGE_PATH))
		.queue(null, o::sendFailed);
	}
}