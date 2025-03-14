package dev.casiebarie.inosso.enums;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import org.jetbrains.annotations.NotNull;

public enum Roles {
	GUEST_ALLOWED(924322437577982042L, 980402357709979718L),
	GUEST_RESTRICTED(924422513532997662L, 980402433828192297L);

	private final Long mainID, testID;
	Roles(Long mainID, Long testID) {this.mainID = mainID; this.testID = testID;}
	public Role getGuildRole(@NotNull Guild guild) {return guild.getRoleById((guild.getIdLong() == 844304271649538058L) ? mainID : testID);}
	public @NotNull String getAsMention(Guild guild) {return getGuildRole(guild).getAsMention();}
}