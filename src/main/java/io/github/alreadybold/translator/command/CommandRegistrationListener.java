package io.github.alreadybold.translator.command;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/**
 * 슬래시 커맨드 목록을 등록하는 전용 리스너.
 *
 * guild.updateCommands()는 호출할 때마다 그 길드의 커맨드 목록 전체를 덮어쓴다.
 * 그래서 커맨드마다 리스너가 각자 updateCommands()를 부르면 서로의 등록 내용을
 * 지워버리게 된다. 등록 자체는 이 클래스 한 곳에서만 하고, 각 커맨드의 실행 로직
 * (JoinCommandListener, LeaveCommandListener 등)은 별도 클래스로 분리한다.
 */
public class CommandRegistrationListener extends ListenerAdapter {

	@Override
	public void onReady(ReadyEvent event) {
		for (Guild guild : event.getJDA().getGuilds()) {
			guild.updateCommands()
					.addCommands(
							Commands.slash("join", "봇을 내가 있는 음성 채널로 불러옵니다"),
							Commands.slash("leave", "봇을 음성 채널에서 내보냅니다"))
					.queue();
		}
	}
}
