package com.mcsilicon.mcsilicon.dsl.ast;

import java.util.ArrayList;
import java.util.List;

/**
 * RULE( ) 크로스 배치도.
 *   front  (그림의 위쪽 = 칩이 바라보는 방향)
 * left SELF right
 *   back   (그림의 아래쪽 = 칩 뒤쪽)
 *
 * front/left/right/back에는 입력 또는 출력 파라미터 이름이 오거나,
 * 그 자리를 쓰지 않는다는 뜻으로 '.' (빈 자리)가 올 수 있다. null이면 '.'로 비운 자리다.
 * 물리적으로 연결되는 포트는 null이 아닌 자리 수만큼(0~4개)이다.
 */
public final class RuleLayout {
    public final String front; // null이면 '.'
    public final String left;
    public final String right;
    public final String back;

    public RuleLayout(String front, String left, String right, String back) {
        this.front = front;
        this.left = left;
        this.right = right;
        this.back = back;
    }

    /** null이 아닌(=실제로 결선된) 자리의 이름들만 모은다. */
    public List<String> boundNames() {
        List<String> names = new ArrayList<>();
        if (front != null) names.add(front);
        if (left != null) names.add(left);
        if (right != null) names.add(right);
        if (back != null) names.add(back);
        return names;
    }
}
