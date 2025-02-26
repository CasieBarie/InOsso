package dev.casiebarie.inosso.jachtseizoen;

import dev.casiebarie.inosso.ClassLoader;
import dev.casiebarie.inosso.Main;
import dev.casiebarie.inosso.enums.Channels;
import dev.casiebarie.inosso.interfaces.CommandListener;
import dev.casiebarie.inosso.interfaces.Information;
import dev.casiebarie.inosso.interfaces.ScheduledTask;
import dev.casiebarie.inosso.utils.ReplyOperation;
import dev.casiebarie.inosso.utils.Utils;
import dev.casiebarie.inosso.utils.logging.Logger;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static dev.casiebarie.inosso.enums.Variables.*;
import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class Jachtseizoen extends ListenerAdapter implements CommandListener, ScheduledTask, Information {
	final ClassLoader classes;
	Random random = new Random();
	Map<String, GuildManager> managers = new HashMap<>();
	public Jachtseizoen(@NotNull ClassLoader classes) {
		this.classes = classes;
		classes.registerAsEventListener(this);
		classes.registerAsCommandListener(this, true);
		classes.registerAsScheduledTaskClass(this);
		classes.registerAsInformationClass("jachtseizoen", this);
	}

	@Override
	public ScheduledFuture<?> startTask(String guildId) {
		return Main.scheduledPool.scheduleAtFixedRate(Main.safeRunnable(() -> {
			GuildManager guildManager = managers.get(guildId);
			if(guildManager != null) {guildManager.updateMessage();}
		}), 0, Utils.jitter(100), TimeUnit.MILLISECONDS);
	}

	@Override
	public CommandData getCommand() {return Commands.slash("jachtseizoen", String.format("%s | De jacht in GTA is geopend!", Emoji.fromFormatted("👮")));}

	@Override
	public void onCommand(@NotNull SlashCommandInteraction e) {
		Guild guild = e.getGuild();
		ReplyOperation o = new ReplyOperation(e);
		GuildManager manager = managers.get(guild.getId());
		if(manager != null) {o.sendNotAllowed("Er is al een jachtseizoen actief!"); return;}
		if(!Utils.isInVoice(e.getMember(), o)) {return;}

		manager = new GuildManager(this, guild);
		managers.put(guild.getId(), manager);
		manager.onCommand(o);
	}

	@Override
	public void onButtonInteraction(@NotNull ButtonInteractionEvent e) {
		String id = e.getButton().getId();
		if(!id.startsWith("jachtseizoen-")) {return;}
		e.deferReply(true).queue();
		Logger.debug(getLogger(), "ButtonInteraction with ID {} by {}", () -> new String[] {e.getComponentId(), Logger.getUserNameAndId(e.getUser())});

		Main.pool.execute(() -> {
			Guild guild = e.getGuild();
			ReplyOperation o = new ReplyOperation(e);
			GuildManager manager = managers.get(guild.getId());
			if(manager == null) {o.sendNotAllowed("Er is geen jachtseizoen actief!"); return;}
			if(!Utils.isInVoice(e.getMember(), o)) {return;}

			switch(id.split("-")[1]) {
			case "start" -> {manager.onStartButton(o); classes.music.playJachtseizoen(guild);}
			case "reroll" -> manager.onRerollButton(o);
			case "stop" -> manager.onStopButton(o);
			case "cancel" -> manager.onCancelButton(o);
			case "pause" -> manager.onPauseButton(o);
			default -> o.sendNotAllowed("Deze knop herken ik niet.");}
		});
	}

	@Override
	public void sendInformation(@NotNull ReplyOperation o) {
		Guild guild = o.e.getGuild();
		String cmdMention = guild.retrieveCommands().complete().stream().filter(cmd -> cmd.getName().equals("jachtseizoen")).map(Command::getAsMention).findFirst().orElse("`/jachtseizoen`");

		EmbedBuilder eb = new EmbedBuilder()
			.setColor(Color.BLUE)
			.setImage("attachment://empty.png")
			.setDescription("# :man_running: Jachtzeizoen Help :police_officer:\n" +
				"Wil je Jachtseizoen spelen? Gebruik dan " + cmdMention + ". " +
				"Hiermee wordt er een bericht gestuurd in " + Channels.MAIN.getAsMention(guild) + " met de regels van deze ronde, " +
				"gebaseerd op de spelers die op dat moment in " + Channels.VOICE.getAsMention(guild) + "zitten. De regels worden willekeurig gegenereerd. " +
				"Zijn ze niet goed? Dan kun je met `🎲 Reroll` nieuwe regels instellen." +
				"\n### Spel starten\n" +
				"Zodra iedereen klaar is, kan je op `✅ Start` drukken om het spel te starten _tip: laat de jager dit doen_. " +
				"De renners krijgen dan een voorsprong van `" + Utils.formatDuration(JACHT_HUNTER_WAIT_TIME) + "`. " +
				"Na deze tijd verschijnt er een bericht in " + Channels.VOICE.getAsMention(guild) + "  waarin staat dat de jager mag vertrekken. " +
				"Daarna volgt er elke `" + Utils.formatDuration(JACHT_LOOK_TIME) + "` een bericht dat de jager en de renners mogen kijken." +
				"\n### Extra Opties\n" +
				"- `⏸️ Pauzeer` Pauzeerd de tijd en voorkomt dat er nieuwe kijkberichten worden gestuurd.\n" +
				"- `⛔ Stop` Beëindigt het spel volledig. Dit gebeurt automatisch als de speeltijd `" + Utils.formatDuration(JACHT_MAX_DURATION) + "` bereikt." +
				"\n\nVeel plezier met spelen!"
			);

		o.e.getHook().sendMessageEmbeds(eb.build())
			.setFiles(Utils.loadImage("empty.png"))
		.queue(null, o::sendFailed);
	}

	public void stopPlaying(Guild guild, boolean isCancel) {
		Logger.debug(getLogger(), "Stopping jachtseizoen game in guild {}", () -> new String[] {Logger.getGuildNameAndId(guild)});
		GuildManager manager = managers.remove(guild.getId());
		if(manager == null) {return;}
		if(!isCancel) {manager.stopPlaying(guild);}
	}

	protected MessageEmbed createRules(@NotNull Member sender) {
		Guild guild = sender.getGuild();
		AudioChannel audioChannel = sender.getVoiceState().getChannel();
		List<Member> members = new ArrayList<>(audioChannel.getMembers());
		members.remove(guild.getSelfMember());

		Member hunter = members.remove(random.nextInt(members.size()));
		StringBuilder runners = new StringBuilder();
		for(Member member : members) {runners.append("- ").append(Utils.getAsMention(member)).append("\n");}

		List<String> endVehicles = new ArrayList<>(List.of("Geen Restricties", "Private Jet", "Helicopter", "Cessna"));
		List<String> bycicleUse = new ArrayList<>(List.of("Niet toegestaan", "3min", "5min", "7min"));
		List<String> hunterWeapons = new ArrayList<>(List.of("Geen", "Pistol", "Up-n-Atomizer"));

		String rules = "- Startlocatie: `24/7 Supermarket - Top van de map`" +
			"\n- Eindlocatie: `Los Santos International Airport (VLIEGEND)`" +
			"\n- Achter op auto tot: `Diamond Casino`" +
			"\n- Gebruik fiets: `" + bycicleUse.get(random.nextInt(bycicleUse.size())) + "`" +
			"\n- Verplicht eindvoertuig: `" + endVehicles.get(random.nextInt(endVehicles.size())) + "`" +
			"\n- Ontsnappingstijd: `" + Utils.formatDuration(JACHT_HUNTER_WAIT_TIME) + "`" +
			"\n- Radar & HUD uitgeschakeld: `" + ((random.nextBoolean()) ? "Jager & Renners`" : "Jager`") +
			"\n- Kijktijd: `" + Utils.formatDuration(JACHT_LOOK_TIME) + "`" +
			"\n- Auto jager: `" + random.nextInt(1, 11) + "e van " + (random.nextBoolean() ? "boven`" : "onder`") +
			"\n- Wapen jager: `" + hunterWeapons.get(random.nextInt(hunterWeapons.size())) + "`" +
			"\n- Wapen renners: `" + ((random.nextBoolean()) ? "Geen`" : "Up-n-Atomizer`");

		return new EmbedBuilder()
			.setDescription("# :rotating_light: JACHTSEIZOEN :rotating_light:\n### De jacht in GTA is geopend!")
			.setColor(Color.BLUE)
			.addField(":police_officer: | Jager", "- " + Utils.getAsMention(hunter), true)
			.addField(":man_running: | Renners", runners.toString(), true)
			.addField(":closed_book: | Regels", rules, false)
			.setImage("attachment://empty.png")
			.setThumbnail("attachment://jachtseizoen.png")
		.build();
	}

	protected MessageEmbed gameEmbed(int gameStatus, boolean isPaused, long gameTime, long nextLookTime) {
		return new EmbedBuilder()
			.setColor(Color.MAGENTA)
			.setImage("attachment://empty.png")
			.setDescription(
				(isPaused ? ":pause_button: Gepauzeerd :pause_button:" : "# :man_running: Aan het spelen... :police_officer:") +
				(gameStatus == 1 ? "\n### Renners pak je voorsprong!" : "") +
				"\n- Speeltijd: `" + Utils.formatDuration(gameTime) + "`" +
				"\n- " + (gameStatus == 1 ? "Tijd tot vertrek jager: `" : "Tijd tot kijken: `") + Utils.formatDuration(nextLookTime, (gameStatus == 1) ? 300000L : 180000L) + "`")
			.setFooter("Maximale speelduur: " + Utils.formatDuration(JACHT_MAX_DURATION))
		.build();
	}
}