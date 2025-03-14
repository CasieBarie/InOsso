package dev.casiebarie.inosso.other;

import dev.casiebarie.inosso.ClassLoader;
import dev.casiebarie.inosso.Main;
import dev.casiebarie.inosso.interfaces.CommandListener;
import dev.casiebarie.inosso.interfaces.Information;
import dev.casiebarie.inosso.utils.ReplyOperation;
import dev.casiebarie.inosso.utils.Utils;
import dev.casiebarie.inosso.utils.logging.Logger;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.*;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static dev.casiebarie.inosso.enums.Variables.EMPTY_IMAGE;
import static dev.casiebarie.inosso.enums.Variables.EMPTY_IMAGE_PATH;
import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class SendEmbed extends ListenerAdapter implements CommandListener, Information {
	public SendEmbed(@NotNull ClassLoader classes) {
		classes.registerAsEventListener(this);
		classes.registerAsCommandListener(this, true);
		classes.registerAsInformationClass("stuur", this);
	}

	@Override
	public CommandData getCommand() {
		return Commands.slash("stuur", "Basis command van stuur.").addSubcommands(
			new SubcommandData("bericht",  String.format("%s | Stuurt een bericht als InOsso.", Emoji.fromFormatted("📤")))
				.addOption(OptionType.STRING, "bericht", "Het bericht om te versturen.", true)
		).addSubcommandGroups(
			new SubcommandGroupData("embed", String.format("%s | Stuurt een embed bericht als InOsso.", Emoji.fromFormatted("📤"))).addSubcommands(
				new SubcommandData("string", String.format("%s | Stuurt een embed bericht als InOsso.", Emoji.fromFormatted("📤")))
					.addOptions(new OptionData(OptionType.STRING, "json", "De JSON van de embed.", true)),
				new SubcommandData("file", String.format("%s | Stuurt een embed bericht als InOsso.", Emoji.fromFormatted("📤")))
					.addOptions(new OptionData(OptionType.ATTACHMENT, "json", "Het JSON-bestand van de embed.", true))
			)
		);
	}

	@Override
	public void onCommand(@NotNull SlashCommandInteraction e) {
		if(!e.getSubcommandName().equals("bericht")) {sendEmbed(e); return;}
		ReplyOperation o = new ReplyOperation(e);
		String message = e.getOption("bericht", OptionMapping::getAsString);
		e.getChannel().sendMessage(message).queue(success -> {
			o.replyEmpty();
			getLogger().info("Message send in {} by {}", Logger.getChannelNameAndId(e.getChannel()), Logger.getUserNameAndId(e.getUser()));
		}, o::sendFailed);
	}

	@Override
	public void onButtonInteraction(@NotNull ButtonInteractionEvent e) {
		if(!e.getButton().getId().equals("send_embed")) {return;}
		e.deferReply(true).queue(null, ReplyOperation::error);
		ReplyOperation o = new ReplyOperation(e);

		Main.pool.execute(() -> {
			Message message = e.getMessage();
			MessageCreateBuilder builder = MessageCreateBuilder.fromMessage(message);
			builder.setComponents();
			e.getChannel().sendMessage(builder.build()).and(message.delete()).queue(success -> {
				o.replyEmpty();
				getLogger().info("Message from JSON send in {} by {}", Logger.getChannelNameAndId(e.getChannel()), Logger.getUserNameAndId(e.getUser()));
			}, o::sendFailed);
		});
	}

	private void sendEmbed(SlashCommandInteraction e) {
		ReplyOperation o = new ReplyOperation(e);
		try {
			DataObject dataObject;
			if(e.getSubcommandName().contains("string")) {
				String input = e.getOption("json", OptionMapping::getAsString);
				dataObject = DataObject.fromJson(input);
			} else {
				Message.Attachment attachment = e.getOption("json", OptionMapping::getAsAttachment);
				String extension = attachment.getFileExtension();
				if(!extension.equals("json") && !extension.equals("txt")) {o.sendNotAllowed("Je kan alleen .json of .txt bestanden gebruiken."); return;}
				try(InputStream is = attachment.getProxy().download().get(30, TimeUnit.SECONDS)) {dataObject = DataObject.fromJson(is);}
			} askToSendMessage(o, dataObject);
		} catch(Exception ex) {
			Thread.currentThread().interrupt();
			o.sendFailed("Ik kan geen embed maken van deze JSON.");
			getLogger().debug(ex.getMessage(), ex);
		}
	}

	private void askToSendMessage(ReplyOperation o, @NotNull DataObject dataObject) {
		List<MessageEmbed> embeds = new ArrayList<>();
		String content = dataObject.getString("content", "");
		DataArray array = dataObject.getArray("embeds");

		for(int i = 0; i < array.length(); i++) {
			DataObject object = array.getObject(i);
			MessageEmbed embed = EmbedBuilder.fromData(object).build();
			if(!embed.isSendable()) {throw new IllegalStateException("Deze JSON bevat een embed die niet gestuurd kan worden.");}
			embeds.add(embed);
		}

		try {o.e.getHook().sendMessage(content).setEmbeds(embeds).setActionRow(Button.of(ButtonStyle.SUCCESS, "send_embed", "Verstuur", Emoji.fromFormatted("🛰️"))).queue(null, o::sendFailed);
		} catch(IllegalArgumentException ex) {
			o.sendFailed("Dit bericht kan niet verstuurd worden!");
			getLogger().debug(ex.getMessage(), ex);
		}
	}

	@Override
	public void sendInformation(@NotNull ReplyOperation o) {
		EmbedBuilder eb = new EmbedBuilder()
			.setColor(Color.CYAN)
			.setImage(EMPTY_IMAGE)
			.setDescription("# :outbox_tray: Sturen Help :outbox_tray:\n" +
				"Met het commando `/stuur` kun je " + o.e.getGuild().getSelfMember().getAsMention() + "  een bericht laten sturen. Dit kan een gewoon bericht zijn of een bericht met meerdere embeds.\n" +
				"Wil je een embed maken? Gebruik dan bijvoorbeeld [deze site](https://message.style/app/editor). " +
				"Kopieer de JSON-code en voeg deze toe aan het commando. Is de code te lang? Dan kun je het ook als een `.json` of `.txt` bestand sturen." +
				"\n\nEen gewoon bericht wordt direct verstuurd. Bij een embed toont de bot eerst een voorbeeld en vraagt of je het wilt verzenden.");
		o.e.getHook().sendMessageEmbeds(eb.build()).setFiles(Utils.loadImage(EMPTY_IMAGE_PATH)).queue(null, o::sendFailed);
	}
}