package dev.casiebarie.inosso;

import com.sedmelluq.discord.lavaplayer.jdaudp.NativeAudioSendFactory;
import dev.casiebarie.inosso.utils.logging.Logger;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.StatusChangeEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;

import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class Main extends ListenerAdapter {
	static JDA jda;
	InstanceManager iManager;
	public static void main(String[] args) {
		Main main = new Main();
		new Logger();
		Thread.setDefaultUncaughtExceptionHandler((t, ex) -> getLogger().error(ex.getMessage(), ex));
		main.iManager = new InstanceManager(main);
		main.iManager.registerAsEventListener(main);
		jda = JDABuilder.createDefault(main.getToken())
			.setChunkingFilter(ChunkingFilter.ALL)
			.setMemberCachePolicy(MemberCachePolicy.ALL)
			.enableIntents(GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_WEBHOOKS, GatewayIntent.MESSAGE_CONTENT)
			.addEventListeners(main.iManager.getEventListeners().toArray())
			.setAudioSendFactory(new NativeAudioSendFactory())
			.setCallbackPool(pool, true)
			.setGatewayPool(scheduledPool, true)
			.setEventPool(pool, true)
		.build();
	}

	@Override
	public void onStatusChange(@NotNull StatusChangeEvent e) {if(e.getNewStatus() == JDA.Status.CONNECTED) {setJDA(e.getJDA());}}

	private @NotNull String getToken() {
		String token = System.getenv("DISCORD_TOKEN");
		if(token != null) {getLogger().info("Token found!"); return token;}
		throw new NullPointerException("No token found!");
	}

	protected void shutdown() {
		getLogger().info("Shutting down...");
		jda.shutdown();

		try {jda.awaitShutdown(5, TimeUnit.SECONDS);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			getLogger().error(ex.getMessage(), ex);
			jda.shutdownNow();
			System.exit(1);
		}

		getLogger().info("Disconnected!");
		getLogger().info("Bye Bye!");
		System.exit(0);
	}

	// --- STATIC METHODS ---
	public static final ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()), task -> {
		Thread thread = new Thread(task, "InOsso MainPool");
		thread.setDaemon(true);
		thread.setUncaughtExceptionHandler((t, ex) -> getLogger().error(ex.getMessage(), ex));
		return thread;
	});

	public static final ScheduledExecutorService scheduledPool = Executors.newScheduledThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()), task -> {
		Thread thread = new Thread(task, "InOsso ScheduledPool");
		thread.setDaemon(true);
		thread.setUncaughtExceptionHandler((t, ex) -> getLogger().error(ex.getMessage(), ex));
		return thread;
	});

	public static @NotNull Runnable safeRunnable(Runnable runnable) {
		return () -> {
			try {runnable.run();
			} catch(RejectedExecutionException ex) {getLogger().debug("Task rejected: {}", ex.getMessage());
			} catch(Exception ex) {getLogger().error(ex.getMessage(), ex);}
		};
	}

	public static JDA jda() {return jda;}
	private static void setJDA(JDA jda) {Main.jda = jda;}
}