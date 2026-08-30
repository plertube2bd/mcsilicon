package com.mcsilicon.mcsilicon.dsl;

import java.util.HashMap;
import java.util.Map;

/**
 * 칩 블록 하나의 현재 입력/출력 신호 값(BIT는 0/1, INT는 임의 정수).
 * 이 모드는 레지스터/메모리(틱을 넘는 상태 저장)를 직접 구현하지 않으므로,
 * 여기 보관되는 값은 오직 "현재 입출력 포트 값"뿐이며 LOCAL 변수는 매 실행마다 새로 계산된다.
 */
public final class ExecutionContext {
    private final Map<String, Long> vars = new HashMap<>();

    public long get(String name) {
        return vars.getOrDefault(name, 0L);
    }

    public void set(String name, long value) {
        vars.put(name, value);
    }
}
