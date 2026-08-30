package com.mcsilicon.mcsilicon.dsl.ast;

/**
 * USE CHIP <경로>; 하나. 경로는 단일 이름(예: HA)이거나, ':' 또는 '/'로
 * 구분된 여러 조각(예: mcs:alice:HA, path/to/chip)일 수 있다.
 *
 * alias: 이 칩 안에서 << 호출/타입에 쓰는 짧은 이름 - 항상 경로의 마지막 조각이다.
 * path : ChipRegistry 조회에 실제로 쓰이는 전체 경로 문자열(구분자 그대로 보존).
 *        단일 이름이면 alias와 path가 같다.
 */
public final class UseRef {
    public final String alias;
    public final String path;

    public UseRef(String alias, String path) {
        this.alias = alias;
        this.path = path;
    }
}
