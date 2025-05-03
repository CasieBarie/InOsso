package dev.casiebarie.inosso.utils;

import dev.casiebarie.inosso.InstanceManager;
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
	public DependencyChecker(@NotNull InstanceManager iManager) {iManager.registerAsEventListener(this);}

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
		} catch(Exception ex) {getLogger().debug("Failed to check version for {}: {}", dependency, ex.getMessage());}

		dependency = dependency.replace("_", ".");
		versionMap.put(dependency, new String[]{currentVersion, latestVersion != null ? latestVersion : currentVersion});
		getLogger().debug("Latest version for {}: {}", dependency, latestVersion);
	}

	private @Nullable String getLatestVersion(@NotNull String dependency) throws IOException {
		String[] parts = dependency.split("_");
		String url = "https://search.maven.org/solrsearch/select?q=g:%22" + parts[0] + "%22+AND+a:%22" + parts[1] + "%22&core=gav&rows=5&wt=json&sort=version+desc";

		HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
		connection.setRequestMethod("GET");
		connection.setConnectTimeout(5000);
		connection.setReadTimeout(5000);

		int responseCode = connection.getResponseCode();
		if(responseCode != 200) {getLogger().debug("Failed to fetch data from {}. HTTP response: {}", url, responseCode); return null;}

		try(Scanner scanner = new Scanner(connection.getInputStream())) {
			String response = scanner.useDelimiter("\\A").next();
			List<String> versions = new ArrayList<>();
			int index = 0;
			while((index = response.indexOf("\"v\":\"", index)) != -1) {
				int start = index + 5;
				int end = response.indexOf("\"", start);
				if(end == -1) break;
				String version = response.substring(start, end);
				versions.add(version);
				index = end + 1;
			}

			for(String version : versions) {
				String lower = version.toLowerCase();
				if(!lower.contains("alpha") && !lower.contains("beta") && !lower.contains("rc") && !lower.contains("snapshot") && !version.contains("-")) {return version;}
			} return null;
		}
	}

	private void sendToCas() {
		List<MessageEmbed> embeds = new ArrayList<>();
		versionMap.forEach((k, v) -> {
			if(v[0] == null) {return;}

			try {
				int currentVersion = Integer.parseInt(v[0].replaceAll("\\.", ""));
				int latestVersion = Integer.parseInt(v[1].replaceAll("\\.", ""));
				if(currentVersion >= latestVersion) {return;}
			} catch(NumberFormatException ex) {if(v[0].equals(v[1])) {return;}}

			getLogger().info("Update available: {} `{} -> {}`", k, v[0], v[1]);
			embeds.add(
				new EmbedBuilder()
					.setColor(Color.PINK)
					.setImage(EMPTY_IMAGE)
					.setDescription("# Dependency update!" +
						"\n### `" + k + "`" +
						"\ncan be updated from `" + v[0] + "` to `" + v[1] + "`!")
				.build()
			);
		});

		PrivateChannel channel = Utils.getCasAsUser().openPrivateChannel().complete();
		channel.getIterableHistory().takeAsync(1000).thenApply(list -> channel.purgeMessages(list.stream().filter(msg -> {
			if(msg.getEmbeds().isEmpty()) {return false;}
			return msg.getEmbeds().get(0).getColor().equals(Color.PINK);
		}).toList())).whenCompleteAsync((success, error) -> {
			if(embeds.isEmpty()) {return;}
			channel.sendMessageEmbeds(embeds).setFiles(Utils.loadImage(EMPTY_IMAGE_PATH)).queue(null, ReplyOperation::error);
		});
	}
}