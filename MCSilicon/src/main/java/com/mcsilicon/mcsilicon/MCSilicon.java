package com.mcsilicon.mcsilicon;

import com.mcsilicon.mcsilicon.registry.ModBlockEntities;
import com.mcsilicon.mcsilicon.registry.ModBlocks;
import com.mcsilicon.mcsilicon.registry.ModItems;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * MCSilicon 진입점.
 *
 * 이 모드는 레드스톤을 사용하지 않는 독자 디지털 신호망(SignalNetwork)과,
 * 칩 블록에 작성한 DSL 코드를 컴파일하여 실행하는 반도체 시뮬레이션을 제공한다.
 *
 * 핵심 규칙:
 *  1) 신호는 레드스톤과 별개의 커스텀 신호(0/1, IBUSRULE<n> 버스)로 전달된다.
 *  2) 별도 지연 명령이 없는 한 한 틱 내에 모든 칩의 연산이 완료된다.
 *  3) 칩 로직은 블록 조합이 아니라, 칩 블록 내부에 저장된 DSL 소스를
 *     컴파일한 프로그램(ChipProgram)을 실행하는 방식으로 동작한다.
 */
@Mod(MCSilicon.MOD_ID)
public class MCSilicon {
    public static final String MOD_ID = "mcsilicon";

    public MCSilicon() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
    }
}
