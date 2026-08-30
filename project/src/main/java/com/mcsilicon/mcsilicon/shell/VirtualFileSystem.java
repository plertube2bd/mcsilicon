package com.mcsilicon.mcsilicon.shell;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;

/**
 * 가상 절대 경로(POSIX 스타일, 항상 "/"로 시작)를 다루는 유틸리티.
 *
 * 안전 규칙: 사용자가 입력한 경로는 절대 실제 java.nio 경로로 바로 넘기지 않는다.
 * 먼저 가상 경로 공간 안에서만 "."/".."를 계산해서 정규화하고(루트 "/" 위로는
 * 절대 못 올라가게 클램프), 그 다음에야 (마운트 여부를 고려해) 실제 디스크 경로로
 * 딱 한 번 매핑한다. 그 실제 경로가 의도한 사용자 홈(또는 마운트 대상 홈) 바깥으로
 * 새는지 마지막으로 한 번 더 확인한다 - 경로 순회(path traversal) 방지.
 */
public final class VirtualFileSystem {
    private VirtualFileSystem() {}

    /** cwd 기준으로 input을 해석해 정규화된 가상 절대 경로를 만든다(파일 접근은 안 함). */
    public static String normalizeVirtual(String cwd, String input) {
        String base = input.startsWith("/") ? "/" : cwd;
        String combined = input.startsWith("/") ? input : (cwd.equals("/") ? "/" + input : cwd + "/" + input);

        Deque<String> stack = new ArrayDeque<>();
        for (String seg : combined.split("/")) {
            if (seg.isEmpty() || seg.equals(".")) continue;
            if (seg.equals("..")) {
                if (!stack.isEmpty()) stack.removeLast();
                // 루트 위로는 못 올라간다 - 조용히 무시(진짜 쉘도 "/"에서 cd ..는 그냥 "/")
            } else {
                stack.addLast(seg);
            }
        }
        if (stack.isEmpty()) return "/";
        return "/" + String.join("/", stack);
    }

    /** 가상 절대 경로를 실제 디스크 경로로 바꾼다(마운트 고려). username은 이 경로를 요청한 세션의 주인. */
    public static Path toReal(String username, String virtualAbsPath) {
        MountEntry m = MountManager.get().findMount(username, virtualAbsPath);
        Path base;
        String remainder;
        if (m != null) {
            base = ShellPaths.homeOf(m.targetUser);
            remainder = virtualAbsPath.substring(m.localPath.length());
        } else {
            base = ShellPaths.homeOf(username);
            remainder = virtualAbsPath.substring(1); // 맨 앞 "/" 제거
        }
        Path real = remainder.isEmpty() ? base : base.resolve(remainder);
        Path normalizedReal = real.normalize();
        Path normalizedBase = base.normalize();
        if (!normalizedReal.startsWith(normalizedBase)) {
            // 정규화 단계에서 이미 막히지만, 방어적으로 한 번 더 확인
            throw new ShellException("경로가 허용된 범위를 벗어납니다");
        }
        return normalizedReal;
    }

    public static void ensureHome(String username) {
        try {
            Files.createDirectories(ShellPaths.homeOf(username));
        } catch (IOException e) {
            throw new ShellException("홈 디렉토리를 만들 수 없습니다: " + e.getMessage());
        }
    }

    // ---------------- 명령어가 쓰는 연산들 ----------------

    public static List<String> ls(Path dir) {
        if (!Files.isDirectory(dir)) throw new ShellException("디렉토리가 아닙니다");
        try (Stream<Path> s = Files.list(dir)) {
            List<String> names = new ArrayList<>();
            s.sorted().forEach(p -> names.add(Files.isDirectory(p) ? p.getFileName() + "/" : p.getFileName().toString()));
            return names;
        } catch (IOException e) {
            throw new ShellException("목록을 읽을 수 없습니다: " + e.getMessage());
        }
    }

    public static String cat(Path file) {
        if (!Files.exists(file)) throw new ShellException("그런 파일이 없습니다");
        if (Files.isDirectory(file)) throw new ShellException("디렉토리입니다");
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ShellException("파일을 읽을 수 없습니다: " + e.getMessage());
        }
    }

    public static void mkdir(Path dir) {
        if (Files.exists(dir)) throw new ShellException("이미 존재합니다");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new ShellException("디렉토리를 만들 수 없습니다: " + e.getMessage());
        }
    }

    public static void rmdir(Path dir) {
        if (!Files.isDirectory(dir)) throw new ShellException("디렉토리가 아닙니다");
        try {
            if (Files.list(dir).findAny().isPresent()) {
                throw new ShellException("디렉토리가 비어있지 않습니다");
            }
            Files.delete(dir);
        } catch (IOException e) {
            throw new ShellException("삭제할 수 없습니다: " + e.getMessage());
        }
    }

    public static void rm(Path target, boolean recursive) {
        if (!Files.exists(target)) throw new ShellException("그런 파일이 없습니다");
        try {
            if (Files.isDirectory(target)) {
                if (!recursive) throw new ShellException("디렉토리입니다(재귀 삭제가 필요하면 rm -r)");
                deleteRecursive(target);
            } else {
                Files.delete(target);
            }
        } catch (IOException e) {
            throw new ShellException("삭제할 수 없습니다: " + e.getMessage());
        }
    }

    private static void deleteRecursive(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.delete(p); } catch (IOException e) { throw new RuntimeException(e); }
            });
        }
    }

    public static void cp(Path src, Path dst, boolean recursive) {
        if (!Files.exists(src)) throw new ShellException("그런 파일이 없습니다");
        try {
            if (Files.isDirectory(src)) {
                if (!recursive) throw new ShellException("디렉토리입니다(재귀 복사가 필요하면 cp -r)");
                copyRecursive(src, dst);
            } else {
                Path target = Files.isDirectory(dst) ? dst.resolve(src.getFileName()) : dst;
                Files.createDirectories(target.getParent());
                Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new ShellException("복사할 수 없습니다: " + e.getMessage());
        }
    }

    private static void copyRecursive(Path src, Path dst) throws IOException {
        Path target = Files.exists(dst) && Files.isDirectory(dst) ? dst.resolve(src.getFileName()) : dst;
        try (Stream<Path> walk = Files.walk(src)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                Path rel = src.relativize(p);
                Path dest = rel.toString().isEmpty() ? target : target.resolve(rel.toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    public static void mv(Path src, Path dst) {
        if (!Files.exists(src)) throw new ShellException("그런 파일이 없습니다");
        try {
            Path target = Files.isDirectory(dst) ? dst.resolve(src.getFileName()) : dst;
            Files.createDirectories(target.getParent());
            Files.move(src, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ShellException("이동할 수 없습니다: " + e.getMessage());
        }
    }

    public static boolean isEmptyOrMissing(Path dir) {
        if (!Files.exists(dir)) return true;
        if (!Files.isDirectory(dir)) return false;
        try (Stream<Path> s = Files.list(dir)) {
            return s.findAny().isEmpty();
        } catch (IOException e) {
            return false;
        }
    }
}
