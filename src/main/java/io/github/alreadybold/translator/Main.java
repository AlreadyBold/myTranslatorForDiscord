package io.github.alreadybold.translator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

import io.github.cdimascio.dotenv.Dotenv;

public class Main {

	private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args) throws InterruptedException {
		Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
		String token = dotenv.get("DISCORD_BOT_TOKEN");

		if (token == null || token.isBlank()) {
			throw new IllegalStateException(".env 파일에 DISCORD_BOT_TOKEN을 설정해주세요.");
		}

		// JDA: 디스코드와의 REST API + Gateway(WebSocket) 통신을 감싸주는 래퍼 라이브러리
		// createLight: 캐시/인텐트를 아무것도 켜지 않은 최소 설정으로 시작 (필요한 것만 추후 추가)
		// build(): JDA 인스턴스를 만들고 백그라운드에서 비동기로 연결을 시작함 (연결 완료를 기다리지 않음)
		JDA jda = JDABuilder.createLight(token).build();
		// awaitReady(): 로그인 및 초기 연결(READY 이벤트 수신)이 끝날 때까지 현재 스레드를 대기시킴
		jda.awaitReady();

		LOGGER.info("로그인 완료: {}", jda.getSelfUser().getName());
	}
}
