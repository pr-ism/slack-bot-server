# 1. 코드 스타일

## 1.1 코드 스타일

- 기본적으로 Google Java Code Convention을 따른다.
    - 단, 다음 항목은 별도로 적용한다.
        - 4.2 블럭 들여쓰기: +2 스페이스가 아닌 4스페이스를 사용한다.
        - 4.4 열 제한: 100 글자가 아닌 120 글자로 제한한다.
- 기술은 단순하고 최소한으로 적용하며, 도입했다면 명확한 도입 이유가 필요하다.
- 객체 지향 생활 체조 원칙을 지킬 수 있도록 노력한다.

## 1.2 프로덕션 영역 네이밍 규칙

- 변수 / 메서드 / 클래스에서는 camel case를 사용한다.

- 변수 / 메서드 / 클래스 네이밍에 UserList와 같이 자료구조를 직접 명시하는 것을 지양한다.

```java
// bad
LatLng // 줄임말은 허용되지 않음

// good
        Position

// normal
LatitudeLongitude // 길게 표현했지만 허용
```

- 줄임말을 지양한다.
- 최대한 해당 상황을 표현하기 좋은 네이밍을 작성하되, 힘들다면 줄임말 대신 길게 표현하는 방식으로 사용한다.


- boolean 지역 변수는 is 접두사를 사용한다.
    - 예: `boolean isValid = validator.check();`
        - boolean 클래스 필드에는 is 접두사를 사용하지 않는다.
    - 이유: Lombok의 `@Getter`가 자동으로 `isXxx()` 형태의 getter를 생성하기 때문
        - 예: `private boolean active;` → `isActive()` 메서드 자동 생성


- DTO는 사용 영역에 따라 접미사를 다르게 사용한다.
    - 웹 : Request/Response 접미사를 사용한다.
    - 도메인 및 서비스 : Dto 접미사를 사용한다.
    - DB : View 접미사를 사용한다.


- 조회하는 메서드의 경우 다음과 같이 네이밍을 구분해서 사용한다.
    - getter
        - Lombok의 `@Getter` 로만 사용한다.
        - private 필드의 어떠한 가공도 하지 않고 그 값을 그대로 반환할 때 사용한다.
        - DTO 변환, JSON 직렬화/역직렬화 등 인프라/표현 계층에서만 사용하며, 핵심 비즈니스 로직에서는 사용을 지양한다.
    - 필드 네이밍 (`size()`)
        - Java Record나 Value Object(VO)의 속성을 조회할 때 사용한다.
        - Entity와 같은 가변 객체에서는 비즈니스 로직상 꼭 필요한 경우가 아니라면 지양한다.
    - Repository / Service 계층 조회
        - find: 조건에 맞는 결과가 없을 수도 있는 경우 사용한다.
            - 단건 조회: Optional<T> 반환
            - 다건 조회: List<T> 반환 (결과가 없으면 빈 리스트 반환, Optional 감싸지 않는다.)
        - get: 조건에 맞는 결과가 반드시 존재해야 하는 경우 사용한다.
            - 결과가 없으면 내부에서 예외를 발생시킨다.
            - Optional을 반환하지 않고 T를 바로 반환한다.


- 다음과 같이 명확하지 않은 네이밍은 지양한다.
    - Data / Info / Item
        - 모든 것이 데이터이며 정보라고 표현할 수 있다.
        - 구체적으로 어떤 정보인지 알 수 없다.
        - 구체적인 내용을 명시한다.
            - UserData, UserInfo → UserProfile
            - ProcessItem → ProcessingTask
    - Util / Common / Global
        - 서로 관련 없는 기능들이 잡다하게 들어갈 수 있다.
        - 기능별로 명확하게 분리한다.
            - CommonUtils → StringUtils
    - Temp / Tmp / Val / Var
        - 임시라는 의미밖에 표현하지 못한다.
        - 임시로 담는 값이라도 명확하게 의미를 부여할 수 있는 네이밍을 사용한다.
            - inputString, loopIndex
    - Stream 내부 람다 표현식의 변수 네이밍 `it`
        - 해당 람다에서 사용되는 변수의 의미를 파악하기 어렵다.
        - 도메인 지식에 맞는 네이밍을 사용한다.
            - `users.stream().map(user -> ...)`

## 1.3 Lombok

