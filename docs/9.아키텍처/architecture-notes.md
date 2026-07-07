# Jesiyo 발표용 아키텍처 노트

> 산출물: `docs/architecture-system.svg`, `docs/architecture-layered.svg`, `docs/architecture.html`

## 1. 한 줄 정의
**Spring Legacy 5.3.9 기반의 중고거래 + 일반 경매 + 라이브 경매 + 실시간 채팅 통합 플랫폼.** Tomcat 9 위에서 동작하며, Oracle Cloud Autonomous DB(전자지갑 인증)와 Redis(Lettuce/Redisson)를 데이터/동시성 계층으로 사용한다.

## 2. 3-Tier 큰 그림
1. **Client Layer** — Web Browser(JSP+JS), Postman, Toss Payments Widget, 개발자 도구(STS3·Git·JUnit4·Postman)
2. **WAS Layer** — Tomcat 9 + Spring Framework 5.3.9 (Filter Chain → DispatcherServlet/Spring Security/WebSocket → Service → MyBatis)
3. **Infra / External** — Oracle 19c (OCI Wallet, mTLS), Redis(127.0.0.1:6379), Toss Payments / Gmail SMTP / Kakao Local API, 로컬 파일 저장소

## 3. 설정 4파일 역할
| 파일 | 역할 |
|---|---|
| `web.xml` | Filter 순서: 인코딩(UTF-8) → HiddenHttpMethod(PUT/DELETE) → springSecurityFilterChain → DispatcherServlet "appServlet" |
| `root-context.xml` | HikariCP/Oracle Wallet, MyBatis(SqlSessionFactory/Template), `DataSourceTransactionManager`, AOP(`aspectj-autoproxy`), Gmail SMTP, **Controller 제외** 컴포넌트 스캔 |
| `servlet-context.xml` | DispatcherServlet 설정, JSP ViewResolver, MultipartResolver(10MB), 정적 자원(`/resources`, `/upload`, `/profile`), `<websocket:handlers>` `/bid-ws` |
| `security-context.xml` | form-login, BCrypt, `customUserDetailsService`/`customSuccessHandler`, `intercept-url` 권한 정책, CSRF off, `X-Frame-Options SAMEORIGIN` |

## 4. 9개 도메인 패키지
`auction`(일반 경매) · `liveauction`(라이브 경매) · `chat`(채팅) · `member`(회원/시큐리티) · `trade`(거래) · `directsale`(중고 직거래) · `payment`(결제) · `location`(동네) · `category`(카테고리)

각 도메인은 `controller` / `api`(REST) / `service` / `repository`(DAO) / `dto` / `config` / `handler` / `aop` 등으로 동일한 4-Tier를 유지.

## 5. 실시간 — 3개의 WebSocket 엔드포인트
| 경로 | 핸들러 | 용도 | 특징 |
|---|---|---|---|
| `/bid-ws` | `BidWebSocketHandler` | 일반 경매 입찰 알림 | 단일 List 세션, 전체 broadcast |
| `/liveAuction` | `LiveAuctionHandler` | 라이브 경매 송출 | 단일 List 세션, 전체 broadcast |
| `/chat/ws/{roomId}` | `ChatWebSocketHandler` | 방별 채팅 | `ChatRoomSessionManager`(`ConcurrentHashMap`), Redis Pub/Sub |

## 6. Redis — 3가지 용도
1. **캐시 (Lettuce)** — 라이브 경매 최고가/최근입찰 (`liveAuction:highestBid:{seq}`, TTL 2시간)
2. **분산 락 (Redisson)** — `lock:liveAuction:{seq}` `tryLock(3, 10, SECONDS)`로 다중 인스턴스 입찰 직렬화
3. **Pub/Sub** — 채팅 메시지를 `chat` 채널로 publish → 모든 서버의 `RedisSubscriber`가 받아 방별 세션에 broadcast (멀티서버 확장 대비)

## 7. 핵심 시퀀스

### 7.1 라이브 경매 입찰
1. Browser → WS send `{bidPrice}` → `LiveAuctionHandler` → `LiveAuctionService.placeLiveBid()`
2. **Fail-Fast** — Redis 캐시(최고가)로 즉시 거절 가능 (DB 안 감)
3. **분산 락 획득** — `redissonClient.getLock("lock:liveAuction:{seq}").tryLock(3,10,SECONDS)`
4. **Critical Section (`@Transactional`)** — DB로 최고가 이중 검증, 가용 예치금(`getAvailablePoint`) 검증, 이전 입찰자 `point_lock` 해제, 새 입찰자 `point_lock` 생성, `live_bid` insert
5. 트랜잭션 성공 후 Redis 캐시 갱신 (`opsForValue().set(... TTL 2h)`)
6. 결과를 핸들러에게 반환 → 모든 세션에 broadcast
7. 경매 종료 시 캐시 삭제 + 정산(`completeLiveAuction`): `point_lock` 상태 변경, 회원 포인트 차감

