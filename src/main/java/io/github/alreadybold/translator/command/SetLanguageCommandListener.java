package io.github.alreadybold.translator.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import io.github.alreadybold.translator.i18n.BotMessage;
import io.github.alreadybold.translator.i18n.Language;
import io.github.alreadybold.translator.settings.UserLanguageRegistry;

/**
 * "/setlang" 슬래시 커맨드를 처리하는 리스너.
 *
 * 커맨드를 실행한 유저가 "내가 말할 언어"를 선택해서 저장한다. 커맨드 등록 자체는
 * CommandRegistrationListener가 담당하고, 이 클래스는 실행 로직만 갖는다.
 */
public class SetLanguageCommandListener extends ListenerAdapter {

	private static final Logger LOGGER = LoggerFactory.getLogger(SetLanguageCommandListener.class);

	private final UserLanguageRegistry languageRegistry;

	public SetLanguageCommandListener(UserLanguageRegistry languageRegistry) {
		this.languageRegistry = languageRegistry;
	}

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!CommandRegistrationListener.SET_LANGUAGE_COMMAND.equals(event.getName())) {
			return;
		}

		OptionMapping languageOption = event.getOption(CommandRegistrationListener.LANGUAGE_OPTION);

		// 이 옵션은 필수(required)로 등록했고, 값도 미리 등록한 선택지(choice) 중에서만 고를 수 있다.
		// 즉 디스코드 쪽에서 이미 검증된 값만 도착하므로, 여기서 잘못된 값을 걱정할 필요가 없다.
		Language language = Language.valueOf(languageOption.getAsString());
		long userId = event.getUser().getIdLong();

		languageRegistry.set(userId, language);

		LOGGER.info("언어 설정: {} -> {}", event.getUser().getName(), language);
		// 확인 메시지는 방금 선택한 언어로 보여준다 (설정이 실제로 적용됐음을 바로 체감할 수 있음).
		event.reply(BotMessage.LANGUAGE_SET.text(language, language.displayName()))
				.setEphemeral(true)
				.queue();
	}
}
