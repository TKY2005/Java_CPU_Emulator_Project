import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class CPUModule16BIT extends CPU {

    // CPU specific variables //

    public static final int min_byte_value = -127;

    /// ///////

    // Memory variables
    public int data_start;
    public int stack_start;


    public int mem_size_B;


    // CPU architecture
    public int[] registers;
    public String[] registerNames;

    int registerPairStart;

    public static final int REGISTER_WORD_MODE = 7;
    public static final int DIRECT_WORD_MODE = 8;
    public static final int INDIRECT_WORD_MODE = 9;

    int PC = 18;
    int SP = 19;
    int SS = 20;
    int SE = 21;
    int DI = 22;
    int DP = 23;
    int RCX;


    // flags
    boolean N, C, O, Z, I, T, E;

    // listeners
    private onStepListener stepListener;

    int memorySize = Integer.parseInt(Launcher.appConfig.get("MemSize"));
    int dataSize = Integer.parseInt(Launcher.appConfig.get("OffsetSize"));
    int stackSize = Integer.parseInt(Launcher.appConfig.get("StackSize"));


    int[] functionPointers;
    HashMap<Integer, String> functionAddresses;


    /// /////////////////////////// HELPER FUNCTIONS /////////////////////////////////////////////////////////
    /// /////////////////////////////////////////////////////////////////////////////////////////////////////


    public int getOperandValue(int[] source){
        Logger.addLog("Fetching source");
        return switch (source[0]){
            case REGISTER_MODE, REGISTER_WORD_MODE -> getRegister(source[1]);
            case DIRECT_MODE -> readByte(source[1]);
            case DIRECT_WORD_MODE -> bytePairToWordLE((readWord( source[1] )));
            case INDIRECT_MODE -> readByte( getRegister( source[1] ) );
            case INDIRECT_WORD_MODE -> bytePairToWordLE( readWord( getRegister( source[1] ) ) );
            case IMMEDIATE_MODE -> source[1];
            default -> max_pair_value + 1;
        };
    }

    public String getDisassembledOperand(int[] operand){
        return switch (operand[0]){
            case REGISTER_MODE, REGISTER_WORD_MODE -> "$" + getRegisterName(operand[1]);
            case DIRECT_MODE, DIRECT_WORD_MODE -> "*" + Integer.toHexString(operand[1]);
            case INDIRECT_MODE, INDIRECT_WORD_MODE -> "&" + getRegisterName(operand[1]);
            case IMMEDIATE_MODE -> "#" + Integer.toHexString(operand[1]).toUpperCase();

            case DATA_MODE-> {
                int high = operand[1], low = operand[2];
                int address = (high << 8) | low;
                yield "#" + Integer.toHexString(address).toUpperCase();
            }

            case FUNCTION_MODE -> {
                int high = operand[1], low = operand[2];
                int address = (high << 8) | low;
                yield String.format("< %s @0x%04X >", functionAddresses.get(address), address);
            }

            default -> "??";
        };
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

    public int getRegisterByte(int registerID){
        if (registerID < registerPairStart){
            return registers[registerID];
        }
        return max_byte_value + 1;
    }

    public int[] getRegisterWord(int registerID){
        int low = 0, high = 0;
        if (registerID >= registerPairStart){
            int registerVal = registers[registerID];
            low = registerVal & 0xff;
            high = (registerVal >> 8) & 0xff;
        }

        return new int[] {low, high};
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
            lowByteIndex += 2;
            highByteIndex += 2;
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

    public void updateFlags(int value){
        Logger.addLog("Updating flags.");
        short flagSetter = (short) value;

        Z = flagSetter == 0;
        N = ((flagSetter >>> 15) & 1) == 1;
        if (N) O = value > Short.MAX_VALUE || value < Short.MIN_VALUE;
        else C = value > max_pair_value;
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
    ///
    public String disassemble(int[] machine_code){
        StringBuilder instruction = new StringBuilder();

        instruction.append(instructionSet.get(machine_code[0])).append(" ");

        if (machine_code.length > 1) {
            for (int i = 1; i < machine_code.length; i += 2) {
                switch (machine_code[i]) {
                    case REGISTER_MODE, REGISTER_WORD_MODE -> instruction.append
                            ('$').append(getRegisterName(machine_code[i + 1])).append(" ");
                    case DIRECT_MODE, DIRECT_WORD_MODE ->
                            instruction.append('%').append(machine_code[i + 1]).append(" ");
                    case INDIRECT_MODE, INDIRECT_WORD_MODE ->
                            instruction.append('&').append(getRegisterName(machine_code[i + 1])).append(" ");
                    case IMMEDIATE_MODE -> instruction.append('!').append(machine_code[i + 1]).append(" ");
                    case 0x4, 0x6 -> instruction.append('#').append(String.format("%X", machine_code[i + 1])).append(" ");
                    default -> instruction.append("??").append(" ");
                }
            }
        }
        return instruction.toString();
    }


    public String getRegisterName(int registerID){
        return registerNames[registerID];
    }
    @Override
    public int[] toMachineCode(String instruction){
        String[] tokens = instruction.trim().split(" ");


        // Instruction format: opcode (1 byte) optional: operand1 (2 bytes) optional: operand2 (2 bytes)
        // NOTE: if the instruction has an address, then operand size will be 3 bytes (1 byte for mode, 2 bytes for address)
        // Output machine code: opcode operand1_addressing_mode operand1_value operand2_addressing_mode operand2_value
        int length = 1; // 1 byte for opcode
        for(int i = 1; i < tokens.length; i++){
            //length += 2; // 2 bytes for all remaining operands
            switch (tokens[i].charAt(0)){
                case REGISTER_PREFIX, DIRECT_MEMORY_PREFIX, INDIRECT_MEMORY_PREFIX, IMMEDIATE_PREFIX -> length += 2;
                default -> length += 3;
            }
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

                // decide if we are dealing with a byte or a word depending on the register name
                if (tokens[tokenIndex].charAt(0) == REGISTER_PREFIX &&
                tokens[tokenIndex].charAt( tokens[tokenIndex].length() - 1 ) == 'x') result[i] = REGISTER_WORD_MODE;

                if (tokens[tokenIndex].charAt(0) == DIRECT_MEMORY_PREFIX &&
                tokens[tokenIndex - 1].charAt(0) == REGISTER_PREFIX &&
                tokens[tokenIndex - 1].charAt( tokens[tokenIndex - 1].length() - 1 ) == 'x') result[i] = DIRECT_WORD_MODE;

                else if (tokens[tokenIndex].charAt(0) == INDIRECT_MEMORY_PREFIX &&
                tokens[tokenIndex].charAt( tokens[tokenIndex].length() - 1 ) == 'x') result[i] = INDIRECT_WORD_MODE;


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
                        int low = dataPointer & 0xff;
                        int high = (dataPointer >> 8) & 0xff;
                        result[i + 1] = high;
                        result[i + 2] = low;
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
                        int low = functionPointer & 0xff;
                        int high = (functionPointer >> 8) & 0xff;
                        result[i + 1] = high;
                        result[i + 2] = low;
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

                             setMemory(data_start + offset, Integer.parseInt(x[j].substring(1)));
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

                if (len == 3){
                    switch (lines[i].trim().split(" ")[2].charAt(0)){
                        case REGISTER_PREFIX, DIRECT_MEMORY_PREFIX, INDIRECT_MEMORY_PREFIX, IMMEDIATE_PREFIX -> {
                            currentByte += 5;
                        }
                        default -> {
                            System.out.println("Address loading detected. adding 6 bytes");
                            currentByte += 6;
                        }
                    }
                    //currentByte += 5;
                }
                else if (len == 2) {
                    switch (lines[i].trim().split(" ")[1].charAt(0)){
                        case REGISTER_PREFIX, DIRECT_MEMORY_PREFIX, INDIRECT_MEMORY_PREFIX, IMMEDIATE_PREFIX -> {
                            System.out.println("Adding 3 bytes due to no function.");
                            currentByte += 3;
                        }
                        default ->{
                            System.out.println("Function detected. adding 4 bytes.");
                            currentByte += 4;
                        }
                    }
                    //currentByte += 3;
                }
                else currentByte += 1;

                fullCode += lines[i] + "\n";
            }
        }
        System.out.println(functionPointers);
        System.out.println(dataMap);

        // Step 2- convert the raw code to machine code array.
        String[] fullLines = fullCode.split("\n");

        currentLine = 1;
        eachInstruction = new HashMap<>();
        for(int i = 0; i < fullLines.length; i++){

            currentLine++;
            String a = Arrays.toString(toMachineCode(fullLines[i])).replace("[", "").replace("]", "");
            //eachInstruction.put(i, toMachineCode(fullLines[i]));
            machineCodeString.append(a);
            if (i < fullLines.length - 1) machineCodeString.append(", ");
        }

        String[] eachNum = machineCodeString.toString().split(", ");
        for(int i = 0; i < eachNum.length; i++){
            if (isNumber(eachNum[i])){
                machineCodeList.add(Integer.parseInt(eachNum[i]));
            }
        }
        machineCodeList.add((int) TEXT_SECTION_END);

        for(int i = 0; i < signature.length(); i++) // My signature, last release date and compiler version
            machineCodeList.add((int) signature.charAt(i));

        for(int i = 0; i < lastUpdateDate.length(); i++)
            machineCodeList.add((int) lastUpdateDate.charAt(i));

        for(int i = 0; i < compilerVersion.length(); i++)
            machineCodeList.add((int) compilerVersion.charAt(i));

        machineCodeList.add( memorySize ); // The memory size in KB
        machineCodeList.add( bit_length ); // the CPU architecture flag

        // The program's entry point.
        int entryPoint = functions.get("MAIN");

        int entryPointLow = entryPoint & 0xff;
        int entryPointHigh = (entryPoint >> 8) & 0xff;

        machineCodeList.add(entryPointHigh);
        machineCodeList.add(entryPointLow);

        machineCode = machineCodeList.stream().mapToInt(Integer::intValue).toArray();

        return machineCode;
    }




    @Override
    public int[] compileToFileBinary(String code){
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

                             setMemory(data_start + offset, Integer.parseInt(x[j].substring(1)));
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
                if (len == 3){
                    switch (lines[i].trim().split(" ")[2].charAt(0)){
                        case REGISTER_PREFIX, DIRECT_MEMORY_PREFIX, INDIRECT_MEMORY_PREFIX, IMMEDIATE_PREFIX -> {
                            currentByte += 5;
                        }
                        default -> {
                            System.out.println("Address loading detected. adding 6 bytes");
                            currentByte += 6;
                        }
                    }
                    //currentByte += 5;
                }
                else if (len == 2) {
                    switch (lines[i].trim().split(" ")[1].charAt(0)){
                        case REGISTER_PREFIX, DIRECT_MEMORY_PREFIX, INDIRECT_MEMORY_PREFIX, IMMEDIATE_PREFIX -> {
                            System.out.println("Adding 3 bytes due to no function.");
                            currentByte += 3;
                        }
                        default ->{
                            System.out.println("Function detected. adding 4 bytes.");
                            currentByte += 4;
                        }
                    }
                    //currentByte += 3;
                }
                else currentByte += 1;

                fullCode += lines[i] + "\n";
            }
        }
        System.out.println(functionPointers);
        System.out.println(dataMap);

        // Step 2- convert the raw code to machine code array.
        String[] fullLines = fullCode.split("\n");

        currentLine = 1;
        eachInstruction = new HashMap<>();
        for(int i = 0; i < fullLines.length; i++){

            currentLine++;
            String a = Arrays.toString(toMachineCode(fullLines[i])).replace("[", "").replace("]", "");
            //eachInstruction.put(i, toMachineCode(fullLines[i]));
            machineCodeString.append(a);
            if (i < fullLines.length - 1) machineCodeString.append(", ");
        }

        String[] eachNum = machineCodeString.toString().split(", ");

        for(int i = 0; i < eachNum.length; i++){ // The TEXT section
            if (isNumber(eachNum[i])){
                machineCodeList.add(Integer.parseInt(eachNum[i]));
            }
        }
        machineCodeList.add((int) TEXT_SECTION_END);

        for(int i = 0; i < memory.length; i++){ // The DATA and STACK sections
            machineCodeList.add((int) memory[i]);
        }
        machineCodeList.add((int) MEMORY_SECTION_END);

        for(int i = 0; i < signature.length(); i++) // My signature, last release date and compiler version
            machineCodeList.add((int) signature.charAt(i));

        for(int i = 0; i < lastUpdateDate.length(); i++)
            machineCodeList.add((int) lastUpdateDate.charAt(i));

        for(int i = 0; i < compilerVersion.length(); i++)
            machineCodeList.add((int) compilerVersion.charAt(i));

        machineCodeList.add( memorySize ); // The memory size in KB
        machineCodeList.add( bit_length ); // the CPU architecture flag

        // Add the program's entry point.
        int entryPoint = functions.get("MAIN");

        int entryPointLow = entryPoint & 0xff;
        int entryPointHigh = (entryPoint >> 8) & 0xff;

        machineCodeList.add(entryPointHigh);
        machineCodeList.add(entryPointLow);

        machineCode = machineCodeList.stream().mapToInt(Integer::intValue).toArray();

        return machineCode;
    }

    @Override
    public void executeCompiledCode(int[] machine_code){

        Integer mainEntryPoint = functions.get("MAIN");
        if (mainEntryPoint == null){
            String err = "MAIN function label not found.";
            triggerProgramError(new ErrorHandler.CodeCompilationError(err),
                    err, ErrorHandler.ERR_CODE_MAIN_NOT_FOUND);
        }
        registers[PC] = mainEntryPoint;
        I = true;

        if (machine_code[ machine_code.length - 3 ] != bit_length){ // Check the architecture
            String err = String.format("This code has been compiled for %d-bit architecture." +
                    " the current CPU architecture is %d-bit.\n",
                    machine_code[ machine_code.length - 3  ], bit_length
                    );

            triggerProgramError( new ErrorHandler.CodeCompilationError(err),
                    err, ErrorHandler.ERR_CODE_INCOMPATIBLE_ARCHITECTURE);
        }

        if (machine_code [machine_code.length - 4] > memorySize ){ // Check the allocated memory
            String err = String.format("The selected binary file is generated with %dKB of memory." +
                    "The current configuration uses %dKB. make sure current CPU uses the same or bigger memory size.",
                    machine_code[machine_code.length - 2], memorySize);

            triggerProgramError(new ErrorHandler.CodeCompilationError(err),
                    err, ErrorHandler.ERR_CODE_INSUFFICIENT_MEMORY);
        }

        while (!programEnd && registers[PC] != TEXT_SECTION_END){

            if (registers[PC] >= machine_code.length) {
                String err = String.format("PC access violation detected. PC => %04X, last available ROM address: %04X",
                        registers[PC], machine_code.length);
                triggerProgramError(new ErrorHandler.InvalidMemoryOperationException(err),
                        err, ErrorHandler.ERR_CODE_PC_ACCESS_VIOLATION);
            }
            if (canExecute) {
                Logger.addLog( String.format("Executing instruction 0x%X -> %s at ROM address 0x%X",
                        machine_code[registers[PC]],
                        instructionSet.get(machine_code[registers[PC]]), registers[PC]) );

                switch (machine_code[registers[PC]]) {

                    case INS_EXT -> {
                        Logger.addLog( "Terminating program." );
                        programEnd = true;
                    }

                    // step function increments PC and returns its value
                    // we step two times for each operand. one step for mode. another step for value
                    case INS_SET -> {
                        Logger.addLog("Fetching operands.");
                        int[] destination = getNextOperand();
                        int[] source = getNextOperand();
                        set(destination, source);
                    }
                    case INS_OUT -> {
                        int[] destination = getNextOperand();
                        out(destination);
                    }

                    case INS_OUTC -> {
                        int[] source = getNextOperand();
                        outc(source);
                    }


                    case INS_ADD -> {
                        int[] destination = getNextOperand();
                        int[] source = getNextOperand();
                        add(destination, source);
                    }

                    case INS_SUB -> {
                        int[] destination = getNextOperand();
                        int[] source = getNextOperand();
                        sub(destination, source);
                    }
                    case INS_MUL -> {
                        int[] destination = getNextOperand();
                        int[] source = getNextOperand();
                        mul(destination, source);
                    }
                    case INS_DIV -> {
                        int[] destination = getNextOperand();
                        int[] source = getNextOperand();
                        div(destination, source);
                    }


                    case INS_POW -> {
                        int[] destination = getNextOperand();
                        int[] source = getNextOperand();
                        pow(destination, source);
                    }

                    case INS_SQRT -> {
                        int[] destination = getNextOperand();
                        sqrt(destination);
                    }

                    case INS_RND -> {
                        int[] destination = getNextOperand();
                        int[] source = getNextOperand();
                        rnd(destination, source);
                    }

                    case INS_INC -> {
                        int[] destination = getNextOperand();
                        inc(destination);
                    }
                    case INS_DEC -> {
                        int[] destination = getNextOperand();
                        dec(destination);
                    }

                    case INS_NOT -> {
                        int[] source = getNextOperand();
                        not(source);
                    }

                    case INS_AND -> {
                        int[] destination = getNextOperand();
                        int[] source = getNextOperand();
                        and(destination, source);
                    }

                    case INS_OR -> {
                        int[] destination = getNextOperand();
                        int[] source = getNextOperand();
                        or(destination, source);
                    }

                    case INS_XOR -> {
                        int[] destination = getNextOperand();
                        int[] source = getNextOperand();
                        xor(destination, source);
                    }

                    case INS_NAND -> {
                        int[] destination = getNextOperand();
                        int[] source = getNextOperand();
                        nand(destination, source);
                    }

                    case INS_NOR -> {
                        int[] destination = getNextOperand();
                        int[] source = getNextOperand();
                        nor(destination, source);
                    }

                    case INS_LA -> {
                        // Get the destination (must be 16-bit compatible). step to the address. load into source
                        int[] destination = getNextOperand();
                        step();
                        step();
                        la(destination);
                    }

                    case INS_LLEN -> {
                        int[] destination = getNextOperand();
                        step();
                        step();
                        int start = ( machine_code[registers[PC]] << 8 ) | machine_code[step()];
                        short len = 0;
                        while (getMemory(start) != NULL_TERMINATOR) {
                            start++;
                            len++;
                        }

                        switch (destination[0]) {
                            case REGISTER_MODE, REGISTER_WORD_MODE -> setRegister(destination[1], len);
                            case DIRECT_MODE, DIRECT_WORD_MODE -> setMemory(destination[1], len);
                            case INDIRECT_MODE, INDIRECT_WORD_MODE -> setMemory(getRegister(destination[1]), len);
                            default -> E = true;
                        }
                    }

                    case INS_OUTS -> {
                        int start = registers[SS];
                        while (readByte(start) != NULL_TERMINATOR) {
                            outputString.append((char) memory[start]);
                            output += (char) memory[start];
                            start++;
                        }
                        //outputString.append("\n");
                        System.out.print(output);
                    }

                    case INS_PUSH -> {
                        int[] source = getNextOperand();
                        push(source);
                    }


                    case INS_POP -> {
                        int[] source = getNextOperand();
                        pop(source);
                    }

                    case INS_CALL -> {
                        step();
                        int address = ( ( machine_code[step()] << 8 ) | machine_code[step()] );
                        int return_address = step() - 1;
                        call(address, return_address);
                    }
                    case INS_RET -> {
                        int return_address = functionCallStack.pop();
                        Logger.addLog(String.format("Popping return address 0x%X from function call stack", return_address));
                        registers[PC] = return_address;
                    }

                    case INS_CE -> {
                        step();
                        int address = machine_code[step()];
                        int return_address = machine_code[step()];
                        if (Z) call(address, return_address);
                        else step();
                    }
                    case INS_CNE -> {
                        step();
                        int address = machine_code[step()];
                        int return_address = machine_code[step()];
                        if (!Z) call(address, return_address);
                    }
                    case INS_CL -> {
                        step();
                        int address = machine_code[step()];
                        int return_address = machine_code[step()];
                        if (N) call(address, return_address);
                        else step();
                    }
                    case INS_CLE -> {
                        step();
                        int address = machine_code[step()];
                        int return_address = machine_code[step()];
                        if (N || Z) call(address, return_address);
                        else step();
                    }
                    case INS_CG -> {
                        step();
                        int address = machine_code[step()];
                        int return_address = machine_code[step()];
                        if (!N) call(address, return_address);
                        else step();
                    }
                    case INS_CGE -> {
                        step();
                        int address = machine_code[step()];
                        int return_address = machine_code[step()];
                        if (!N || Z) call(address, return_address);
                        else step();
                    }

                    case INS_JMP -> {
                        step();
                        step();
                        jmp();
                    }
                    case INS_JE -> {
                        step();
                        step();
                        if (Z) jmp();
                        else step();
                    }
                    case INS_JNE -> {
                        step();
                        step();
                        if (!Z) jmp();
                        else step();
                    }
                    case INS_JL -> {
                        step();
                        step();
                        if (N) jmp();
                        else step();
                    }
                    case INS_JLE -> {
                        step();
                        step();
                        if (N || Z) jmp();
                        else step();
                    }
                    case INS_JG -> {
                        step();
                        step();
                        if (!N) jmp();
                        else step();
                    }
                    case INS_JGE -> {
                        step();
                        step();
                        if (!N || Z) jmp();
                        else step();
                    }

                    case INS_CMP -> {
                        int[] destination = getNextOperand();
                        int[] source = getNextOperand();
                        cmp(destination, source);
                    }

                    case INS_LOOP -> {
                        // if RCX > 0: decrement RC and jump to the label address specified.
                        step();
                        step();

                        registers[RCX]--;
                        if (registers[RCX] > 0) {
                            jmp();
                        }else step();
                    }

                    case INS_INT -> {
                        if (I) {
                            boolean x = VirtualMachine.interruptHandler(registers, memory);
                            if (!x) E = true;
                        } else System.out.println("Interrupt flag not set. skipping.");
                    }



                    default -> {
                        String err = "Undefined instruction. please check the instruction codes : " + machine_code[registers[PC]];
                        status_code = ErrorHandler.ERR_CODE_INVALID_INSTRUCTION_FORMAT;
                        triggerProgramError(new ErrorHandler.InvalidInstructionException(err),
                                err, status_code);
                    }
                }

                if (E) {
                    status_code = ErrorHandler.ERR_CODE_PROGRAM_ERROR;
                    String err = String.format("The program triggered an error with code : %s", status_code);
                    triggerProgramError(new ErrorHandler.ProgramErrorException(err),
                            err, status_code);
                }

                canExecute = !T;
                output = "";
                step();
            }

        }

        outputString.append("Program terminated with code : ").append(status_code);
        output = "Program terminated with code : " + status_code;
        Logger.addLog( "Program terminated with code : " + status_code );

        Logger.addLog(String.format("""
                ==============================================
                %s
                %s
                ==============================================
                %s
                ==============================================
                """, dumpRegisters(), dumpFlags(), dumpMemory()));


        if (Launcher.appConfig.get("WriteDump").equals("true")){
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd hh.mm.ss");
            LocalDateTime time = LocalDateTime.now();
            String filename = time.format(formatter);
            Logger.writeLogFile("./" + filename + ".log");
        }
    }


    public String disassembleMachineCode(int[] machine_code){

        StringBuilder code = new StringBuilder();

        code.append("Disassembled by T.K.Y CPU compiler V1.0\n");
        registers[PC] = 0;
        delayAmountMilliseconds = 0;

        Set<Integer> functionCollector = new TreeSet<>();

        for(int i = 0; machine_code[i] != (TEXT_SECTION_END & 0xff); i++){

            if (machine_code[i] >= INS_CALL && machine_code[i] <= INS_JB || machine_code[i] == INS_LOOP){
                if (machine_code[i + 1] == FUNCTION_MODE){
                    int high = machine_code[i + 2], low = machine_code[i + 3];
                    int address = bytePairToWordLE(low, high);
                    functionCollector.add(address);
                }
            }
        }
        functionPointers = functionCollector.stream().mapToInt(Integer::intValue).toArray();
        functionAddresses = new HashMap<>();

        for(int i = 0; i < functionPointers.length; i++){
            functionAddresses.put(functionPointers[i], "func_" + i);
        }

        int mainEntryPoint = ((machine_code[ machine_code.length - 2 ] & 0xff) | machine_code[machine_code.length - 1]);

        if (machine_code[ machine_code.length - 3 ] != bit_length){ // Check the architecture
            String err = String.format("This code has been compiled for %d-bit architecture." +
                    " the current CPU architecture is %d-bit.\n",
                    machine_code[ machine_code.length - 3  ], bit_length
                    );

            triggerProgramError( new ErrorHandler.CodeCompilationError(err),
                    err, ErrorHandler.ERR_CODE_INCOMPATIBLE_ARCHITECTURE);
        }


        while (!programEnd && machine_code[registers[PC]] != TEXT_SECTION_END){
            if (canExecute) {

                if (registers[PC] == mainEntryPoint) code.append("\n<.MAIN @0x").append(String.format("%04X>", registers[PC])).append("\n");
                else{
                    for(int i = 0; i < functionPointers.length; i++){
                        if (registers[PC] == functionPointers[i])
                            code.append("\n<").append(functionAddresses.get(functionPointers[i])).
                                    append(" @0x").append(String.format("%04X", functionPointers[i])).append(">").append("\n");
                    }
                }

                code.append(String.format("%04X:\t",
                        registers[PC])).
                        append(instructionSet.get(machine_code[registers[PC]])).append(" ");
                switch (machine_code[registers[PC]]) {

                    // step function increments PC and returns its value
                    // we step two times for each operand. one step for mode. another step for value
                    case INS_SET -> {
                        int[] destination = getNextOperand();
                        code.append(getDisassembledOperand(destination)).append(" ");
                        int[] source = getNextOperand();
                        code.append(getDisassembledOperand(source));
                    }
                    case INS_OUT,
                         INS_SQRT,
                         INS_INC, INS_DEC,
                         INS_NOT, INS_PUSH, INS_POP -> {
                        int[] destination = getNextOperand();
                        code.append(getDisassembledOperand(destination));
                    }

                    case INS_OUTC -> {
                        int[] source = getNextOperand();
                        code.append(getDisassembledOperand(source));
                        outc(source);
                    }


                    case INS_ADD, INS_SUB, INS_MUL, INS_DIV, INS_POW,
                         INS_RND-> {
                        int[] destination = getNextOperand();
                        code.append(getDisassembledOperand(destination)).append(" ");
                        int[] source = getNextOperand();
                        code.append(getDisassembledOperand(source));
                    }


                    case INS_AND -> {
                        int[] destination = getNextOperand();
                        code.append(getDisassembledOperand(destination)).append(" ");
                        int[] source = getNextOperand();
                        code.append(getDisassembledOperand(source));
                    }

                    case INS_OR -> {
                        int[] destination = getNextOperand();
                        code.append(getDisassembledOperand(destination)).append(" ");
                        int[] source = getNextOperand();
                        code.append(getDisassembledOperand(source));
                    }

                    case INS_XOR -> {
                        int[] destination = getNextOperand();
                        code.append(getDisassembledOperand(destination)).append(" ");
                        int[] source = getNextOperand();
                        code.append(getDisassembledOperand(source));
                    }

                    case INS_NAND -> {
                        int[] destination = getNextOperand();
                        code.append(getDisassembledOperand(destination)).append(" ");
                        int[] source = getNextOperand();
                        code.append(getDisassembledOperand(source));
                    }

                    case INS_NOR -> {
                        int[] destination = getNextOperand();
                        code.append(getDisassembledOperand(destination)).append(" ");
                        int[] source = getNextOperand();
                        code.append(getDisassembledOperand(source));
                    }

                    case INS_LA -> {
                        // Get the destination (must be 16-bit compatible). step to the address. load into source
                        int[] destination = getNextOperand();
                        code.append(getDisassembledOperand(destination)).append(" ");
                        int[] source = new int[] {machineCode[step()], machineCode[step()], machineCode[step()]};
                        code.append(getDisassembledOperand(source));
                    }

                    case INS_LLEN -> {
                        int[] destination = getNextOperand();
                        code.append(getDisassembledOperand(destination)).append(" ");
                        int[] source = new int[] {machineCode[step()], machineCode[step()], machineCode[step()]};
                        code.append(getDisassembledOperand(source));
                    }


                    case INS_CALL -> {
                        int source[] = new int[] {machineCode[step()], machineCode[step()], machineCode[step()]};
                        code.append(getDisassembledOperand(source));
                    }


                    case INS_CE, INS_CNE, INS_CL, INS_CLE, INS_CG, INS_CGE, INS_JMP, INS_JE, INS_JNE, INS_JL, INS_JLE,
                         INS_JG, INS_JGE -> {
                        int[] source = new int[] {machineCode[step()], machineCode[step()], machineCode[step()]};
                        code.append(getDisassembledOperand(source));
                    }

                    case INS_CMP -> {
                        int[] destination = getNextOperand();
                        code.append(getDisassembledOperand(destination)).append(" ");
                        int[] source = getNextOperand();
                        code.append(getDisassembledOperand(source));
                    }

                    case INS_LOOP -> {
                        // if RCX > 0: decrement RC and jump to the label address specified.
                        int[] source = new int[] {machineCode[step()], machineCode[step()], machineCode[step()]};
                        code.append(getDisassembledOperand(source));
                    }

                    case INS_INT, INS_OUTS, INS_EXT, INS_RET,
                         INS_END, INS_NOP -> System.out.print("");


                    case TEXT_SECTION_END & 0xff -> {
                        System.out.println("Code ends here."); programEnd = true;
                    }
                    default -> {
                        String err = "Undefined instruction. please check the instruction codes : " + machine_code[registers[PC]];
                        status_code = ErrorHandler.ERR_CODE_INVALID_INSTRUCTION_FORMAT;
                        triggerProgramError(new ErrorHandler.InvalidInstructionException(err),
                                err, status_code);
                    }
                }

                if (E) {
                    status_code = ErrorHandler.ERR_CODE_PROGRAM_ERROR;
                    String err = String.format("The program triggered an error with code : %s", status_code);
                    triggerProgramError(new ErrorHandler.ProgramErrorException(err),
                            err, status_code);
                }

                canExecute = !T;
                output = "";
                code.append("\n");
                currentLine++;
                step();
            }

        }

        outputString.append("Program terminated with code : ").append(status_code);
        output = "Program terminated with code : " + status_code;
        Logger.addLog( "Program terminated with code : " + status_code );

        Logger.addLog(String.format("""
                ==============================================
                %s
                %s
                ==============================================
                %s
                ==============================================
                """, dumpRegisters(), dumpFlags(), dumpMemory()));


        if (Launcher.appConfig.get("WriteDump").equals("true")){
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd hh.mm.ss");
            LocalDateTime time = LocalDateTime.now();
            String filename = time.format(formatter);
            Logger.writeLogFile("./" + filename + ".log");
        }

        return code.toString();
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////
    /// //////////////////////////////////////////////////////////////////////////////////////////////////////
    ///
    /// ///////////////////////////// INSTRUCTION SET ////////////////////////////////////////////////////////
    ///
    ///


    public void set(int[] destination, int[] source){

        int operandValue = getOperandValue(source);

        Logger.addLog("Fetching destination.");
        switch (destination[0]){

            case REGISTER_MODE, REGISTER_WORD_MODE -> setRegister( destination[1], operandValue );
            case DIRECT_MODE -> setMemory( destination[1], operandValue );
            case INDIRECT_MODE, INDIRECT_WORD_MODE -> setMemory( getRegister( destination[1] ), operandValue );
            default -> E = true;
        }
    }


    public void out(int[] source){

        Logger.addLog("Fetching operands");
        outputString.append(getOperandValue(source));
        output = String.valueOf(getOperandValue(source));
        System.out.print(output);
    }

    public void outc(int[] source){
        Logger.addLog("Fetching operands");
        outputString.append( (char) getOperandValue(source) );
        output = String.valueOf( (char) getOperandValue(source) );
        System.out.print(output);
    }


    public void add(int[] destination, int[] source){

        int operandValue = getOperandValue(source);
        int newVal = 0;

        Logger.addLog("Setting destination");
        switch (destination[0]){
            case REGISTER_MODE, REGISTER_WORD_MODE ->{
                newVal = getRegister(destination[1]) + operandValue;

                setRegister( destination[1], newVal);
            }

            case DIRECT_MODE ->{
                newVal = readByte( destination[1] ) + operandValue;
                setMemory( destination[1], newVal);
            }

            case DIRECT_WORD_MODE ->{
                newVal = bytePairToWordLE( readWord( destination[1] ) ) + operandValue;
                setMemory( destination[1], newVal);
            }

            case INDIRECT_MODE ->{
                newVal = readByte( getRegister( destination[1] ) ) + operandValue;
                setMemory( getRegister( destination[1] ), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = bytePairToWordLE( readWord( getRegister( destination[1] ) ) ) + operandValue;
                setMemory( getRegister( destination[1] ), newVal);
            }

            default -> E = true;
        }
        updateFlags(newVal);
    }


    public void sub(int[] destination, int[] source){
        int operandValue = getOperandValue(source);
        int newVal = 0;

        switch (destination[0]){
            case REGISTER_MODE, REGISTER_WORD_MODE ->{
                newVal = getRegister(destination[1]) - operandValue;

                setRegister( destination[1], newVal);
            }

            case DIRECT_MODE ->{
                newVal = readByte( destination[1] ) - operandValue;
                setMemory( destination[1], newVal);
            }

            case DIRECT_WORD_MODE ->{
                newVal = bytePairToWordLE( readWord( destination[1] ) ) - operandValue;
                setMemory( destination[1], newVal);
            }

            case INDIRECT_MODE ->{
                newVal = readByte( getRegister( destination[1] ) ) - operandValue;
                setMemory( getRegister( destination[1] ), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = bytePairToWordLE( readWord( getRegister( destination[1] ) ) ) - operandValue;
                setMemory( getRegister( destination[1] ), newVal);
            }

            default -> E = true;
        }
        updateFlags(newVal);
    }


    public void mul(int[] destination, int[] source){
        int operandValue = getOperandValue(source);
        int newVal = 0;

        switch (destination[0]){
            case REGISTER_MODE, REGISTER_WORD_MODE ->{
                newVal = getRegister(destination[1]) * operandValue;

                setRegister( destination[1], newVal);
            }

            case DIRECT_MODE ->{
                newVal = readByte( destination[1] ) * operandValue;
                setMemory( destination[1], newVal);
            }

            case DIRECT_WORD_MODE ->{
                newVal = bytePairToWordLE( readWord( destination[1] ) ) * operandValue;
                setMemory( destination[1], newVal);
            }

            case INDIRECT_MODE ->{
                newVal = readByte( getRegister( destination[1] ) ) * operandValue;
                setMemory( getRegister( destination[1] ), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = bytePairToWordLE( readWord( getRegister( destination[1] ) ) ) * operandValue;
                setMemory( getRegister( destination[1] ), newVal);
            }

            default -> E = true;
        }
        updateFlags(newVal);
    }


    public void div(int[] destination, int[] source){
        int operandValue = getOperandValue(source);
        int newVal = 0;

        switch (destination[0]){
            case REGISTER_MODE, REGISTER_WORD_MODE ->{
                newVal = getRegister(destination[1]) / operandValue;

                setRegister( destination[1], newVal);
            }

            case DIRECT_MODE ->{
                newVal = readByte( destination[1] ) / operandValue;
                setMemory( destination[1], newVal);
            }

            case DIRECT_WORD_MODE ->{
                newVal = bytePairToWordLE( readWord( destination[1] ) ) / operandValue;
                setMemory( destination[1], newVal);
            }

            case INDIRECT_MODE ->{
                newVal = readByte( getRegister( destination[1] ) ) / operandValue;
                setMemory( getRegister( destination[1] ), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = bytePairToWordLE( readWord( getRegister( destination[1] ) ) ) / operandValue;
                setMemory( getRegister( destination[1] ), newVal);
            }

            default -> E = true;
        }
        updateFlags(newVal);
    }


    public void not(int[] source){
        int newVal = 0;

        switch (source[0]) {

            case REGISTER_MODE, REGISTER_WORD_MODE ->{
                newVal = ~getRegister(source[1]);
                setRegister( source[1], newVal );
            }

            case DIRECT_MODE -> {
                newVal = ~readByte( source[1] );
                setMemory( source[1], newVal );
            }

            case DIRECT_WORD_MODE -> {
                newVal = ~bytePairToWordLE( readWord( source[1] ) );
                setMemory( source[1], newVal );
            }

            case INDIRECT_MODE -> {
                newVal = ~readByte( getRegister(source[1]) );
                setMemory( getRegister(source[1]), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = ~bytePairToWordLE( readWord( getRegister( source[1] ) ) );
                setMemory( getRegister( source[1] ), newVal );
            }

            case IMMEDIATE_MODE -> newVal = ~source[1];

            default -> E = true;
        }
        updateFlags(newVal);
    }


    public void and(int[] destination, int[] source){
        int operandValue = getOperandValue(source);
        int newVal = 0;

        switch (destination[0]){
            case REGISTER_MODE, REGISTER_WORD_MODE ->{
                newVal = getRegister(destination[1]) & operandValue;

                setRegister( destination[1], newVal);
            }

            case DIRECT_MODE ->{
                newVal = readByte( destination[1] ) & operandValue;
                setMemory( destination[1], newVal);
            }

            case DIRECT_WORD_MODE ->{
                newVal = bytePairToWordLE( readWord( destination[1] ) ) & operandValue;
                setMemory( destination[1], newVal);
            }

            case INDIRECT_MODE ->{
                newVal = readByte( getRegister( destination[1] ) ) & operandValue;
                setMemory( getRegister( destination[1] ), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = bytePairToWordLE( readWord( getRegister( destination[1] ) ) ) & operandValue;
                setMemory( getRegister( destination[1] ), newVal);
            }

            default -> E = true;
        }
        updateFlags(newVal);
    }


    public void or(int[] destination, int[] source){
        int operandValue = getOperandValue(source);
        int newVal = 0;

        switch (destination[0]){
            case REGISTER_MODE, REGISTER_WORD_MODE ->{
                newVal = getRegister(destination[1]) | operandValue;

                setRegister( destination[1], newVal);
            }

            case DIRECT_MODE ->{
                newVal = readByte( destination[1] ) | operandValue;
                setMemory( destination[1], newVal);
            }

            case DIRECT_WORD_MODE ->{
                newVal = bytePairToWordLE( readWord( destination[1] ) ) | operandValue;
                setMemory( destination[1], newVal);
            }

            case INDIRECT_MODE ->{
                newVal = readByte( getRegister( destination[1] ) ) | operandValue;
                setMemory( getRegister( destination[1] ), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = bytePairToWordLE( readWord( getRegister( destination[1] ) ) ) | operandValue;
                setMemory( getRegister( destination[1] ), newVal);
            }

            default -> E = true;
        }
        updateFlags(newVal);
    }


    public void xor(int[] destination, int[] source){
        int operandValue = getOperandValue(source);
        int newVal = 0;

        switch (destination[0]){
            case REGISTER_MODE, REGISTER_WORD_MODE ->{
                newVal = getRegister(destination[1]) ^ operandValue;

                setRegister( destination[1], newVal);
            }

            case DIRECT_MODE ->{
                newVal = readByte( destination[1] ) ^ operandValue;
                setMemory( destination[1], newVal);
            }

            case DIRECT_WORD_MODE ->{
                newVal = bytePairToWordLE( readWord( destination[1] ) ) ^ operandValue;
                setMemory( destination[1], newVal);
            }

            case INDIRECT_MODE ->{
                newVal = readByte( getRegister( destination[1] ) ) ^ operandValue;
                setMemory( getRegister( destination[1] ), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = bytePairToWordLE( readWord( getRegister( destination[1] ) ) ) ^ operandValue;
                setMemory( getRegister( destination[1] ), newVal);
            }

            default -> E = true;
        }
        // TODO : Update flags
    }


    public void nand(int[] destination, int[] source){
        int operandValue = getOperandValue(source);
        int newVal = 0;

        switch (destination[0]){
            case REGISTER_MODE, REGISTER_WORD_MODE ->{
                newVal = getRegister(destination[1]) & operandValue;

                setRegister( destination[1], newVal);
            }

            case DIRECT_MODE ->{
                newVal = ~(readByte( destination[1] ) & operandValue);
                setMemory( destination[1], newVal);
            }

            case DIRECT_WORD_MODE ->{
                newVal = ~(bytePairToWordLE( readWord( destination[1] ) ) & operandValue);
                setMemory( destination[1], newVal);
            }

            case INDIRECT_MODE ->{
                newVal = ~(readByte( getRegister( destination[1] ) ) & operandValue);
                setMemory( getRegister( destination[1] ), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = ~(bytePairToWordLE( readWord( getRegister( destination[1] ) ) ) & operandValue);
                setMemory( getRegister( destination[1] ), newVal);
            }

            default -> E = true;
        }
        updateFlags(newVal);
    }


    public void nor(int[] destination, int[] source){
        int operandValue = getOperandValue(source);
        int newVal = 0;

        switch (destination[0]){
            case REGISTER_MODE, REGISTER_WORD_MODE ->{
                newVal = ~(getRegister(destination[1]) | operandValue);

                setRegister( destination[1], newVal);
            }

            case DIRECT_MODE ->{
                newVal = ~(readByte( destination[1] ) | operandValue);
                setMemory( destination[1], newVal);
            }

            case DIRECT_WORD_MODE ->{
                newVal = ~(bytePairToWordLE( readWord( destination[1] ) ) | operandValue);
                setMemory( destination[1], newVal);
            }

            case INDIRECT_MODE ->{
                newVal = ~(readByte( getRegister( destination[1] ) ) | operandValue);
                setMemory( getRegister( destination[1] ), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = ~(bytePairToWordLE( readWord( getRegister( destination[1] ) ) ) | operandValue);
                setMemory( getRegister( destination[1] ), newVal);
            }

            default -> E = true;
        }
        updateFlags(newVal);
    }


    public void pow(int[] destination, int[] source){
        int operandValue = getOperandValue(source);
        int newVal = 0;

        switch (destination[0]){
            case REGISTER_MODE, REGISTER_WORD_MODE ->{
                newVal = (int) Math.pow( getRegister(destination[1]), operandValue );

                setRegister( destination[1], newVal);
            }

            case DIRECT_MODE ->{
                newVal = (int) Math.pow( readByte( destination[1] ) , operandValue);
                setMemory( destination[1], newVal);
            }

            case DIRECT_WORD_MODE ->{
                newVal = (int) Math.pow( bytePairToWordLE( readWord( destination[1] ) ), operandValue );
                setMemory( destination[1], newVal);
            }

            case INDIRECT_MODE ->{
                newVal = (int) Math.pow( readByte( getRegister( destination[1] ) ), operandValue );
                setMemory( getRegister( destination[1] ), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = (int) Math.pow( bytePairToWordLE( readWord( getRegister( destination[1] ) ) ), operandValue );
                setMemory( getRegister( destination[1] ), newVal);
            }

            default -> E = true;
        }
        updateFlags(newVal);
    }


    public void sqrt(int[] source){
        int newVal = 0;

        switch (source[0]) {

            case REGISTER_MODE, REGISTER_WORD_MODE ->{
                newVal = (int) Math.sqrt( getRegister( source[1] ) );
                setRegister( source[1], newVal );
            }

            case DIRECT_MODE -> {
                newVal = (int) Math.sqrt( readByte( source[1] ) );
                setMemory( source[1], newVal );
            }

            case DIRECT_WORD_MODE -> {
                newVal = (int) Math.sqrt( bytePairToWordLE( readWord( source[1] ) ) );
                setMemory( source[1], newVal );
            }

            case INDIRECT_MODE -> {
                newVal = (int) Math.sqrt( readByte( getRegister(source[1]) ) );
                setMemory( getRegister(source[1]), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = (int) Math.sqrt( bytePairToWordLE( readWord( getRegister(source[1]) ) ) );
                setMemory( getRegister( source[1] ), newVal );
            }

            case IMMEDIATE_MODE -> newVal = ~source[1];

            default -> E = true;
        }
        updateFlags(newVal);
    }


    public void rnd(int[] destination, int[] source){
        int operandValue = getOperandValue(source);
        int newVal = 0;

        switch (destination[0]){
            case REGISTER_MODE, REGISTER_WORD_MODE ->{
                newVal = (int) (Math.random() * operandValue);

                setRegister( destination[1], newVal);
            }

            case DIRECT_MODE, DIRECT_WORD_MODE ->{
                newVal = (int) (Math.random() * operandValue);
                setMemory( destination[1], newVal);
            }

            case INDIRECT_MODE, INDIRECT_WORD_MODE ->{
                newVal = (int) (Math.random() * operandValue);
                setMemory( getRegister( destination[1] ), newVal );
            }

            default -> E = true;
        }
        updateFlags(newVal);
    }


    public void inc(int[] source){
        int newVal = 0;

        switch (source[0]) {

            case REGISTER_MODE, REGISTER_WORD_MODE ->{
                newVal = getRegister(source[1]) + 1;
                setRegister( source[1], newVal );
            }

            case DIRECT_MODE -> {
                newVal = readByte( source[1] ) + 1;
                setMemory( source[1], newVal );
            }

            case DIRECT_WORD_MODE -> {
                newVal = bytePairToWordLE( readWord( source[1] ) ) + 1;
                setMemory( source[1], newVal );
            }

            case INDIRECT_MODE -> {
                newVal = readByte( getRegister(source[1]) ) + 1;
                setMemory( getRegister(source[1]), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = bytePairToWordLE( readWord( getRegister( source[1] ) ) ) + 1;
                setMemory( getRegister( source[1] ), newVal );
            }

            case IMMEDIATE_MODE -> newVal = source[1] + 1;

            default -> E = true;
        }
        updateFlags(newVal);
    }


    public void dec(int[] source){
        int newVal = 0;

        switch (source[0]) {

            case REGISTER_MODE, REGISTER_WORD_MODE ->{
                newVal = getRegister(source[1]) - 1;
                setRegister( source[1], newVal );
            }

            case DIRECT_MODE -> {
                newVal = readByte( source[1] ) - 1;
                setMemory( source[1], newVal );
            }

            case DIRECT_WORD_MODE -> {
                newVal = bytePairToWordLE( readWord( source[1] ) ) - 1;
                setMemory( source[1], newVal );
            }

            case INDIRECT_MODE -> {
                newVal = readByte( getRegister(source[1]) ) - 1;
                setMemory( getRegister(source[1]), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = bytePairToWordLE( readWord( getRegister( source[1] ) ) ) - 1;
                setMemory( getRegister( source[1] ), newVal );
            }

            case IMMEDIATE_MODE -> newVal = source[1] - 1;

            default -> E = true;
        }
        updateFlags(newVal);
    }

    public void la(int[] source){
        int address = (machineCode[ registers[PC] ] << 8) | machineCode[step()];
        Logger.addLog(String.format("Loading address present in PC : 0x%X", address));
        switch (source[0]){
            case REGISTER_MODE, REGISTER_WORD_MODE -> setRegister( source[1], address);
            case DIRECT_MODE, DIRECT_WORD_MODE -> setMemory( source[1], address );
            case INDIRECT_MODE, INDIRECT_WORD_MODE -> setMemory( getRegister(source[1]) , address );
        }
    }


    public void push(int[] source){
        switch (source[0]){

            case REGISTER_MODE ->{
                memory[ registers[SP] ] = (short) getRegisterByte( source[1] );
                registers[SP]--;
            }

            case REGISTER_WORD_MODE -> {
                int[] val = getRegisterWord( source[1] );
                for (int j : val) {
                    memory[registers[SP]] = (short) j;
                    registers[SP]--;
                }

            }

            case DIRECT_MODE -> {
                memory[ registers[SP] ] = (short) readByte( source[1] );
                registers[SP]--;
            }

            case DIRECT_WORD_MODE -> {
                int[] val = readWord( source[1] );
                for (int j : val) {
                    memory[registers[SP]] = (short) j;
                    registers[SP]--;
                }
            }

            case INDIRECT_MODE -> {
                memory[ registers[ SP ]] = (short) readByte( getRegister( source[1] ) );
                registers[SP]--;
            }

            case INDIRECT_WORD_MODE -> {
                int[] val = readWord( getRegister(source[1]) );

                for(int j : val){
                    memory[ registers[SP] ] = (short) j;
                    registers[SP]--;
                }
            }

            case IMMEDIATE_MODE -> {
                int val = source[1];
                if (val <= max_byte_value) {
                    memory[registers[SP]] = (short) val;
                    registers[SP]--;
                }
                else{
                    int low = val & 0xff;
                    int high = (val >> 8) & 0xff;
                    memory[registers[SP]] = (short) low;
                    registers[SP]--;
                    memory[registers[SP]] = (short) high;
                    registers[SP]--;
                }
            }

        }
    }


    public void pop(int[] source){

        switch (source[0]){
            case REGISTER_MODE -> {
                registers[SP]++;
                setRegister( source[1], memory[registers[SP]] );
                memory[registers[SP]] = 0;
            }

            case REGISTER_WORD_MODE -> {
                registers[SP]++;
                int val = bytePairToWordBE( new int[] {memory[registers[SP]], memory[registers[SP] + 1]} );
                memory[registers[SP] - 1] = 0;
                memory[registers[SP]] = 0;
                memory[registers[SP] + 1] = 0;
                registers[SP]++;

                setRegister( source[1], val );
            }

            case DIRECT_MODE -> {
                registers[SP]++;
                memory[source[1]] = memory[registers[SP]];
                memory[registers[SP]] = 0;
            }

            case DIRECT_WORD_MODE -> {
                registers[SP]++;
                int[] val = new int[] {memory[registers[SP]], memory[registers[SP] + 1]};
                memory[ source[1] ] = (short) val[0];
                memory[source[1] + 1] = (short) val[1];

                memory[registers[SP] - 1] = 0;
                memory[registers[SP]] = 0;
                memory[registers[SP] + 1] = 0;
                registers[SP]++;
            }

            case INDIRECT_MODE -> {
                registers[SP]++;
                setMemory( getRegisterByte( source[1] ), memory[registers[SP]] );
            }

            case INDIRECT_WORD_MODE -> {
                registers[SP]++;
                setMemory( getRegister( source[1] ),
                        bytePairToWordBE( new int[] {memory[registers[SP]], memory[registers[SP] + 1]} ) );

                memory[registers[SP] - 1] = 0;
                memory[registers[SP]] = 0;
                memory[registers[SP] + 1] = 0;
                registers[SP]++;
            }
        }
    }


    public void call(int address, int return_address){
        Logger.addLog(String.format("Pushing return address %X into function call stack.", return_address));
        functionCallStack.push(return_address); // save the return address
        Logger.addLog(String.format("Updating PC to point to caller's address : %X", address));
        registers[PC] = address - 1; // sub 2 to nullify the step() and the address byte
    }


    public void jmp(){
        Logger.addLog("Updating PC to point to caller's address : 0x" +
                Integer.toHexString( (machineCode[registers[PC]] << 8) | machineCode[registers[PC] + 1] ));
        registers[PC] = ( ( machineCode[registers[PC]] << 8 ) | machineCode[step()] ) - 1;
    }


    public void cmp(int[] destination, int[] source){
        int val1 = getOperandValue(source);
        int val2 = getOperandValue(destination);

        Z = val2 == val1;
        N = val2 < val1;
    }

    /// //////////////////////////////////////////////////////////////////////////////////////////////////
    /// ////
    ///
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////
    ///
    ///
    /// ///////////////////////////// CPU INITIALIZATION //////////////////////////////////////////////////


    public CPUModule16BIT(){
        super();

        System.out.println("Starting 16-bit CPU module");
        bit_length = 16;

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
                Launcher.appConfig.get("Cycles"), delayAmountMilliseconds);

        Logger.addLog(String.format("""
                Starting with %dKB of memory. Total of %d locations
                Data size %dKB starts at address 0x%X(%d)
                Stack size %dKB start at address 0x%X(%d)
                last addressable location : 0x%X(%d)
                """, memorySize, mem_size_B,
                dataSize, data_start, data_start,
                stackSize, stack_start, stack_start,
                last_addressable_location, last_addressable_location));


        Logger.addLog(String.format("CPU speed set to %s Cycles per second. With a step delay of %sMS\n",
                Launcher.appConfig.get("Cycles"), delayAmountMilliseconds));

        reset();
    }


    public void reset(){
        System.out.println("Initializing memory.");
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

        eachInstruction = new HashMap<>();

        System.out.println("Initializing registers.");
        registerPairStart = 0;

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

        RCX = registerPairStart + 2;


        System.out.println("Setting the CPU state.");
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

        registers[SP] = (stack_start + memory.length - stack_start - 1);
        registers[PC] = 0;

    }
    /// //////////////////////////////////////////////////////////////////////////////
    /// /////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////

}