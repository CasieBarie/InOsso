package dev.casiebarie.inosso.other;

import dev.casiebarie.inosso.InstanceManager;
import dev.casiebarie.inosso.Main;
import dev.casiebarie.inosso.utils.ReplyOperation;
import dev.casiebarie.inosso.utils.Utils;
import dev.casiebarie.inosso.utils.logging.Logger;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import static dev.casiebarie.inosso.utils.logging.Logger.getLogger;

public class MessageDeleter extends ListenerAdapter {
	public MessageDeleter(@NotNull InstanceManager iManager) {iManager.registerAsEventListener(this);}

	@Override
	public void onMessageReactionAdd(@NotNull MessageReactionAddEvent e) {
		Main.pool.execute(() -> {
			getLogger().debug("Messagereaction added by {}.", Logger.getUserNameAndId(e.getUser()));
			if(e.getUser().isBot()) {return;}
			if(!e.getEmoji().getName().equals("❌")) {return;}
			if(!e.getMessageAuthorId().equals("0") && !e.getJDA().getSelfUser().getId().equals(e.getMessageAuthorId())) {return;}
			if(!Utils.getCasAsUser().getId().equals(e.getUserId())) {return;}
			e.retrieveMessage().queue(message -> message.delete().queue(success -> getLogger().debug("Message with ID {} has been deleted", e.getMessageId()), ReplyOperation::error));
		});
	}
}