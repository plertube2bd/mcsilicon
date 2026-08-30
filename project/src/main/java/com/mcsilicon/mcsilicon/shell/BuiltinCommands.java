package com.mcsilicon.mcsilicon.shell;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** cp/mv/mkdir/rmdir/rm/clear/cd/ls/cat/exit - 스펙에 명시된 기본 명령어들. */
public final class BuiltinCommands {
    private BuiltinCommands() {}

    private static Path real(ShellSession s, String arg) {
        String v = VirtualFileSystem.normalizeVirtual(s.cwd, arg);
        return VirtualFileSystem.toReal(s.username, v);
    }

    private static List<String> stripFlag(List<String> args, String flag, boolean[] outFound) {
        List<String> rest = new ArrayList<>();
        boolean found = false;
        for (String a : args) {
            if (a.equals(flag)) found = true; else rest.add(a);
        }
        outFound[0] = found;
        return rest;
    }

    public static final ShellCommand LS = (player, s, args) -> {
        String target = args.isEmpty() ? s.cwd : VirtualFileSystem.normalizeVirtual(s.cwd, args.get(0));
        Path dir = VirtualFileSystem.toReal(s.username, target);
        List<String> names = VirtualFileSystem.ls(dir);
        return names.isEmpty() ? List.of("(비어있음)") : names;
    };

    public static final ShellCommand CD = (player, s, args) -> {
        String target = args.isEmpty() ? "/" : args.get(0);
        String v = VirtualFileSystem.normalizeVirtual(s.cwd, target);
        Path real = VirtualFileSystem.toReal(s.username, v);
        if (!java.nio.file.Files.isDirectory(real)) throw new ShellException("그런 디렉토리가 없습니다: " + target);
        s.cwd = v;
        return List.of();
    };

    public static final ShellCommand CAT = (player, s, args) -> {
        if (args.isEmpty()) throw new ShellException("사용법: cat <파일>");
        List<String> out = new ArrayList<>();
        for (String a : args) {
            String content = VirtualFileSystem.cat(real(s, a));
            out.addAll(List.of(content.split("\n", -1)));
        }
        return out;
    };

    public static final ShellCommand MKDIR = (player, s, args) -> {
        if (args.isEmpty()) throw new ShellException("사용법: mkdir <디렉토리>");
        for (String a : args) VirtualFileSystem.mkdir(real(s, a));
        return List.of();
    };

    public static final ShellCommand RMDIR = (player, s, args) -> {
        if (args.isEmpty()) throw new ShellException("사용법: rmdir <디렉토리>");
        for (String a : args) VirtualFileSystem.rmdir(real(s, a));
        return List.of();
    };

    public static final ShellCommand RM = (player, s, args) -> {
        boolean[] recursive = new boolean[1];
        List<String> rest = stripFlag(args, "-r", recursive);
        if (rest.isEmpty()) throw new ShellException("사용법: rm [-r] <파일...>");
        for (String a : rest) VirtualFileSystem.rm(real(s, a), recursive[0]);
        return List.of();
    };

    public static final ShellCommand CP = (player, s, args) -> {
        boolean[] recursive = new boolean[1];
        List<String> rest = stripFlag(args, "-r", recursive);
        if (rest.size() != 2) throw new ShellException("사용법: cp [-r] <원본> <대상>");
        VirtualFileSystem.cp(real(s, rest.get(0)), real(s, rest.get(1)), recursive[0]);
        return List.of();
    };

    public static final ShellCommand MV = (player, s, args) -> {
        if (args.size() != 2) throw new ShellException("사용법: mv <원본> <대상>");
        VirtualFileSystem.mv(real(s, args.get(0)), real(s, args.get(1)));
        return List.of();
    };

    /** 진짜 화면을 지울 수는 없으니, 빈 줄을 넉넉히 보내 채팅창에서 이전 내용을 밀어내는 방식으로 흉내만 낸다. */
    public static final ShellCommand CLEAR = (player, s, args) -> {
        List<String> blanks = new ArrayList<>();
        for (int i = 0; i < 40; i++) blanks.add("");
        return blanks;
    };

    public static final ShellCommand EXIT = (player, s, args) -> List.of("__EXIT__");
}
