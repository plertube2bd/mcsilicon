package com.mcsilicon.mcsilicon.shell;

import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

/**
 * "가상 리눅스 쉘"이 실제로 디스크에 쓰는 위치.
 *
 * 월드 세이브 폴더가 아니라 마인크래프트 실행 디렉토리(FMLPaths.GAMEDIR - 기본
 * 설치 기준으로는 Windows에서 %APPDATA%/.minecraft, Linux에서 ~/.minecraft에
 * 해당하는 위치를 Forge가 알아서 찾아준다) 아래 mcs/root 에 저장한다. 그래서
 * 월드를 나갔다 다시 들어오거나, 다른 월드를 새로 만들어도 이 파일들은 그대로
 * 남는다 - 특정 월드 세이브에 종속되지 않는다.
 *
 * 구조:
 *   <게임 디렉토리>/mcs/root/home/<username>/...   각 유저의 홈
 *   <게임 디렉토리>/mcs/root/.mounts.properties     마운트 테이블
 *   <게임 디렉토리>/mcs/root/.accmnt.properties     유저 간 마운트 허용 목록
 */
public final class ShellPaths {
    private ShellPaths() {}

    public static Path root() {
        return FMLPaths.GAMEDIR.get().resolve("mcs").resolve("root");
    }

    public static Path homeOf(String username) {
        return root().resolve("home").resolve(username);
    }

    public static Path mountsFile() {
        return root().resolve(".mounts.properties");
    }

    public static Path accmntFile() {
        return root().resolve(".accmnt.properties");
    }
}
