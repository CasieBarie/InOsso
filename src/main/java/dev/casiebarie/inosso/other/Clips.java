package dev.casiebarie.inosso.other;

import dev.casiebarie.inosso.InstanceManager;
import dev.casiebarie.inosso.Main;
import dev.casiebarie.inosso.enums.Channels;
import dev.casiebarie.inosso.interfaces.Information;
import dev.casiebarie.inosso.utils.ReplyOperation;
import dev.casiebarie.inosso.utils.Utils;
import dev.casiebarie.inosso.utils.logging.Logger;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static dev.casiebarie.inosso.enums.Variables.EMPTY_IMAGE;
import static dev.casiebarie.inosso.enums.Variables.EMPTY_IMAGE_PATH;
import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class Clips extends ListenerAdapter implements Information {
	public Clips(@NotNull InstanceManager iManager) {
		iManager.registerAsEventListener(this);
		iManager.registerAsInformationClass("climpies", this);
	}

	@Override
	public void onMessageReceived(@NotNull MessageReceivedEvent e) {
		if(!e.isFromGuild() || e.getAuthor().isBot()) {return;}
		Guild guild = e.getGuild();
		if(!e.getGuildChannel().equals(guild.getGuildChannelById(Channels.CLIPS.getId(guild)))) {return;}
		Message message = e.getMessage();
		if(message.getAttachments().stream().anyMatch(Message.Attachment::isVideo)) {return;}
		Main.scheduledPool.schedule(() -> checkMessage(guild, message.getId()), 1, TimeUnit.SECONDS);
	}

	private void checkMessage(Guild guild, String messageId) {
		TextChannel channel = Channels.CLIPS.getAsChannel(guild);
		Message message = channel.retrieveMessageById(messageId).complete();
		if(message == null) {getLogger().debug("Clip message can no longer be found in {}", Logger.getGuildNameAndId(guild)); return;}

		boolean hasVideoEmbed = false;
		List<MessageEmbed> embeds = message.getEmbeds();
		for(MessageEmbed embed : embeds) {
			if(!embed.getType().equals(EmbedType.VIDEO)) {continue;}
			hasVideoEmbed = true;
		} if(!hasVideoEmbed) {message.delete().queue(success -> getLogger().debug("Clip message send by {} has been deleted", Logger.getUserNameAndId(message.getAuthor())), ReplyOperation::error);}
	}

	@Override
	public void sendInformation(@NotNull ReplyOperation o) {
		Guild guild = o.e.getGuild();
		Member cas = Utils.getCasAsMember(guild);

		EmbedBuilder eb = new EmbedBuilder()
			.setColor(Color.BLACK)
			.setImage(EMPTY_IMAGE)
			.setDescription("# :video_camera: Climpies Help :video_camera:\n" +
				"Het " + Channels.CLIPS.getAsMention(guild) + " kanaal is alleen voor clipjes. Alle berichten die geen video-link of bestand bevatten, worden automatisch verwijderd. Deze controle duurt ongeveer `1` seconde.")
			.setFooter("Vraag aan " + cas.getEffectiveName() + " voor eventuele versoepelingen.");

		o.e.getHook().sendMessageEmbeds(eb.build()).setFiles(Utils.loadImage(EMPTY_IMAGE_PATH)).queue(null, o::sendFailed);
	}
}