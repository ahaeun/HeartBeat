# HeartBeat

브라우저 종료 + 무활동 기반 자동 로그아웃을 구현한 Spring Boot 학습용 프로젝트.

## 기능 요약

- 단일 테스트 계정(`test` / `qwer`)으로 로그인
- 로그인 성공 시 사용자 ID가 노출되는 홈 페이지 진입
- **브라우저(또는 모든 탭)를 닫으면 자동 로그아웃**
- **사용자가 30분간 아무 활동이 없으면 자동 로그아웃**
- 페이지 간 이동이나 잠시 외부 사이트를 다녀와도 30분 이내라면 로그인 유지
- 홈 페이지 상단에서 "활동 감지 / 대기 중 / 세션 만료" 상태를 실시간 표시

## 기술 스택

- Java 21
- Spring Boot 4.0.6 (WebMVC, WAR 패키징)
- Thymeleaf
- Gradle

## 프로젝트 구조

```
src/main/java/com/example/haeun/
├── HeartbeatApplication.java
├── ServletInitializer.java
├── controller/
│   └── AuthController.java          # 로그인/로그아웃/홈/하트비트 엔드포인트
├── service/
│   ├── AuthService.java             # 인증 서비스 인터페이스
│   └── impl/
│       └── AuthServiceImpl.java     # 인메모리 세션 저장 구현
└── model/
    ├── User.java                    # 사용자 계정 모델 (record)
    ├── LoginRequest.java            # 로그인 폼 DTO
    └── Session.java                 # 서버 세션 정보 (sessionId, username, lastActivity)

src/main/resources/
├── application.properties
└── templates/
    ├── login.html                   # 로그인 페이지
    └── home.html                    # 로그인 후 홈 (활동 감지 + 핑 스크립트 포함)
```

## 페이지

| 경로 | 메서드 | 설명 |
|------|--------|------|
| `/` | GET | `/login` 으로 리다이렉트 |
| `/login` | GET | 로그인 폼 (이미 로그인 시 `/home`으로 이동) |
| `/login` | POST | 로그인 처리. 성공 시 `SESSION_ID` 쿠키 발급 후 `/home` |
| `/home` | GET | 로그인된 사용자 ID 노출. 미인증 시 `/login` |
| `/api/heartbeat` | POST | 활동 핑 (`?active=true|false`). JSON `{alive: boolean}` 반환 |
| `/logout` | POST | 세션 + 쿠키 삭제 후 `/login` |

## 동작 원리

### 1) 브라우저 종료 = 로그아웃

`SESSION_ID` 쿠키를 발급할 때 `maxAge`를 지정하지 않습니다.

```java
Cookie cookie = new Cookie(SESSION_COOKIE, sessionId);
cookie.setPath("/");
cookie.setHttpOnly(true);
response.addCookie(cookie);
```

`maxAge`를 안 주면 **세션 쿠키**가 되어 브라우저가 완전히 종료되는 순간 자동으로 삭제됩니다. 다시 브라우저를 열면 쿠키가 없으므로 로그인 페이지로 이동합니다.

> 서버 메모리에는 해당 세션이 잠깐 남아있을 수 있지만, 쿠키가 사라졌으므로 누구도 접근할 수 없는 잔여물이며 마지막 활동으로부터 30분이 지나면 다음 요청 시 자동 정리됩니다.

### 2) 30분 무활동 = 로그아웃

서버는 세션별로 `lastActivity`(마지막 활동 시각)를 기록하고, 매 요청 시 30분 초과 여부를 검사합니다.

**서버 측 ([`AuthServiceImpl`](src/main/java/com/example/haeun/service/impl/AuthServiceImpl.java))**

```java
private static final long IDLE_TIMEOUT_MS = 30L * 60L * 1000L;

private Session validSession(String sessionId) {
    Session session = sessions.get(sessionId);
    if (session == null) return null;
    if (System.currentTimeMillis() - session.getLastActivity() > IDLE_TIMEOUT_MS) {
        sessions.remove(sessionId);
        return null;
    }
    return session;
}
```

API는 두 가지로 분리되어 있습니다:
- `recordActivity(sessionId)` → 활동이 있었을 때 호출. `lastActivity`를 현재 시각으로 갱신
- `isAlive(sessionId)` → 단순 상태 확인. 갱신 없이 30분 초과 여부만 체크

### 3) 클라이언트의 활동 감지 + 핑 ([`home.html`](src/main/resources/templates/home.html))

`mousemove`, `mousedown`, `keydown`, `scroll`, `touchstart` 5가지 이벤트를 감지합니다.

```javascript
const activityEvents = ['mousemove', 'mousedown', 'keydown', 'scroll', 'touchstart'];
activityEvents.forEach(evt => {
    document.addEventListener(evt, () => {
        activeSinceLastPing = true;
        showActive();
        scheduleIdle();
    }, { passive: true });
});
```