- 다음과 같은 기능만을 허용한다.
    - `@Getter`
    - `@Builder`
    - JPA Entity의 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`
    - JPA Entity가 아닌 도메인 객체의 `@EqualsAndHashCode`
    - `@RequiredArgsConstructor`
    - `@Slf4j`

## 1.4 final

- 메서드 파라미터의 경우 final 키워드를 적용하지 않는다.
- 클래스 레벨의 경우 기본적으로 final 키워드를 적용하지 않는다.
    - 해당 클래스를 확장해서는 안 된다는 것을 강조할 때만 final 키워드를 허용한다.
- 불변인 멤버 변수의 경우 final 키워드를 적용한다.
- 지역 변수의 경우 기본적으로 final 키워드를 적용하지 않는다.
    - 해당 지역 변수가 불변임을 강조할 때만 final 키워드를 허용한다.

## 1.5 Annotation 규칙

```java
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {
}
```

- Annotation은 연관이 있는 것들끼리 묶어서 표현한다.
- 연관이 있는 것들은 가독성을 고려해 길이 순서로 정렬한다.

## 1.6 import 규칙

```java
// bad 
import java.util.*;

// good 
import java.util.List;
import java.util.Set;
```

- import 시 `*` 을 사용하지 않는다.

```java
// bad 
SMUGGLER

// good 
TeamRole.SMUGGLER
```

- 원칙적으로 프로덕션 코드에서 static import를 지양한다.
    - 코드의 출처를 명확히 하기 위함이다.


- 단, 다음의 경우는 예외로 static import를 허용(또는 필수로) 한다.

```java
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
```

- 테스트 영역에서는 검증 관련 로직에 한해 반드시 import static을 사용한다.

```java
import static com.example.project.domain.user.QAccount.account;
```

- Querydsl의 Q파일은 반드시 import static을 사용한다.

```java
// bad 
java.util.List<Account> accounts = new ArrayList<>();

// good 
import java.util.List;

List<Account> accounts = new ArrayList<>();
```

- FQCN은 사용하지 않는다.
- 다른 패키지에 동일한 클래스 네이밍이 있는 경우 네이밍을 적절하게 변경한다.
    - 동일한 클래스 네이밍을 만들지 않는다.

## 1.7 중괄호

```java
// bad
if (a < 0) return true;

// good 
        if (a < 0) {
        return true;
        }
