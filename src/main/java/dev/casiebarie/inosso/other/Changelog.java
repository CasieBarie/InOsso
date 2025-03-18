package dev.casiebarie.inosso.other;

import dev.casiebarie.inosso.ClassLoader;
import dev.casiebarie.inosso.interfaces.Information;
import dev.casiebarie.inosso.utils.ReplyOperation;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class Changelog implements Information {
	public Changelog(@NotNull ClassLoader classes) {classes.registerAsInformationClass("changelog", this);}

	@Override
	public void sendInformation(@NotNull ReplyOperation o) {o.e.getHook().sendFiles(createFile(getChangelog())).queue(null, o::sendFailed);}

	private @NotNull String getChangelog() {
		try {
			URL url = new URL("https://raw.githubusercontent.com/CasieBarie/InOsso/refs/heads/master/README.md");
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			try(BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
				StringBuilder builder = new StringBuilder();
				String line;
				boolean showContent = false;
				while((line = reader.readLine()) != null) {
					if(line.startsWith("## Changelog:")) {showContent = true; continue;}
					if(showContent) {builder.append(line).append("\n");}
				} return builder.toString();
			}
		} catch(Exception ex) {getLogger().error(ex.getMessage(), ex); return "";}
	}

	private @NotNull FileUpload createFile(@NotNull String changeLog) {
		String line = "=====================================================================================";
		changeLog = changeLog.replaceAll("\\*\\*", "").replaceAll("##+\\s+", "# ");

		String title = "📜 CHANGELOG 📜",
			desc = "https://github.com/CasieBarie/InOsso",
			centeredTitle = String.format("%" + ((line.length() + title.length()) / 2) + "s", title),
			centeredDesc = String.format("%" + ((line.length() + desc.length()) / 2) + "s",desc),
			finalString = line + "\n" + centeredTitle + "\n" + centeredDesc + "\n" + line + "\n\n" + changeLog + "\n" + line;

		byte[] fileContent = finalString.getBytes(StandardCharsets.UTF_8);
		return FileUpload.fromData(fileContent, "Changelog.md");
	}
}