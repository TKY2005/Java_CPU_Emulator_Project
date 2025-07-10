import java.util.*;

public abstract class CPU {

    public static final int INS_EXT = 0xff;
    public static final int INS_NOP = 0x01;
    public static final int INS_SET = 0x02;
    public static final int INS_OUT = 0x03;
    public static final int INS_ADD = 0x04;
    public static final int INS_SUB = 0x05;
    public static final int INS_MUL = 0x06;
    public static final int INS_DIV = 0x07;
    public static final int INS_POW = 0x08;
    public static final int INS_SQRT = 0x09;
    public static final int INS_RND = 0x0a;
    public static final int INS_CTI = 0x0b;
    public static final int INS_INC = 0x0c;
    public static final int INS_DEC = 0x0d;
    public static final int INS_LA = 0x0e;
    public static final int INS_LLEN = 0x0f;
    public static final int INS_STR = 0x10;
    public static final int INS_OUTS = 0x11;
    public static final int INS_INP = 0x12;
    public static final int INS_INPS = 0x13;
    public static final int INS_PUSH = 0x14;
    public static final int INS_POP = 0x15;
    public static final int INS_CALL = 0x16;
    public static final int INS_CE = 0x17;
    public static final int INS_CNE = 0x18;
    public static final int INS_CL = 0x19;
    public static final int INS_CLE = 0x1a;
    public static final int INS_CG = 0x1b;
    public static final int INS_CGE = 0x1c;
    public static final int INS_CB = 0x1d;
    public static final int INS_JMP = 0x1e;
    public static final int INS_JE = 0x1f;
    public static final int INS_JNE = 0x20;
    public static final int INS_JL = 0x21;
    public static final int INS_JLE = 0x22;
    public static final int INS_JG = 0x23;
    public static final int INS_JGE = 0x24;
    public static final int INS_JB = 0x25;
    public static final int INS_AND = 0x26;
    public static final int INS_OR = 0x27;
    public static final int INS_XOR = 0x28;
    public static final int INS_NOT = 0x29;
    public static final int INS_NAND = 0x2a;
    public static final int INS_NOR = 0x2b;
    public static final int INS_CMP = 0x2c;
    public static final int INS_LOOP = 0x2d;
    public static final int INS_RET = 0x2e;
    public static final int INS_END = 0x2f;
    public static final int INS_INT = 0x30;


    public static final int REGISTER_MODE = 0;
    public static final int DIRECT_MODE = 1;
    public static final int INDIRECT_MODE = 2;
    public static final int IMMEDIATE_MODE = 3;
    public static final int DATA_MODE = 4;
    public static final int STRING_MODE = 5;
    public static final int FUNCTION_MODE = 6;


    // General CPU variables
    protected final int REGISTER_COUNT = 6;
    protected int[] machineCode;
    protected int currentByte = 0;
    public int bit_length = 0;
    protected boolean canExecute = true;
    protected boolean programEnd = false;
    protected boolean noStep = false;
    protected boolean UIMode = false;

    protected int delayAmountMilliseconds = (int) ( (1.0 / Integer.parseInt(Launcher.appConfig.get("Cycles"))) * 1000 );


    protected StringBuilder outputString = new StringBuilder();

    protected int status_code = 0;


    //Instruction set //
    HashMap<Integer, String> instructionSet = new HashMap<>();
    HashMap<String, Integer> translationMap = new HashMap<>();
    protected HashMap<String, Integer> dataMap = new HashMap<>();
    protected HashMap<String, Integer> functions = new HashMap<>();
    protected Stack<Integer> functionCallStack = new Stack<>();


    public final static char REGISTER_PREFIX = '$';
    public final static char DIRECT_MEMORY_PREFIX = '%';
    public final static char IMMEDIATE_PREFIX = '!';
    public final static char INDIRECT_MEMORY_PREFIX = '&';
    public final static char DATA_PREFIX = '~';
    public final static char STRING_PREFIX = '\"';
    public final static String HEX_PREFIX = "#";
    public final static String CHAR_PREFIX = "@";
    public static final String HEX_MEMORY = "*";
    public final static String SIGNAL_PREFIX = "^";

    public static final byte NULL_TERMINATOR = 0x00;

