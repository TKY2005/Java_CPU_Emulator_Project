public final class Instruction {
    final int opCode;
    final String mnemonic;
    final int operandCount;
    int cycles;

    Operand[] operands;

    public Instruction(int opCode, String mnemonic, int operandCount){
        this.opCode = opCode;
        this.mnemonic = mnemonic;
        this.operandCount = operandCount;

        // cycles are decided upon the operandCount
        // 1 cycle for the opcode
        // 2 cycles for each operand

        cycles = 1;
        for(int i = 0; i < operandCount; i++) cycles += 2;

        if (operandCount > 0) operands = new Operand[operandCount];
    }

    public int[] toMachineCode(){

        //Instruction format: opCode operandType operandValue
        int[] machineCode = new int[cycles];
        machineCode[0] = opCode;
        int operandIndex = 0;
        if (cycles > 1) {
            for (int i = 1; i < cycles; i += 2) {
                machineCode[i] = operands[operandIndex].type;
                machineCode[i + 1] = operands[operandIndex].value;
                operandIndex++;
            }
        }
        return machineCode;
    }

}

final class Operand{
    public static final int REGISTER = 0;
    public static final int MEMORY_DIRECT = 1;
    public static final int IMMEDIATE = 2;
    public static final int MEMORY_INDIRECT = 3;

    int type;
    int value;
    public Operand(int type, int value){
        this.type = type;
        this.value = value;
    }
}
