package io.github.alreadybold.translator.command;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import io.github.alreadybold.translator.i18n.BotMessage;
import io.github.alreadybold.translator.i18n.Language;
import io.github.alreadybold.translator.settings.UserLanguageRegistry;

/**
 * "/stoptranslate" 슬래시 커맨드를 처리하는 리스너.
 *
 * 대상(기본은 커맨드를 실행한 본인)의 번역을 끈다. /setlang으로 다른 사람이 나를 번역
 * 대상으로 지정할 수 있게 된 만큼, 지정당한 사람이 언제든 스스로 끌 수 있어야 한다.
 * 말할 언어 설정만 지우고(그게 곧 번역 켜짐 여부라서) 출력 언어 설정은 남겨둔다 - 다시
 * 켤 때 /setlang만 하면 되도록.
 */
public class StopTranslationCommandListener extends ListenerAdapter {

	private static final Logger LOGGER = LoggerFactory.getLogger(StopTranslationCommandListener.class);

	private final UserLanguageRegistry languageRegistry;

	public StopTranslationCommandListener(UserLanguageRegistry languageRegistry) {
		this.languageRegistry = languageRegistry;
	}

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!CommandRegistrationListener.STOP_TRANSLATION_COMMAND.equals(event.getName())) {
			return;
		}

		// 지우기 전에 읽어둔다 - 본인 번역을 끄면 그 뒤로는 기본값으로 돌아가서, 방금까지
		// 쓰던 언어로 응답할 수 없게 된다.
		Language invokerLanguage = languageRegistry.get(event.getUser().getIdLong());
		Optional<User> target = CommandTargets.resolve(event);

		if (target.isEmpty()) {
			event.reply(BotMessage.TARGET_NOT_IN_SAME_VOICE_CHANNEL.text(invokerLanguage)).setEphemeral(true).queue();
			return;
		}

		User targetUser = target.get();

		if (!languageRegistry.hasExplicitLanguage(targetUser.getIdLong())) {
			event.reply(BotMessage.TRANSLATION_NOT_ACTIVE.text(invokerLanguage)).setEphemeral(true).queue();
			return;
		}

		languageRegistry.remove(targetUser.getIdLong());
		LOGGER.info("번역 끔: {} (실행한 사람: {})", targetUser.getName(), event.getUser().getName());

		if (CommandTargets.isSelf(event, targetUser)) {
			event.reply(BotMessage.TRANSLATION_STOPPED.text(invokerLanguage)).setEphemeral(true).queue();
			return;
		}

		event.reply(BotMessage.TRANSLATION_STOPPED_FOR_OTHER.text(
						invokerLanguage, CommandTargets.invokerName(event), targetUser.getAsMention()))
				.queue();
	}
}