```

- 중괄호 `{}` 는 생략할 수 없다.

## 1.8 depth 제한

```java
// bad 
public boolean canDeletePost(User user, Post post) {
    if (user != null) {
        if (post != null) {
            if (user.isAdmin()) {
                return true;
            } else if (post.getAuthorId().equals(user.getId())) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    } else {
        return false;
    }
}

// good
public boolean canDeletePost(User user, Post post) {
    if (user == null) {
        return false;
    }
    if (post == null) {
        return false;
    }
    if (user.isAdmin()) {
        return true;
    }

    return post.isAuthorId(user.getId());
}
```

- depth는 최대 2까지 허용한다.
- depth를 1 이하로 유지하도록 노력한다.
    - early return, 메서드 분리 등을 적극적으로 활용한다.

## 1.9 개행

```java
// bad 
List<TodayQuizOptionDto> todayQuizOptionDtos = todayQuizOptions.stream()
                                                               .map(option -> new TodayQuizOptionDto(
                                                                       option.getId(),
                                                                       option.getWordId(),
                                                                       option.getContent(),
                                                                       option.getOptionOrder())
                                                               )
                                                               .toList();

// good
List<TodayQuizOptionDto> todayQuizOptionDtos = todayQuizOptions.stream()
                                                               .map(
                                                                       option -> new TodayQuizOptionDto(
                                                                               option.getId(),
                                                                               option.getWordId(),
                                                                               option.getContent(),
                                                                               option.getOptionOrder()
                                                                       )
                                                               )
                                                               .toList();
```

```java
// bad
List<QuizOption> quizOptions = IntStream.range(0, splitWords.size())
                                        .mapToObj(index ->
                                                convert(
                                                        splitWords.get(index),
                                                        quizQuestionIds.get(index)
                                                ))
                                        .flatMap(List::stream)
                                        .toList();

// good
List<QuizOption> quizOptions = IntStream.range(0, splitWords.size())
                                        .mapToObj(
                                                index -> convert(splitWords.get(index), quizQuestionIds.get(index)))
                                        .flatMap(List::stream)
                                        .toList();
```

- 하나의 행에 120글자를 초과할 경우 괄호 시작 시 개행을 진행한다.
- 한 번에 여러 괄호가 중첩되서 작성될 시 각 괄호에 대한 들여쓰기를 적용한다.

```java
// bad
private ContrabandGame(TeamState teamState, int totalRounds, RoundEngine roundEngine,
        GameStatus status) {
    this.teamState = teamState;
    this.totalRounds = totalRounds;
    this.roundEngine = roundEngine;
    this.status = status;
}

// good 
private ContrabandGame(
        TeamState teamState,
        int totalRounds,
        RoundEngine roundEngine,
        GameStatus status
) {
    this.teamState = teamState;
    this.totalRounds = totalRounds;
    this.roundEngine = roundEngine;
    this.status = status;
}
```

- 메서드 시그니처 작성 시 하나의 행에 120글자를 넘어간 경우 각 메서드 파라미터마다 개행한다.

```java
// bad 
command.clientSession().tell(new HandleExceptionMessage(ExceptionCode.GAME_ROOM_NOT_FOUND));

// good 
        command.clientSession()
       .tell(new HandleExceptionMessage(ExceptionCode.GAME_ROOM_NOT_FOUND));
```

- 한 줄에는 하나의 `.` 만을 사용한다.

## 1.10 매직 넘버, 리터럴 가독성

```java
long a = 1L;
double b = 1.0D;
```

- 매직 넘버 작성 시 해당 타입에 맞는 접미사를 대문자로 붙인다.

```java
long a = 1_000L;
long b = 10_000_000L;
```

- 매직 넘버 작성 시 그 값이 큰 경우 `_` (언더스코어)를 통해 가독성을 확보한다.

```java
// bad
public String buildSlackMessageJson(String channel, String text) {
    return "{"
            + "\"channel\":\"" + channel + "\","
            + "\"text\":\"" + text.replace("\"", "\\\"") + "\""
            + "}";
}

// good
public String buildSlackMessageJson(String channel, String text) {
    return """
           {
             "channel": "%s",
             "text": "%s"
           }
           """.formatted(channel, escapedText);
}
```

- 복잡한 텍스트 작성 시 text block을 사용한다.
- 동적으로 텍스트를 변경해야 하는 경우 StringBuilder를 사용한다.

## 1.11 변수

```java
// bad
command.clientSession()
       .tell(new HandleExceptionMessage(ExceptionCode.LOBBY_FULL));
       
// good 
HandleExceptionMessage lobbyFullExceptionMessage = new HandleExceptionMessage(ExceptionCode.LOBBY_FULL);

command.clientSession()
       .tell(lobbyFullExceptionMessage);
```

- 파라미터로 객체를 전달하는 경우 해당 객체를 별도의 변수에 할당해 전달한다.

## 1.12 wrapper type

- 제네릭과 같이 primitive type을 문법적 상황으로 인해 wrapping 해야 하는 경우를 제외하면 반드시 primitive type을 사용한다.
- JPA Entity의 ID 및 Nullable 컬럼과 같이 null을 허용해야 하는 경우를 제외하고는 Primitive Type을 사용한다.

# 2. 프로젝트 구조

## 2.1 패키지 네이밍

```java
// bad 
PullRequest
pullRequest
pull-request

// good
pullrequest
```

- 디렉토리 명은 모두 소문자를 사용한다.
- 중간에 `-` 을 넣지 않는다.

## 2.2 패키지 구성

- 도메인 계층을 가진 변경된 Layered Architecture를 사용한다.
- 각 계층은 반드시 자신의 하위 계층을 의존하도록 한다.
- 패키지 간 순환참조가 발생하지 않도록 한다.
- 하위 계층이 상위 계층을 의존하지 않도록 한다.

```java
com.example.project
├── application                # Application Layer (비즈니스 흐름, Usecase)
├── domain                     # Domain Layer (핵심 도메인 로직, Entity)
├── presentation               # Interface Layer (Web Controller, DTO)
├── infrastructure             # Infrastructure Layer (구현체)
│   ├── jwt                    # JWT 토큰 처리
│   └── persistence            # DB 접근 구현체 (JpaRepository 등)
└── global                     # 전역 공통 모듈
    ├── config                 # 설정 (SecurityConfig, WebConfig 등)
    ├── datasource             # 데이터소스 및 DB 설정
    ├── exception              # 전역 예외 처리 (GlobalExceptionHandler)
    ├── log                    # 로깅 설정 및 AOP
    └── security               # 스프링 시큐리티 및 인증/인가 필터
```

- 다음과 같은 Layered Architecture 패키지 내부에 도메인 별로 패키지를 추가해 관리한다.
    - presentation : 컨트롤러(웹) 계층
    - application : 애플리케이션 서비스 계층
    - domain : 도메인 계층
    - infrastructure : 외부 계층
        - 해당 패키지 내에서 DB 관리 패키지인 persistence를 정의해서 사용해야 한다.

```java
com.example.project
├── application
│   └── user
│       └── dto
│           ├── request
│           │   ├── UserLoginRequest.java
│           │   └── UserUpdatePasswordRequest.java
│           └── response
│               ├── UserResponse.java
│               └── UserLoginResponse.java
├── domain
│   └── user
│       └── dto
│           ├── UserSimpleDto.java
│           └── UserDetailDto.java
├── infrastructure
│   └── persistence
│       └── user
│           └── dto
│               ├── UserListView.java
│               └── UserStatisticsView.java
└── presentation
    └── user
        └── UserController.java
```

- DTO 위치는 필요한 패키지 내에 정의해 사용한다.
    - presentation : application 계층의 DTO(Request / Response)를 바로 받으므로 별도 DTO 패키지를 선언하지 않는다.
    - application : 웹에서 바로 받을 Request / Response DTO를 정의한다.
    - domain : domain ~ application 내에서만 사용할 DTO를 정의한다.
    - infrastructure : infrastructure 내에서만 사용할 DTO를 정의한다.

```java
com.example.project
├── domain
│   └── user
│       └── repository
│           └── UserRepository.java          // [Interface] 도메인에 정의한 리포지토리
└── infrastructure
    └── user
        └── persistence
            ├── JpaUserRepository.java       // [Interface] JpaRepository 확장 인터페이스
            └── UserRepositoryAdapter.java    // [Class] 실제 DB에 접근하기 위한 기능을 모아놓은 UserRepository 구현체
```

- 도메인 레벨에 인터페이스로 해당 도메인을 찾을 수 있는 인터페이스를 정의한다.
- infrastructure에 구현 기술에 따라 적절한 구현체를 추가한다.
    - 위 아스키 트리는 JPA를 사용했을 때의 예시이다.

# 3. 자바 및 객체 지향

## 3.1 주석

- 주석은 최대한 사용하지 않는다.
    - 변수 명, 메서드 명 등으로 최대한 자연스럽게 표현하기 위해 노력한다.

- 다음과 같은 상황에서는 주석을 허용한다.

    ```java
    // given
    
    // when
    
    // then
    ```

    - 테스트에서 BDD 패턴을 적용시킬 때

    ```java
     public record Position(double lat, double lng) {
    
        public Position {
            if (Double.isNaN(lat) || Double.isNaN(lng)) {
                throw new IllegalArgumentException("lat/lng must be a number");
            }
            // 좌표의 (0, 0)은 초기 값이 아닌 유효하지 않은 값으로 처리한다.
            if (lat == 0.0D && lng == 0.0D) {
                throw new IllegalArgumentException("lat/lng is not set (0,0)");
            }
            if (lat < -90.0D || lat > 90.0D) {
                throw new IllegalArgumentException("lat out of range");
            }
            if (lng < -180.0D || lng > 180.0D) {
                throw new IllegalArgumentException("lng out of range");
            }
        }
    }
    ```

    - 비즈니스 로직에서 논리적으로 이질적인 케이스를 표현할 때

    ```java
    try {
    } catch (Exception ignored) {
        // 외부 컴포넌트에서 처리하므로 예외 처리는 하지 않음
    }
    ```

    - 예외 처리 블록에서 예외를 처리하지 않는 이유를 설명할 때

## 3.2 enum

```java
// bad 
if (TeamRole.SMUGGLER == status) {
}

// good 
if (status.isSmuggler()) {
}
```

- enum 비교 시 enum 내부적으로 비교 메서드를 수행한다.

```java
// bad
return TeamRole.SMUGGLER.equals(status);

// good 
return TeamRole.SMUGGLER == status;
```

- enum 비교 시 `==` 비교를 진행한다.

## 3.3 early return

```java
// bad
public int parseAge(String input) {
    if (input == null) {
        return -1;
    } else if (input.isBlank()) {
        return -1;
    } else if (!input.chars().allMatch(Character::isDigit)) {
        return -1;
    } else {
        int age = Integer.parseInt(input);
        if (age < 0 || age > 120) {
            return -1;
        } else {
            return age;
        }
    }
}

// good
public int parseAge(String input) {
    if (input == null) {
        return -1;
    }
    if (input.isBlank()) {
        return -1;
    }
    if (!input.chars().allMatch(Character::isDigit)) {
        return -1;
    }

    int age = Integer.parseInt(input);
    
    if (age < 0 || age > 120) {
        return -1;
    }

    return age;
}
```

- else if, else는 최대한 사용하지 않는다.
- early return를 최대한 활용한다.

## 3.4 부정형 표현 메서드

```java
// bad
public int parseAge(String input) {
    if (!input.chars().allMatch(Character::isDigit)) {
        return -1;
    }
    
    return Integer.parseInt(input);
}

// good 
public int parseAge(String input) {
    if (isNonMatch(input)) {
        return -1;
    }
    
    return Integer.parseInt(input);
}

private boolean isNonMatch(String input) {
		return !input.chars().allMatch(Character::isDigit);
}
```

```java
// 긍정 메서드
public boolean isEqualId(Long id) {
    return this.id.equals(id);
}

// 부정 표현을 위한 편의 메서드
public boolean isNotEqualId(Long id) {
    return !isEqualId(id);
}
```

- 조건문에 바로 `!` (부정문 표현)은 지양한다.
    - 별도 편의 메서드를 지원한다.



## 3.5 Stream

```java
// bad
List<String> bad1 = names.stream()
                           .filter(s -> s != null) 
                           .collect(Collectors.toList());

List<String> bad2 = names.stream()
                            .filter(s -> {
                                if (s != null) {
                                    return true;
                                }
                                return false;
                            })
                            .collect(Collectors.toList());

// good
List<String> nonNullNames = names.stream()
                                 .filter(Objects::nonNull)
                                 .toList();
```

- Stream 시 null 체크는 Objects의 static 메서드를 활용한다.

```java
// bad
List<String> bad = names.stream()
                          .filter(Objects::nonNull)
                          .collect(Collectors.toList());

// good
List<String> good = names.stream()
                           .filter(Objects::nonNull)
                           .toList();
```

- Stream을 List로 변환해야 한다면 `toList()`를 사용한다.
    - `toList()`는 불변 리스트를 반환하므로 조작이 필요하다면 최대한 해당 Stream에서 조작한다.

```java
// bad
Map<Long, User> bad = users.stream()
        .collect(
            Collectors.toMap(
                    User::getId,
                    Function.identity()
           )
        );

// good
Map<Long, User> good = users.stream()
        .collect(
            Collectors.toMap(
                User::getId,
                user -> user
            )
        );
```

- Stream에서 Map으로 collect 시 value를 그대로 사용해야 한다면 Function.identity()를 사용하지 않고 자기 자신을 반환한다는 내용을 명확하게 표현한다.

## 3.6 디미터의 법칙

```java
// bad
post.getAuthorId().equals(user.getId())

// good 
post.isAuthorId(user.getId())
```

- 디미터의 법칙을 지키기 위해 getter로 값을 꺼낸 뒤 비교하지 않는다.
- getter가 아닌 해당 기능을 제공하는 별도 메서드를 제공한다.

## 3.7 setter

```java
// bad
post.setTitle("타이틀 변경");

// good
post.changeTitle("타이틀 변경");
```

- setter는 절대 사용하지 않는다.
- setter를 공통적으로 사용해야 하는 상황을, 별도의 도메인 특화 메서드로 분리한다.

## 3.8 instanceof

```java
@Override
public final int hashCode() {
    if (this instanceof HibernateProxy proxy) {
        return proxy.getHibernateLazyInitializer()
                .getPersistentClass()
                .hashCode();
    }

    return getClass().hashCode();
}
```

- instanceof는 특이사항(JPA equals(), hashcode(), 테스트 영역 등)이 아니라면 사용하지 않는다.
- 사용한다면 자바 17의 instanceof 패턴을 반드시 사용한다.

## 3.9 try-catch

```java
// bad 
try {
    ...
} finally {
    input.close();
}

// good 
try (Scanner input = new Scanner(System.in)) {
}
```

- 반드시 try-with-resources를 사용한다.

```java
try {
} catch (Exception ignored) {
}

try {
} catch (Exception ignored) {
    // 외부 컴포넌트에서 처리하므로 예외 처리는 하지 않음
}
```

- 예외 처리를 하지 않는 경우 예외 변수명은 ignored를 사용한다.
- catch 부분에서 왜 해당 예외를 핸들링하지 않는지 주석으로 설명할 수 있다.

```java
// bad 
public UserDto getUser(Long userId) {
    try {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));

        validateUser(user);
        auditLog(user);
        return UserDto.from(user);
    } catch (Exception e) {
        log.error("사용자 조회 실패", e);
    }
}