    // Special Purpose Register Codes for faster access
    protected int PC = 6;
    protected int SP = 7;
    protected int SS = 8;
    protected int SE = 9;
    protected int DP = 10;
    protected int DI = 11;
    ///
    /// ////////////////////////////////////////////
    public CPU() {
        System.out.println("Setting up CPU.");
        instructionSet.put(INS_EXT, "ext");
        instructionSet.put(INS_NOP, "nop");
        instructionSet.put(INS_SET, "set");
        instructionSet.put(INS_OUT, "out");
        instructionSet.put(INS_ADD, "add");
        instructionSet.put(INS_SUB, "sub");
        instructionSet.put(INS_MUL, "mul");
        instructionSet.put(INS_DIV, "div");
        instructionSet.put(INS_POW, "pow");
        instructionSet.put(INS_SQRT, "sqrt");
        instructionSet.put(INS_RND, "rnd");
        instructionSet.put(INS_CTI, "cti");
        instructionSet.put(INS_INC, "inc");
        instructionSet.put(INS_DEC, "dec");
        instructionSet.put(INS_LA, "la");
        instructionSet.put(INS_LLEN, "llen");
        instructionSet.put(INS_STR, "str");
        instructionSet.put(INS_OUTS, "outs");
        instructionSet.put(INS_INP, "inp");
        instructionSet.put(INS_INPS, "inps");
        instructionSet.put(INS_PUSH, "push");
        instructionSet.put(INS_POP, "pop");
        instructionSet.put(INS_CALL, "call");
        instructionSet.put(INS_CE, "ce");
        instructionSet.put(INS_CNE, "cne");
        instructionSet.put(INS_CL, "cl");
        instructionSet.put(INS_CLE, "cle");
        instructionSet.put(INS_CG, "cg");
        instructionSet.put(INS_CGE, "cge");
        instructionSet.put(INS_CB, "cb");
        instructionSet.put(INS_JMP, "jmp");
        instructionSet.put(INS_JE, "je");
        instructionSet.put(INS_JNE, "jne");
        instructionSet.put(INS_JL, "jl");
        instructionSet.put(INS_JLE, "jle");
        instructionSet.put(INS_JG, "jg");
        instructionSet.put(INS_JGE, "jge");
        instructionSet.put(INS_JB, "jb");
        instructionSet.put(INS_AND, "and");
        instructionSet.put(INS_OR, "or");
        instructionSet.put(INS_XOR, "xor");
        instructionSet.put(INS_NOT, "not");
        instructionSet.put(INS_NAND, "nand");
        instructionSet.put(INS_NOR, "nor");
        instructionSet.put(INS_CMP, "cmp");
        instructionSet.put(INS_LOOP, "loop");
        instructionSet.put(INS_RET, "ret");
        instructionSet.put(INS_END, "end");
        instructionSet.put(INS_INT, "int");


        translationMap = createTranslationMap(instructionSet);

    }

    public abstract void reset();

    public abstract int readByte(int address);
    public abstract int[] readWord(int startAddress);

    private HashMap<String, Integer> createTranslationMap(HashMap<Integer, String> instructionSet){
        HashMap<String, Integer> translationMap = new HashMap<>();
        for(Map.Entry<Integer, String> entry : instructionSet.entrySet()){
            translationMap.put(entry.getValue(), entry.getKey());
        }
        return translationMap;
    }

   public abstract int[] toMachineCode(String instruction);
    public abstract int[] compileCode(String code);
    public abstract void executeCompiledCode(int[] machine_code);

    public String dumpROM(){
        StringBuilder hexDump = new StringBuilder();
        int padding = 16 / 4;

        for(int i = 0; i < machineCode.length; i++){
            if (i % 5 == 0){
                hexDump.append("\n");
                hexDump.append(String.format("%04X : \t", i));
            }

            hexDump.append(String.format("0x%X" ,machineCode[i])).append(" ");
            //hexDump.append("0x").append(leftPad(Integer.toHexString(machineCode[i]).toUpperCase(), padding, '0')).append(" ");
        }
        return hexDump.toString();
    }

    public abstract String dumpMemory();
    public abstract String dumpFlags();
    public abstract String dumpRegisters();


   public static boolean isNumber(String str) {
        if (str == null || str.trim().isEmpty()) return false;

        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public String leftPad(String s, int l, char p) {

        // Create a StringBuilder to build the padded string
        StringBuilder sb = new StringBuilder();

        // Append the pad character until the
        // total length matches the required length
        while (sb.length() + s.length() < l) {
            sb.append(p);
        }

        sb.append(s);
        return sb.toString();
    }

    public abstract void setUIupdateListener(onStepListener listener);


    public abstract void set(short[] destination, short[] source);
    public abstract void out(short[] destination);
    public abstract void add(short[] destination, short[] source);
    public abstract void sub(short[] destination, short[] source);
    public abstract void mul(short[] destination, short[] source);
    public abstract void div(short[] destination, short[] source);
    public abstract void not(short[] source);
    public abstract void and(short[] destination, short[] source);
    public abstract void or(short[] destination, short[] source);
    public abstract void xor(short[] destination, short[] source);
    public abstract void nand(short[] destination, short[] source);
    public abstract void nor(short[] destination, short[] source);
    public abstract void pow(short[] destination, short[] source);
    public abstract void sqrt(short[] destination);
    public abstract void rnd(short[] destination, short[] source);
    public abstract void inc(short[] destination);
    public abstract void dec(short[] destination);
    public abstract void la(short[] source);
    // llen and outs don't have functions.

    public abstract void push(short[] source);
    public abstract void pop(short[] source);
    public abstract void call(int address, int return_address);
    public abstract void jmp();
    public abstract void cmp(short[] destination, short[] source);
}
