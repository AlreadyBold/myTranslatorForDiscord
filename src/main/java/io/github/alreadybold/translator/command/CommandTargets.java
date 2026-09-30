package io.github.alreadybold.translator.command;

import java.util.Optional;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

/**
 * 커맨드의 선택 옵션(user)으로 "누구의 번역 설정을 바꿀지" 고르는 공통 로직.
 *
 * 옵션을 비우거나 자기 자신을 고르면 본인이 대상이다. 다른 사람을 고르면 그 사람의 말이
 * 본인 동의 없이 받아적혀 채널에 올라갈 수 있으므로, 커맨드를 실행한 사람과 같은 음성
 * 채널에 있는 사람만 지정할 수 있게 제한한다.
 */
final class CommandTargets {

	private CommandTargets() {
	}

	/**
	 * 설정 대상 유저. 다른 사람을 골랐는데 같은 음성 채널에 없으면 empty.
	 *
	 * 대상의 음성 상태는 guild 멤버 캐시에서 찾는다 - MemberCachePolicy.VOICE라서
	 * 음성 채널에 없는 사람은 캐시에 없고, 그 경우도 "같은 채널이 아님"으로 처리된다.
	 */
	static Optional<User> resolve(SlashCommandInteractionEvent event) {
		User invoker = event.getUser();
		OptionMapping userOption = event.getOption(CommandRegistrationListener.USER_OPTION);

		if (userOption == null || userOption.getAsUser().getIdLong() == invoker.getIdLong()) {
			return Optional.of(invoker);
		}

		User target = userOption.getAsUser();
		Guild guild = event.getGuild();

		if (guild == null) {
			return Optional.empty();
		}

		AudioChannel invokerChannel = voiceChannelOf(event.getMember());
		AudioChannel targetChannel = voiceChannelOf(guild.getMemberById(target.getIdLong()));

		if (invokerChannel == null || targetChannel == null
				|| invokerChannel.getIdLong() != targetChannel.getIdLong()) {
			return Optional.empty();
		}

		return Optional.of(target);
	}

	static boolean isSelf(SlashCommandInteractionEvent event, User target) {
		return event.getUser().getIdLong() == target.getIdLong();
	}

	/**
	 * 공개 알림에 쓸 커맨드 실행자 이름. 멘션 대신 서버 별명을 써서, 본인이 방금 실행한
	 * 커맨드 응답으로 본인에게 알림이 또 가지 않게 한다.
	 */
	static String invokerName(SlashCommandInteractionEvent event) {
		Member member = event.getMember();
		return member == null ? event.getUser().getName() : member.getEffectiveName();
	}

	private static AudioChannel voiceChannelOf(Member member) {
		if (member == null) {
			return null;
		}

		GuildVoiceState voiceState = member.getVoiceState();
		return voiceState == null ? null : voiceState.getChannel();
	}
}
