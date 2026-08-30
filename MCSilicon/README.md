# MCSilicon

레드스톤과 무관한 독자 디지털 신호 + 칩 DSL 컴파일 기반 반도체 모드 (Forge 1.21.1)

## 요구 조건 대응

1. **독자 신호**: `signal.SignalValue`(0/1)가 레드스톤 세기와 완전히 분리되어 있음.
   `WireBlock`/`WireBlockEntity`가 신호선, `SignalNetworkManager`가 연결성과 값을 관리.
2. **한 틱 내 완결**: `SignalNetworkManager.settle()`이 매 레벨 틱마다 모든 망과 칩을
   값이 더 이상 바뀌지 않을 때까지 같은 틱 안에서 반복 계산한다. DSL 자체에는
   틱을 넘겨 지연시키는 문법이 없으므로(레지스터/메모리 미지원 - 아래 참고),
   모든 칩 로직은 항상 같은 틱 안에서 끝난다.
3. **포지 Java 코드**: 순수 Java, `net.minecraftforge` API 사용.

블록 여러 개를 조합해 게이트를 만드는 대신, **칩 블록 하나**에 DSL 소스를 저장하고
`ChipProgram.compile()`로 컴파일한 뒤 매 settle 사이클마다 `Interpreter.run()`으로 실행한다.

## 디렉토리

```
src/main/java/com/mcsilicon/mcsilicon/
  MCSilicon.java              모드 진입점
  registry/                   ModBlocks / ModItems / ModBlockEntities
  signal/                     레드스톤과 무관한 신호망 엔진
    SignalValue.java          비트 값 + 비트 연산
    SignalPort.java           칩의 면(face)에 붙는 접점
    WireNetwork.java          연결된 와이어 집합 (하나의 값 공유)
    SignalNetworkManager.java 연결성 재계산 + 틱당 settle 루프 (핵심)
    SignalTickHandler.java    레벨 틱 이벤트 -> SignalNetworkManager 구동
  block/
    WireBlock(Entity).java    신호선 블록
    ChipBlock.java             DSL을 담는 칩 블록 - FACING(방향성) 보유
    ChipBlockEntity.java       DSL 컴파일/실행, RULE 배치도를 FACING 기준 월드 방향으로 변환
  dsl/                        칩 DSL(v2) 컴파일러
    Lexer.java / Token(Type).java
    ast/ (ChipDef, Param, RuleLayout, GateOp, Clause, Stmt, Expr)
    Parser.java
    ChipProgram.java          컴파일 결과 + 실행 진입점
    ChipRegistry.java         레벨별 "이름 -> 컴파일된 칩" 레지스트리 (USE CHIP이 참조)
    Interpreter.java          DO 블록 실행기(게이트 매칭, JMP/LABEL, 칩 호출)
    ExecutionContext.java     칩 하나의 현재 입출력 값
```

## DSL(v2.1) 문법 요약

전체 문법(EBNF), 오류 메시지 목록, 개정 이력은 **`docs/LANGUAGE.md`**에 자세히
정리되어 있다. 여기는 요약만.

```
NEW <칩이름>(<입력 파라미터, ...>) -> (<출력 파라미터, ...>)
    (USE CHIP <다른칩이름>;)*
    RULE (
         <front|.>
      <left|.> SELF <right|.>
         <back|.>
    )
DO                                    -- 또는 아래 TABLE 중 하나만
    <게이트> <인자, ...> THEN
        <문장>*
    END
    ...
    LABEL <이름>
        <문장>*
    END
    NOTRETURN THEN
        <문장>*
    END
END
```
```
    ...
    RULE ( ... )
TABLE (                                -- DO 대신 진리표를 직접 서술(빠른 칩)
    <입력값, ...> -> <출력값, ...>;
    ...
)
```

- **파라미터**: 타입 생략 시 `BIT`. `INT <이름>` 으로 정수 타입 지정 가능(내부 계산용, 아래 참고).
  개수 제한 없음.
- **RULE 배치도**: 4자리(front/left/right/back) 각각은 파라미터 이름이거나 `.`(빈 자리)다.
  `.`이 아닌 이름은 반드시 실제 파라미터여야 하지만, 반대로 모든 파라미터가 RULE에
  등장할 필요는 없다 — 등장 안 하면 그냥 물리 포트가 없을 뿐이다.
  `front`/`back`은 절대 방향이 아니라 칩의 `FACING` 블록스테이트 기준
  앞/뒤이며, `left`/`right`는 그 기준 좌/우다(칩을 놓는 방향에 따라 자동 회전).
  → **한 칩은 물리적으로 최대 4개의 포트만 가질 수 있다**(4자리 전부 `.`이면 포트 0개 —
  `USE CHIP`으로만 쓰는 "라이브러리 전용" 칩도 가능).
- **바디는 DO 또는 TABLE 둘 중 하나**: `DO`는 게이트 조건절 인터프리터로 매 실행마다
  절을 순회, `TABLE`은 컴파일 시 조회 맵을 미리 만들어 실행 시 O(1) 조회만 한다("빠른 칩").
- **게이트 조건절**(`DO` 전용): `AND OR XOR NAND NOR XNOR NOT` 7종 고정. 인자는 칩의 입력 파라미터.
  조건이 참이면 `THEN` 본문을 실행.
