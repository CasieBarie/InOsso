package dev.casiebarie.inosso.utils;

import dev.casiebarie.inosso.enums.Channels;
import dev.casiebarie.inosso.enums.Roles;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class Utils {
	static final Random random = new Random();
	public static boolean isGuest(Member member, boolean isAllowed) {
		if(isAllowed) {return (member.getRoles().contains(Roles.GUEST_ALLOWED.getGuildRole(member.getGuild())));
		} else {return (member.getRoles().contains(Roles.GUEST_RESTRICTED.getGuildRole(member.getGuild())));}
	}

	public static boolean isSpecial(Member member) {return !isGuest(member, false) && !isGuest(member, true);}

	public static @NotNull String getAsMention(Member member) {
		if(isSpecial(member)) {return member.getRoles().get(0).getAsMention();
		} else {return member.getAsMention();}
	}

	public static boolean isInVoice(@NotNull Member member, ReplyOperation o) {
		AudioChannel audioChannel = member.getVoiceState().getChannel();
		VoiceChannel voice = Channels.VOICE.getAsChannel(member.getGuild());
		if(audioChannel == null || !audioChannel.getId().equals(voice.getId())) {o.sendNotAllowed("Je zit niet in " + voice.getAsMention() + "!"); return false;}
		return true;
	}

	public static Member getCasAsMember(@NotNull Guild guild) {return guild.getMemberById(515179486329962502L);}
	public static IMentionable getCas(Guild guild) {
		Member cas = getCasAsMember(guild);
		if(isSpecial(cas)) {return cas.getRoles().get(0);
		} else {return cas;}
	}

	public static @NotNull FileUpload loadImage(String filename) {return FileUpload.fromData(Utils.class.getClassLoader().getResourceAsStream("images/" + filename), filename);}

	public static @NotNull FileUpload loadAvatar(String avatarUrl) {return loadAvatar(avatarUrl, null);}
	public static @NotNull FileUpload loadAvatar(String avatarUrl, Integer number) {
		String numberString = number != null ? number + "" : "";
		try {
			URL url = new URL(avatarUrl);
			try(InputStream is = url.openStream()) {
				byte[] data = is.readAllBytes();
				return FileUpload.fromData(data, String.format("avatar%s.png", numberString));
			}
		} catch (Exception ex) {
			getLogger().error(ex.getMessage(), ex);
			return FileUpload.fromData(Utils.class.getClassLoader().getResourceAsStream("images/goat.png"), String.format("avatar%s.png", numberString));
		}
	}

	public static @NotNull String formatDuration(long length) {return formatDuration(length, length);}
	public static @NotNull String formatDuration(long length, long maxLength) {
		if(length == Long.MAX_VALUE) {return "LIVE";}
		if(length == Long.MIN_VALUE) {return "ONBEKEND";}
		if(length > maxLength) {length = maxLength;}
		length /= 1000;
		long seconds = length % 60;
		length /= 60;
		long minutes = length % 60;
		long hours = length / 60;
		return escapeMarkdown((hours > 0) ? String.format("%d:%02d:%02d", hours, minutes, seconds) : String.format("%d:%02d", minutes, seconds));
	}

	public static @NotNull String truncate(String text, int maxLength) {
		if(text == null) {return "";}
		text = text.replace("\r\n", "\n");
		if(text.codePointCount(0, text.length()) <= maxLength) {return text;}
		int endIndex = text.offsetByCodePoints(0, maxLength - 1);
		return escapeMarkdown(text.substring(0, endIndex) + "…");
	}

	/**0 = Search<br>1 = Supported Link<br>2 = Unsupported*/
	public static int getAudioType(String url) {
		Set<String> extensions = Set.of("mp3", "flac", "wav", "mka", "webm", "mp4", "m4a", "ogg", "opus", "aac", "m3u", "pls");
		Set<String> domains = Set.of("soundcloud.com", "m.soundcloud.com", "on.soundcloud.com", "snd.sc", "bandcamp.com", "vimeo.com", "twitch.tv", "m.twitch.tv", "clips.twitch.tv");
		try {
			URL link = new URL(url);
			String host = link.getHost().toLowerCase();
			if(domains.stream().anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain))) {return 1;}
			String path = link.getPath().toLowerCase();
			int dotIndex = path.lastIndexOf('.');
			return dotIndex == -1 || !extensions.contains(path.substring(dotIndex + 1)) ? 2 : 1;
		} catch(MalformedURLException ex) {return 0;}
	}

	public static @NotNull Properties getProperties() {
		Properties properties  = new Properties();
		try {properties.load(Utils.class.getClassLoader().getResourceAsStream("pom.properties"));
		} catch (IOException e) {getLogger().error(e.getMessage(), e);}
		return properties;
	}

	public static @NotNull String getUptime() {
		long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
		long days = TimeUnit.MILLISECONDS.toDays(uptime);
		long hours = TimeUnit.MILLISECONDS.toHours(uptime) % 24;
		long minutes = TimeUnit.MILLISECONDS.toMinutes(uptime) % 60;
		long seconds = TimeUnit.MILLISECONDS.toSeconds(uptime) % 60;
		return String.format("%d %s, %d %s, %d %s en %d %s",
			days, (days == 1) ? "dag" : "dagen",
			hours, (hours == 1) ? "uur" : "uren",
			minutes, (minutes == 1) ? "minuut" : "minuten",
			seconds, (seconds == 1) ? "seconde" : "seconden"
		);
	}

	public static long jitter(long around) {
		long jitter = random.nextInt(51);
		return around + jitter;
	}

	public static @NotNull String escapeMarkdown(@NotNull String input) {
		return input.replace("*", "\\*")
			.replace("_", "\\_")
			.replace("~", "\\~")
			.replace("`", "\\`")
			.replace(">", "\\>");
	}
}