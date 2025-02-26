package dev.casiebarie.inosso.utils;

import dev.casiebarie.inosso.ClassLoader;
import dev.casiebarie.inosso.Main;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import static dev.casiebarie.inosso.Main.safeRunnable;
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
			String[] parts = value.toString().split(";repo=");
			checkLatestVersion(dependency, parts[0], parts[1]);
		}); getLogger().debug("Checking dependencies complete");
	}

	private void checkLatestVersion(String dependency, String currentVersion, String repo) {
		String latestVersion = null;
		try {
			latestVersion = switch (repo) {
				case "lavalink" -> getLatestVersion(dependency, false);
				case "mavenCentral" -> getLatestVersion(dependency, true);
			default -> throw new IllegalArgumentException("Unknown repository: " + repo);};
		} catch(Exception ex) {getLogger().warn("Failed to check version for {}: {}", dependency, ex.getMessage());}

		dependency = dependency.replace("_", ".");
		versionMap.put(dependency, new String[]{currentVersion, latestVersion != null ? latestVersion : currentVersion});
		if(latestVersion != null && !latestVersion.equals(currentVersion)) {getLogger().warn("Update available: {} `{} -> {}`", dependency, currentVersion, latestVersion);}
	}

	private @Nullable String getLatestVersion(@NotNull String dependency, boolean isCentral) throws IOException {
		String[] parts = dependency.split("_");
		String groupId = isCentral ? parts[0] : parts[0].replace(".", "/");
		String url = (isCentral) ? "https://search.maven.org/solrsearch/select?q=g:%22" + groupId + "%22+AND+a:%22" + parts[1] + "%22&rows=1&wt=json" : "https://maven.lavalink.dev/releases/" + groupId + "/" + parts[1] + "/maven-metadata.xml";

		HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
		connection.setRequestMethod("GET");
		connection.setConnectTimeout(5000);
		connection.setReadTimeout(5000);

		int responseCode = connection.getResponseCode();
		if(responseCode != 200) {getLogger().warn("Failed to fetch data from {}. HTTP response: {}", url, responseCode); return null;}

		try(Scanner scanner = new Scanner(connection.getInputStream())) {
			String response = scanner.useDelimiter("\\A").next();
			String latestVersion = null;
			if(isCentral) {
				int versionIndex = response.indexOf("\"latestVersion\":\"");
				if(versionIndex == -1) {return null;}
				int start = versionIndex + 17;
				int end = response.indexOf("\"", start);
				if(end == -1) {return null;}
				latestVersion = response.substring(start, end);
			} else {
				int start = response.indexOf("<latest>") + 8;
				int end = response.indexOf("</latest>");
				if(start > 8 && end > start) {latestVersion = response.substring(start, end);}
			}

			if(latestVersion == null || latestVersion.contains("beta") || latestVersion.contains("SNAPSHOT") || latestVersion.contains("alpha")) {return null;}
			return latestVersion;
		}
	}
}