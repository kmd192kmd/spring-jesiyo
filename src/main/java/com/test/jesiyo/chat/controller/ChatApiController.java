package com.test.jesiyo.chat.controller;

import java.util.List;

import javax.servlet.http.HttpSession;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.test.jesiyo.chat.dto.ChatLogDto;
import com.test.jesiyo.chat.dto.ChatRoomDto;
import com.test.jesiyo.chat.dto.EnterRoomDto;
import com.test.jesiyo.chat.handler.ChatWebSocketHandler;
import com.test.jesiyo.chat.service.ChatRoomService;
import com.test.jesiyo.chat.service.ChatService;
import com.test.jesiyo.member.dto.MemberDto;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/chat")  // 공통 prefix를 클래스 레벨에 적용
@RequiredArgsConstructor
public class ChatApiController {
	
	private final ChatRoomService chatRoomService;
	private final ChatService chatService;
	private final ChatWebSocketHandler chatWebSocketHandler; // 

	
	// 채팅방 목록 조회 - 세션에서 꺼내기 
	@GetMapping("/rooms")
	public ResponseEntity<List<ChatRoomDto>> getRooms(HttpSession session) {
        MemberDto user = (MemberDto) session.getAttribute("user");
        String seq = user.getSeq(); // 혹은 auth가 UserDto면 getUserSeq() 등
        return ResponseEntity.ok(chatRoomService.getChatRoomList(seq));
    }
	
	// 그 채팅방 가져오기
	@GetMapping("/rooms/{seq}")
	public ChatRoomDto getChatRoom(@PathVariable int seq) {
	    return chatRoomService.getChatRoom(seq);
	}
	
	// 채티방 채팅 내역 가져오기
	@GetMapping("/rooms/logs/{seq}")
	public List<ChatLogDto> getChatlogs(@PathVariable int seq) {
	    return chatRoomService.getChatlogs(seq);
	}
	
	// 채팅방 등록
	@PostMapping("/rooms")
	public ResponseEntity<?> add(@RequestBody ChatRoomDto dto){
		
		try {
			chatRoomService.add(dto); // 실패하면 예외 던짐
	        return ResponseEntity.ok("ok");
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		}
	
	}
	
	// 채팅방에서 자기 seq 가져오기
	@GetMapping("/rooms/{chatRoomSeq}/member/{memberSeq}")
	public int getChatRoomSeq(@PathVariable String chatRoomSeq, @PathVariable String memberSeq) {
		
		ChatLogDto dto = new ChatLogDto();
	    dto.setChatRoomSeq(chatRoomSeq);
	    dto.setChatMemberSeq(memberSeq);
	    return chatRoomService.getChatRoomSeq(dto);
	}
	
	// 채팅방 참여자 비동기 처리
	@PostMapping("/enter")
	@ResponseBody
	public int enterRoom(@RequestBody EnterRoomDto dto) throws Exception {
	    
	    int roomId = chatRoomService.enterRoom(dto);
	    
	    //  해당 방에 있는 모든 ws 세션에 브로드캐스트
	    String msg = "{\"code\":\"REFRESH_MEMBERS\"}";
	    for (WebSocketSession s : chatWebSocketHandler.getSessionManager().getSessions(String.valueOf(roomId))) {
	        if (s.isOpen()) {
	            s.sendMessage(new TextMessage(msg));
	        }
	    }
	    
	    return roomId;
	}
	
	
	// 채팅방 참여자 리스트
	@GetMapping("/room/{roomId}/members")
	@ResponseBody
	public List<MemberDto> getRoomMembers(@PathVariable String roomId) {
	    
	    List<MemberDto> members = chatRoomService.getRoomMembers(roomId);
	    
	    return members;
	}
	
}
