package com.mcsilicon.mcsilicon.signal;

import com.mcsilicon.mcsilicon.MCSilicon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 레벨(차원)마다 하나씩 존재하며, 다음 두 가지를 담당한다.
 *
 * 1) 와이어 블록들의 연결성(WireNetwork)을 계산한다. 레드스톤과 무관한 독자 회로이므로
 *    감쇠 없이 하나의 망 전체가 동일한 SignalValue(0/1 또는 IBUSRULE&lt;n&gt; 버스)를 공유한다.
 * 2) 매 틱마다, 특별한 지연 명령이 없는 한 모든 칩의 조합 논리가 같은 틱 안에서
 *    완전히 안정화(settle)되도록 반복 실행한다. 이것이 "요구 조건 2"의 구현이다.
 */
public final class SignalNetworkManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCSilicon/SignalNetwork");
    private static final int MAX_SETTLE_ITERATIONS = 256;

    private static final Map<ResourceKey<Level>, SignalNetworkManager> MANAGERS = new HashMap<>();

    public static SignalNetworkManager get(Level level) {
        return MANAGERS.computeIfAbsent(level.dimension(), k -> new SignalNetworkManager());
    }

    /** 위상이 변경되어 다시 계산해야 하는 좌표(와이어 놓임/부서짐, 칩 로드 등). */
    private final Set<BlockPos> topologyDirty = new LinkedHashSet<>();

    /** 이번 틱에 재실행이 필요한 칩 좌표. */
    private final Set<BlockPos> executeQueue = new LinkedHashSet<>();

    private final Map<BlockPos, WireNetwork> netByWirePos = new HashMap<>();

    public void markTopologyDirty(BlockPos pos) {
        topologyDirty.add(pos.immutable());
    }

    public void markChipDirty(BlockPos pos) {
        executeQueue.add(pos.immutable());
    }

    /** 서버 레벨 틱 종료 시 호출된다 (ServerTickEvents 참고). */
    public void tick(ServerLevel level) {
        if (!topologyDirty.isEmpty()) {
            rebuildDirtyNetworks(level);
            topologyDirty.clear();
        }
        settle(level);
    }

    // ---------------------------------------------------------------
    // 1) 연결성(위상) 재계산
    // ---------------------------------------------------------------

    private void rebuildDirtyNetworks(ServerLevel level) {
        Set<BlockPos> seeds = new LinkedHashSet<>(topologyDirty);
        Set<BlockPos> processed = new HashSet<>();

        for (BlockPos seed : seeds) {
            if (processed.contains(seed)) continue;

            // 이전에 속했던 망에서 제거(빈 망 정리)
            netByWirePos.remove(seed);

            if (!(level.getBlockEntity(seed) instanceof IWireConnectable)) {
                continue; // 와이어가 아니면(부서짐 등) 위상만 초기화하고 끝
            }

            WireNetwork net = floodFillNetwork(level, seed);
            for (BlockPos p : net.wirePositions()) {
                netByWirePos.put(p, net);
                processed.add(p);
            }
            attachChipPorts(level, net);
        }
    }

    private WireNetwork floodFillNetwork(ServerLevel level, BlockPos start) {
        WireNetwork net = new WireNetwork();
        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            BlockEntity be = level.getBlockEntity(cur);
            if (!(be instanceof IWireConnectable wire)) continue;

            net.wirePositions().add(cur.immutable());
            net.setWidth(wire.preferredWidth());

            for (Direction dir : Direction.values()) {
                BlockPos next = cur.relative(dir);
                if (visited.contains(next)) continue;
                if (level.getBlockEntity(next) instanceof IWireConnectable) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }
        return net;
    }

    /** 망에 인접한 칩들의 포트(INPUT/OUTPUT)를 driver/consumer로 등록한다. */
    private void attachChipPorts(ServerLevel level, WireNetwork net) {
        net.drivers().clear();
        net.consumers().clear();

        for (BlockPos wirePos : net.wirePositions()) {
            for (Direction dir : Direction.values()) {
                BlockPos chipPos = wirePos.relative(dir);
                if (!(level.getBlockEntity(chipPos) instanceof IChipNode chip)) continue;

                Direction faceTowardWire = dir.getOpposite();
                for (SignalPort port : chip.ports()) {
                    if (port.face() == faceTowardWire) {
                        net.setWidth(port.width());
                        if (port.direction() == PortDirection.OUTPUT) {
                            net.drivers().add(port);
                        } else {
                            net.consumers().add(port);
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // 2) 매 틱 조합 논리 settle 루프 (요구 조건 2)
    // ---------------------------------------------------------------

    private void settle(ServerLevel level) {
        Set<WireNetwork> networks = new LinkedHashSet<>(netByWirePos.values());
        Set<BlockPos> pending = new LinkedHashSet<>(executeQueue);
        executeQueue.clear();

        int iterations = 0;
        boolean changed = true;

        while (changed) {
            changed = false;
            iterations++;
            if (iterations > MAX_SETTLE_ITERATIONS) {
                LOGGER.warn("[{}] 조합 루프(오실레이션) 감지 - {}회 반복 후 강제 종료", level.dimension().location(), iterations);
                break;
            }

            // 2-a. 각 망의 값을 driver 출력들의 와이어드-OR로 재계산
            for (WireNetwork net : networks) {
                SignalValue resolved = SignalValue.zero(net.width());
                for (SignalPort driver : net.drivers()) {
                    if (!(level.getBlockEntity(driver.chipPos()) instanceof IChipNode chip)) continue;
                    resolved = resolved.or(chip.getOutputValue(driver).resized(net.width()));
                }

                if (!resolved.equals(net.value())) {
                    net.setValue(resolved);
                    changed = true;

                    for (BlockPos wirePos : net.wirePositions()) {
                        if (level.getBlockEntity(wirePos) instanceof IWireConnectable wire) {
                            wire.onNetworkValueChanged(resolved);
                        }
                    }
                    for (SignalPort consumer : net.consumers()) {
                        if (level.getBlockEntity(consumer.chipPos()) instanceof IChipNode chip) {
                            chip.setInputValue(consumer, resolved.resized(consumer.width()));
                            pending.add(consumer.chipPos());
                        }
                    }
                }
            }

            // 2-b. 입력이 바뀐 칩들의 DSL 프로그램을 즉시(같은 틱 내) 재실행
            Set<BlockPos> toRun = new LinkedHashSet<>(pending);
            pending.clear();
            for (BlockPos chipPos : toRun) {
                if (level.getBlockEntity(chipPos) instanceof IChipNode chip) {
                    boolean outputChanged = chip.tickExecute();
                    if (outputChanged) changed = true;
                }
            }
        }
    }

    public void remove(BlockPos pos) {
        netByWirePos.remove(pos);
        topologyDirty.remove(pos);
        executeQueue.remove(pos);
    }
}
