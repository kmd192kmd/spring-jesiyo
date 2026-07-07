package com.test.jesiyo.chat.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.jesiyo.chat.dto.ChatLogDto;
import com.test.jesiyo.chat.dto.ChatRoomDto;
import com.test.jesiyo.chat.dto.EnterRoomDto;
import com.test.jesiyo.chat.repository.ChatRoomDao;
import com.test.jesiyo.member.dto.MemberDto;

@Service
public class ChatRoomService {

	private final ChatRoomDao chatRoomDao;
	private final RedisTemplate<String, String> redisTemplate;
	private final ObjectMapper objectMapper = new ObjectMapper();

	private static final String CHAT_LOGS_KEY_PREFIX = "chat:logs:";

	public ChatRoomService(ChatRoomDao chatRoomDao,
			@Qualifier("chatRedisTemplate") RedisTemplate<String, String> redisTemplate) {
		this.chatRoomDao = chatRoomDao;
		this.redisTemplate = redisTemplate;
	}

	@Transactional
	public void add(ChatRoomDto dto) {
		
		// 방코드 추가
		String code = createCode();;
		
		// 중복체크
		while(checkCode(code) > 0) {
			code = createCode();
		}
		
		dto.setCode(code);
		
		int resultRoom = chatRoomDao.add(dto);
        
        if (resultRoom == 0) {
            throw new RuntimeException("채팅방 생성 실패");
        }
        
        // 채팅방 생성에 성공했다면 그 방장의 정보를 채팅방 인원에 포함
        int resultMember = chatRoomDao.addMember(dto);
        
		
	}
	
	public String createCode() {
		
		String code = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
	    SecureRandom random = new SecureRandom();
	    StringBuilder sb = new StringBuilder(8);

	    for (int i = 0; i < 8; i++) {
	        // chars 문자열에서 랜덤하게 하나씩 선택
	        int randomIndex = random.nextInt(code.length());
	        sb.append(code.charAt(randomIndex));
	    }
	    
	    return sb.toString();
	}
	
	public int checkCode(String code) {
		
		return chatRoomDao.checkCode(code);
	}
	
	
	public List<ChatRoomDto> getChatRoomList(String seq) {
		
		return chatRoomDao.getChatRoomList(seq);
	}

	public ChatRoomDto getChatRoom(int seq) {
		return chatRoomDao.getChatRoom(seq);
	}

	public int getChatRoomSeq(ChatLogDto dto) {
		return chatRoomDao.getChatRoomSeq(dto);
	}

	public List<ChatLogDto> getChatlogs(int seq) {

		String key = CHAT_LOGS_KEY_PREFIX + seq;

		// 1. Redis 캐시 먼저 확인
		List<String> cached = redisTemplate.opsForList().range(key, 0, -1);

		if (cached != null && !cached.isEmpty()) {
			try {
				List<ChatLogDto> result = new ArrayList<>();
				for (String json : cached) {
					result.add(objectMapper.readValue(json, ChatLogDto.class));
				}
				System.out.println("[Redis] 채팅 내역 캐시 HIT - roomId: " + seq);
				return result;
			} catch (Exception e) {
				// 역직렬화 실패 시 DB로 fallback
				e.printStackTrace();
			}
		}

		// 2. Redis에 없으면 DB 조회
		System.out.println("[Redis] 채팅 내역 캐시 MISS - roomId: " + seq + " → DB 조회");
		List<ChatLogDto> logs = chatRoomDao.getChatlogs(seq);

		// 3. DB 결과를 Redis에 저장
		try {
			for (ChatLogDto log : logs) {
				redisTemplate.opsForList().rightPush(key, objectMapper.writeValueAsString(log));
			}
			redisTemplate.expire(key, 24, TimeUnit.HOURS);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return logs;
	}

	@Transactional
	public int enterRoom(EnterRoomDto dto) {
		
		String roomSeq = checkRoomCode(dto); 
		
		dto.setRoomSeq(roomSeq);
		
		int result = chatRoomDao.enterRoom(dto);
		if(result>0) {
			return chatRoomDao.enterSeq();
		} else {
			return 0;
		}
		
	}

	private String checkRoomCode(EnterRoomDto dto) {
		return chatRoomDao.checkRoomCode(dto);
	}

	public List<MemberDto> getRoomMembers(String roomId) {
		return chatRoomDao.getRoomMembers(roomId);
	}
	
}