// good
public UserDto getUser(Long userId) {
    User user = findUser(userId);

    validateUser(user);
    auditLog(user);

    return UserDto.from(user);
}

private User findUser(Long userId) {
    try {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));
    } catch (Exception e) {
        log.error("사용자 조회 실패", e);
    }
}

```

- try 블록은 `실패할 수 있는 단 하나의 행위`만 감싸야 한다.
    - 실패할 가능성이 있는 핵심 비즈니스 로직 단위로 최소화해야 한다.
    - try-catch 안에 단 한 줄의 코드만 있을 수 있도록 노력한다.

# 4. 테스트

- 테스트 파일은 반드시 테스트 대상 클래스와 동일한 패키지에 위치해야 한다.
- 다음 대상에 대해 테스트를 수행한다.
    - 도메인 -> 단위 테스트
    - 서비스 -> 통합 테스트
    - 컨트롤러 -> Spring Rest Docs 문서화를 위한 단위 테스트

## 4.1 테스트 포맷

```java
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlidingWindowCounterTest {
		
    @Test
    void 밀수꾼_방장으로_로비를_생성한다() {
    }
}
```

- 테스트 메서드 네이밍은 한글을 사용한다.
    - 스페이스 바 대신 `_` 를 사용한다.
- 한글 메서드 네이밍을 사용해 발생하는 경고를 억제하기 위해 `@SuppressWarnings` 를 사용한다.
- `_`를 공백으로 치환하는 `@DisplayNameGeneration` 를 사용한다.

```java
@Test
void 밀수꾼_방장으로_로비를_생성한다() {
    // given
    PlayerProfile hostProfile = PlayerProfile.create(1L, "방장", TeamRole.SMUGGLER);

    // when
    Lobby actual = Lobby.create(1L, "게임방", hostProfile, 6);

    // then
    assertAll(
            () -> assertThat(actual.getId()).isEqualTo(1L),
            () -> assertThat(actual.getName()).isEqualTo("게임방"),
            () -> assertThat(actual.getHostId()).isEqualTo(1L),
            () -> assertThat(actual.getPhase()).isEqualTo(LobbyPhase.LOBBY),
            () -> assertThat(actual.getSmugglerDraft()).hasSize(1),
            () -> assertThat(actual.getInspectorDraft()).isEmpty(),
            () -> assertThat(actual.getReadyStates()).containsEntry(1L, false)
    );
}
```

- BDD 패턴을 사용한다.
- 각 패턴에 맞는 구역에 주석을 추가한다.
    - given, when, then
    - when & then
- 검증 대상이 되는 대상은 `actual` 이라고 명시한다.
- 하나 이상의 검증 메서드를 수행한다.
    - 두 개 이상의 검증 메서드가 있는 경우 `assertAll()`로 묶어서 검증한다.
- 테스트 검증을 위해 `@Getter`를 추가하는 행위는 지양한다.

## 4.2 테스트 범위

- 도메인과 서비스에 대한 테스트를 진행한다.
- Repository의 경우 직접 쿼리를 작성한 경우에만 테스트를 진행한다.

## 4.3 테스트 설정

- 테스트 더블은 별도 패키지로 분리해 직접 생성한다.
- `BDDMockito.mock()` / `BDDMockito.given()`은 외부 의존성 및 복잡한 경우에만 사용한다.

## 4.4 파라미터화 테스트

```java
@ParameterizedTest
@NullAndEmptySource
void 방_이름이_비어_있으면_생성할_수_없다(String name) {
    // given
    PlayerProfile hostProfile = PlayerProfile.create(1L, "방장", TeamRole.SMUGGLER);

    // when & then
    assertThatThrownBy(() -> Lobby.create(1L, name, hostProfile, 6))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("방 이름은 비어 있을 수 없습니다.");
}
```

- String과 같은 파라미터가 비어 있는 경우에 대한 엣지 케이스 테스트 시 `@NullAndEmptySource`를 사용한다.

```java
static Stream<Arguments> provideUserCreationScenarios() {
    return Stream.of(
        arguments(20, "테스터", true),
        arguments(15, "테스터", false),
        arguments(20, "", false),
        arguments(0, "테스터", false)
    );
}

