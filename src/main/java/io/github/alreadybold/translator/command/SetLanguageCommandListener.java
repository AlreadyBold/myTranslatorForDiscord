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

/**
 * "/setlang" 슬래시 커맨드를 처리하는 리스너.
 *
 * 대상(기본은 커맨드를 실행한 본인)의 "말할 언어"를 저장하고, 그 자체로 그 사람의 번역을
 * 켠다. user 옵션으로 같은 음성 채널의 다른 사람을 지정할 수도 있는데, 이때는 그 사람
 * 모르게 번역이 켜지지 않도록 채널 전체가 보는 공개 알림으로 응답한다. 커맨드 등록 자체는
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

		languageRegistry.set(targetUser.getIdLong(), language);
		LOGGER.info("언어 설정: {} -> {} (실행한 사람: {})", targetUser.getName(), language, event.getUser().getName());

		if (CommandTargets.isSelf(event, targetUser)) {
			// 본인 설정의 확인 메시지는 방금 선택한 언어로 보여준다 (설정이 적용됐음을 바로 체감할 수 있음).
			event.reply(BotMessage.LANGUAGE_SET.text(language, language.displayName())).setEphemeral(true).queue();
			return;
		}

		event.reply(BotMessage.LANGUAGE_SET_FOR_OTHER.text(
						invokerLanguage,
						CommandTargets.invokerName(event),
						targetUser.getAsMention(),
						language.displayName()))
				.queue();
	}
}
