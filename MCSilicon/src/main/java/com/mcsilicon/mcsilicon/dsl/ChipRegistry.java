package com.mcsilicon.mcsilicon.dsl;

import com.mcsilicon.mcsilicon.dsl.ast.ChipDef;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 레벨(차원)마다 하나씩 존재하는 "컴파일된 칩" 이름 registry.
 * USE CHIP <name>; 이 참조하는 대상은 이 registry에 실제로 존재해야 한다
 * (해당 이름으로 NEW ... 를 성공적으로 컴파일한 칩 블록이 월드 어딘가에 있어야 함).
 *
 * 보통 칩(GLOBAL 아님): 이름 하나에 슬롯 하나. 같은 이름으로 여러 블록이 컴파일되면
 * 그냥 마지막으로 publish한 것으로 덮어써진다(참조 카운트로 마지막 참조가 사라지면 비워짐).
 *
 * GLOBAL 칩(NEW GLOBAL ...): 이름 하나에 여러 인스턴스가 동시에 존재할 수 있다.
 * get()으로 조회할 때마다 그중 하나를 무작위로 골라 돌려준다 - 여러 인스턴스 중
 * 어느 게 "진짜"인지는 따로 관리하지 않는다(사용자 요청: "그 관리는 하지 마").
 */
public final class ChipRegistry {
    private static final Map<ResourceKey<Level>, ChipRegistry> REGISTRIES = new HashMap<>();

    public static ChipRegistry get(Level level) {
        return REGISTRIES.computeIfAbsent(level.dimension(), k -> new ChipRegistry());
    }

    // ---- 보통 칩: 이름 하나 -> 슬롯 하나 ----
    private final Map<String, ChipDef> chips = new HashMap<>();
    private final Map<String, Integer> refCount = new HashMap<>();

    // ---- GLOBAL 칩: 이름 하나 -> 여러 인스턴스(무작위 선택 풀) ----
    private final Map<String, List<ChipDef>> globalPool = new HashMap<>();

    public synchronized void publish(String name, ChipDef def) {
        if (def.isGlobal) {
            globalPool.computeIfAbsent(name, k -> new ArrayList<>()).add(def);
            return;
        }
        chips.put(name, def);
        refCount.merge(name, 1, Integer::sum);
    }

    /** def를 반드시 함께 넘겨야 한다 - GLOBAL 풀에서 "정확히 이 인스턴스"를 빼기 위해서다. */
    public synchronized void unpublish(String name, ChipDef def) {
        if (def != null && def.isGlobal) {
            List<ChipDef> pool = globalPool.get(name);
            if (pool != null) {
                pool.remove(def); // 참조 동일성으로 정확히 이 인스턴스만 제거
                if (pool.isEmpty()) globalPool.remove(name);
            }
            return;
        }
        Integer c = refCount.get(name);
        if (c == null) return;
        if (c <= 1) {
            refCount.remove(name);
            chips.remove(name);
        } else {
            refCount.put(name, c - 1);
        }
    }

    /** GLOBAL 풀에 인스턴스가 있으면 그중 하나를 무작위로 골라 돌려주고, 없으면 보통 칩 슬롯을 본다. */
    public synchronized ChipDef get(String name) {
        List<ChipDef> pool = globalPool.get(name);
        if (pool != null && !pool.isEmpty()) {
            int i = ThreadLocalRandom.current().nextInt(pool.size());
            return pool.get(i);
        }
        return chips.get(name);
    }
}
