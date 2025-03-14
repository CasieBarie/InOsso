package dev.casiebarie.inosso.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.casiebarie.inosso.music.lavaplayer.GuildMusicManager;
import dev.casiebarie.inosso.music.lavaplayer.PlayerManager;
import dev.casiebarie.inosso.utils.ReplyOperation;
import dev.casiebarie.inosso.utils.Utils;
import dev.casiebarie.inosso.utils.logging.Logger;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Queue;

import static dev.casiebarie.inosso.enums.Variables.ACTION_CANCELLED_MSG;
import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class QueueViewer {
	public void viewQueue(Music music, @NotNull ButtonInteractionEvent e) {
		Guild guild = e.getGuild();
		ReplyOperation o = new ReplyOperation(e);
		GuildMusicManager manager = PlayerManager.getInstance(music).getGuildMusicManager(guild);
		Queue<AudioTrack> queue = manager.scheduler.queue;
		if(queue.isEmpty()) {o.sendFailed(ACTION_CANCELLED_MSG); return;}
		e.getHook().editOriginal("_De wachtrij wordt mogelijk niet goed weergegeven op mobiele apparaten._")
			.setEmbeds(new MessageEmbed[0])
			.setFiles(createQueue(queue, guild))
		.queue(success -> {}, o::sendFailed);
	}

	private @NotNull FileUpload createQueue(@NotNull Queue<AudioTrack> queue, Guild guild) {
		int trackNumber = 1;
		long totalDuration = 0;
		String line = "====================================================================";
		StringBuilder builder = new StringBuilder();

		for(AudioTrack track : queue) {
			totalDuration += track.getDuration();
			String trackNumberStr = trackNumber + ". ";
			String indent = " ".repeat(trackNumberStr.length());
			builder.append(trackNumberStr).append(Utils.truncate(track.getInfo().title, line.length() - trackNumberStr.length() - 1)).append("\n")
				.append(indent).append(Utils.truncate(track.getInfo().author, line.length())).append("\n")
				.append(indent).append("(").append(Utils.formatDuration(track.getDuration())).append(")\n")
				.append(indent).append(guild.getMemberById(track.getUserData().toString()).getEffectiveName()).append("\n\n");
			trackNumber++;
		}

		String title = "📃 WACHTRIJ 📃",
			trackCount = "Aantal nummers: " + queue.size(),
			duration = "Totale lengte: " + Utils.formatDuration(totalDuration),
			centeredTitle = String.format("%" + ((line.length() + title.length()) / 2) + "s", title),
			centeredTrackCount = String.format("%" + ((line.length() + trackCount.length()) / 2) + "s", trackCount),
			centeredDuration = String.format("%" + ((line.length() + duration.length()) / 2) + "s", duration),
			header = line + "\n" + centeredTitle + "\n" + centeredTrackCount + "\n" + centeredDuration + "\n" + line + "\n\n",
			finalString = header + builder + line;

		byte[] fileContent = finalString.getBytes(StandardCharsets.UTF_8);
		getLogger().debug("Queue created for guild {}", Logger.getGuildNameAndId(guild));
		return FileUpload.fromData(fileContent, "Wachtrij.txt");
	}
}