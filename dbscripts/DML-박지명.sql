select * from member;

-- 회원 더미
INSERT INTO MEMBER (SEQ, NAME, POINT, ID, PW, ADDRESS, BIRTH, NICKNAME, PERMISSION, REGDATE, PW_TOKEN, TOKEN_EXPIRY, STATUS, EMAIL_ADDRESS, LOCATION_SEQ) VALUES (member_seq.nextval, '엄준식', default, 'um1234', 'java1234', '서울시 강남구 대치동', '2000-02-03', '어떻게사람이름이엄준식', 0, DEFAULT, null, null, DEFAULT, 'um1234@test.com', null);

INSERT INTO MEMBER (SEQ, NAME, POINT, ID, PW, ADDRESS, BIRTH, NICKNAME, PERMISSION, REGDATE, PW_TOKEN, TOKEN_EXPIRY, STATUS, EMAIL_ADDRESS, LOCATION_SEQ) VALUES (member_seq.nextval, '윤진석', default, 'yun1234', 'java1234', '서울시 강남구 역삼동', '1995-04-14', '윤가놈', 0, DEFAULT, null, null, DEFAULT, 'yun1234@test.com', null);

commit;

SELECT * from CATEGORY;

-- 채팅방 더미
INSERT INTO CHAT_ROOM (SEQ, TITLE, STATUS, MAX_MEMBER_CNT, CURRENT_MEMBER_CNT, CATEGORY_SEQ, MEMBER_SEQ, CODE) VALUES (CHAT_ROOM_SEQ.nextval, '윤진석의 전자기기 채팅방', DEFAULT, 50, default, 1, 4, 'aI32IKdw');

INSERT INTO CHAT_ROOM (SEQ, TITLE, STATUS, MAX_MEMBER_CNT, CURRENT_MEMBER_CNT, CATEGORY_SEQ, MEMBER_SEQ, CODE) VALUES (CHAT_ROOM_SEQ.nextval, '윤진석이 그냥 만든 채팅방', DEFAULT, 50, default, 2, 4, 'aI32Ivcw');

select * from CHAT_member where MEMBER_SEQ=3;

select * from chat_room cr
         join member m on cr.MEMBER_SEQ = m.seq
         where MEMBER_SEQ = 3 order by seq

select * from chat_room cr where cr.SEQ in (select cm.chat_room_seq from CHAT_member cm where MEMBER_SEQ=3);


select cr.* from chat_member cm
         where cm.member_seq= 3;

commit;
-- 채팅방 참여자 더미
insert into CHAT_MEMBER (seq, LAST_READ_MESSAGE, ALARM, STATUS, MEMBER_SEQ, CHAT_ROOM_SEQ) VALUES (CHAT_MEMBER_SEQ.nextval, null, DEFAULT, DEFAULT, 4, 2);


select * from CHAT_MEMBER;



-- 채팅방 채팅 내역 더미
insert into CHAT_LOG (SEQ, CONTENT, REGDATE, TYPE, CHAT_MEMBER_SEQ, CHAT_ROOM_SEQ) values (CHAT_LOG_SEQ.nextval, '안녕하세요2', default, 1, 11, 18);

insert into CHAT_LOG (SEQ, CONTENT, REGDATE, TYPE, CHAT_MEMBER_SEQ, CHAT_ROOM_SEQ) values (CHAT_LOG_SEQ.nextval, '안녕하세요 엄준식입니다.2', default, 1, 11, 18);

insert into CHAT_LOG (SEQ, CONTENT, REGDATE, TYPE, CHAT_MEMBER_SEQ, CHAT_ROOM_SEQ) values (CHAT_LOG_SEQ.nextval, '채팅방 확인용 내역입니다.2', default, 1, 11, 18);

select * from chat_log;

select seq from chat_member where member_seq = 3 and CHAT_ROOM_SEQ = 17;

select * from chat_room;

SELECT cl.*, m.NICKNAME
FROM chat_log cl
JOIN member m ON cl.CHAT_MEMBER_SEQ = m.seq
WHERE cl.chat_room_seq = 17
ORDER BY cl.seq ASC;

select * from chat_log where chat_room_seq = 17 order by seq asc;

select cl.*, m.NICKNAME from chat_log cl
            join chat_member cm on cl.CHAT_MEMBER_SEQ = cm.SEQ
            join member m on m.seq = cm.MEMBER_SEQ
where cl.chat_room_seq = 17 order by cl.seq asc;



select cm.*, m.NICKNAME from chat_member cm
         join member m on m.seq = cm.MEMBER_SEQ
         where chat_room_seq = 2;

select * from CATEGORY;

SELECT
        SEQ,
        NAME
FROM CATEGORY
WHERE INSTR('아 헤드폰 사고싶다.', NAME) > 0;


SELECT * FROM auction
WHERE category_seq = 1 AND status = 0
ORDER BY seq DESC
OFFSET 0 ROWS FETCH NEXT 2 ROWS ONLY;

select * from CATEGORY;

SELECT * FROM direct_sale
		WHERE category_seq = 46 AND status = '판매중'
		ORDER BY seq DESC
		OFFSET 0 ROWS FETCH NEXT 2 ROWS ONLY;



SELECT SESSIONTIMEZONE, DBTIMEZONE FROM DUAL;
SELECT SYSDATE FROM DUAL;
SELECT SYSTIMESTAMP FROM DUAL;
SELECT CURRENT_DATE FROM DUAL;
SELECT CURRENT_TIMESTAMP FROM DUAL;



ALTER SESSION SET TIME_ZONE = 'Asia/Seoul';
SELECT SESSIONTIMEZONE FROM DUAL;
SELECT CURRENT_DATE FROM DUAL;
SELECT CURRENT_TIMESTAMP FROM DUAL;