이벤트가 발생하면 다음 세 가지가 일어납니다.

1. **`activeSinceLastPing = true`** — 다음 서버 핑에 실어 보낼 플래그를 켭니다.
2. **`showActive()`** — UI 상태를 즉시 "활동 감지 (시각)"으로 갱신.
3. **`scheduleIdle()`** — 1.5초 뒤 "대기 중"으로 전환하는 디바운스 타이머를 (재)예약. 활동이 계속되면 매번 리셋되고, 멈추면 1.5초 뒤 단 한 번 발화합니다.

idle 검사를 폴링(`setInterval`로 매 N ms마다 시간 차이 확인) 대신 디바운스 방식으로 처리하므로, 사용자가 가만히 있는 동안에는 idle 검사용 타이머가 전혀 돌지 않습니다. 마지막 활동으로부터 1.5초 뒤에 콜백이 단 한 번 발화되고 그대로 종료됩니다. (단, 서버 핑용 `setInterval`은 세션 만료 감지를 위해 10초 주기로 별개로 계속 동작합니다.)

### 4) 서버 핑 (10초 주기)

```javascript
const PING_INTERVAL_MS = 10_000;

async function ping() {
    const active = activeSinceLastPing;
    activeSinceLastPing = false;
    const res = await fetch('/api/heartbeat?active=' + active, { method: 'POST' });
    const data = await res.json();
    if (!data.alive) {
        // 세션 만료 → /login 으로 리다이렉트
    }
}

setInterval(ping, PING_INTERVAL_MS);
```

- 10초마다 한 번씩 서버에 핑을 보내며, 그 사이에 한 번이라도 활동이 있었으면 `active=true`로 전송
- `active=true` 도착 → 서버가 `lastActivity` 갱신 → 30분 카운터 리셋
- `active=false` 도착 → 갱신 없음 → 30분 카운터 계속 흐름
- 30분간 `active=true`가 한 번도 안 오면 서버가 세션 삭제 → 다음 핑의 응답이 `alive=false` → 로그인 페이지로 리다이렉트

서버 부하를 줄이기 위해 활동마다 즉시 보내지 않고 10초 단위로 묶어서 보냅니다(throttle). 30분 타임아웃 기준에서는 무시 가능한 오차입니다.

### 5) 페이지 이동 시 로그아웃되지 않는 이유

- 같은 탭에서 다른 페이지로 잠깐 갔다가 30분 이내에 돌아오면 → 쿠키 살아있음 + 서버 세션 살아있음 → 로그인 유지
- 다른 탭이나 백그라운드 탭에 있어도 home 탭이 살아있는 한 핑이 계속 감
- 브라우저 자체가 닫혀야 쿠키가 사라지므로 그때만 진짜 로그아웃

## UI 상태

홈 페이지 상단의 점 색깔과 텍스트로 현재 상태를 보여줍니다.

| 상태 | 표시 |
|------|------|
| 활동 감지 직후 | 🟢 활동 감지 (시각) |
| 활동 멈춘 지 1.5초 경과 | 🟢 대기 중 |
| 세션 만료 | 🔴 세션 만료 - 로그인 페이지로 이동합니다 |
| 서버 연결 실패 | 🔴 서버 연결 실패 |

## 실행

```bash
./gradlew bootRun
```

브라우저에서 `http://localhost:8100` 접속 후 `test` / `qwer`로 로그인.

> 포트는 [`application.properties`](src/main/resources/application.properties)의 `server.port`로 조정.

## 주요 상수

| 값 | 위치 | 의미 |
|----|------|------|
| `IDLE_TIMEOUT_MS = 30 * 60 * 1000` | [`AuthServiceImpl`](src/main/java/com/example/haeun/service/impl/AuthServiceImpl.java) | 서버 세션 유지 한도 (30분) |
| `PING_INTERVAL_MS = 10_000` | [`home.html`](src/main/resources/templates/home.html) | 클라이언트 → 서버 핑 주기 (10초) |
| `IDLE_DISPLAY_MS = 1500` | [`home.html`](src/main/resources/templates/home.html) | UI "대기 중" 전환까지 대기 시간 (1.5초) |

## 한계 및 메모

- **인메모리 세션 저장**이라 서버 재시작 시 모든 세션이 사라짐 (DB 미사용 테스트 프로젝트)
- 사용자는 `test` / `qwer` 하나만 하드코딩 (테스트 용)
- 활동 신호는 10초 단위로 묶여서 서버에 보고됨 (서버 부하 throttle)
- 보안 강화가 필요하면 HTTPS 사용 + `cookie.setSecure(true)` 활성화 권장