@ParameterizedTest(name = "나이가 {0}살이고 이름이 {1}인 경우 {2}를 반환한다")
@MethodSource("provideUserCreationScenarios")
void 나이와_이름_조건에_따라_유효성_검사_결과가_달라진다(int age, String name, boolean expected) {
    // given
    UserValidator validator = new UserValidator();

    // when
    boolean actual = validator.isValid(age, name);

    // then
    assertThat(actual).isEqualTo(expected);
}
```

- 복잡한 파라미터가 필요한 경우 `@MethodSource`를 사용한다.
- `@MethodSource` 사용 시 `@ParameterizedTest` 의 `name` 속성을 사용한다.
- `@MethodSource` 는 테스트 메서드 위에 명시한다.

## 4.5 테스트 데이터 관리

- Fixture는 테스트 클래스 외부로 관리한다.
- 통합 테스트 시 `@Sql`을 통해 필요한 시점의 배경 데이터를 세팅한다.

## 4.6 Controller Slice Test

```java
@SuppressWarnings("NonAsciiCharacters")
class RefreshTokenControllerTest extends CommonControllerSliceTestSupport {

    @Autowired
    GenerateTokenService generateTokenService;

    @Test
    void 토큰을_재발급_한다() throws Exception {
        given(tokenProperties.accessExpiredSeconds()).willReturn(3_600);
        given(tokenProperties.refreshExpiredSeconds()).willReturn(259_200);
        given(generateTokenService.refreshToken(refreshToken)).willReturn(tokenDto);
    }
}
```

- 생성한 컨트롤러 테스트 클래스에서 `CommonControllerSliceTestSupport`를 확장한다.
- 컨트롤러가 의존하고 있는 대상은 `@Autowired`로 주입받는다.
    - 이 때 주입되는 대상은 모두 mock 컴포넌트들이다.
- 필요한 값을 반환하도록 mocking 한다.

# 5. JPA

## 5.1 공통 Entity

- 다음 공통 Entity를 확장해서 사용한다.

```java
@MappedSuperclass
@Getter
public abstract class BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        
        Class<?> oEffectiveClass;
        if (o instanceof HibernateProxy proxy) {
            oEffectiveClass = proxy.getHibernateLazyInitializer().getPersistentClass();
        } else {
            oEffectiveClass = o.getClass();
        }
        
        Class<?> thisEffectiveClass;
        if (this instanceof HibernateProxy proxy) {
            thisEffectiveClass = proxy.getHibernateLazyInitializer().getPersistentClass();
        } else {
            thisEffectiveClass = this.getClass();
        }
        
        if (thisEffectiveClass != oEffectiveClass) {
            return false;
        }
        
        BaseEntity that = (BaseEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }
    
    @Override
    public final int hashCode() {
        if (this instanceof HibernateProxy proxy) {
            return proxy.getHibernateLazyInitializer()
                    .getPersistentClass()
                    .hashCode();
        }
        return getClass().hashCode();
    }
}
```

- Long id 및 equals(), hashcode()를 정의한 Entity이다.

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class CreatedAtEntity extends BaseEntity {
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

- 생성 시간을 관리하는 Entity이다.

```java
@MappedSuperclass
@Getter
public abstract class BaseTimeEntity extends CreatedAtEntity {
    
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
```

- 생성 시간과 수정 시간을 관리하는 Entity이다.
    - 생성 시간은 CreatedAtEntity로부터 상속받는다.

## 5.2 Entity 정의

```java
@Table(name = "accounts")
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account extends BaseTimeEntity {
}
```

- 롬복을 적극적으로 사용한다.
- Table을 항상 명시하며, 테이블 네이밍은 도메인 엔티티의 복수형(Account → accounts)로 명시한다.

## 5.3 복잡한 쿼리

```java
// bad 
public interface AccountRepository extends JpaRepository<Account, Long> {

