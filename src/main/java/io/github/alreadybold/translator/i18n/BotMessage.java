package io.github.alreadybold.translator.i18n;

import java.util.EnumMap;
import java.util.Map;

/**
 * 봇이 유저에게 보여주는 문구를 언어별로 관리하는 enum.
 *
 * 화자의 언어(UserLanguageRegistry로 선택한 값)에 맞춰 응답을 보여주기 위한 것으로,
 * 로그(개발자용)는 대상이 아니고 계속 한국어로 남긴다.
 */
public enum BotMessage {

	NOT_IN_VOICE_CHANNEL(Map.of(
			Language.KO, "먼저 음성 채널에 들어가 있어야 해요.",
			Language.EN, "You need to join a voice channel first.",
			Language.CN, "请先加入语音频道。",
			Language.JA, "先にボイスチャンネルに参加してください。")),

	NO_CONNECT_PERMISSION(Map.of(
			Language.KO, "이 채널에 들어갈 권한이 없어요. (Connect 권한 필요)",
			Language.EN, "I don't have permission to join this channel. (Connect permission required)",
			Language.CN, "没有加入该频道的权限。(需要连接权限)",
			Language.JA, "このチャンネルに参加する権限がありません。(接続権限が必要です)")),

	JOINED_CHANNEL(Map.of(
			Language.KO, "`%s` 채널에 들어갈게요!",
			Language.EN, "Joining `%s`!",
			Language.CN, "即将加入 `%s` 频道!",
			Language.JA, "`%s` チャンネルに参加します!")),

	GUILD_ONLY(Map.of(
			Language.KO, "서버 안에서만 쓸 수 있는 명령어예요.",
			Language.EN, "This command only works inside a server.",
			Language.CN, "此命令只能在服务器内使用。",
			Language.JA, "このコマンドはサーバー内でのみ使用できます。")),

	NOT_CONNECTED(Map.of(
			Language.KO, "지금 음성 채널에 들어가 있지 않아요.",
			Language.EN, "I'm not in a voice channel right now.",
			Language.CN, "现在不在语音频道中。",
			Language.JA, "現在ボイスチャンネルに接続していません。")),

	LEFT_CHANNEL(Map.of(
			Language.KO, "음성 채널에서 나갈게요!",
			Language.EN, "Leaving the voice channel!",
			Language.CN, "即将离开语音频道!",
			Language.JA, "ボイスチャンネルから退出します!")),

	LANGUAGE_SET(Map.of(
			Language.KO, "말하는 언어를 `%s`로 설정했어요.",
			Language.EN, "Your spoken language is now set to `%s`.",
			Language.CN, "已将你所说的语言设置为 `%s`。",
			Language.JA, "話す言語を `%s` に設定しました。")),

	ALREADY_CONNECTED(Map.of(
			Language.KO, "이미 `%s` 채널에 들어가 있어요.",
			Language.EN, "I'm already in `%s`.",
			Language.CN, "已经在 `%s` 频道里了。",
			Language.JA, "すでに `%s` チャンネルにいます。")),

	OUTPUT_LANGUAGE_SET(Map.of(
			Language.KO, "내 발화를 `%s`로 번역해서 보여줄게요.",
			Language.EN, "I'll translate what you say into `%s`.",
			Language.CN, "会把你说的话翻译成 `%s`。",
			Language.JA, "あなたの発話を `%s` に翻訳して表示します。")),

	OUTPUT_LANGUAGE_SAME_AS_SPOKEN(Map.of(
			Language.KO, "말하는 언어(`%s`)와 같은 언어로는 번역 언어를 설정할 수 없어요.",
			Language.EN, "You can't set the translation language to the same as your spoken language (`%s`).",
			Language.CN, "翻译语言不能和你说的语言(`%s`)相同。",
			Language.JA, "話す言語(`%s`)と同じ言語には翻訳先を設定できません。")),

	JOIN_FAILED(Map.of(
			Language.KO, "음성 채널 접속에 실패했어요. 잠시 후 다시 시도해주세요.",
			Language.EN, "Failed to join the voice channel. Please try again in a moment.",
			Language.CN, "加入语音频道失败,请稍后再试。",
			Language.JA, "ボイスチャンネルへの接続に失敗しました。しばらくしてからもう一度お試しください。"));

	private final Map<Language, String> textByLanguage;

	BotMessage(Map<Language, String> textByLanguage) {
		this.textByLanguage = new EnumMap<>(textByLanguage);
	}

	/**
	 * 주어진 언어의 문구를 반환한다. args가 있으면 %s 같은 포맷 자리표시자에 채워 넣는다.
	 */
	public String text(Language language, Object... args) {
		String template = textByLanguage.get(language);
		return args.length == 0 ? template : String.format(template, args);
	}
}
