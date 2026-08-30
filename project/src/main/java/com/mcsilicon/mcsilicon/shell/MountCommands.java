package com.mcsilicon.mcsilicon.shell;

import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * %mount anotherusername /wantdir  - 다른 유저의 홈을 내 가상 경로에 붙인다.
 *   대상 디렉토리(/wantdir)는 내 쪽에서 존재하지 않거나 비어있어야 한다.
 *   OP면 무조건 가능, 아니면 상대가 %accmnt로 미리 허용해뒀어야 한다.
 * %accmnt username  - (이 쉘의 주인이) username이 나를 마운트하는 걸 허용한다.
 * %denmnt username  - 허용을 취소한다. 이미 마운트되어 있던 자리는 그대로 남지만,
 *   그 시점 내용을 owner 쪽에 스냅샷으로 복사해두고 마운트 연결을 끊는다 - 이후로는
 *   양쪽에 변경사항이 동기화되지 않는다(그냥 owner 소유의 독립된 사본이 된다).
 */
public final class MountCommands {
    private MountCommands() {}

    private static boolean isOp(ServerPlayer player) {
        return player.hasPermissions(2);
    }

    public static final ShellCommand MOUNT = (player, s, args) -> {
        if (args.size() != 2) throw new ShellException("사용법: %mount <다른유저> <붙일디렉토리>");
        String targetUser = args.get(0);
        String wantDirArg = args.get(1);

        if (targetUser.equals(s.username)) throw new ShellException("자기 자신은 마운트할 수 없습니다");

        boolean allowed = isOp(player) || MountManager.get().isAccepted(targetUser, s.username);
        if (!allowed) {
            throw new ShellException("권한이 없습니다 - OP가 아니면 '" + targetUser
                + "'가 먼저 자기 쉘에서 %accmnt " + s.username + " 을 해야 합니다");
        }

        String vWantDir = VirtualFileSystem.normalizeVirtual(s.cwd, wantDirArg);
        Path realWantDir = VirtualFileSystem.toReal(s.username, vWantDir);
        if (!VirtualFileSystem.isEmptyOrMissing(realWantDir)) {
            throw new ShellException("대상 디렉토리가 이미 존재하고 비어있지 않습니다: " + wantDirArg);
        }

        VirtualFileSystem.ensureHome(targetUser);
        MountManager.get().addMount(s.username, vWantDir, targetUser);
        return List.of("마운트됨: " + vWantDir + " -> " + targetUser + "의 홈");
    };

    public static final ShellCommand ACCMNT = (player, s, args) -> {
        if (args.size() != 1) throw new ShellException("사용법: %accmnt <owner유저>");
        MountManager.get().accept(s.username, args.get(0));
        return List.of(args.get(0) + "이(가) 이제 나를 마운트할 수 있습니다");
    };

    public static final ShellCommand DENMNT = (player, s, args) -> {
        if (args.size() != 1) throw new ShellException("사용법: %denmnt <owner유저>");
        String owner = args.get(0);
        MountManager.get().deny(s.username, owner);

        List<MountEntry> removed = MountManager.get().removeMountsBetween(owner, s.username);
        for (MountEntry m : removed) {
            snapshotFreeze(m);
        }
        return List.of(owner + "의 마운트 권한을 취소했습니다"
            + (removed.isEmpty() ? "" : " (이미 마운트된 " + removed.size() + "개 자리는 그 시점 내용으로 고정됩니다)"));
    };

    /** owner가 target의 홈을 m.localPath에 마운트해뒀던 것을, 그 시점 내용의 독립 사본으로 바꾼다. */
    private static void snapshotFreeze(MountEntry m) {
        Path liveSource = ShellPaths.homeOf(m.targetUser).resolve(m.localPath.substring(1));
        Path ownerCopy = ShellPaths.homeOf(m.owner).resolve(m.localPath.substring(1));
        try {
            if (Files.exists(liveSource)) {
                if (Files.isDirectory(liveSource)) {
                    VirtualFileSystem.cp(liveSource, ownerCopy, true);
                } else {
                    VirtualFileSystem.cp(liveSource, ownerCopy, false);
                }
            }
        } catch (ShellException ignored) {
            // 스냅샷이 실패해도 마운트 해제 자체는 이미 끝난 상태로 둔다
        }
    }
}
