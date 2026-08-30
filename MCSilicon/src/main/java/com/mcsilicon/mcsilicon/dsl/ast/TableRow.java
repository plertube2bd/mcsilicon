package com.mcsilicon.mcsilicon.dsl.ast;

import java.util.List;

/**
 * TABLE( ) 본문의 한 행: 입력 값 조합 -> 출력 값 조합.
 * 값의 개수는 각각 칩의 입력/출력 파라미터 개수와 정확히 같아야 한다(컴파일 시 검증).
 */
public final class TableRow {
    public final List<Long> inputs;
    public final List<Long> outputs;

    public TableRow(List<Long> inputs, List<Long> outputs) {
        this.inputs = inputs;
        this.outputs = outputs;
    }
}
