import java.util.*;

public class CPUModule16BIT extends CPU {

    // CPU specific variables //
    public static final int bit_length = 16;
    public static final int max_pair_value = 0xffff;
    public static final int min_pair_value = -32768;

    public static final int max_byte_value = 255;
    public static final int min_byte_value = -127;
    public int currentLine = 1;
    /// ///////

    // Memory variables
    public int data_start;
    public int stack_start;
    public int last_addressable_location;

    public int mem_size_B;


    // CPU architecture
    public int[] registers;
    public String[] registerNames;
    public short[] memory;

    int registerPairStart;

    int PC = 18;
    int SP = 19;
    int SS = 20;
    int SE = 21;
    int DI = 22;
    int DP = 23;


    // flags
    boolean N, C, O, Z, I, T, E;

    // listeners
    private onStepListener stepListener;

    int memorySize = Integer.parseInt(Launcher.appConfig.get("MemSize"));
    int dataSize = Integer.parseInt(Launcher.appConfig.get("OffsetSize"));
    int stackSize = Integer.parseInt(Launcher.appConfig.get("StackSize"));


    /// /////////////////////////// HELPER FUNCTIONS /////////////////////////////////////////////////////////
    /// /////////////////////////////////////////////////////////////////////////////////////////////////////
    public void triggerProgramError(RuntimeException exceptionType, String errMsg, int errCode){
        status_code = errCode;
        outputString.append("line " + currentLine + " : " + errMsg);
        programEnd = true;
        exceptionType = new RuntimeException("line " + currentLine + " : " + errMsg);
        throw exceptionType;
    }


    public void setRegister(int registerID, int value){

        if (registerID < registers.length){

            if (registerID == PC && Launcher.appConfig.get("OverwritePC").equals("false")){
                 String err = "Direct modification of PC register is not allowed." +
                            " if you wish to proceed, change that in the settings.";
                    triggerProgramError(new ErrorHandler.InvalidMemoryOperationException(err),
                            err, ErrorHandler.ERR_CODE_PC_MODIFY_UNALLOWED);
            }
            else if (registerID < registerPairStart){

                if (value > max_byte_value) {
                    String err = String.format("The value 0x%X(%d) is bigger than the selected register (%s) bit width.",
                            value, value, registerNames[registerID]);
                    triggerProgramError(new ErrorHandler.InvalidMemoryOperationException(err),
                            err, ErrorHandler.ERR_CODE_CPU_SIZE_VIOLATION);
                }
                else {
                    registers[registerID] = value;
                    updateRegisterPairs();
                }
            }
            else if (registerID >= registerPairStart){

                if (value > max_pair_value){
                    String err = String.format("The value 0x%X(%d) is bigger than the selected register (%s) bit width.",
                            value, value, registerNames[registerID]);
                    triggerProgramError(new ErrorHandler.InvalidMemoryOperationException(err),
                            err, ErrorHandler.ERR_CODE_CPU_SIZE_VIOLATION);
                }
                else{
                    registers[registerID] = value;
                    updateRegisterBytes();
                }
            }
        }
    }


    public int getRegisterCode(String registerName){
        for(int i = 0; i < registerNames.length; i++){
            if (registerNames[i].equals(registerName)) return i;
        }
        return -1;
    }

    public int getRegister(int registerID){
        return registers[registerID];
    }

    public int getMemory(int address){
        if (isValidMemoryAddress(address)) return memory[address];
        else return max_pair_value + 1;
    }


    public void updateRegisterPairs(){
        int lowByteIndex = 0;
        int highByteIndex = 1;

        for(int i = registerPairStart; i < registerPairStart + 6; i++){
            registers[i] = (registers[highByteIndex] << 8) | registers[lowByteIndex];
            lowByteIndex++;
            highByteIndex++;
        }
    }

    public void updateRegisterBytes(){
        int registerLowByteIndex = 0;
        int registerHighByteIndex = 1;

        for(int i = registerPairStart; i < registerPairStart + 6; i++){

            registers[registerLowByteIndex] = registers[i] & 0xff;
            registers[registerHighByteIndex] = (registers[i] >> 8) & 0xff;

            registerLowByteIndex += 2;
            registerHighByteIndex += 2;
        }
    }

