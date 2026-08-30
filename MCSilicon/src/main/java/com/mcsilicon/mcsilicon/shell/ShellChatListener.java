package com.mcsilicon.mcsilicon.shell;

import com.mcsilicon.mcsilicon.MCSilicon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * /lx로 접속해 있는 플레이어가 보내는 일반 채팅 메시지를 쉘 명령으로 가로챈다
 * (다른 플레이어에게는 채팅으로 안 보이게 이벤트를 취소한다).
 */
@Mod.EventBusSubscriber(modid = MCSilicon.MOD_ID)
public final class ShellChatListener {
    private ShellChatListener() {}

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (!ShellManager.get().isActive(player.getUUID())) return;

        event.setCanceled(true);
        ShellSession session = ShellManager.get().sessionOf(player.getUUID());
        String line = event.getRawText();

        java.util.List<String> output = ShellManager.get().run(player, session, line);

        boolean exiting = output.contains("__EXIT__");
        for (String out : output) {
            if (out.equals("__EXIT__")) continue;
            player.sendSystemMessage(Component.literal(out));
        }

        if (exiting) {
            ShellManager.get().close(player.getUUID());
            player.sendSystemMessage(Component.literal("쉘을 종료했습니다"));
            return;
        }

        player.sendSystemMessage(Component.literal(session.prompt()));
    }
}
