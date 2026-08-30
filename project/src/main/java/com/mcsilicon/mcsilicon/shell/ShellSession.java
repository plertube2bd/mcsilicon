package com.mcsilicon.mcsilicon.shell;

/** 플레이어 하나가 /lx로 접속해 있는 동안의 상태. */
public final class ShellSession {
    public final String username;
    public String cwd = "/"; // 항상 정규화된 가상 절대 경로

    public ShellSession(String username) {
        this.username = username;
        VirtualFileSystem.ensureHome(username);
    }

    public String prompt() {
        return username + ":" + cwd + "% ";
    }
}
