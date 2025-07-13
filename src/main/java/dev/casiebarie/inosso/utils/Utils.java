package dev.casiebarie.inosso.utils;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.casiebarie.inosso.enums.Channels;
import dev.casiebarie.inosso.enums.Roles;
import dev.casiebarie.inosso.enums.Variables.AudioTypes;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static dev.casiebarie.inosso.Main.jda;
import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public final class Utils {
	private static final Random RANDOM = new Random();
	public static boolean isGuest(@NotNull Member member, boolean isAllowed) {
		Roles role = isAllowed ? Roles.GUEST_ALLOWED : Roles.GUEST_RESTRICTED;
		return member.getRoles().contains(role.getGuildRole(member.getGuild()));
	}

	public static boolean isSpecial(@NotNull Member member) {return !isGuest(member, false) && !isGuest(member, true);}

	public static @NotNull String getAsMention(@NotNull Member member) {return isSpecial(member) ? member.getRoles().get(0).getAsMention() : member.getAsMention();}

	public static boolean isInVoice(@NotNull Member member, @NotNull ReplyOperation operation) {
		Optional<AudioChannel> audioChannel = Optional.ofNullable(member.getVoiceState().getChannel());
		VoiceChannel voice = Channels.VOICE.getAsChannel(member.getGuild());
		if(audioChannel.isEmpty() || !audioChannel.get().getId().equals(voice.getId())) {
			operation.sendNotAllowed("Je zit niet in " + voice.getAsMention() + "!");
			return false;
		} return true;
	}

	public static User getCasAltAsUser() {return jda().getUserById(967727194967257208L);}
	public static User getCasAsUser() {return jda().getUserById(515179486329962502L);}
	public static Member getCasAsMember(@NotNull Guild guild) {return guild.getMember(getCasAsUser());}

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

	public static @NotNull String truncate(@NotNull String text, int maxLength) {
		text = text.replace("\r\n", "\n");
		if(text.codePointCount(0, text.length()) <= maxLength) {return text;}
		return escapeMarkdown(text.substring(0, text.offsetByCodePoints(0, maxLength - 1)) + "…");
	}

	public static AudioTypes getAudioType(@NotNull Message message) {
		Set<String> extensions = Set.of("mp3", "flac", "wav", "mka", "webm", "mp4", "m4a", "ogg", "opus", "aac", "m3u", "pls");
		Set<String> domains = Set.of("soundcloud.com", "m.soundcloud.com", "on.soundcloud.com", "snd.sc", "bandcamp.com", "vimeo.com", "twitch.tv", "m.twitch.tv", "clips.twitch.tv");

		if(!message.getAttachments().isEmpty()) {
			String extension = message.getAttachments().get(0).getFileExtension().replace(".", "").toLowerCase();
			return extensions.contains(extension) ? AudioTypes.FILE : AudioTypes.UNSUPPORTED_FILE;
		}

		try {
			URL url = new URL(message.getContentRaw());
			String host = url.getHost().toLowerCase();
			if(domains.stream().anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain))) {return AudioTypes.LINK;}
			String path = url.getPath().toLowerCase();
			int dotIndex = path.lastIndexOf('.');
			return dotIndex == -1 || !extensions.contains(path.substring(dotIndex + 1)) ? AudioTypes.UNSUPPORTED_LINK : AudioTypes.LINK;
		} catch(MalformedURLException ex) {return AudioTypes.SEARCH;}
	}

	public static boolean isSoundCloudGoPlus(@NotNull AudioTrack track) {return track.getInfo().identifier.endsWith("/preview/hls");}

	public static @NotNull Properties getProperties() {
		Properties properties = new Properties();
		try {properties.load(Utils.class.getClassLoader().getResourceAsStream("pom.properties"));
		} catch(IOException ex) {getLogger().error(ex.getMessage(), ex);}
		return properties;
	}

	public static @NotNull String getUptime() {
		long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
		long days = TimeUnit.MILLISECONDS.toDays(uptime);
		long hours = TimeUnit.MILLISECONDS.toHours(uptime) % 24;
		long minutes = TimeUnit.MILLISECONDS.toMinutes(uptime) % 60;
		long seconds = TimeUnit.MILLISECONDS.toSeconds(uptime) % 60;
		return String.format("%d %s, %d %s, %d %s en %d %s",
			days, days == 1 ? "dag" : "dagen",
			hours, hours == 1 ? "uur" : "uren",
			minutes, minutes == 1 ? "minuut" : "minuten",
			seconds, seconds == 1 ? "seconde" : "seconden"
		);
	}

	public static long jitter(long around) {return around + RANDOM.nextInt(51);}

	public static @NotNull String escapeMarkdown(@NotNull String input) {
		return input.replace("*", "\\*")
			.replace("_", "\\_")
			.replace("~", "\\~")
			.replace("`", "\\`")
			.replace(">", "\\>");
	}
}