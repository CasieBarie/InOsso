package dev.casiebarie.inosso.other;

import dev.casiebarie.inosso.InstanceManager;
import dev.casiebarie.inosso.enums.Channels;
import dev.casiebarie.inosso.enums.Roles;
import dev.casiebarie.inosso.interfaces.CommandListener;
import dev.casiebarie.inosso.utils.ReplyOperation;
import dev.casiebarie.inosso.utils.Utils;
import dev.casiebarie.inosso.utils.WebhookManager;
import dev.casiebarie.inosso.utils.logging.Logger;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

import static dev.casiebarie.inosso.enums.Variables.EMPTY_IMAGE_PATH;
import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class Guest implements CommandListener {
	public Guest(@NotNull InstanceManager iManager) {iManager.registerAsCommandListener(this, true);}

	@Override
	public CommandData getCommand() {
		return Commands.slash("gast", "Basis van gast command.").addSubcommands(
			new SubcommandData("toevoegen", String.format("%s | Geef iemand de gastrol.", Emoji.fromFormatted("✅")))
				.addOption(OptionType.USER, "gast", "De gast", true),
			new SubcommandData("verwijderen", String.format("%s | Verwijder de gastrol van iemand.", Emoji.fromFormatted("❌")))
				.addOption(OptionType.USER, "gast", "De gast", true)
		);
	}

	@Override
	public void onCommand(@NotNull SlashCommandInteraction e) {
		Guild guild = e.getGuild();
		Member guest = e.getOption("gast", OptionMapping::getAsMember);
		Role gastAllowed = Roles.GUEST_ALLOWED.getGuildRole(guild);
		Role gastRestricted = Roles.GUEST_RESTRICTED.getGuildRole(guild);
		TextChannel channel = Channels.MAIN.getAsChannel(guild);
		ReplyOperation o = new ReplyOperation(e);
		if(Utils.isSpecial(guest)) {o.sendNotAllowed("Je kan de gast role van " + guest.getAsMention() + " niet veranderen."); return;}

		boolean add = e.getSubcommandName().equals("toevoegen");
		if(Utils.isGuest(guest, true) && add) {o.sendNotAllowed(guest.getAsMention() + " is al een " + gastAllowed.getAsMention() + "!"); return;}
		if(Utils.isGuest(guest, false) && !add) {o.sendNotAllowed(guest.getAsMention() + " is al een " + gastRestricted.getAsMention() + "!"); return;}

		Webhook webhook = WebhookManager.getWebhook(channel, "GateKeeper");
		switchGuest(guest, add).whenComplete((success, error) -> {
			if(error != null) {o.sendFailed(error); return;}
			getLogger().debug("Switched guest role of {}", Logger.getUserNameAndId(guest.getUser()));
			if(!add || webhook == null) {o.sendSuccess(String.format("%s is nu een %s!", guest.getAsMention(), add ? gastAllowed.getAsMention() : gastRestricted.getAsMention())); return;}

			webhook.sendMessageEmbeds(Request.answeredEmbed(guest, true))
				.setFiles(Utils.loadImage(EMPTY_IMAGE_PATH), Utils.loadAvatar(guest.getEffectiveAvatarUrl()))
			.queue(s -> o.replyEmpty(), o::sendFailed);
		});
	}

	public CompletableFuture<?> switchGuest(@NotNull Member guest, boolean allow) {
		Guild guild = guest.getGuild();
		Role gastAllowed = Roles.GUEST_ALLOWED.getGuildRole(guild);
		Role gastRestricted = Roles.GUEST_RESTRICTED.getGuildRole(guild);
		return guild.addRoleToMember(guest, (allow) ? gastAllowed : gastRestricted).and(guild.removeRoleFromMember(guest, (allow) ? gastRestricted : gastAllowed)).submit();
	}
}