package com.mcsilicon.mcsilicon.dsl.ast;

/** DO 블록의 게이트 조건절에서 쓸 수 있는 고정 7종 게이트. */
public enum GateOp {
    AND, OR, XOR, NAND, NOR, XNOR, NOT;

    /** 인자들(0/1이 아닌 값은 0이 아니면 참으로 취급)에 게이트를 적용해 조건 매칭 결과를 낸다. */
    public boolean apply(long[] args) {
        if (this == NOT) {
            if (args.length != 1) throw new IllegalArgumentException("NOT은 인자가 1개여야 합니다");
            return args[0] == 0;
        }
        int trueCount = 0;
        for (long a : args) if (a != 0) trueCount++;
        return switch (this) {
            case AND -> trueCount == args.length;
            case OR -> trueCount > 0;
            case NAND -> trueCount != args.length;
            case NOR -> trueCount == 0;
            case XOR -> (trueCount % 2) == 1;
            case XNOR -> (trueCount % 2) == 0;
            default -> throw new IllegalStateException();
        };
    }
}
