package dev.casiebarie.inosso.other;

import dev.casiebarie.inosso.ClassLoader;
import dev.casiebarie.inosso.enums.Channels;
import dev.casiebarie.inosso.interfaces.CommandListener;
import dev.casiebarie.inosso.interfaces.Information;
import dev.casiebarie.inosso.utils.ReplyOperation;
import dev.casiebarie.inosso.utils.Utils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.context.UserContextInteraction;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static dev.casiebarie.inosso.enums.Variables.EMPTY_IMAGE;
import static dev.casiebarie.inosso.enums.Variables.EMPTY_IMAGE_PATH;

public class Choose implements Information {
	final Random random = new Random();
	public Choose(ClassLoader classes) {
		new Context(classes);
		new Slash(classes);
		classes.registerAsInformationClass("kies", this);
	}

	private void choose(@NotNull GenericCommandInteractionEvent e) {
		Guild guild = e.getGuild();
		ReplyOperation o = new ReplyOperation(e);
		VoiceChannel voiceChannel = Channels.VOICE.getAsChannel(guild);
		TextChannel textChannel = Channels.MAIN.getAsChannel(guild);
		if(!Utils.isInVoice(e.getMember(), o)) {return;}
		o.replyEmpty();

		boolean isForced = true;
		Member chosen;
		if(e.getCommandType().equals(Command.Type.SLASH)) {
			List<Member> members = new ArrayList<>(voiceChannel.getMembers());
			members.remove(guild.getSelfMember());
			chosen = members.get(random.nextInt(members.size()));
			isForced = false;
		} else {chosen = ((UserContextInteractionEvent) e).getTargetMember();}

		EmbedBuilder eb = new EmbedBuilder()
			.setTitle("En het is:")
			.setDescription("# " + Utils.getAsMention(chosen) + "\n### Gefeliciteerd" + (isForced ? "?" : "!"))
			.setColor(chosen.getColor())
			.setImage(EMPTY_IMAGE)
			.setThumbnail("attachment://avatar.png");
		textChannel.sendMessageEmbeds(eb.build()).setFiles(Utils.loadImage(EMPTY_IMAGE_PATH), Utils.loadAvatar(chosen.getEffectiveAvatarUrl())).queue();
	}

	@Override
	public void sendInformation(@NotNull ReplyOperation o) {
		Guild guild = o.e.getGuild();
		String cmdMention = guild.retrieveCommands().complete().stream().filter(cmd -> cmd.getName().equals("kies")).map(Command::getAsMention).findFirst().orElse("`/kies`");

		EmbedBuilder eb = new EmbedBuilder()
			.setColor(Color.WHITE)
			.setImage(EMPTY_IMAGE)
			.setDescription("# :game_die: Kies Help :game_die:\n" +
				"Het " + cmdMention + " commando is heel eenvoudig. Het kiest random iemand uit " + Channels.VOICE.getAsMention(guild) + ". Zelf moet je daar ook in zitten.\n" +
				"Als je het niet eens bent met de uitkomst, jammer! **De uitkomst is altijd leidend.**"
			);

		if(random.nextInt(10) == 5) {eb.setFooter("Er is een manier om de uitkomst te beïnvloeden. Hoe dat precies werkt, mag je zelf uitzoeken!");}
		o.e.getHook().sendMessageEmbeds(eb.build()).setFiles(Utils.loadImage(EMPTY_IMAGE_PATH)).queue(null, o::sendFailed);
	}

	class Context implements CommandListener {
		public Context(@NotNull ClassLoader classes) {classes.registerAsCommandListener(this, true);}
		@Override public CommandData getCommand() {return Commands.user("Kies 🎲");}
		@Override public void onCommand(UserContextInteraction e) {choose((GenericCommandInteractionEvent) e);}
	}

	class Slash implements CommandListener {
		public Slash(@NotNull ClassLoader classes) {classes.registerAsCommandListener(this, true);}
		@Override public CommandData getCommand() {return Commands.slash("kies", String.format("%s | Selecteer willekeurig iemand uit de voicecall." , Emoji.fromFormatted("🎲")));}
		@Override public void onCommand(SlashCommandInteraction e) {choose((GenericCommandInteractionEvent) e);}
	}
}