package dev.casiebarie.inosso.other;

import dev.casiebarie.inosso.ClassLoader;
import dev.casiebarie.inosso.Main;
import dev.casiebarie.inosso.enums.Channels;
import dev.casiebarie.inosso.enums.Roles;
import dev.casiebarie.inosso.utils.ReplyOperation;
import dev.casiebarie.inosso.utils.Utils;
import dev.casiebarie.inosso.utils.logging.Logger;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class JoinManager extends ListenerAdapter {
	public JoinManager(@NotNull ClassLoader classes) {classes.registerAsEventListener(this);}

	@Override
	public void onGuildMemberJoin(GuildMemberJoinEvent e) {
		Main.pool.execute(Main.safeRunnable(() -> {
			Guild guild = e.getGuild();
			User user = e.getUser();
			User.Profile profile = user.retrieveProfile().complete();
			checkRoles(guild);
			TextChannel channel = Channels.MAIN.getAsChannel(guild);

			EmbedBuilder eb = new EmbedBuilder()
				.setDescription("# :wave: Welkom :wave:\n## " + user.getAsMention())
				.setThumbnail("attachment://avatar.png")
				.setColor(profile.getAccentColor());
			channel.sendMessageEmbeds(eb.build()).setFiles(Utils.loadAvatar(user.getEffectiveAvatarUrl())).queue(null, ReplyOperation::error);
		}));
	}

	@Override
	public void onGuildReady(@NotNull GuildReadyEvent e) {checkRoles(e.getGuild());}

	@Override
	public void onGuildMemberRoleAdd(@NotNull GuildMemberRoleAddEvent e) {checkRoles(e.getGuild());}

	@Override
	public void onGuildMemberRoleRemove(@NotNull GuildMemberRoleRemoveEvent e) {checkRoles(e.getGuild());}

	private void checkRoles(Guild guild) {
		Main.pool.execute(() -> guild.getMembers().forEach(member -> {
			if(!member.getRoles().isEmpty()) {return;}
			Role guest = Roles.GUEST_RESTRICTED.getGuildRole(guild);
			guild.addRoleToMember(member, guest).queue(success -> getLogger().debug("Added role {} to {}", Logger.getRoleNameAndId(guest), Logger.getUserNameAndId(member.getUser())), ReplyOperation::error);
		}));
	}
}