package com.mcsilicon.mcsilicon.dsl.ast;

/** GROUP 안에서 선언되는 필드 하나: NEW <스칼라타입> <이름> [초기값]; */
public final class GroupFieldDecl {
    public final Param param;   // 필드 이름 + 스칼라 타입/폭
    public final Expr init;     // 초기값 표현식(없으면 null - 기본값 0)

    public GroupFieldDecl(Param param, Expr init) {
        this.param = param;
        this.init = init;
    }
}
