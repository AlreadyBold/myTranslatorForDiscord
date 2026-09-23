package io.github.alreadybold.translator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import io.github.alreadybold.translator.command.JoinCommandListener;
import io.github.cdimascio.dotenv.Dotenv;
import moe.kyokobot.libdave.NativeDaveFactory;
import moe.kyokobot.libdave.jda.LDJDADaveSessionFactory;

/**
 * 봇의 시작점(entry point).
 *
 * 이 클래스는 "봇을 어떻게 켤지"(토큰 로드, 인텐트 설정, 리스너 등록)만 책임지고,
 * 커맨드별 실제 동작은 io.github.alreadybold.translator.command 패키지의 리스너들에 위임한다.
 * 부팅 로직과 기능 로직이 한 클래스에 섞이면 커맨드가 늘어날수록 이 파일이 계속 커지기 때문에,
 * 처음부터 책임을 분리해둔다.
 */
public class Main {

	private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args) throws InterruptedException {
		// 토큰처럼 민감한 값은 코드에 하드코딩하지 않고 .env에서 읽어온다.
		// .env는 .gitignore로 막혀있으므로, 저장소를 공개해도 토큰이 노출되지 않는다.
		Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
		String token = dotenv.get("DISCORD_BOT_TOKEN");

		// 토큰이 없으면 이후 모든 동작이 의미가 없으므로, 여기서 바로 실패시켜 원인을 명확히 알린다.
		if (token == null || token.isBlank()) {
			throw new IllegalStateException(".env 파일에 DISCORD_BOT_TOKEN을 설정해주세요.");
		}

		// JDA: 디스코드와의 REST API + Gateway(WebSocket) 통신을 감싸주는 래퍼 라이브러리.
		// 이 객체 하나가 "디스코드에 연결된 우리 봇"을 나타낸다.
		//
		// createLight: 캐시/인텐트를 아무것도 켜지 않은 최소 설정으로 시작하는 빌더.
		// 인텐트는 "어떤 이벤트를 받을지"를 미리 선언하는 개념인데, 안 쓰는 인텐트를 켜두면
		// 불필요한 이벤트/메모리를 낭비하므로 실제로 쓰는 것만 명시적으로 추가한다.
		//
		// GUILD_VOICE_STATES: 유저가 어느 음성 채널에 있는지 알아야 하고, JDA가 음성 연결
		// (AudioManager) 기능을 쓰려면 이 인텐트를 요구한다. JoinCommandListener가 음성 채널
		// 접속 기능을 쓰기 때문에 필수로 추가했다.
		//
		// 인텐트는 "이벤트를 받을지"만 결정할 뿐, 받은 정보를 저장해서 나중에 조회 가능하게
		// 하려면 별도로 CacheFlag를 켜야 한다. createLight는 기본적으로 모든 캐시를 꺼두기
		// 때문에, VOICE_STATE 캐시를 켜지 않으면 member.getVoiceState()가 항상 null을 반환한다.
		//
		// addEventListeners: 슬래시 커맨드 등록/처리를 JoinCommandListener에 위임한다.
		//
		// setAudioModuleConfig(DAVE): 2026-03-01부터 디스코드는 DAVE(종단간 암호화)를
		// 지원하지 않는 음성 연결을 전부 거부한다. JDA 자체는 이 프로토콜의 "인터페이스"만
		// 제공하고 실제 구현은 없어서, libdave-jvm(NativeDaveFactory + LDJDADaveSessionFactory)을
		// 붙여줘야 한다. 이걸 안 하면 음성 채널에 접속하자마자 디스코드가 연결을 끊어버리고,
		// JDA가 자동 재접속을 반복하면서 "들어왔다 나갔다"를 반복하는 것처럼 보인다.
		//
		// build(): 위 설정으로 JDA 인스턴스를 생성하고, 백그라운드 스레드에서 비동기로
		// 디스코드 게이트웨이 연결을 시작한다. 이 메서드 자체는 연결 완료를 기다리지 않는다.
		JDA jda = JDABuilder.createLight(token, GatewayIntent.GUILD_VOICE_STATES)
				.enableCache(CacheFlag.VOICE_STATE)
				.addEventListeners(new JoinCommandListener())
				.setAudioModuleConfig(new AudioModuleConfig()
						.withDaveSessionFactory(new LDJDADaveSessionFactory(new NativeDaveFactory())))
				.build();

		// awaitReady(): build()가 비동기이기 때문에, 로그인/연결(READY 이벤트 수신)이 끝날 때까지
		// 현재 스레드를 대기시킨다. 이게 없으면 바로 아래 getSelfUser() 호출 시점에 아직
		// 정보가 채워지지 않아 오류가 날 수 있다.
		jda.awaitReady();

		LOGGER.info("로그인 완료: {}", jda.getSelfUser().getName());
	}
}