    @Query("""
            select a
            from Account a
            where a.email = :email
              and a.status = com.example.AccountStatus.ACTIVE
            """)
    Optional<Account> findActiveByEmail(@Param("email") String email);
}

// good 
import static QAccount.account;

@Override
public Optional<Account> findActiveByEmail(String email) {
    Account found = queryFactory.selectFrom(account)
                                  .where(
                                          account.email.eq(email),
                                          account.status.eq(AccountStatus.ACTIVE)
                                  )
                                  .fetchOne();

    return Optional.ofNullable(found);
}
```

- JPQL은 사용하지 않는다.
- querydsl을 사용한다.

## 5.4 Entity DDL 스키마

- 양방향 연관관계는 지양한다.

```java
// bad
@Column(name = "nickname", length = 50, nullable = false, unique = true, columnDefinition = "varchar(50) comment '유저 닉네임'")
private String nickname;

// good
private String nickname;
```

- Entity에 DDL 스키마(길이, 유니크 여부 등)을 명시하지 않는다.
- 필드 길이와 같은 경우는 DB와는 별도로 애플리케이션에서 검증 로직으로 방어한다.

```java
// bad
@ManyToOne
@JoinColumn(
        name = "team_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "FK_MEMBER_TEAM"),
        unique = true
)
private Team team;

// good
@ManyToOne(optional = false)
@JoinColumn(name = "team_id")
private Team team;
```

- 양방향 연관관계 사용 시 `@JoinColumn`을 명시한다.
- querydsl을 사용하지 않고 JPA 쿼리 자동완성을 사용하는 경우에만 INNER JOIN 최적화를 위해 `optional` 속성을 활용한다.

# 6. 스프링

## 6.1 @Transactional

- 클래스 레벨에 `@Transactional` 을 명시하지 않는다.
- JPA 사용 시 메서드 레벨로 `@Transactional` 과 `@Transactional(readOnly = true)` 를 명시한다.
- JPA를 사용하지 않는다면 `@Transactional(readOnly = true)` 를 사용하지 않는다.

## 6.2 컨트롤러 관련

- 요청을 받는 DTO에 각 필드를 `validation`으로 검증한다.

```java
@PostMapping("/change-info")
public ResponseEntity<UserInfoResponse> changeUserInfo(@RequestBody @Valid ChangedUserInfoRequest request) {
		UserInfoResponse response = userService.changeUserInfo(request);
		
		return ResponseEntity.ok(response);
}
```

- 컨트롤러에서 받는 입력/출력의 패키지 위치는 application(service) 패키지 내부여야 한다.
- DTO ↔ 도메인 변환 로직은 DTO가 가지고 있는다.
    - 별도 Mapper를 가지지 않는다.
    - 도메인에서는 DTO를 알아서는 안 된다.

- 컨트롤러에서 반환 시 ResponseEntity를 사용한다.

## 6.3 @Profile

- `@Profile` 을 통해 각 환경별로 필요한 컴포넌트를 관리한다.
- 필요하다면 Spring Profile을 조합해서 처리한다.

## 6.4 설정 파일

- 설정 파일은 `application.yml` 을 사용한다.
- 각 `@Profile` 마다 별도의 `application-*.yml` 을 사용한다.

## 6.5 로그

- 로그 사용 시 기본적으로 동기 로그를 사용한다.
- 로그 사용 시 MDC를 활용해 로그 추적을 용이하게 수행한다.
- 로그 사용 시 로그로 인한 병목(성능)을 고려해야 하는 경우 비동기 로그를 사용한다.

# 7. 예외

- Custom Exception과 자바 기본 예외를 적절히 활용한다.
    - 일반적인 경우 자바 기본 예외를 활용한다.
    - 해당 예외 상황을 반드시 자바 기본 예외와 다른 방식으로 처리해야 하는 경우 Custom Exception을 사용한다.

## 7.1 Custom Exception

```java
public class RefreshTokenNotFoundException extends IllegalArgumentException {

