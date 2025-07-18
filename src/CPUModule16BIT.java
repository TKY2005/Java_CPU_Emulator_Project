import java.util.*;

public class CPUModule16BIT extends CPU {

    // CPU specific variables //
    public static final int bit_length = 16;

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
    public static final int INDIRECT_MEMORY_WORD_MODE = 9;

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


    /// /////////////////////////// HELPER FUNCTIONS /////////////////////////////////////////////////////////
    /// /////////////////////////////////////////////////////////////////////////////////////////////////////


    public int getOperandValue(int[] source){
        return switch (source[0]){
            case REGISTER_MODE, REGISTER_WORD_MODE -> getRegister(source[1]);
            case DIRECT_MODE -> readByte(source[1]);
            case DIRECT_WORD_MODE -> bytePairToWordLE((readWord( source[1] )));
            case INDIRECT_MODE -> readByte( getRegister( source[1] ) );
            case INDIRECT_MEMORY_WORD_MODE -> bytePairToWordLE( readWord( getRegister( source[1] ) ) );
            case IMMEDIATE_MODE -> source[1];
            default -> max_pair_value + 1;
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

                // decide if we are dealing with a byte or a word depending on the register name
                if (tokens[tokenIndex].charAt(0) == REGISTER_PREFIX &&
                tokens[tokenIndex].charAt( tokens[tokenIndex].length() - 1 ) == 'x') result[i] = REGISTER_WORD_MODE;

                if (tokens[tokenIndex].charAt(0) == DIRECT_MEMORY_PREFIX &&
                tokens[tokenIndex - 1].charAt(0) == REGISTER_PREFIX &&
                tokens[tokenIndex - 1].charAt( tokens[tokenIndex - 1].length() - 1 ) == 'x') result[i] = DIRECT_WORD_MODE;

                else if (tokens[tokenIndex].charAt(0) == INDIRECT_MEMORY_PREFIX &&
                tokens[tokenIndex].charAt( tokens[tokenIndex].length() - 1 ) == 'x') result[i] = INDIRECT_MEMORY_WORD_MODE;


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

        Integer mainEntryPoint = functions.get("MAIN");
        if (mainEntryPoint == null){
            String err = "MAIN function label not found.";
            triggerProgramError(new ErrorHandler.CodeCompilationError(err),
                    err, ErrorHandler.ERR_CODE_MAIN_NOT_FOUND);
        }
        registers[PC] = (short) (int) mainEntryPoint;
        I = true;

        while (!programEnd && registers[PC] < machine_code.length){
            if (canExecute) {
                // System.out.printf("Executing machine code : 0x%X -> 0x%X -> %s.\n",
                //       registers[PC], machine_code[registers[PC]], instructionSet.get( machine_code[registers[PC]] ));
                switch (machine_code[registers[PC]]) {
                    case INS_EXT -> {
                        programEnd = true;
                    }

                    // step function increments PC and returns its value
                    // we step two times for each operand. one step for mode. another step for value
                    case INS_SET -> {
                        int[] destination = getNextOperand();
                        int[] source = getNextOperand();
                        set(destination, source);
                    }
                    case INS_OUT -> {
                        int[] destination = getNextOperand();
                        out(destination);
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

                    /*
                    case INS_POW -> {
                        short[] destination = getNextOperand();
                        short[] source = getNextOperand();
                        pow(destination, source);
                    }

                    case INS_SQRT -> {
                        short[] destination = getNextOperand();
                        sqrt(destination);
                    }

                    case INS_RND -> {
                        short[] destination = getNextOperand();
                        short[] source = getNextOperand();
                        rnd(destination, source);
                    }

                    case INS_INC -> {
                        short[] destination = getNextOperand();
                        inc(destination);
                    }
                    case INS_DEC -> {
                        short[] destination = getNextOperand();
                        dec(destination);
                    }

                    case INS_NOT -> {
                        short[] source = getNextOperand();
                        not(source);
                    }

                    case INS_AND -> {
                        short[] destination = getNextOperand();
                        short[] source = getNextOperand();
                        and(destination, source);
                    }

                    case INS_OR -> {
                        short[] destination = getNextOperand();
                        short[] source = getNextOperand();
                        or(destination, source);
                    }

                    case INS_XOR -> {
                        short[] destination = getNextOperand();
                        short[] source = getNextOperand();
                        xor(destination, source);
                    }

                    case INS_NAND -> {
                        short[] destination = getNextOperand();
                        short[] source = getNextOperand();
                        nand(destination, source);
                    }

                    case INS_NOR -> {
                        short[] destination = getNextOperand();
                        short[] source = getNextOperand();
                        nor(destination, source);
                    }

                    case INS_LA -> {
                        // Get the source (must be 16-bit compatible). step to the address. load into source
                        short[] source = getNextOperand();
                        step();
                        step();
                        la(source);
                    }

                    case INS_LLEN -> {
                        short[] destination = getNextOperand();
                        step();
                        step();
                        int start = machine_code[registers[PC]];
                        short len = 0;
                        while (getMemory(start) != NULL_TERMINATOR) {
                            start++;
                            len++;
                        }

                        switch (destination[0]) {
                            case REGISTER_MODE -> setRegister(destination[1], len);
                            case DIRECT_MODE -> setMemory(destination[1], len);
                            case INDIRECT_MODE -> setMemory(getRegister(destination[1]), len);
                            default -> E = true;
                        }
                    }

                    case INS_OUTS -> {
                        int start = registers[SS];
                        while (getMemory(start) != NULL_TERMINATOR) {
                            outputString.append((char) getMemory(start));
                            start++;
                        }
                        outputString.append("\n");
                    }

                    case INS_PUSH -> {
                        short[] source = getNextOperand();
                        push(source);
                    }

                    case INS_POP -> {
                        short[] source = getNextOperand();
                        pop(source);
                    }

                    case INS_CALL -> {
                        step();
                        int address = machine_code[step()];
                        int return_address = step() - 1;
                        call(address, return_address);
                    }
                    case INS_RET -> {
                        System.out.println(functionCallStack);
                        int return_address = functionCallStack.pop();
                        System.out.printf("Popping address 0x%X from the stack.\n", return_address);
                        registers[PC] = (short) return_address;
                    }

                    case INS_CE -> {
                        step();
                        int address = machine_code[step()];
                        int return_address = machine_code[step()];
                        if (Z) call(address, return_address);
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
                    }
                    case INS_CLE -> {
                        step();
                        int address = machine_code[step()];
                        int return_address = machine_code[step()];
                        if (N || Z) call(address, return_address);
                    }
                    case INS_CG -> {
                        step();
                        int address = machine_code[step()];
                        int return_address = machine_code[step()];
                        if (!N) call(address, return_address);
                    }
                    case INS_CGE -> {
                        step();
                        int address = machine_code[step()];
                        int return_address = machine_code[step()];
                        if (!N || Z) call(address, return_address);
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
                    }
                    case INS_JNE -> {
                        step();
                        step();
                        if (!Z) jmp();
                    }
                    case INS_JL -> {
                        step();
                        step();
                        if (N) jmp();
                    }
                    case INS_JLE -> {
                        step();
                        step();
                        if (N || Z) jmp();
                    }
                    case INS_JG -> {
                        step();
                        step();
                        if (!N) jmp();
                    }
                    case INS_JGE -> {
                        step();
                        step();
                        if (!N || Z) jmp();
                    }

                    case INS_CMP -> {
                        int[] destination = getNextOperand();
                        int[] source = getNextOperand();
                        cmp(destination, source);
                    }

                    case INS_LOOP -> {
                        // if RC > 0: decrement RC and jump to the label address specified.
                        step();
                        step();
                        //short address = (short) machine_code[ registers[PC] ];

                        registers[2]--;
                        if (registers[2] > 0) {
                            jmp();
                        }
                    }

                    case INS_INT -> {
                        if (I) {
                            boolean x = VirtualMachine.interruptHandler(registers, memory);
                            if (!x) E = true;
                        } else System.out.println("Interrupt flag not set. skipping.");
                    }
                    */


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
                step();
            }

        }

        outputString.append("Program terminated with code : ").append(status_code);
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////
    /// //////////////////////////////////////////////////////////////////////////////////////////////////////
    ///
    /// ///////////////////////////// INSTRUCTION SET ////////////////////////////////////////////////////////
    ///
    ///


    public void set(int[] destination, int[] source){

        int operandValue = getOperandValue(source);

        switch (destination[0]){

            case REGISTER_MODE, REGISTER_WORD_MODE -> setRegister( destination[1], operandValue );
            case DIRECT_MODE -> setMemory( destination[1], operandValue );
            case INDIRECT_MODE, INDIRECT_MEMORY_WORD_MODE -> setMemory( getRegister( destination[1] ), operandValue );
            default -> E = true;
        }
    }


    public void out(int[] source){

        outputString.append(getOperandValue(source));
    }


    public void add(int[] destination, int[] source){

        int operandValue = getOperandValue(source);

        switch (destination[0]){
            case REGISTER_MODE, REGISTER_WORD_MODE -> setRegister( destination[1],
                    getRegister( destination[1] ) + operandValue);

            case DIRECT_MODE -> setMemory( destination[1],
                    readByte( destination[1] ) + operandValue);

            case DIRECT_WORD_MODE -> setMemory( destination[1],
                    bytePairToWordLE( readWord( destination[1] ) ) + operandValue);

            case INDIRECT_MODE -> setMemory( getRegister( destination[1] ),
                     readByte( getRegister( destination[1] ) ) + operandValue );

            case INDIRECT_MEMORY_WORD_MODE -> setMemory( getRegister( destination[1] ),
                    bytePairToWordLE( readWord( destination[1] ) ) + operandValue);

            default -> E = true;
        }
        // TODO : Update flags
    }


    public void sub(int[] destination, int[] source){
        int operandValue = getOperandValue(source);

        switch (destination[0]){
            case REGISTER_MODE, REGISTER_WORD_MODE -> setRegister( destination[1],
                    getRegister( destination[1] ) - operandValue);

            case DIRECT_MODE -> setMemory( destination[1],
                    readByte( destination[1] ) - operandValue);

            case DIRECT_WORD_MODE -> setMemory( destination[1],
                    bytePairToWordLE( readWord( destination[1] ) ) - operandValue);

            case INDIRECT_MODE -> setMemory( getRegister( destination[1] ),
                     readByte( getRegister( destination[1] ) ) - operandValue );

            case INDIRECT_MEMORY_WORD_MODE -> setMemory( getRegister( destination[1] ),
                    bytePairToWordLE( readWord( destination[1] ) ) - operandValue);

            default -> E = true;
        }
        // TODO : Update flags
    }


    public void mul(int[] destination, int[] source){
        int operandValue = getOperandValue(source);

        switch (destination[0]){
            case REGISTER_MODE, REGISTER_WORD_MODE -> setRegister( destination[1],
                    getRegister( destination[1] ) * operandValue);

            case DIRECT_MODE -> setMemory( destination[1],
                    readByte( destination[1] ) * operandValue);

            case DIRECT_WORD_MODE -> setMemory( destination[1],
                    bytePairToWordLE( readWord( destination[1] ) ) * operandValue);

            case INDIRECT_MODE -> setMemory( getRegister( destination[1] ),
                     readByte( getRegister( destination[1] ) ) * operandValue );

            case INDIRECT_MEMORY_WORD_MODE -> setMemory( getRegister( destination[1] ),
                    bytePairToWordLE( readWord( destination[1] ) ) * operandValue);

            default -> E = true;
        }
        // TODO : Update flags
    }


    public void div(int[] destination, int[] source){
        int operandValue = getOperandValue(source);

        switch (destination[0]){
            case REGISTER_MODE, REGISTER_WORD_MODE -> setRegister( destination[1],
                    getRegister( destination[1] ) / operandValue);

            case DIRECT_MODE -> setMemory( destination[1],
                    readByte( destination[1] ) / operandValue);

            case DIRECT_WORD_MODE -> setMemory( destination[1],
                    bytePairToWordLE( readWord( destination[1] ) ) / operandValue);

            case INDIRECT_MODE -> setMemory( getRegister( destination[1] ),
                     readByte( getRegister( destination[1] ) ) / operandValue );

            case INDIRECT_MEMORY_WORD_MODE -> setMemory( getRegister( destination[1] ),
                    bytePairToWordLE( readWord( destination[1] ) ) / operandValue);

            default -> E = true;
        }
        // TODO : Update flags
    }


    public void not(short[] source){

    }


    public void and(short[] destination, short[] source){

    }


    public void or(short[] destination, short[] source){

    }


    public void xor(short[] destination, short[] source){

    }


    public void nand(short[] destination, short[] source){

    }


    public void nor(short[] destination, short[] source){

    }


    public void pow(short[] destination, short[] source){

    }


    public void sqrt(short[] source){

    }


    public void rnd(short[] destination, short[] source){

    }


    public void inc(short[] source){

    }


    public void dec(short[] source){

    }

    public void la(short[] source){

    }


    public void push(short[] source){

    }


    public void pop(short[] source){

    }


    public void call(int address, int return_address){

    }


    public void jmp(){

    }


    public void cmp(short[] destination, short[] source){

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

        registers[SP] = (short) (stack_start + memory.length - stack_start - 1);
        registers[PC] = 0;
    }
    /// //////////////////////////////////////////////////////////////////////////////
    /// /////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////

}