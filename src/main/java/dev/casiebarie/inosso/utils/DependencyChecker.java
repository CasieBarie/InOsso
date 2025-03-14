package dev.casiebarie.inosso.utils;

import dev.casiebarie.inosso.ClassLoader;
import dev.casiebarie.inosso.Main;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static dev.casiebarie.inosso.Main.safeRunnable;
import static dev.casiebarie.inosso.enums.Variables.EMPTY_IMAGE;
import static dev.casiebarie.inosso.enums.Variables.EMPTY_IMAGE_PATH;
import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class DependencyChecker extends ListenerAdapter {
	protected static final Map<String, String[]> versionMap = new LinkedHashMap<>();
	public DependencyChecker(@NotNull ClassLoader classes) {classes.registerAsEventListener(this);}

	@Override
	public void onGuildReady(GuildReadyEvent e) {Main.scheduledPool.scheduleAtFixedRate(safeRunnable(this::checkDependencies), 0, 1, TimeUnit.DAYS);}

	private void checkDependencies() {
		getLogger().debug("Checking dependencies...");
		Properties properties = Utils.getProperties();
		properties.forEach((key, value) -> {
			if(!key.toString().endsWith(".version")) {return;}
			String dependency = key.toString().replace(".version", "");
			checkLatestVersion(dependency, value.toString());
		}); sendToCas();
		getLogger().debug("Checking dependencies complete");
	}

	private void checkLatestVersion(String dependency, String currentVersion) {
		String latestVersion = null;
		try {latestVersion = getLatestVersion(dependency);
		} catch(Exception ex) {getLogger().warn("Failed to check version for {}: {}", dependency, ex.getMessage());}

		dependency = dependency.replace("_", ".");
		versionMap.put(dependency, new String[]{currentVersion, latestVersion != null ? latestVersion : currentVersion});
		if(latestVersion != null && !latestVersion.equals(currentVersion)) {getLogger().warn("Update available: {} `{} -> {}`", dependency, currentVersion, latestVersion);}
	}

	private @Nullable String getLatestVersion(@NotNull String dependency) throws IOException {
		String[] parts = dependency.split("_");
		String url = "https://search.maven.org/solrsearch/select?q=g:%22" + parts[0] + "%22+AND+a:%22" + parts[1] + "%22&rows=1&wt=json";

		HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
		connection.setRequestMethod("GET");
		connection.setConnectTimeout(5000);
		connection.setReadTimeout(5000);

		int responseCode = connection.getResponseCode();
		if(responseCode != 200) {getLogger().warn("Failed to fetch data from {}. HTTP response: {}", url, responseCode); return null;}

		try(Scanner scanner = new Scanner(connection.getInputStream())) {
			String response = scanner.useDelimiter("\\A").next();
			int versionIndex = response.indexOf("\"latestVersion\":\"");
			if(versionIndex == -1) {return null;}
			int start = versionIndex + 17;
			int end = response.indexOf("\"", start);
			if(end == -1) {return null;}
			String latestVersion = response.substring(start, end);
			if(latestVersion == null || latestVersion.contains("beta") || latestVersion.contains("SNAPSHOT") || latestVersion.contains("alpha")) {return null;}
			return latestVersion;
		}
	}

	private void sendToCas() {
		List<MessageEmbed> embeds = new ArrayList<>();
		versionMap.forEach((k, v) -> {
			if(v[1] == null || v[0].equals(v[1])) {return;}
			embeds.add(
				new EmbedBuilder()
					.setColor(Color.ORANGE)
					.setImage(EMPTY_IMAGE)
					.setDescription("# Dependency update!" +
						"\n### `" + k + "`" +
						"\ncan be updated from `" + v[0] + "` to `" + v[1] + "`!")
				.build()
			);
		});

		PrivateChannel channel = Utils.getCasAsUser().openPrivateChannel().complete();
		channel.getIterableHistory().takeAsync(1000).thenApply(channel::purgeMessages).whenComplete((success, error) -> {
			if(embeds.isEmpty()) {return;}
			channel.sendMessageEmbeds(embeds).setFiles(Utils.loadImage(EMPTY_IMAGE_PATH)).queue(null, ReplyOperation::error);
		});
	}
}