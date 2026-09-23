package io.github.alreadybold.translator.command;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import io.github.alreadybold.translator.i18n.Language;

/**
 * 슬래시 커맨드 목록을 등록하는 전용 리스너.
 *
 * guild.updateCommands()는 호출할 때마다 그 길드의 커맨드 목록 전체를 덮어쓴다.
 * 그래서 커맨드마다 리스너가 각자 updateCommands()를 부르면 서로의 등록 내용을
 * 지워버리게 된다. 등록 자체는 이 클래스 한 곳에서만 하고, 각 커맨드의 실행 로직
 * (JoinCommandListener, LeaveCommandListener 등)은 별도 클래스로 분리한다.
 *
 * 커맨드/옵션 이름을 상수로 두는 이유는, 등록하는 쪽과 처리하는 쪽이 같은 문자열을 쓰도록
 * 강제해서 오타로 커맨드가 조용히 동작하지 않는 상황을 막기 위함이다.
 */
public class CommandRegistrationListener extends ListenerAdapter {

	public static final String JOIN_COMMAND = "join";
	public static final String LEAVE_COMMAND = "leave";
	public static final String SET_LANGUAGE_COMMAND = "setlang";
	public static final String LANGUAGE_OPTION = "language";

	@Override
	public void onReady(ReadyEvent event) {
		for (Guild guild : event.getJDA().getGuilds()) {
			guild.updateCommands()
					.addCommands(
							Commands.slash(JOIN_COMMAND, "봇을 내가 있는 음성 채널로 불러옵니다"),
							Commands.slash(LEAVE_COMMAND, "봇을 음성 채널에서 내보냅니다"),
							Commands.slash(SET_LANGUAGE_COMMAND, "내가 말할 언어를 설정합니다")
									.addOptions(buildLanguageOption()))
					.queue();
		}
	}

	/**
	 * 언어 선택 옵션. 선택지(choice)를 등록해두면 디스코드가 그 목록 외의 값을 아예 못 보내게
	 * 막아주기 때문에, 처리하는 쪽에서 잘못된 문자열을 검증할 필요가 없어진다.
	 * 선택지 value로 enum 이름을 그대로 쓰면 Language.valueOf()로 바로 되돌릴 수 있다.
	 */
	private OptionData buildLanguageOption() {
		OptionData languageOption = new OptionData(
				OptionType.STRING, LANGUAGE_OPTION, "말할 언어", true);

		for (Language language : Language.values()) {
			languageOption.addChoice(language.displayName(), language.name());
		}

		return languageOption;
	}
}
