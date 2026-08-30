package com.mcsilicon.mcsilicon.shell;

import com.mcsilicon.mcsilicon.dsl.ChipProgram;
import com.mcsilicon.mcsilicon.dsl.ChipRegistry;
import com.mcsilicon.mcsilicon.dsl.DslException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * mcc <칩파일> [-o <등록이름>]
 *
 * <칩파일>(확장자 없으면 ".mcsl"도 시도)을 MCSL로 컴파일한다. 성공하면
 * ChipRegistry(호출한 플레이어가 있는 차원 기준)에 다음 경로로 등록되어
 * 바로 USE CHIP으로 쓸 수 있게 된다:
 *   -o 없이: mcs:<사용자>:<칩파일이름>
 *   -o 있으면: mcs:<사용자>:<등록이름>
 *
 * 물리적으로 칩 블록을 놓지 않아도 이 방식으로 등록된 칩은 그대로 유효하다
 * (등록 경로는 그 칩 정의 내부의 NEW 이름과는 무관하게 독립적으로 관리된다 -
 * 물리 블록이 쓰는 "이름만 있는" 등록과 절대 경로가 겹칠 일이 없다. 일반
 * 식별자(IDENT)는 애초에 ':'를 포함할 수 없기 때문이다).
 */
public final class MccCommand {
    private MccCommand() {}

    public static final ShellCommand MCC = (player, s, args) -> {
        if (args.isEmpty()) throw new ShellException("사용법: mcc <칩파일> [-o <등록이름>]");

        String sourceArg = args.get(0);
        String outName = null;
        for (int i = 1; i < args.size(); i++) {
            if (args.get(i).equals("-o")) {
                if (i + 1 >= args.size()) throw new ShellException("-o 뒤에 등록할 이름이 필요합니다");
                outName = args.get(i + 1);
                i++;
            }
        }

        Path real = resolveSourceFile(s, sourceArg);
        String source = VirtualFileSystem.cat(real);

        ChipProgram program;
        try {
            program = ChipProgram.compile(source);
        } catch (DslException e) {
            return List.of("컴파일 실패: " + e.getMessage());
        }

        String baseName = outName != null ? outName : bareName(sourceArg);
        String registered = "mcs:" + s.username + ":" + baseName;

        ChipRegistry.get(player.level()).publish(registered, program.def());

        List<String> out = new ArrayList<>();
        out.add("컴파일 성공: " + registered);
        out.add("이제 다른 칩에서 USE CHIP " + registered + "; 로 쓸 수 있습니다");
        return out;
    };

    private static Path resolveSourceFile(ShellSession s, String arg) {
        String v = VirtualFileSystem.normalizeVirtual(s.cwd, arg);
        Path direct = VirtualFileSystem.toReal(s.username, v);
        if (Files.exists(direct) && !Files.isDirectory(direct)) return direct;

        String v2 = VirtualFileSystem.normalizeVirtual(s.cwd, arg + ".mcsl");
        Path withExt = VirtualFileSystem.toReal(s.username, v2);
        if (Files.exists(withExt) && !Files.isDirectory(withExt)) return withExt;

        throw new ShellException("그런 파일이 없습니다: " + arg + " (또는 " + arg + ".mcsl)");
    }

    private static String bareName(String arg) {
        String name = arg;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        if (name.endsWith(".mcsl")) name = name.substring(0, name.length() - 5);
        return name;
    }
}
