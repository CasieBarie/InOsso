package dev.casiebarie.inosso.other;

import dev.casiebarie.inosso.InstanceManager;
import dev.casiebarie.inosso.Main;
import dev.casiebarie.inosso.enums.Channels;
import dev.casiebarie.inosso.enums.Roles;
import dev.casiebarie.inosso.interfaces.CommandListener;
import dev.casiebarie.inosso.interfaces.Information;
import dev.casiebarie.inosso.utils.ReplyOperation;
import dev.casiebarie.inosso.utils.Utils;
import dev.casiebarie.inosso.utils.WebhookManager;
import dev.casiebarie.inosso.utils.logging.Logger;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static dev.casiebarie.inosso.Main.jda;
import static dev.casiebarie.inosso.enums.Variables.*;
import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class Come extends ListenerAdapter implements CommandListener, Information {
	static final String WEBHOOK_ID = "Come";
	Map<String, GuildComeManager> managers = new HashMap<>();
	public Come(@NotNull InstanceManager iManager) {
		iManager.registerAsEventListener(this);
		iManager.registerAsCommandListener(this, true);
		iManager.registerAsInformationClass("komen", this);
	}

	@Override
	public CommandData getCommand() {return Commands.slash("komen", String.format("%s | Ping iedereen om te komen!", Emoji.fromFormatted("📢")));}

	@Override
	public void onCommand(@NotNull SlashCommandInteraction e) {
		Guild guild = e.getGuild();
		GuildComeManager manager = managers.get(guild.getId());
		ReplyOperation o = new ReplyOperation(e);

		if(manager != null) {
			if(manager.cooldown >= System.currentTimeMillis()) {o.sendNotAllowed(String.format("Komen zit op dit moment in cooldown! Je kan %s weer een request sturen.", TimeFormat.RELATIVE.format(manager.cooldown))); return;}
			manager.deleteManager();
			managers.remove(guild.getId());
		}

		manager = new GuildComeManager(guild.getId(), e.getMember().getId());
		managers.put(guild.getId(), manager);
		manager.onCommand(o);
	}

	@Override
	public void onButtonInteraction(@NotNull ButtonInteractionEvent e) {
		String btnId = e.getButton().getCustomId();
		if(!btnId.startsWith("come_") || e.getUser().isBot()) {return;}
		e.deferReply(true).queue(null, ReplyOperation::error);
		getLogger().debug("ButtonInteraction with ID {} by {}", e.getComponentId(), Logger.getUserNameAndId(e.getUser()));

		Main.pool.execute(() -> {
			ReplyOperation o = new ReplyOperation(e);
			GuildComeManager manager = managers.get(e.getGuild().getId());
			if(manager == null) {o.sendFailed(new NullPointerException("Mananger not found!")); return;}
			manager.onButton(e, o);
		});
	}

	@Override
	public void sendInformation(@NotNull ReplyOperation o) {
		Guild guild = o.e.getGuild();
		String cmdMention = guild.retrieveCommands().complete().stream().filter(cmd -> cmd.getName().equals("komen")).map(Command::getAsMention).findFirst().orElse("`/komen`");

		EmbedBuilder eb = new EmbedBuilder()
			.setColor(Color.RED)
			.setImage(EMPTY_IMAGE)
			.setDescription("# :loudspeaker: Komen Help :loudspeaker:\n" +
				"Wil je iedereen bij elkaar roepen? Gebruik dan het " + cmdMention + " command. " +
				"Iedereen krijgt een ping om te komen, en in " + Channels.MAIN.getAsMention(guild) + " verschijnt een bericht waarop ze kunnen aangeven of ze erbij zijn. " +
				"Dit bericht blijft `" + COME_COOLDOWN_HOURS + "` uur actief, waarin iedereen kan reageren. Tijdens deze periode kan het command niet opnieuw gebruikt worden.");

		o.e.getHook().sendMessageEmbeds(eb.build()).setFiles(Utils.loadImage(EMPTY_IMAGE_PATH)).queue(null, o::sendFailed);
	}

	class GuildComeManager {
		long cooldown = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(COME_COOLDOWN_HOURS);
		String messageId;
		final String guildId, requesterId;
		Map<String, Integer> commingSpecial = new LinkedHashMap<>(), commingGuest = new LinkedHashMap<>();
		public GuildComeManager(String guildId, String requesterId) {
			this.guildId = guildId;
			this.requesterId = requesterId;
		}

		private void onCommand(@NotNull ReplyOperation o) {
			Guild guild = o.e.getGuild();
			Member requester = o.e.getMember();
			TextChannel channel = Channels.MAIN.getAsChannel(guild);

			StringBuilder builder = new StringBuilder("||");
			Arrays.stream(Roles.values())
				.filter(role -> !role.equals(Roles.GUEST_RESTRICTED))
				.forEach(role -> builder.append(role.getGuildRole(guild).getAsMention()));
			builder.append("||");

			guild.getMembers().forEach(member -> {
				if(member.getUser().isBot()) {return;}
				if(Utils.isSpecial(member)) {commingSpecial.put(member.getId(), 0);}
				if(Utils.isGuest(member, true)) {commingGuest.put(member.getId(), 0);}
			}); commingSpecial.put(requester.getId(), 1);

			Webhook webhook = WebhookManager.getWebhook(channel, WEBHOOK_ID);
			if(webhook == null) {o.sendFailed("Kan het komen bericht niet versturen!"); return;}

			webhook.sendMessage(builder.toString())
				.setUsername(guild.getSelfMember().getEffectiveName() + " -  Komen📢")
				.setEmbeds(getComeEmbed(guild))
				.setComponents(ActionRow.of(Button.success("come_yes", "Ja"), Button.secondary("come_maybe", "Misschien"), Button.danger("come_no", "Nee")))
				.setFiles(Utils.loadImage("goat.png"), Utils.loadImage(EMPTY_IMAGE_PATH))
			.queue(msg -> {o.replyEmpty(); messageId = msg.getId();}, o::sendFailed);
			Main.scheduledPool.schedule(this::deleteManager, cooldown - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
		}

		private void onButton(@NotNull ButtonInteractionEvent e, ReplyOperation o) {
			Guild guild = e.getGuild();
			Member member = e.getMember();
			TextChannel channel = Channels.MAIN.getAsChannel(guild);
			Webhook webhook = WebhookManager.getWebhook(channel, WEBHOOK_ID);
			if(webhook == null) {o.sendFailed("Ik kan op dit moment je reactie niet verwerken."); return;}

			String btnId = e.getButton().getCustomId();
			int response = btnId.equals("come_yes") ? 1 : (btnId.equals("come_no") ? 2 : 0);
			if(Utils.isSpecial(member)) {commingSpecial.put(member.getId(), response);}
			if(Utils.isGuest(member, true)) {commingGuest.put(member.getId(), response);}

			webhook.editMessageEmbedsById(messageId, getComeEmbed(guild)).queue(success -> o.replyEmpty(), o::sendFailed);
		}

		private @NotNull MessageEmbed getComeEmbed(@NotNull Guild guild) {
			Member requester = guild.getMemberById(requesterId);
			EmbedBuilder eb = new EmbedBuilder()
				.setDescription("# Komen Bois!" +
					"\n" + Utils.getAsMention(requester) + " wil met jullie spelen..." +
					"\n### Kom je ook?")
				.setColor(requester.getColor())
				.setImage(EMPTY_IMAGE)
				.setThumbnail("attachment://goat.png");

			addField(eb, "Ja", 1, guild);
			addField(eb, "Misschien", 0, guild);
			addField(eb, "Nee", 2, guild);
			return eb.build();
		}

		private void addField(@NotNull EmbedBuilder eb, String emoji, int status, Guild guild) {
			StringBuilder builder = new StringBuilder();
			commingSpecial.entrySet().stream().filter(entry -> entry.getValue().equals(status)).forEach(entry -> builder.append(Utils.getAsMention(guild.getMemberById(entry.getKey()))).append("\n"));
			commingGuest.entrySet().stream().filter(entry -> entry.getValue().equals(status)).forEach(entry -> builder.append(guild.getMemberById(entry.getKey()).getAsMention()).append("\n"));
			eb.addField(emoji, builder.toString(), true);
		}

		private void deleteManager() {
			Guild guild = jda().getGuildById(guildId);
			TextChannel channel = Channels.MAIN.getAsChannel(guild);
			Webhook webhook = WebhookManager.getWebhook(channel, WEBHOOK_ID);
			if(webhook == null) {return;}
			webhook.editMessageById(messageId, "").setEmbeds(getComeEmbed(guild)).setComponents().queue(null, ReplyOperation::error);
		}
	}
}