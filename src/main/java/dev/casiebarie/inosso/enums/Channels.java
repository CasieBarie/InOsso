package dev.casiebarie.inosso.enums;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildChannel;
import org.jetbrains.annotations.NotNull;

public enum Channels {
	MAIN(844308837238046751L, 973138227672272948L, TextChannel.class),
	VOICE(844304272241721438L, 973138227672272949L, VoiceChannel.class),
	MUSIC(1341862670449770588L, 1017818694912581702L, TextChannel.class),
	CLIPS(1064578752299941888L, 1059567100479406110L, TextChannel.class),
	REQUEST(1341862786392658020L, 1029346068623798292L, TextChannel.class);

	private final Long mainId, testId;
	private final Class<? extends StandardGuildChannel> channelType;
	<G extends StandardGuildChannel> Channels(Long mainId, Long testId, Class<G> channelType) {this.mainId = mainId; this.testId = testId; this.channelType = channelType;}
	public Long getId(@NotNull Guild guild) {return (guild.getIdLong() == 840529735599259648L) ? mainId : testId;}
	public @NotNull String getAsMention(@NotNull Guild guild) {return guild.getGuildChannelById(getId(guild)).getAsMention();}
	@SuppressWarnings("unchecked")
	public <G extends StandardGuildChannel> G getAsChannel(@NotNull Guild guild) {return (G) guild.getChannelById(channelType, getId(guild));}
}