- **실행 순서**: DO 블록은 위에서 아래로, 매치되는 절을 **전부** 순서대로 실행한다
  (`RETURN;`을 만나면 즉시 종료). 순서를 건너뛰려면 `LABEL 이름 ... END`로 표시해두고
  `JMP 이름;`으로 그 지점으로 점프한다.
- **NOTRETURN THEN ... END**: 여기까지 RETURN 없이 도달하면 항상 실행되는 기본 분기.
- **SET**:
  - `SET <기존 이름> <expr>;` — 입력/출력/이미 선언한 LOCAL 변수에 대입
  - `SET LOCAL BIT|INT <이름> <expr>;` — 지역 변수 선언 + 초기화. **틱을 넘기지 않고
    이번 실행 한 번 동안만 유지된다** (아래 "레지스터/메모리" 참고).
- **표현식**:
  - 정수 리터럴, 변수 이름
  - `(연산자 expr expr ...)` 접두 표기 — `ADD SUB MUL DIV`(산술) 또는
    `AND OR XOR NAND NOR XNOR NOT`(비트 논리, 다중 인자 지원)
  - `칩이름(expr, expr, ...)` — 다른 칩을 함수처럼 호출(입력 파라미터 순서대로 전달).
    반드시 상단에 `USE CHIP 칩이름;` 이 선언되어 있어야 하고, 그 이름으로 실제
    컴파일되어 존재하는 칩이 월드에(ChipRegistry에) 있어야 한다.
  - `칩호출.필드` — 호출 결과에서 특정 출력값 하나를 읽음 (예: `HA(A, B).SUM`)

### 레지스터/메모리에 대해

이 모드는 상태 저장(레지스터/RAM)을 직접 제공하지 않는다. 칩의 LOCAL 변수는
매 실행마다 새로 계산되고 출력 이외에는 밖으로 노출되지 않는다. CPU 등 순차 회로가
필요하면 칩 설계자가 직접(예: 다른 칩과 배선으로 되먹임을 구성하는 등) 구현해야 한다.
상수는 `SET LOCAL BIT|INT <이름> <숫자리터럴>;` 로 선언해서 쓰면 된다.

### 예시: 반가산기(Half Adder) - 기본 칩 소스

```
NEW HA(A, B) -> (SUM, CARRY) RULE (
     SUM
  A SELF CARRY
     B
) DO
    AND A, B THEN
        SET SUM 0;
        SET CARRY 1;
        RETURN;
    END
    XOR A, B THEN
        SET SUM 1;
        SET CARRY 0;
        RETURN;
    END
    NAND A, B THEN
        SET SUM 0;
        SET CARRY 0;
        RETURN;
    END
    NOTRETURN THEN RETURN; END
END
```

절(clause) 하나마다 `END`가 필요하고, 마지막에 전체 `DO`를 닫는 `END`가 한 번 더 온다.

### 예시: 다른 칩 재사용

```
NEW FA(A, B) -> (SUM, C)
    USE CHIP HA;
    RULE (
         SUM
      A SELF C
         B
    ) DO
        NOTRETURN THEN
            SET LOCAL INT S1 HA(A, B).SUM;
            SET LOCAL INT C1 HA(A, B).CARRY;
            SET SUM S1;
            SET C C1;
            RETURN;
        END
    END
```

### 예시: 같은 반가산기를 TABLE로 (빠른 칩)

```
NEW HAT(A, B) -> (SUM, CARRY) RULE (
     SUM
  A SELF CARRY
     B
) TABLE (
    0, 0 -> 0, 0;
    0, 1 -> 1, 0;
    1, 0 -> 1, 0;
    1, 1 -> 0, 1;
)
```

`dsltest`(테스트 하네스, `/home/claude/dsltest` — Minecraft 의존 없는 dsl 코어만
떼어 javac/java로 직접 돌리는 독립 검증 환경)로 위 예시들 + JMP/LABEL 분기 +
RULE `.` 빈 자리 + TABLE 바디 + 각종 컴파일 오류 케이스까지 전부 실제
인터프리터로 돌려 기대한 결과가 나오는 것을 확인했다(`Main.java`, `Main2.java`,
`Main3.java`).

## 아직 비어있는 부분 / 다음에 정할 것

- 칩 블록 GUI(코드 편집 화면) - 현재는 기본 HA 예시 소스만 하드코딩, `setSource()`로 교체 가능
- 블록/아이템 텍스처 PNG (모델 JSON만 있고 실제 텍스처 파일은 없음)
- RULE 배치도가 4자리 고정이라 포트가 4개보다 많이 필요한 칩(UP/DOWN 포함 등)은 아직 불가 -
  확장하려면 배치도 문법 자체를 늘려야 함
- `INT` 타입 파라미터가 RULE로 물리 결선될 때 실제로는 1비트(LSB)만 전달됨(단순화) -
  더 넓은 버스가 필요하면 별도 설계 필요
- USE CHIP이 참조하는 칩이 아직 컴파일되지 않았거나 언로드된 경우의 에러 처리/재시도 정책
- 여러 칩이 같은 이름으로 동시에 존재할 때의 충돌 처리(현재는 마지막 publish가 이김)
- `TABLE`에서 빠뜨린 입력 조합이 조용히 0으로 처리됨 - 엄격 모드(누락 시 컴파일 오류)는 미구현

무엇부터 이어서 구현할지 알려주면 계속 진행할게.
