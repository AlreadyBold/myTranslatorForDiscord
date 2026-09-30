package io.github.alreadybold.translator.command;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
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
 *
 * ReadyEvent와 GuildJoinEvent 둘 다 들어야 한다 - ReadyEvent는 봇이 게이트웨이에
 * 최초로 연결될 때 그 시점에 이미 들어가 있던 길드들에만 한 번 발생한다. 봇이 이미
 * 켜져 있는 상태에서 새 서버에 초대되면 ReadyEvent는 다시 안 일어나고 대신
 * GuildJoinEvent가 발생하는데, 이걸 안 들으면 그 서버에는 커맨드가 영영 등록되지
 * 않는다.
 */
public class CommandRegistrationListener extends ListenerAdapter {

	public static final String JOIN_COMMAND = "join";
	public static final String LEAVE_COMMAND = "leave";
	public static final String SET_LANGUAGE_COMMAND = "setlang";
	public static final String SET_OUTPUT_LANGUAGE_COMMAND = "setoutputlang";
	public static final String STOP_TRANSLATION_COMMAND = "stoptranslate";
	public static final String LANGUAGE_OPTION = "language";
	public static final String USER_OPTION = "user";

	@Override
	public void onReady(ReadyEvent event) {
		for (Guild guild : event.getJDA().getGuilds()) {
			registerCommands(guild);
		}
	}

	@Override
	public void onGuildJoin(GuildJoinEvent event) {
		registerCommands(event.getGuild());
	}

	private void registerCommands(Guild guild) {
		guild.updateCommands()
				.addCommands(
						Commands.slash(JOIN_COMMAND, "봇을 내가 있는 음성 채널로 불러옵니다"),
						Commands.slash(LEAVE_COMMAND, "봇을 음성 채널에서 내보냅니다"),
						Commands.slash(SET_LANGUAGE_COMMAND, "말할 언어를 설정하고 번역을 켭니다")
								.addOptions(buildLanguageOption("말할 언어"), buildTargetUserOption()),
						Commands.slash(SET_OUTPUT_LANGUAGE_COMMAND, "발화를 번역해서 보여줄 언어를 설정합니다")
								.addOptions(buildLanguageOption("번역해서 보여줄 언어"), buildTargetUserOption()),
						Commands.slash(STOP_TRANSLATION_COMMAND, "번역을 끕니다")
								.addOptions(buildTargetUserOption()))
				.queue();
	}

	/**
	 * 설정 대상 유저 옵션. 선택(required=false)이라 비우면 커맨드를 실행한 본인이 대상이다.
	 * 디스코드는 필수 옵션이 선택 옵션보다 앞에 와야 해서, 언어 옵션 뒤에 붙인다.
	 */
	private OptionData buildTargetUserOption() {
		return new OptionData(
				OptionType.USER, USER_OPTION, "대상 (비우면 나 자신, 같은 음성 채널에 있는 사람만 가능)", false);
	}

	/**
	 * 언어 선택 옵션. 선택지(choice)를 등록해두면 디스코드가 그 목록 외의 값을 아예 못 보내게
	 * 막아주기 때문에, 처리하는 쪽에서 잘못된 문자열을 검증할 필요가 없어진다.
	 * 선택지 value로 enum 이름을 그대로 쓰면 Language.valueOf()로 바로 되돌릴 수 있다.
	 *
	 * /setlang(화자가 말하는 언어)과 /setoutputlang(그 발화를 번역해서 보여줄 언어) 둘 다
	 * 언어 하나를 고르는 건 같아서 옵션 구조는 공유하고, 설명 문구만 커맨드 의미에 맞게
	 * 다르게 넣는다.
	 */
	private OptionData buildLanguageOption(String description) {
		OptionData languageOption = new OptionData(
				OptionType.STRING, LANGUAGE_OPTION, description, true);

		for (Language language : Language.values()) {
			languageOption.addChoice(language.displayName(), language.name());
		}

		return languageOption;
	}
}
