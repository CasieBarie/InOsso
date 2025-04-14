package dev.casiebarie.inosso.other;

import dev.casiebarie.inosso.InstanceManager;
import dev.casiebarie.inosso.Main;
import dev.casiebarie.inosso.utils.ReplyOperation;
import dev.casiebarie.inosso.utils.Utils;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class RoleNameManager extends ListenerAdapter {
	public RoleNameManager(@NotNull InstanceManager iManager) {iManager.registerAsEventListener(this);}

	@Override
	public void onGuildReady(GuildReadyEvent e) {Main.pool.execute(() -> e.getGuild().getMembers().stream().filter(Utils::isSpecial).forEach(this::changeRoleName));}

	@Override
	public void onGuildMemberUpdateNickname(@NotNull GuildMemberUpdateNicknameEvent e) {
		Member member = e.getMember();
		if(!Utils.isSpecial(member)) {return;}
		Main.pool.execute(() -> changeRoleName(member));
	}

	private void changeRoleName(@NotNull Member member) {
		if(member.getGuild().getSelfMember().equals(member)) {return;}
		String roleName = member.getEffectiveName();
		Role role = member.getRoles().get(0);
		String oldRoleName = role.getName();
		if(roleName.equals(oldRoleName)) {return;}
		role.getManager().setName(roleName).queue(success -> getLogger().info("Change special role name from {} to {}", oldRoleName, roleName), ReplyOperation::error);
	}
}