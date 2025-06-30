import java.util.*;

public abstract class CPU {

    // General CPU variables
    protected final int REGISTER_COUNT = 6;
    protected int[] machineCode;
    protected int currentByte = 0;
    public int bit_length = 0;
    protected boolean canExecute = true;
    protected boolean programEnd = false;


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
    public final static char HEX_PREFIX = '#';
    public final static char CHAR_PREFIX = '@';
    public final static char SIGNAL_PREFIX = '^';

    public static final byte NULL_TERMINATOR = 0x00;

    // Special Purpose Register Codes for faster access
    protected int PC = 6;
    protected int SP = 7;
    protected int SS = 8;
    protected int SE = 9;
    ///
    /// ////////////////////////////////////////////
    public CPU() {
        System.out.println("Setting up CPU.");
        instructionSet.put(0xff, "ext");
        instructionSet.put(0x01, "nop");
        instructionSet.put(0x02, "set");
        instructionSet.put(0x03, "out");
        instructionSet.put(0x04, "add");
        instructionSet.put(0x05, "sub");
        instructionSet.put(0x06, "mul");
        instructionSet.put(0x07, "div");
        instructionSet.put(0x08, "pow");
        instructionSet.put(0x09, "sqrt");
        instructionSet.put(0x0a, "rnd");
        instructionSet.put(0x0b, "cti");
        instructionSet.put(0x0c, "inc");
        instructionSet.put(0x0d, "dec");
        instructionSet.put(0x0e, "la");
        instructionSet.put(0x0f, "llen");
        instructionSet.put(0x10, "str");
        instructionSet.put(0x11, "outs");
        instructionSet.put(0x12, "inp");
        instructionSet.put(0x13, "inps");
        instructionSet.put(0x14, "push");
        instructionSet.put(0x15, "pop");
        instructionSet.put(0x16, "call");
        instructionSet.put(0x17, "ce");
        instructionSet.put(0x18, "cne");
        instructionSet.put(0x19, "cl");
        instructionSet.put(0x1a, "cle");
        instructionSet.put(0x1b, "cg");
        instructionSet.put(0x1c, "cge");
        instructionSet.put(0x1d, "cb");
        instructionSet.put(0x1e, "jmp");
        instructionSet.put(0x1f, "je");
        instructionSet.put(0x20, "jne");
        instructionSet.put(0x21, "jl");
        instructionSet.put(0x22, "jle");
        instructionSet.put(0x23, "jg");
        instructionSet.put(0x24, "jge");
        instructionSet.put(0x25, "jb");
        instructionSet.put(0x26, "and");
        instructionSet.put(0x27, "or");
        instructionSet.put(0x28, "xor");
        instructionSet.put(0x29, "not");
        instructionSet.put(0x2a, "nand");
        instructionSet.put(0x2b, "nor");
        instructionSet.put(0x2c, "cmp");
        instructionSet.put(0x2d, "loop");
        instructionSet.put(0x2e, "ret");
        instructionSet.put(0x2f, "end");
        instructionSet.put(0x30, "int");


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

}
