package com.mcsilicon.mcsilicon.shell;

import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * 접속한(=/lx로 들어온) 플레이어들의 세션을 들고 있다가, 채팅으로 들어오는 한 줄을
 * 명령어+인자로 나눠 해당 ShellCommand에 넘긴다.
 *
 * 지원 안 하는 것(요청대로 의도적으로 뺌): sudo류 권한 상승, xserver류 프로그램,
 * '>' '>>' '<' '|' '[' ']' 같은 쉘 문법(리다이렉션/파이프/글롭) - 전부 그냥 명령어
 * 인자의 평범한 문자로 취급되거나(따옴표도 없음, 공백으로만 토큰을 나눈다),
 * 애초에 그런 걸 해석하는 코드가 없다.
 */
public final class ShellManager {
    private static final ShellManager INSTANCE = new ShellManager();
    public static ShellManager get() { return INSTANCE; }

    private final Map<UUID, ShellSession> sessions = new HashMap<>();
    private final Map<String, ShellCommand> commands = new HashMap<>();

    private ShellManager() {
        commands.put("ls", BuiltinCommands.LS);
        commands.put("cd", BuiltinCommands.CD);
        commands.put("cat", BuiltinCommands.CAT);
        commands.put("mkdir", BuiltinCommands.MKDIR);
        commands.put("rmdir", BuiltinCommands.RMDIR);
        commands.put("rm", BuiltinCommands.RM);
        commands.put("cp", BuiltinCommands.CP);
        commands.put("mv", BuiltinCommands.MV);
        commands.put("clear", BuiltinCommands.CLEAR);
        commands.put("exit", BuiltinCommands.EXIT);
        commands.put("mcc", MccCommand.MCC);
        commands.put("%mount", MountCommands.MOUNT);
        commands.put("%accmnt", MountCommands.ACCMNT);
        commands.put("%denmnt", MountCommands.DENMNT);
    }

    public boolean isActive(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public ShellSession open(ServerPlayer player) {
        ShellSession session = new ShellSession(player.getGameProfile().getName());
        sessions.put(player.getUUID(), session);
        return session;
    }

    public void close(UUID playerId) {
        sessions.remove(playerId);
    }

    public ShellSession sessionOf(UUID playerId) {
        return sessions.get(playerId);
    }

    /** 금지된 쉘 기호가 섞여 있으면 바로 거절 메시지를 준다(사용자가 헷갈리지 않게). */
    private static final Set<Character> FORBIDDEN_SYMBOLS = Set.of('>', '<', '|', '[', ']');

    /** 한 줄을 명령어+인자로 나눠 실행한다. 결과는 채팅에 그대로 보여줄 줄들이다. */
    public List<String> run(ServerPlayer player, ShellSession session, String line) {
        line = line.trim();
        if (line.isEmpty()) return List.of();

        for (char c : line.toCharArray()) {
            if (FORBIDDEN_SYMBOLS.contains(c)) {
                return List.of("지원하지 않는 쉘 기호입니다: '" + c + "' (리다이렉션/파이프/글롭 없음)");
            }
        }
        if (line.startsWith("sudo") || line.equals("sudo") || line.startsWith("sudo ")) {
            return List.of("sudo는 지원하지 않습니다");
        }

        String[] tokens = line.split("\\s+");
        String cmdName = tokens[0];
        List<String> args = new ArrayList<>(Arrays.asList(tokens).subList(1, tokens.length));

        ShellCommand cmd = commands.get(cmdName);
        if (cmd == null) {
            return List.of("알 수 없는 명령어입니다: " + cmdName);
        }
        try {
            return cmd.execute(player, session, args);
        } catch (ShellException e) {
            return List.of("오류: " + e.getMessage());
        } catch (Exception e) {
            return List.of("내부 오류: " + e.getMessage());
        }
    }
}
