package com.mcsilicon.mcsilicon.signal;

import com.mcsilicon.mcsilicon.MCSilicon;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 매 서버 레벨 틱의 끝에서 SignalNetworkManager#tick 을 호출한다.
 * 여기서 한 틱 내에 모든 조합 논리가 완전히 settle 될 때까지 반복되므로,
 * 별도의 지연(DELAY/CLK) 명령이 없는 칩 로직은 항상 같은 틱 안에서 끝난다.
 */
@Mod.EventBusSubscriber(modid = MCSilicon.MOD_ID)
public final class SignalTickHandler {
    private SignalTickHandler() {}

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel serverLevel)) return;

        SignalNetworkManager.get(serverLevel).tick(serverLevel);
    }
}
