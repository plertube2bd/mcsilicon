package com.mcsilicon.mcsilicon.signal;

import java.util.List;

/**
 * 칩 블록 엔티티(ChipBlockEntity)가 구현하는 인터페이스.
 * SignalNetworkManager가 이 칩의 포트 목록을 조회하고, 입력이 바뀌었을 때
 * 프로그램을 재실행시키기 위해 사용한다.
 */
public interface IChipNode {
    /** 이 칩이 현재 정의하고 있는 모든 포트(INPUT/OUTPUT). */
    List<SignalPort> ports();

    /** 지정한 입력 포트에 새 값이 도착했을 때 호출된다. 즉시 반영만 하고 실행은 하지 않는다. */
    void setInputValue(SignalPort port, SignalValue value);

    /** 현재 지정한 출력 포트의 값. */
    SignalValue getOutputValue(SignalPort port);

    /**
     * 칩의 DSL 프로그램을 한 번 실행한다(조합 논리 기준 즉시 완료).
     * 실행 후 출력 포트 값이 이전과 달라졌다면 true를 반환하여
     * SignalNetworkManager가 연결된 신호망에 재전파하도록 한다.
     */
    boolean tickExecute();
}
