package com.mcsilicon.mcsilicon.shell;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 두 가지 테이블을 디스크에 영속시키며 관리한다.
 *
 * 1) 마운트 테이블: owner가 자기 가상 경로(localPath)에 targetUser의 홈을 물려놓은 것들.
 * 2) 허용 목록: targetUser가 "나를 마운트해도 좋다"고 허락한 owner 이름 집합
 *    (%accmnt owner / %denmnt owner로 관리). OP는 이 허용 목록과 무관하게 항상 마운트 가능.
 *
 * 파일 형식은 단순 텍스트(한 줄에 항목 하나, '|'로 필드 구분) - 사람이 봐도 읽을 수 있게.
 */
public final class MountManager {
    private static MountManager instance;

    public static synchronized MountManager get() {
        if (instance == null) instance = new MountManager();
        return instance;
    }

    private final List<MountEntry> mounts = new ArrayList<>();
    // targetUser -> 그 사람을 마운트해도 되는 owner 이름 집합
    private final Map<String, Set<String>> accepted = new HashMap<>();

    private MountManager() {
        load();
    }

    // ---------------- 마운트 ----------------

    public synchronized List<MountEntry> mountsOf(String owner) {
        List<MountEntry> result = new ArrayList<>();
        for (MountEntry m : mounts) if (m.owner.equals(owner)) result.add(m);
        return result;
    }

    /** owner의 가상 경로들 중, virtualPath와 가장 길게 일치하는 마운트를 찾는다(없으면 null). */
    public synchronized MountEntry findMount(String owner, String virtualPath) {
        MountEntry best = null;
        for (MountEntry m : mounts) {
            if (!m.owner.equals(owner)) continue;
            if (virtualPath.equals(m.localPath) || virtualPath.startsWith(m.localPath + "/")) {
                if (best == null || m.localPath.length() > best.localPath.length()) best = m;
            }
        }
        return best;
    }

    public synchronized void addMount(String owner, String localPath, String targetUser) {
        mounts.add(new MountEntry(owner, localPath, targetUser));
        save();
    }

    /** denmnt로 허용이 취소됐을 때, 그 owner가 targetUser를 마운트해둔 자리들을 전부 떼어낸다(정적 스냅샷은 호출부에서 처리). */
    public synchronized List<MountEntry> removeMountsBetween(String owner, String targetUser) {
        List<MountEntry> removed = new ArrayList<>();
        Iterator<MountEntry> it = mounts.iterator();
        while (it.hasNext()) {
            MountEntry m = it.next();
            if (m.owner.equals(owner) && m.targetUser.equals(targetUser)) {
                removed.add(m);
                it.remove();
            }
        }
        save();
        return removed;
    }

    // ---------------- 허용 목록 ----------------

    public synchronized boolean isAccepted(String targetUser, String owner) {
        return accepted.getOrDefault(targetUser, Set.of()).contains(owner);
    }

    public synchronized void accept(String targetUser, String owner) {
        accepted.computeIfAbsent(targetUser, k -> new HashSet<>()).add(owner);
        save();
    }

    public synchronized void deny(String targetUser, String owner) {
        accepted.getOrDefault(targetUser, Set.of()).remove(owner);
        save();
    }

    // ---------------- 영속화 ----------------

    private void load() {
        loadMounts();
        loadAccepted();
    }

    private void loadMounts() {
        Path f = ShellPaths.mountsFile();
        if (!Files.exists(f)) return;
        try {
            for (String line : Files.readAllLines(f)) {
                if (line.isBlank()) continue;
                String[] parts = line.split("\\|", 3);
                if (parts.length == 3) mounts.add(new MountEntry(parts[0], parts[1], parts[2]));
            }
        } catch (IOException ignored) {}
    }

    private void loadAccepted() {
        Path f = ShellPaths.accmntFile();
        if (!Files.exists(f)) return;
        try {
            for (String line : Files.readAllLines(f)) {
                if (line.isBlank()) continue;
                String[] parts = line.split("\\|", 2);
                if (parts.length == 2) {
                    accepted.computeIfAbsent(parts[0], k -> new HashSet<>()).add(parts[1]);
                }
            }
        } catch (IOException ignored) {}
    }

    private synchronized void save() {
        try {
            Files.createDirectories(ShellPaths.root());
            List<String> mountLines = mounts.stream()
                .map(m -> m.owner + "|" + m.localPath + "|" + m.targetUser)
                .collect(Collectors.toList());
            Files.write(ShellPaths.mountsFile(), mountLines);

            List<String> accLines = new ArrayList<>();
            for (var e : accepted.entrySet()) {
                for (String owner : e.getValue()) accLines.add(e.getKey() + "|" + owner);
            }
            Files.write(ShellPaths.accmntFile(), accLines);
        } catch (IOException ignored) {
            // 저장 실패해도 세션은 계속 동작(메모리 상태는 유지) - 다음 저장 시도에서 복구될 수 있음
        }
    }
}
