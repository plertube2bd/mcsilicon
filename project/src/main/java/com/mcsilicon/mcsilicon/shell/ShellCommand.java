package com.mcsilicon.mcsilicon.shell;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** 명령어 하나. args는 명령어 이름을 뺀 나머지 토큰(공백 기준, 따옴표/파이프/리다이렉션 없음). */
@FunctionalInterface
public interface ShellCommand {
    /** 출력으로 보여줄 줄(채팅 메시지로 한 줄씩 전송). 실패하면 ShellException을 던진다. */
    List<String> execute(ServerPlayer player, ShellSession session, List<String> args);
}
