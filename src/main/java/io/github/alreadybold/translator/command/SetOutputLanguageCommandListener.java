package io.github.alreadybold.translator.command;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.User;
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
 * 대상(기본은 커맨드를 실행한 본인)의 "발화를 번역해서 보여줄 언어"를 저장한다. /setlang
 * (말하는 언어, 번역의 source)과 짝을 이루는 target 쪽 설정이다 - 예: 한국어로 말하고
 * (setlang=KO) 영어로 보여주고 싶으면(setoutputlang=EN) 이 커맨드로 EN을 선택한다.
 * user 옵션으로 같은 음성 채널의 다른 사람을 지정하면 공개 알림으로 응답한다. 커맨드 등록
 * 자체는 CommandRegistrationListener가 담당하고, 이 클래스는 실행 로직만 갖는다.
 *
 * 대상의 말하는 언어와 같은 언어를 번역 언어로 고르는 건 번역이 항상 원문 그대로 나오는
 * 무의미한 설정이라 막는다. (디스코드 슬래시 커맨드의 choice 목록은 유저별로 동적으로 못
 * 바꾸므로, 실행 시점에 검증하는 방식밖에 없다.)
 *
 * 응답 문구는 방금 고른 출력 언어가 아니라 커맨드를 실행한 사람의 말하는 언어로 보여준다.
 * 출력 언어는 애초에 화자가 모르는 청자를 위한 언어라서, 그 언어로 확인 메시지를 띄우면
 * 정작 설정한 사람이 못 읽는 모순이 생긴다.
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

		Language invokerLanguage = languageRegistry.get(event.getUser().getIdLong());
		Optional<User> target = CommandTargets.resolve(event);

		if (target.isEmpty()) {
			event.reply(BotMessage.TARGET_NOT_IN_SAME_VOICE_CHANNEL.text(invokerLanguage)).setEphemeral(true).queue();
			return;
		}

		// 이 옵션은 필수(required)로 등록했고, 값도 미리 등록한 선택지(choice) 중에서만 고를 수 있다.
		// 즉 디스코드 쪽에서 이미 검증된 값만 도착하므로, 여기서 잘못된 값을 걱정할 필요가 없다.
		OptionMapping languageOption = event.getOption(CommandRegistrationListener.LANGUAGE_OPTION);
		Language language = Language.valueOf(languageOption.getAsString());
		User targetUser = target.get();
		Language targetSpokenLanguage = languageRegistry.get(targetUser.getIdLong());

		if (language == targetSpokenLanguage) {
			event.reply(BotMessage.OUTPUT_LANGUAGE_SAME_AS_SPOKEN.text(
							invokerLanguage, targetSpokenLanguage.displayName()))
					.setEphemeral(true)
					.queue();
			return;
		}

		outputLanguageRegistry.set(targetUser.getIdLong(), language);
		LOGGER.info("출력 언어 설정: {} -> {} (실행한 사람: {})", targetUser.getName(), language, event.getUser().getName());

		if (CommandTargets.isSelf(event, targetUser)) {
			event.reply(BotMessage.OUTPUT_LANGUAGE_SET.text(invokerLanguage, language.displayName()))
					.setEphemeral(true)
					.queue();
			return;
		}

		event.reply(BotMessage.OUTPUT_LANGUAGE_SET_FOR_OTHER.text(
						invokerLanguage,
						CommandTargets.invokerName(event),
						targetUser.getAsMention(),
						language.displayName()))
				.queue();
	}
}