### 7.2 채팅 (Pub/Sub + AOP 추천)
1. Browser → WS open `/chat/ws/{roomId}` → `ChatWebSocketHandler.afterConnectionEstablished` → `ChatRoomSessionManager.addSession`
2. 메시지 수신 → `ChatService.addChat()`(DB 저장) → `RedisPublisher.publish("chat", json)`
3. `RedisMessageListenerContainer` → `RedisSubscriber.onMessage()` → 같은 방 세션에 broadcast
4. `ChatService.triggerRecommend(dto, session)` 호출 후 → AOP `ChatRecommendAspect`가 `@AfterReturning`으로 가로채서:
   - `CategoryDao.findMatchedCategories(content)`로 키워드 매칭
   - 매칭된 카테고리의 경매/중고거래 최신 2건씩 조회
   - **본인 세션에만** RECOMMEND 메시지 push

### 7.3 결제(Toss)
1. `POST /api/payments/init` — `PaymentService.add(memberSeq, amount)` → `payment` row 생성(READY) → `chargeId="PAY-"+seq`로 `updateChargeId`
2. 브라우저가 Toss Payments Widget으로 결제
3. 성공 리다이렉트 `GET /payments/success?paymentKey&orderId&amount`
4. `PaymentService.confirm()` — `RestTemplate`으로 `https://api.tosspayments.com/v1/payments/confirm` Basic Auth 호출
5. `updateStatus("DONE")` + `addPointByOrderId()` (포인트 지급, 중복 지급 시 RuntimeException으로 차단)

## 8. 보안
- Spring Security 5.3.9 form-login + `BCryptPasswordEncoder`
- `CustomUserDetailsService` → `CustomUser(MemberDto)` 반환 → `CustomSuccessHandler`가 세션에 `user` 저장 (이후 컨트롤러는 모두 `(MemberDto) session.getAttribute("user")` 사용)
- intercept-url: `/member/login`·`/member/regist`·`/index`·`/resources/**` permitAll, `/member/mypage` ROLE_USER, `/direct-sales/*/edit`·`POST /direct-sales` isAuthenticated
- CSRF off (REST/SPA-style 사용), iframe SAMEORIGIN
- **Oracle Cloud Wallet(mTLS)** 로 DB 인증 — `oraclepki/osdt_core/osdt_cert` PKCS#12 지갑

## 9. 트랜잭션 / 동시성 정리
- `@Transactional(rollbackFor=Exception.class)` — 입찰/정산처럼 사용자 돈이 묶이는 핵심 로직에 적용
- `point_lock` 테이블로 입찰 예치금 잠금 (status 0=잠김, 1=해제, 2=정산완료)
- Redisson 분산 락 → 동일 경매 동시 입찰 직렬화
- Hikari `connectionTestQuery=SELECT 1 FROM DUAL` + `keepaliveTime=5분`으로 Wallet 끊김 방지

## 10. 빌드 / 배포 / 협업
- Maven war 패키징 → Tomcat 9에 외부 배포 (Java 11)
- 개발 IDE: STS3 (Eclipse Spring), 형상관리: Git/GitHub
- 테스트: spring-test + JUnit4 (`DBTest`, `TradeTest`, `PaymentTest`, `TradeLocationTest`, `DirectSaleTest`)
- API 검증: Postman
- 로깅: SLF4J + Logback + log4jdbc-log4j2 (SQL 가독성)

---

## 발표할 때 흐름 추천
1. **첫 슬라이드: 시스템 전체도 (architecture-system.svg)** — "사용자가 들어오면 Tomcat에서 Filter → DispatcherServlet → 그리고 3가지 데이터 통로로 갈라집니다 (Oracle/Redis/외부 API)" 큰 흐름 설명
2. **두 번째 슬라이드: 계층 + 시퀀스 (architecture-layered.svg)** — 9개 도메인이 동일한 4-Tier로 일관되게 만들어졌다는 점 + 라이브 경매 입찰의 Fail-Fast→분산 락→DB→캐시 흐름과 채팅 Pub/Sub 흐름을 짧게 설명
3. **기술 스택 표**로 "왜 이 기술을 선택했는가"를 한 줄씩 답변
4. **마무리** — 가장 자랑할 포인트(라이브 경매 동시성 / 채팅 멀티서버화)로 마침

