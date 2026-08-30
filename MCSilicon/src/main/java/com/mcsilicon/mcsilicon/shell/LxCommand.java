package com.mcsilicon.mcsilicon.shell;

import com.mcsilicon.mcsilicon.MCSilicon;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 콘솔/채팅에 "/lx"를 치면 가상 쉘에 접속한다. 접속 위치는 항상 "/"이고,
 * 프롬프트는 "username:path% " 형태다. 접속해 있는 동안 보내는 일반 채팅
 * 메시지는 전부 쉘 명령으로 해석된다(ShellChatListener 참고). "exit"으로 나간다.
 */
@Mod.EventBusSubscriber(modid = MCSilicon.MOD_ID)
public final class LxCommand {
    private LxCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("lx").executes(ctx -> {
            CommandSourceStack src = ctx.getSource();
            if (!(src.getEntity() instanceof ServerPlayer player)) {
                src.sendFailure(Component.literal("플레이어만 쓸 수 있습니다"));
                return 0;
            }
            if (ShellManager.get().isActive(player.getUUID())) {
                player.sendSystemMessage(Component.literal("이미 접속해 있습니다"));
                return 0;
            }
            ShellSession session = ShellManager.get().open(player);
            player.sendSystemMessage(Component.literal("MCS 가상 쉘 접속됨. 나가려면 exit"));
            player.sendSystemMessage(Component.literal(session.prompt()));
            return 1;
        }));
    }
}
