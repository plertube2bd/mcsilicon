package com.mcsilicon.mcsilicon.dsl;

/** 칩 DSL 컴파일(파싱) 또는 실행 중 발생하는 오류. */
public class DslException extends RuntimeException {
    public DslException(String message) {
        super(message);
    }
}
