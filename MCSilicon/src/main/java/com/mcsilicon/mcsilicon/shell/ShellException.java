package com.mcsilicon.mcsilicon.shell;

/** 쉘 명령 실행 중 사용자에게 그대로 보여줄 오류(존재하지 않는 파일, 잘못된 인자 등). */
public class ShellException extends RuntimeException {
    public ShellException(String message) {
        super(message);
    }
}