    public RefreshTokenNotFoundException() {
        super("Cookie에서 refreshToken을 찾을 수 없습니다.");
    }
}

public class InvalidTokenException extends IllegalArgumentException {

    public InvalidTokenException(String s) {
        super(s);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- Custom Exception 생성 시 원하는 방식으로 작성한다.
- 단 Custom Exception에 프론트엔드 관련 개념, 지식이 포함되서는 안 된다.

## 7.2 예외 핸들링 관련

- `@RestControllerAdvice` 로 예외를 핸들링한다.
    - `ResponseEntityExceptionHandler` 를 상속한다.

```java
@ExceptionHandler(RefreshTokenNotFoundException.class)
public ResponseEntity<ExceptionResponse> handleRefreshTokenNotFoundException() {
    ExceptionResponse response = ExceptionResponse.from(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND);

    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                         .body(response);
}

public record ExceptionResponse(String errorCode, String message) {

    public static ExceptionResponse from(ErrorCode errorCode) {
        return new ExceptionResponse(errorCode.getErrorCode(), errorCode.getMessage());
    }
}
```

- 예외 핸들링 시 반드시 `ExceptionResponse`를 반환하도록 한다.

```java
public interface ErrorCode {

    String getErrorCode();

    String getMessage();
}
```

- `ExceptionResponse`에 사용될 enum은 각 도메인마다 ErrorCode 인터페이스를 구현해 사용한다.

```java
@Getter
public enum AuthErrorCode implements ErrorCode {

    REFRESH_TOKEN_NOT_FOUND("A00", "토큰 재발급 실패");

    private final String errorCode;
    private final String message;

    AuthErrorCode(String errorCode, String message) {
        this.errorCode = errorCode;
        this.message = message;
    }
}
```

- 다음과 같은 네이밍 컨벤션을 따른다.
    - enum 상수 명 : 코드 레벨에서 바로 이해할 수 있을 네이밍을 지정한다.
    - code : 프론트엔드에서 예외 처리를 위해 분기 처리를 하기 위한 수단으로 도메인의 첫 글자(`A`)와 번호(`00`부터 시작, 1씩 증가)를 합쳐서 명시한다.
    - message : 프론트엔드에게 예외 상황을 전달하기 위한 메시지로 너무 과도하게 서버 설명을 하지 않도록 유의해 작성한다.
