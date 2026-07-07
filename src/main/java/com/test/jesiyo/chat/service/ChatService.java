package com.test.jesiyo.chat.service;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.jesiyo.chat.dto.ChatLogDto;
import com.test.jesiyo.member.dto.MemberDto;
import com.test.jesiyo.chat.repository.ChatDao;

@Service
public class ChatService {

	private final ChatDao chatDao;
	private final RedisTemplate<String, String> redisTemplate;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public ChatService(ChatDao chatDao,
			@Qualifier("chatRedisTemplate") RedisTemplate<String, String> redisTemplate) {
		this.chatDao = chatDao;
		this.redisTemplate = redisTemplate;
	}

	// Redis 채팅 내역 키 prefix
	private static final String CHAT_LOGS_KEY_PREFIX = "chat:logs:";
	// 방당 최대 보관 메시지 수
	private static final long MAX_CHAT_SIZE = 50;

	public MemberDto findBySeq(String seq) {
		return chatDao.findBySeq(seq);
	}

	public void addChat(ChatLogDto dto) {

		// 1. DB에 저장
		chatDao.addChat(dto);

		// 2. Redis 리스트 우측에 push (최신 메시지를 뒤에 쌓음)
		try {
			String key = CHAT_LOGS_KEY_PREFIX + dto.getChatRoomSeq();
			String json = objectMapper.writeValueAsString(dto);

			redisTemplate.opsForList().rightPush(key, json);

			// 50개 초과 시 오래된 메시지 제거 (0 ~ 49번 인덱스만 유지)
			redisTemplate.opsForList().trim(key, -MAX_CHAT_SIZE, -1);

			// TTL 24시간 (대화 없으면 자동 만료)
			redisTemplate.expire(key, 24, TimeUnit.HOURS);

		} catch (Exception e) {
			// Redis 실패해도 DB 저장은 이미 됐으므로 무시
			e.printStackTrace();
		}
	}

	public void triggerRecommend(ChatLogDto dto, WebSocketSession session) {
		// 채팅 추천 기능 AOP가 가로챔
	}

}
