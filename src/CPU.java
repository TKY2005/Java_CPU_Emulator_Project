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
    public static final int INS_OUTC = 0x31;
    public static final int INS_SHL = 0x32;
    public static final int INS_SHR = 0x33;
    public static final int INS_OUTSW = 0x34;
    public static final int INS_LENW = 0x35;



    /// ///////////// Interrupts ////////////////////
    public static final int INT_INPUT_STR = 0x00;
    public static final int INT_INPUT_NUM = 0x01;
    public static final int INT_STRING_CONCAT = 0x02;
    public static final int INT_DEBUG = 0x03;
    public static final int INT_DATE = 0x04;
    public static final int INT_FILE = 0x05;
    public static final int INT_STR_CPY = 0x06;
    public static final int INT_MEM_CPY = 0x07;

    public static final int FILE_READ = 0x00;
    public static final int FILE_WRITE = 0x01;
    public static final int FILE_APPEND = 0x02;
    public static final int FILE_DELETE = 0x03;


    public static final int REGISTER_MODE = 0;
    public static final int DIRECT_MODE = 1;
    public static final int INDIRECT_MODE = 2;
    public static final int IMMEDIATE_MODE = 3;
    public static final int DATA_MODE = 4;
    public static final int STRING_MODE = 5;
    public static final int FUNCTION_MODE = 6;
    public static final int REGISTER_WORD_MODE = 7;
    public static final int DIRECT_WORD_MODE = 8;
    public static final int INDIRECT_WORD_MODE = 9;

    public static final int DATA_BYTE_MODE = 1;
    public static final int DATA_WORD_MODE = 2;

    public static final long UI_UPDATE_MAX_INTERVAL = Long.parseLong(Launcher.appConfig.get("UiUpdateInterval"));
    protected long lastTimeSinceUpdate = 0;


    // General CPU variables
    protected final int REGISTER_COUNT = 6;
    protected int[] machineCode;
    protected HashMap<Integer, Integer[]> eachInstruction;
    protected int currentByte = 0;
    public int bit_length = 0;
    protected boolean canExecute = true;
    protected boolean programEnd = false;
    protected boolean noStep = false;
    protected boolean UIMode = false;
    protected int currentLine = 1;

    protected int MAX_STRING_LENGTH = 250;

    protected HashMap<Integer, Integer> lineMap = new HashMap<>();

    protected int max_pair_value = 0xffff;
    protected int max_byte_value = 255;
    protected int last_addressable_location;

    protected int delayAmountMilliseconds = (int) ( (1.0 / Integer.parseInt(Launcher.appConfig.get("Cycles"))) * 1000 );


    protected StringBuilder outputString = new StringBuilder();
    protected String output = "";

    protected int status_code = 0;

    protected short[] memory;

    //protected float ROMpercentage = (35.0f / 100);
    protected float DATApercentage = (Float.parseFloat(Launcher.appConfig.get("DataPercentage")) / 100);
    protected float STACKpercentage = (Float.parseFloat(Launcher.appConfig.get("StackPercentage")) / 100);

    protected int dataOffset;
    protected int dataOrigin;

    protected float memorySizeKB;
    protected float ROMsizeKB, DATAsizeKB, STACKsizeKB;
    protected int ROMsizeB, DATAsizeB, STACKsizeB, mem_size_B;
    protected int rom_start, data_start, stack_start;
    protected int rom_end, data_end, stack_end;

    String memInitMsg;

    // Flags N = negative, C = carry, O = overflow, Z = zero //
    protected boolean N, C, O, Z;
    // T = trap, E = Error, I = Interrupt
    protected boolean T, E, I;
    /// /////////////////////////////////////////////


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
    public static final char MEMORY_SEGMENT_OFFSET_PREFIX = '[';
    public final static char DATA_PREFIX = '~';
    public static final char DATA_PREFIX_ALT = '=';
    public final static char STRING_PREFIX = '\"';
    public final static String HEX_PREFIX = "0x";
    public final static String CHAR_PREFIX = "@";
    public static final String HEX_MEMORY = "*";
    public final static String SIGNAL_PREFIX = "^";
    public static final String COMMENT_PREFIX = ";";

    public static final char ESC_NEWLINE = 'n';
    public static final char ESC_NULL = '0';
    public static final char ESC_TAB = 't';
    public static final char ESC_BACKLASH = '\\';
    public static final char ESC_DOUBLE_QUOTE = '\"';
    public static final char ESC_UNICODE = 'Ω';
    public static final char ESC_UNICODE_ESCAPE = 'u';

    public static final byte ARRAY_TERMINATOR = 0x7F;
    public static final byte TEXT_SECTION_END = (byte) 0xEA;
    public static final byte MEMORY_SECTION_END = (byte) 0xCC;

    public HashMap<String, Byte> programSignals = new HashMap<>();

    // Special Purpose Register Codes for faster access
    protected int PC = 6;
    protected int SP = 7;
    protected int SS = 8;
    protected int SE = 9;
    protected int DP = 10;
    protected int DI = 11;
    ///
    /// ////////////////////////////////////////////
    ///
    static String signature = "Made by T.K.Y";
    static String lastUpdateDate = " 9/8/2025";
    static String compilerVersion = " V1.1";


    public CPU() {

        Logger.source = "CPU_GENERIC";

        System.out.println("Setting up CPU.");
        Logger.addLog("Setting up CPU.");

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
        instructionSet.put(INS_LLEN, "len");
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
        instructionSet.put(INS_OUTC, "outc");
        instructionSet.put(INS_SHL, "shl");
        instructionSet.put(INS_SHR, "shr");
        instructionSet.put(INS_OUTSW, "outsw");
        instructionSet.put(INS_LENW, "lenw");


        translationMap = createTranslationMap(instructionSet);
        programSignals.put("at", ARRAY_TERMINATOR);
        programSignals.put("re", TEXT_SECTION_END);
        programSignals.put("me", MEMORY_SECTION_END);

    }

    public abstract void reset();
    public abstract Integer getPC();


    private HashMap<String, Integer> createTranslationMap(HashMap<Integer, String> instructionSet){
        HashMap<String, Integer> translationMap = new HashMap<>();
        for(Map.Entry<Integer, String> entry : instructionSet.entrySet()){
            translationMap.put(entry.getValue(), entry.getKey());
        }
        return translationMap;
    }

   public abstract int[] toMachineCode(String instruction);
    public abstract int getInstructionLength(String instruction);
    public abstract int[] compileCode(String code);
    public abstract int[] compileToFileBinary(String code);
    public abstract String disassembleMachineCode(int[] machine_code);
    public abstract void executeCompiledCode(int[] machine_code);

    public String dumpROM(){
        StringBuilder hexDump = new StringBuilder();

        for(int i = 0; i < machineCode.length; i++){
            if (i % 5 == 0){
                hexDump.append("\n");
                hexDump.append(String.format("%04X : \t", i));
            }

            hexDump.append(String.format("0x%02X" ,machineCode[i])).append(" ");
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

    static List<Integer> toByteString(String fullString) {
        List<Integer> string_bytes = new ArrayList<>();

        for(int j = 0; j < fullString.length(); j++){
            if (fullString.charAt(j) == '\\'){
                char a;
                switch (fullString.charAt(j + 1)){

                    case ESC_NEWLINE -> a = '\n';
                    case ESC_NULL -> a = '\0';
                    case ESC_TAB -> a = '\t';
                    case ESC_BACKLASH -> a = '\\';
                    case ESC_UNICODE -> a = 'Ω';
                    case ESC_DOUBLE_QUOTE -> a = '\"';
                    case ESC_UNICODE_ESCAPE -> a = (char) Integer.parseInt( fullString.substring(j + 2), 16 );
                    default -> a = '.';
                }
                string_bytes.add((int) a);
                j++;
                continue;
            }
            string_bytes.add((int) fullString.charAt(j));
        }
        return string_bytes;
    }


    public abstract void setUIupdateListener(onStepListener listener);


    public void triggerProgramError(String errMsg, int errCode){
        status_code = errCode;
        outputString.append("line " + currentLine + " : " + errMsg);
        programEnd = true;
        RuntimeException exceptionType = new RuntimeException("line " + currentLine + " : " + errMsg);
        Logger.addLog("line : " + currentLine + " : " + errMsg);
        Logger.addLog("Program terminated with code : " + status_code);
        Logger.addLog("=============Program ROM=================");
        Logger.addLog(dumpROM());
        Logger.addLog("=============Program registers=================");
        Logger.addLog(dumpRegisters());
        Logger.addLog(dumpFlags());
        Logger.addLog("=============Program memory===================");
        Logger.addLog(dumpMemory());
        Logger.writeLogFile("./ErrLog.log");
        System.out.println("Program terminated with code : " + status_code);
        throw exceptionType;
    }

    public int readByte(int address){
        if (!isValidMemoryAddress(address)){
            String err = String.format("0x%X(%d) is an invalid memory address.");
            triggerProgramError(
                    err, ErrorHandler.ERR_CODE_INVALID_MEMORY_ADDRESS);
        }
        return memory[address];
    }

    public int[] readWord(int startAddress){
        if (!isValidMemoryAddress(startAddress)){
            String err = String.format("0x%X(%d) is an invalid memory address.");
            triggerProgramError(
                    err, ErrorHandler.ERR_CODE_INVALID_MEMORY_ADDRESS);
        }
        if (!isValidMemoryAddress(startAddress + 1)){
            String err = String.format("0x%X(%d) is an invalid memory address.");
            triggerProgramError(
                    err, ErrorHandler.ERR_CODE_INVALID_MEMORY_ADDRESS);
        }

        return new int[] {memory[startAddress], memory[startAddress + 1]};
    }

    // read in little-endian format
    public int bytePairToWordLE(int lowByte, int highByte){
        return (highByte << 8) | lowByte;
    }
    public int bytePairToWordLE(int[] pair){
        return (pair[1] << 8) | pair[0];
    }

    // read in big-endian format
    public int bytePairToWordBE(int lowByte, int highByte){
        return (lowByte << 8) | highByte;
    }
    public int bytePairToWordBE(int[] pair){
        return (pair[0] << 8) | pair[1];
    }

    public void setMemory(int address, int value, int mode){

        if (isValidMemoryAddress(address)){

            if (mode == DATA_BYTE_MODE) memory[address] = (short) value;
            else if (mode == DATA_WORD_MODE){
                if (!isValidMemoryAddress(address + 1)){
                    String err = String.format("0x%X(%d) is an invalid memory address.", address, address);
                    triggerProgramError(
                            err, ErrorHandler.ERR_CODE_INVALID_MEMORY_ADDRESS);
                }
                else{
                    int low = value & 0xff;
                    int high = (value >> 8) & 0xff;
                    memory[address] = (short) low;
                    memory[address + 1] = (short) high;
                }
            }

        }else{
            String err = String.format("0x%X(%d) is an invalid memory address.", address, address);
            triggerProgramError(
                    err, ErrorHandler.ERR_CODE_INVALID_MEMORY_ADDRESS);
        }
    }

    public void setMemory(int address, int value){

        if (isValidMemoryAddress(address)){

            if (value <= max_byte_value) memory[address] = (short) value;
            else {
                if (!isValidMemoryAddress(address + 1)){
                    String err = String.format("0x%X(%d) is an invalid memory address.", address, address);
                    triggerProgramError(
                            err, ErrorHandler.ERR_CODE_INVALID_MEMORY_ADDRESS);
                }
                else{
                    int low = value & 0xff;
                    int high = (value >> 8) & 0xff;
                    memory[address] = (short) low;
                    memory[address + 1] = (short) high;
                }
            }

        }else{
            String err = String.format("0x%X(%d) is an invalid memory address.", address, address);
            triggerProgramError(
                    err, ErrorHandler.ERR_CODE_INVALID_MEMORY_ADDRESS);
        }
    }



    public boolean isValidMemoryAddress(int address){
        return address <= last_addressable_location && address >= 0;
    }

    public String dumpMemoryDebug(int startAddress){
        int chunkSize = 10;
        StringBuilder result = new StringBuilder();
        StringBuilder charSet = new StringBuilder();
        for(int i = startAddress; i <= startAddress + 30; i++){

            if (i % chunkSize == 0) result.append(String.format("%05X :\t", i));

            result.append(String.format("0x%02X\t", memory[i] & 0xff));
            charSet.append( (Character.isLetterOrDigit(memory[i])) ? (char) memory[i] : "." );

            if ((i + 1) % chunkSize == 0){
                result.append("\t\t").append("|").append(charSet).append("|").append("\n");
                charSet.setLength(0);
            }
        }
        return result.toString();
    }

    public void calculateMemorySegments(){
        memorySizeKB = Float.parseFloat(Launcher.appConfig.get("MemSize"));

        mem_size_B = (int) (memorySizeKB * 1024);

        DATAsizeKB = (DATApercentage * memorySizeKB);
        STACKsizeKB = (STACKpercentage * memorySizeKB);

        DATAsizeB = (int) (DATAsizeKB * 1024);
        STACKsizeB = (int) (STACKsizeKB * 1024);

        data_start = 0;
        stack_start = data_start + DATAsizeB;

        data_end = stack_start - 1;
        stack_end = stack_start + STACKsizeB;

        last_addressable_location = data_end;
        dataOrigin = (int) (0.25 * DATAsizeB);
        dataOffset = dataOrigin;

        memInitMsg = String.format("""
                Starting with %sKB of memory. Total of %d locations
                DATA section size: %.3fKB(%dB), start address: 0x%X(%d) -> end address: 0x%X(%d)
                STACK section size: %.3fKB(%dB), start address: 0x%X(%d) -> end address: 0x%X(%d)
                last addressable location: 0x%X(%d)
                data offset location: 0x%X(%d)
                """,
                memorySizeKB, mem_size_B,
                DATAsizeKB, DATAsizeB, data_start, data_start, data_end, data_end,
                STACKsizeKB, STACKsizeB, stack_start, stack_start, stack_end, stack_end,
                last_addressable_location, last_addressable_location,
                dataOffset, dataOffset
                );
    }
}
