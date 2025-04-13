package dev.casiebarie.inosso.other;

import dev.casiebarie.inosso.ClassLoader;
import dev.casiebarie.inosso.Main;
import dev.casiebarie.inosso.utils.ReplyOperation;
import dev.casiebarie.inosso.utils.Utils;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class MessageDeleter extends ListenerAdapter {
	public MessageDeleter(@NotNull ClassLoader classes) {classes.registerAsEventListener(this);}

	@Override
	public void onMessageReactionAdd(@NotNull MessageReactionAddEvent e) {
		Main.pool.execute(() -> {
			if(e.getUser().isBot()) {return;}
			if(!e.getEmoji().getName().equals("❌")) {return;}
			if(!e.getJDA().getSelfUser().getId().equals(e.getMessageAuthorId())) {return;}
			if(!Utils.getCasAsUser().getId().equals(e.getUserId())) {return;}
			e.retrieveMessage().queue(message -> message.delete().queue(null, ReplyOperation::error));
		});
	}
}