    public int readByte(int address){
        if (!isValidMemoryAddress(address)){
            String err = String.format("0x%X(%d) is an invalid memory address.");
            triggerProgramError(new ErrorHandler.InvalidMemoryOperationException(err),
                    err, ErrorHandler.ERR_CODE_INVALID_MEMORY_ADDRESS);
        }
        return memory[address];
    }

    public int[] readWord(int startAddress){
        if (!isValidMemoryAddress(startAddress)){
            String err = String.format("0x%X(%d) is an invalid memory address.");
            triggerProgramError(new ErrorHandler.InvalidMemoryOperationException(err),
                    err, ErrorHandler.ERR_CODE_INVALID_MEMORY_ADDRESS);
        }
        if (!isValidMemoryAddress(startAddress + 1)){
            String err = String.format("0x%X(%d) is an invalid memory address.");
            triggerProgramError(new ErrorHandler.InvalidMemoryOperationException(err),
                    err, ErrorHandler.ERR_CODE_INVALID_MEMORY_ADDRESS);
        }

        return new int[] {memory[startAddress], memory[startAddress + 1]};
    }

    public void setMemory(int address, int value){

        if (isValidMemoryAddress(address)){

            if (value <= max_byte_value) memory[address] = (short) value;
            else{
                if (!isValidMemoryAddress(address + 1)){
                    String err = String.format("0x%X(%d) is an invalid memory address.", address, address);
                    triggerProgramError(new ErrorHandler.InvalidMemoryOperationException(err),
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
            triggerProgramError(new ErrorHandler.InvalidMemoryOperationException(err),
                    err, ErrorHandler.ERR_CODE_INVALID_MEMORY_ADDRESS);
        }
    }

    public boolean isValidMemoryAddress(int address){
        return address <= last_addressable_location && address >= 0;
    }

    public String dumpMemory(){
        int chunkSize = 10;
        StringBuilder result = new StringBuilder();
        StringBuilder charSet = new StringBuilder();
        for(int i = 0; i < memory.length; i++){

            if (i % chunkSize == 0) result.append(String.format("%05X :\t", i));

            result.append(String.format("0x%02X\t", memory[i]));
            charSet.append( (Character.isLetterOrDigit(memory[i])) ? (char) memory[i] : "." );

            if ((i + 1) % chunkSize == 0){
                result.append("\t\t").append("|").append(charSet).append("|").append("\n");
                charSet.setLength(0);
            }
        }
        return result.toString();
    }


    public String dumpMemory(int start, int end){
        int chunkSize = 10;
        StringBuilder result = new StringBuilder();
        StringBuilder charSet = new StringBuilder();
        for(int i = start; i <= end; i++){

            if (i % chunkSize == 0) result.append(String.format("%05X :\t", i));

            result.append(String.format("0x%02X\t", memory[i]));
            charSet.append( (Character.isLetterOrDigit(memory[i])) ? (char) memory[i] : "." );

            if ((i + 1) % chunkSize == 0){
                result.append("\t\t").append("|").append(charSet).append("|").append("\n");
                charSet.setLength(0);
            }
        }
        return result.toString();
    }



    public String dumpRegisters(){
        int registersPerLine = 3;
        StringBuilder result = new StringBuilder();
        for(int i = registerPairStart; i < registers.length; i++){

            if ( i  % registersPerLine == 0) result.append("\n");
            result.append(registerNames[i].toUpperCase()).append(": ").append(String.format("0x%04X", registers[i])).append("\t");
        }
        return result.toString();
    }

    @Override
    public String dumpFlags(){
        StringBuilder result = new StringBuilder();

        result.append(String.format("N = %d\tO = %d\tC = %d\n" +
                        "Z = %d\tT = %d\tE = %d\n" +
                        "I = %d\n",
                N ? 1 : 0,
                O ? 1 : 0,
                C ? 1 : 0,
                Z ? 1 : 0,
                T ? 1 : 0,
                E ? 1 : 0,
                I ? 1 : 0));

        return result.toString();
    }


    public int step(){

        registers[PC]++;
        if (stepListener != null) stepListener.updateUI();
        try {
            Thread.sleep(delayAmountMilliseconds);
        }catch (Exception e){
            throw new RuntimeException(e);
        }
        return registers[PC];
    }

    public int[] getNextOperand(){
        return new int[] {machineCode[ step() ], machineCode[ step() ]};
    }


    public void setUIupdateListener(onStepListener listener){
        this.stepListener = listener;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ///
    ///
    ///
    /// //////////////////////////// CPU FUNCTIONALITY ////////////////////////////////////////////////////////

    @Override
    public int[] toMachineCode(String instruction){
        String[] tokens = instruction.trim().split(" ");


        // Instruction format: opcode (1 byte) optional: operand1 (2 bytes) optional: operand2 (2 bytes)
        // Output machine code: opcode operand1_addressing_mode operand1_value operand2_addressing_mode operand2_value
        int length = 1; // 1 byte for opcode
        for(int i = 1; i < tokens.length; i++){
            length += 2; // 2 bytes for all remaining operands
        }
        int[] result = new int[length];

        Integer opCode = translationMap.get(tokens[0]);
        if (opCode == null){
            String err = String.format("Unknown instruction : %s\n", tokens[0]);
            status_code = ErrorHandler.ERR_COMP_UNDEFINED_INSTRUCTION;
            triggerProgramError(new ErrorHandler.CodeCompilationError(err), err, status_code);
        }
        else result[0] = opCode; // tokens[0] should always be the opcode.

        // figure out which addressing mode is used.
        if (tokens.length > 1) {
            int tokenIndex = 1;
            for (int i = 1; i < result.length; i += 2) {

                result[i] = switch (tokens[tokenIndex].charAt(0)) {
                    case REGISTER_PREFIX -> 0x0;
                    case DIRECT_MEMORY_PREFIX -> 0x1;
                    case INDIRECT_MEMORY_PREFIX -> 0x2;
                    case IMMEDIATE_PREFIX -> 0x3;
                    case DATA_PREFIX -> 0x4;
                    case STRING_PREFIX -> 0x5;
                    default -> 0x6;
                };


                if (result[i] == 0x4){
                    Integer dataPointer = dataMap.get(tokens[tokenIndex].substring(1));

                    if (dataPointer == null){
                        String err = String.format("The variable '%s' doesn't exist in the data section.\n",
                                tokens[tokenIndex].substring(1));
                        status_code = ErrorHandler.ERR_COMP_NULL_DATA_POINTER;
                        triggerProgramError(new ErrorHandler.CodeCompilationError(err),
                                err, status_code);
                        return new int[] {-1};
                    }

                    else {
                        result[i + 1] = dataPointer;
                        tokenIndex++;
                        break;
                    }
                }
                if (result[i] == 0x6){
                    Integer functionPointer = functions.get(tokens[tokenIndex]);

                    if (functionPointer == null){
                        String err = String.format("The function '%s' doesn't exist in the ROM.\n",
                                tokens[tokenIndex]);
                        status_code = ErrorHandler.ERR_COMP_NULL_FUNCTION_POINTER;
                        triggerProgramError(new ErrorHandler.CodeCompilationError(err),
                                err, status_code);
                        return new int[] {-1};
                    }

                    else {
                        result[i + 1] = functions.get(tokens[tokenIndex]);
                        tokenIndex++;
                        break;
                    }
                }

                if (isNumber(tokens[tokenIndex].substring(1))){
                    result[i + 1] = Integer.parseInt(tokens[tokenIndex].substring(1));
                }else{
                    int registerCode = getRegisterCode(tokens[tokenIndex].substring(1));
                    if (registerCode == -1){
                        String err = String.format("Unknown register '%s'.\n", tokens[tokenIndex].substring(1));
                        status_code = ErrorHandler.ERR_COMP_INVALID_CPU_CODE;
                        triggerProgramError(new ErrorHandler.CodeCompilationError(err), err, status_code);
                    }
                    else result[i + 1] = registerCode;
                }
                tokenIndex++;
            }
        }
        System.out.print(instruction + " => ");
        for (int j : result) System.out.printf("0x%X ", j);
        System.out.println();
        return result;
    }


    @Override
    public int[] compileCode(String code){
        String[] lines = code.split("\n");
        List<Integer> machineCodeList = new ArrayList<>();

        StringBuilder machineCodeString = new StringBuilder();

        // Step 1- Calculate the function offset addresses, add .DATA variables to the data section, and build a raw code string
        String fullCode = "";
        for(int i = 0; i < lines.length; i++){
            currentLine++;
            // Which section are we in? (is it a line of code? is it a function. and if it starts with '.' is it the data section?)
            if (lines[i].equals(".DATA")){
                System.out.println("Data section detected.");
                int offset = 0;
                i++; // skip .DATA line

             while (!lines[i].equals("end")) {

                 String[] x = lines[i].trim().split(" ");
                 if (x[0].equals("org")) data_start = Integer.parseInt(x[1].substring(1)) - offset;

                 else {
                     dataMap.put(x[0], data_start + offset);
                     if (x[1].startsWith(String.valueOf(STRING_PREFIX))) { // 34 in decimal 0x22 in hex
                         String fullString = String.join(" ", x);

                         int startIndex = fullString.indexOf(34) + 1;
                         int endIndex = fullString.length() - 1;
                         fullString = fullString.substring(startIndex, endIndex);
                         for (int j = 0; j < fullString.length(); j++) {
                             System.out.printf("Setting memory location 0x%X(%d) to char %c\n",
                                     data_start + offset, data_start + offset, fullString.charAt(j));
                             setMemory(data_start + offset, (short) fullString.charAt(j));
                             offset++;
                         }
                         setMemory(offset, NULL_TERMINATOR);
                         offset++;
                     } else {
                         for (int j = 1; j < x.length; j++) {
                             System.out.printf("Setting memory location 0x%X(%d) to value 0x%X(%d)\n",
                                     data_start + offset, data_start + offset,
                                     Integer.parseInt(x[j].substring(1)), Integer.parseInt(x[j].substring(1)));

                             setMemory(data_start + offset, Short.parseShort(x[j].substring(1)));
                             offset++;
                         }
                         setMemory(offset, NULL_TERMINATOR);
                         offset++;
                     }
                 }
                 i++;
             }
            }
            else if (lines[i].startsWith(".")){ // regular function. add the function along with the calculated offset
                functions.put(lines[i].substring(1), currentByte);
            }
            else{ // code line. append the offset based on the string length.
                // in this architecture there's only 3 possible cases
                // no-operand instruction = 1 byte
                // single-operand instruction = 3 bytes
                // 2 operand instruction = 5 bytes
                if (lines[i].isEmpty() || lines[i].startsWith(COMMENT_PREFIX)) continue;
                int len = lines[i].trim().split(" ").length;
                if (len == 3) currentByte += 5;
                else if (len == 2) currentByte += 3;
                else currentByte += 1;

                fullCode += lines[i] + "\n";
            }
        }
        System.out.println(functions);
        System.out.println(dataMap);

        // Step 2- convert the raw code to machine code array.
        String[] fullLines = fullCode.split("\n");

        currentLine = 1;
        for(int i = 0; i < fullLines.length; i++){

            currentLine++;
            String a = Arrays.toString(toMachineCode(fullLines[i])).replace("[", "").replace("]", "");

            machineCodeString.append(a);
            if (i < fullLines.length - 1) machineCodeString.append(", ");
        }

        String[] eachNum = machineCodeString.toString().split(", ");
        for(int i = 0; i < eachNum.length; i++){
            if (isNumber(eachNum[i])){
                machineCodeList.add(Integer.parseInt(eachNum[i]));
            }
        }
        machineCode = machineCodeList.stream().mapToInt(Integer::intValue).toArray();

        return machineCode;
    }

    @Override
    public void executeCompiledCode(int[] machine_code){

    }

    @Override
    public void set(short[] destination, short[] source){
        int operandValue = switch (source[0]){
            case REGISTER_MODE -> getRegister(source[1]);
            case DIRECT_MODE -> getMemory(source[1]);
            case INDIRECT_MODE -> getRegister( getMemory( source[1] ) );
            default -> max_pair_value + 1;
        };
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////
    /// //////////////////////////////////////////////////////////////////////////////////////////////////////

    public CPUModule16BIT(){
        super();

        System.out.println("Starting 16-bit CPU module");

        mem_size_B = memorySize * 1024;
        stack_start = mem_size_B - (stackSize * 1024);
        data_start = stack_start - (dataSize * 1024);
        last_addressable_location = stack_start - 1;

        memory = new short[mem_size_B];


        if (stack_start < 0){
            String errMsg = "Invalid memory layout (stack). " + stack_start;
            triggerProgramError(new ErrorHandler.InvalidMemoryLayoutException(errMsg),
                    errMsg, ErrorHandler.ERR_CODE_INVALID_MEMORY_LAYOUT);

        }
        if (data_start < 0){
            String errMsg = "Invalid memory layout (data). " + data_start;
            triggerProgramError(new ErrorHandler.InvalidMemoryLayoutException(errMsg),
                    errMsg, ErrorHandler.ERR_CODE_INVALID_MEMORY_LAYOUT);
        }

        System.out.println(String.format("""
                Starting with %dKB of memory. Total of %d locations
                Data size %dKB starts at address 0x%X(%d)
                Stack size %dKB start at address 0x%X(%d)
                last addressable location : 0x%X(%d)
                """, memorySize, mem_size_B,
                dataSize, data_start, data_start,
                stackSize, stack_start, stack_start,
                last_addressable_location, last_addressable_location));

        System.out.printf("CPU speed set to %s Cycles per second. With a step delay of %sMS\n",
                Launcher.appConfig.get("Cycles"), /*delayAmountMilliseconds*/10);

        reset();
    }


    public void reset(){
        memory = new short[mem_size_B];

        // al, ah => ax
        // bl, bh => bx
        // cl, ch => cx
        // dl, dh => dx
        // el, eh => ex
        // fl, fh => fx
        // PC
        // SP
        // SS
        // SE
        // DI
        // DP

        int totalRegisterCount = 6 * 2 + 6 + 6;
        registers = new int[totalRegisterCount];
        registerNames = new String[totalRegisterCount];

        int byteRegisterCount = 6 * 2;
        int registerPairCount = 6;


        char currentChar = 'a';
        char byteChar = 'l';
        int index = 0;
        for(int i = 0; i < byteRegisterCount; i++){
            if (index == 0) byteChar = 'l';
            else if (index == 1) byteChar = 'h';

            registerNames[i] = "r" + currentChar + byteChar;
            index++;
            if (index == 2){
                index = 0;
                currentChar++;
            }
            registerPairStart++;
        }

        currentChar = 'a';
        for(int i = byteRegisterCount; i < byteRegisterCount + registerPairCount; i++){
            registerNames[i] = "r" + currentChar + "x";
            currentChar++;
        }

        registerNames[PC] = "pc";
        registerNames[SP] = "sp";
        registerNames[SS] = "ss";
        registerNames[SE] = "se";
        registerNames[DI] = "di";
        registerNames[DP] = "dp";


        N = false;
        C = false;
        O = false;
        I = false;
        E = false;
        Z = false;
        T = false;

        programEnd = false;
        canExecute = true;
        currentByte = 0;
        currentLine = 1;
        status_code = 0;

        functionCallStack = new Stack<>();
        dataMap = new HashMap<>();
        functions = new HashMap<>();

        outputString = new StringBuilder();

        machineCode = new int[] {0};

        data_start = stack_start - (dataSize * 1024);


        System.out.println(dumpRegisters());
        System.out.println("==============================");

        System.out.println(dumpMemory(0x00, 39));
    }

}