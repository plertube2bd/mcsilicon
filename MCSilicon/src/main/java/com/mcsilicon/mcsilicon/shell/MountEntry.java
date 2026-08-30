package com.mcsilicon.mcsilicon.shell;

/** 마운트 하나: owner(마운트를 건 사람) 관점에서 localPath 자리에 targetUser의 홈이 물려있다. */
public final class MountEntry {
    public final String owner;
    public final String localPath; // 정규화된 가상 절대 경로, 예: "/borrowed"
    public final String targetUser;

    public MountEntry(String owner, String localPath, String targetUser) {
        this.owner = owner;
        this.localPath = localPath;
        this.targetUser = targetUser;
    }
}
