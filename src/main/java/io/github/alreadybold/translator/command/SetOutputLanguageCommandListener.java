package io.github.alreadybold.translator.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import io.github.alreadybold.translator.i18n.BotMessage;
import io.github.alreadybold.translator.i18n.Language;
import io.github.alreadybold.translator.settings.UserLanguageRegistry;
import io.github.alreadybold.translator.settings.UserOutputLanguageRegistry;

/**
 * "/setoutputlang" 슬래시 커맨드를 처리하는 리스너.
 *
 * 커맨드를 실행한 화자가 "내 발화를 번역해서 보여줄 언어"를 선택해서 저장한다. /setlang
 * (화자가 말하는 언어, 번역의 source)과 짝을 이루는 target 쪽 설정으로, 둘 다 화자
 * 본인이 정한다 - 예: 한국어로 말하고(setlang=KO) 영어로 보여주고 싶으면(setoutputlang=EN)
 * 이 커맨드로 EN을 선택한다. 커맨드 등록 자체는 CommandRegistrationListener가 담당하고,
 * 이 클래스는 실행 로직만 갖는다.
 *
 * 말하는 언어와 같은 언어를 번역 언어로 선택하는 건 번역이 항상 원문 그대로 나오는
 * 무의미한 설정이라, languageRegistry로 현재 설정된 spoken language를 조회해서 막는다.
 * (디스코드 슬래시 커맨드의 choice 목록은 유저별로 동적으로 못 바꾸므로, 실행 시점에
 * 검증하는 방식밖에 없다.)
 *
 * 이 커맨드의 모든 응답 문구는 outputLanguage가 아니라 spokenLanguage 기준으로 보여준다.
 * outputLanguage는 애초에 "화자 본인이 못 알아듣는 청자를 위한" 번역 대상 언어라서,
 * 화자 본인은 그 언어를 모른다고 가정해야 한다 - 그 언어로 확인 메시지를 띄우면 정작
 * 설정한 화자가 못 읽는 모순이 생긴다.
 */
public class SetOutputLanguageCommandListener extends ListenerAdapter {

	private static final Logger LOGGER = LoggerFactory.getLogger(SetOutputLanguageCommandListener.class);

	private final UserLanguageRegistry languageRegistry;
	private final UserOutputLanguageRegistry outputLanguageRegistry;

	public SetOutputLanguageCommandListener(
			UserLanguageRegistry languageRegistry, UserOutputLanguageRegistry outputLanguageRegistry) {
		this.languageRegistry = languageRegistry;
		this.outputLanguageRegistry = outputLanguageRegistry;
	}

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!CommandRegistrationListener.SET_OUTPUT_LANGUAGE_COMMAND.equals(event.getName())) {
			return;
		}

		OptionMapping languageOption = event.getOption(CommandRegistrationListener.LANGUAGE_OPTION);

		// 이 옵션은 필수(required)로 등록했고, 값도 미리 등록한 선택지(choice) 중에서만 고를 수 있다.
		// 즉 디스코드 쪽에서 이미 검증된 값만 도착하므로, 여기서 잘못된 값을 걱정할 필요가 없다.
		Language language = Language.valueOf(languageOption.getAsString());
		long userId = event.getUser().getIdLong();
		Language spokenLanguage = languageRegistry.get(userId);

		if (language == spokenLanguage) {
			event.reply(BotMessage.OUTPUT_LANGUAGE_SAME_AS_SPOKEN.text(spokenLanguage, spokenLanguage.displayName()))
					.setEphemeral(true)
					.queue();
			return;
		}

		outputLanguageRegistry.set(userId, language);

		LOGGER.info("출력 언어 설정: {} -> {}", event.getUser().getName(), language);
		// 확인 메시지는 화자가 말하는 언어(spokenLanguage)로 보여준다 - 방금 고른 outputLanguage로
		// 보여주면, 그 언어를 모르는 화자 본인이 설정이 됐는지조차 확인할 수 없다.
		event.reply(BotMessage.OUTPUT_LANGUAGE_SET.text(spokenLanguage, language.displayName()))
				.setEphemeral(true)
				.queue();
	}
}
