package dev.casiebarie.inosso.utils;

import dev.casiebarie.inosso.utils.logging.Logger;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class ReplyOperation {
	String messageId;
	boolean suppress;
	final Guild guild;
	final Webhook webhook;
	public final IReplyCallback e;
	final String webhookName;
	final GuildMessageChannel channel;

	public ReplyOperation(@NotNull GuildMessageChannel channel) {
		this.guild = channel.getGuild();
		this.webhookName = null;
		this.webhook = null;
		this.e = null;
		this.channel = channel;
	}

	public ReplyOperation(@NotNull IReplyCallback e) {
		this.guild = e.getGuild();
		this.webhookName = null;
		this.webhook = null;
		this.e = e;
		this.channel = null;
	}

	public ReplyOperation(@NotNull Webhook webhook, String webhookName) {
		this.guild = webhook.getGuild();
		this.webhookName = webhookName;
		this.webhook = webhook;
		this.e = null;
		this.channel = null;
	}

	public void replyEmpty() {
		if(e == null) {return;}
		if(!e.isAcknowledged()) {e.deferReply(true).queue(null, ReplyOperation::error);}
		e.getHook().deleteOriginal().queue(null, ReplyOperation::error);
		Logger.debug(getLogger(), "Replied empty to {}", () -> new String[] {Logger.getUserNameAndId(e.getUser())});
	}

	public void sendSuccess(String msg) {
		EmbedBuilder eb = new EmbedBuilder()
			.setDescription("『<:success:1334483959685582878>』" + msg)
			.setColor(Color.decode("#00FF00"));
		send(eb.build(), 5);
		getLogger().debug("Success Message: {}", msg);
	}

	public void sendNotAllowed(String msg) {sendNotAllowed(msg, 5);}
	public void sendNotAllowed(String msg, int deleteAfterSeconds) {
		EmbedBuilder eb = new EmbedBuilder()
			.setDescription("『:no_entry:』" + msg)
			.setColor(Color.decode("#ff4444"));
		send(eb.build(), deleteAfterSeconds);
		getLogger().debug("NotAllowed Message: {}", msg);
	}

	public void sendFailed(String msg) {suppress = true; sendFailed(new Exception(msg));}
	public void sendFailed(@NotNull Throwable error) {
		EmbedBuilder eb = new EmbedBuilder()
			.setDescription("『:warning:』" + error.getMessage())
			.setColor(Color.decode("#FFCC4D"));
		if(!suppress) {
			eb.setFooter("Vraag aan " + Utils.getCasAsMember(guild).getEffectiveName() + " om in de console te kijken!");
			eb.setTimestamp(Instant.now());
			getLogger().error(error.getMessage(), error);
		} send(eb.build(), 5);
		getLogger().debug("Failed Message: {}", error.getMessage());
	}

	private void send(MessageEmbed embed, int deleteAfterSeconds) {
		if(e != null) {
			if(!e.isAcknowledged()) {e.deferReply(true).queue();}
			e.getHook().editOriginalEmbeds(embed).queue(null, ReplyOperation::error);
			return;
		}

		if(webhook != null) {
			if(messageId != null) {webhook.editMessageEmbedsById(messageId, embed).queue(message -> deleteAfterSeconds(message, deleteAfterSeconds), ReplyOperation::error);
			} else {webhook.sendMessageEmbeds(embed).setUsername(webhookName).queue(message -> deleteAfterSeconds(message, deleteAfterSeconds), ReplyOperation::error);}
			return;
		}

		if(channel == null) {return;}
		channel.sendMessageEmbeds(embed).queue(message -> deleteAfterSeconds(message, deleteAfterSeconds), ReplyOperation::error);
	}

	private void deleteAfterSeconds(Message message, int deleteAfterSeconds) {
		messageId = message.getId();
		if(deleteAfterSeconds <= 0 || message.getChannelType() == ChannelType.PRIVATE) {return;}
		message.delete().queueAfter(deleteAfterSeconds, TimeUnit.SECONDS, null, ReplyOperation::error);
	}

	public static void error(Throwable error) {
		if(error instanceof ErrorResponseException err) {
			if(err.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE || err.getErrorResponse() == ErrorResponse.UNKNOWN_WEBHOOK) {getLogger().debug("Error supressed: {}", error.getMessage()); return;}
			getLogger().error(error.getMessage(), error);
		} else {getLogger().error(error.getMessage(), error);}
	}
}