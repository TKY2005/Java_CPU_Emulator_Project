import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class CPUModule16BIT extends CPU {

    // CPU specific variables //

    public static final int min_byte_value = -127;

    /// ///////


    // CPU architecture
    public int[] registers;
    public String[] registerNames;

    int registerPairStart;


    static int PC = 18;
    static int SP = 19;
    static int SS = 20;
    static int SE = 21;
    static int DI = 22;
    static int DP = 23;
    static int CX;


    // flags
    boolean N, C, O, Z, I, T, E;

    // listeners
    private onStepListener stepListener;

    String debugSource = "CPU_MODULE_16_BIT";

    int[] functionPointers;
    int[] dataPointers;
    HashMap<Integer, String> functionAddresses;
    HashMap<Integer, String> dataAddresses;
    StringBuilder code;


    /// /////////////////////////// HELPER FUNCTIONS /////////////////////////////////////////////////////////
    /// /////////////////////////////////////////////////////////////////////////////////////////////////////

    public Integer getPC(){
        return registers[PC];
    }

    public int getOperandValue(int[] source) {
        Logger.addLog("Fetching source", logDevice);
        return switch (source[0]) {
            case REGISTER_MODE, REGISTER_WORD_MODE -> getRegister(source[1]);
            case DIRECT_MODE -> memoryController.readByte( ( source[1] << 8 ) | source[2]);
            case DIRECT_WORD_MODE -> memoryController.bytePairToWordLE((memoryController.readWord( ( source[1] << 8 ) | source[2])));
            case INDIRECT_MODE -> memoryController.readByte(getRegister(source[1]));
            case INDIRECT_WORD_MODE -> memoryController.bytePairToWordLE(memoryController.readWord(getRegister(source[1])));
            case IMMEDIATE_MODE -> (source[1] << 8) | source[2];
            default -> max_pair_value + 1;
        };
    }

    public String getDisassembledOperand(int[] operand) {
        return switch (operand[0]) {
            case REGISTER_MODE, REGISTER_WORD_MODE -> {
                String mode = ((operand[0] == REGISTER_MODE) && operand[1] < 18) ? "BYTE" : "WORD";
                yield mode + " " + CPU.REGISTER_PREFIX + getRegisterName(operand[1], false);
            }
            case DIRECT_MODE, DIRECT_WORD_MODE ->{
                String mode = (operand[0] == DIRECT_MODE) ? "BYTE" : "WORD";
                yield mode + " " + CPU.HEX_MEMORY + Integer.toHexString((operand[1] << 8) | operand[2]);
            }
            case INDIRECT_MODE, INDIRECT_WORD_MODE ->{
                String mode = ((operand[0] == INDIRECT_MODE)) ? "BYTE" : "WORD";
                yield mode + " " + CPU.INDIRECT_MEMORY_PREFIX + getRegisterName(operand[1], false);
            }
            case IMMEDIATE_MODE -> CPU.HEX_PREFIX + Integer.toHexString( ( operand[1] << 8 ) | operand[2] ).toUpperCase();

            case DATA_MODE -> {
                int high = operand[1], low = operand[2];
                int address = (high << 8) | low;
                yield String.format("%s%s @0x%04X", DATA_PREFIX, dataAddresses.get(address), address);
            }

            case FUNCTION_MODE -> {
                int high = operand[1], low = operand[2];
                int address = (high << 8) | low;
                yield String.format("< %s @0x%04X >", functionAddresses.get(address), address);
            }

            default -> "??";
        };
    }


    public void setRegister(int registerID, int value) {

        if (registerID < registers.length) {

            if (registerID == PC && Launcher.appConfig.get("OverwritePC").equals("false")) {
                String err = "Direct modification of PC register is not allowed." +
                        " if you wish to proceed, change that in the settings.";
                triggerProgramError(
                        err, ErrorHandler.ERR_CODE_PC_MODIFY_UNALLOWED);
            } else if (registerID < registerPairStart) {

                if (value > max_byte_value) {
                    String err = String.format("The value 0x%X(%d) is bigger than the selected register (%s) bit width.",
                            value, value, registerNames[registerID]);
                    triggerProgramError(
                            err, ErrorHandler.ERR_CODE_CPU_SIZE_VIOLATION);
                } else {
                    registers[registerID] = value;
                    updateRegisterPairs();
                }
            } else if (registerID >= registerPairStart) {

                if (value > max_pair_value) {
                    String err = String.format("The value 0x%X(%d) is bigger than the selected register (%s) bit width.",
                            value, value, registerNames[registerID]);
                    triggerProgramError(
                            err, ErrorHandler.ERR_CODE_CPU_SIZE_VIOLATION);
                } else {
                    registers[registerID] = value;
                    updateRegisterBytes();
                }
            }
        }
    }


    public int getRegisterCode(String registerName) {
        for (int i = 0; i < registerNames.length; i++) {
            if (registerNames[i].equalsIgnoreCase(registerName)) return i;
        }
        return -1;
    }

    public int getRegister(int registerID) {
        return registers[registerID];
    }

    public int getRegisterByte(int registerID) {
        if (registerID < registerPairStart) {
            return registers[registerID];
        }
        return max_byte_value + 1;
    }

    public int[] getRegisterWord(int registerID) {
        int low = 0, high = 0;
        if (registerID >= registerPairStart) {
            int registerVal = registers[registerID];
            low = registerVal & 0xff;
            high = (registerVal >> 8) & 0xff;
        }

        return new int[]{low, high};
    }


    public void updateRegisterPairs() {
        int lowByteIndex = 0;
        int highByteIndex = 1;

        for (int i = registerPairStart; i < registerPairStart + 6; i++) {
            registers[i] = (registers[highByteIndex] << 8) | registers[lowByteIndex];
            lowByteIndex += 2;
            highByteIndex += 2;
        }
    }

    public void updateRegisterBytes() {
        int registerLowByteIndex = 0;
        int registerHighByteIndex = 1;

        for (int i = registerPairStart; i < registerPairStart + 6; i++) {

            registers[registerLowByteIndex] = registers[i] & 0xff;
            registers[registerHighByteIndex] = (registers[i] >> 8) & 0xff;

            registerLowByteIndex += 2;
            registerHighByteIndex += 2;
        }
    }



    public String dumpRegisters() {
        int registersPerLine = 3;
        StringBuilder result = new StringBuilder();
        for (int i = registerPairStart; i < registers.length; i++) {

            if (i % registersPerLine == 0) result.append("\n");

            result.append(String.format("%s: 0x%04X\t", registerNames[i].toUpperCase(), registers[i]));
        }
        return result.toString();
    }

    @Override
    public String dumpFlags() {
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

    public void updateFlags(int value) {
        Logger.addLog("Updating flags.", logDevice);
        short flagSetter = (short) value;

        Z = flagSetter == 0;
        N = ((flagSetter >>> 15) & 1) == 1;
        if (N) O = value > Short.MAX_VALUE || value < Short.MIN_VALUE;
        else C = value > max_pair_value;
    }


    public int step() {

        registers[PC]++;
        long currentTime = System.currentTimeMillis();
        if (stepListener != null && (currentTime - lastTimeSinceUpdate) > UI_UPDATE_MAX_INTERVAL ){
            stepListener.updateUI();
            lastTimeSinceUpdate = currentTime;
        }
        try {
            Thread.sleep(delayAmountMilliseconds);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return registers[PC];
    }

    public int[] getNextOperand() {
        List<Integer> n = new ArrayList<>();
        n.add(machineCode[step()]);
        if (n.getFirst() == DIRECT_MODE || n.getFirst() == DIRECT_WORD_MODE) n.add(machineCode[step()]);
        else if (n.getFirst() == IMMEDIATE_MODE) n.add(machineCode[step()]);
        n.add(machineCode[step()]);
        return n.stream().mapToInt(Integer::intValue).toArray();
    }


    public void setUIupdateListener(onStepListener listener) {
        this.stepListener = listener;
    }


    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ///
    ///
    ///
    /// //////////////////////////// CPU FUNCTIONALITY ////////////////////////////////////////////////////////

    public String getRegisterName(int registerID, boolean toUpperCase) {

        if (!toUpperCase) return registerNames[registerID];
        else return registerNames[registerID].toUpperCase();
    }

    @Override
    public int[] toMachineCode(String instruction) {return new int[] {0};}


    public List<Integer> toMachineCode16(String instruction) {
        String[] tokens = instruction.trim().split(" ");
        List<Integer> result = new ArrayList<>();

        // Instruction format: opcode (1 byte) optional: operand1 (2 bytes) optional: operand2 (2 bytes)
        // NOTE: if the instruction has an address, then operand size will be 3 bytes (1 byte for mode, 2 bytes for address)
        // Output machine code: opcode operand1_addressing_mode operand1_value operand2_addressing_mode operand2_value

        Integer opCode = translationMap.get(tokens[0].toLowerCase());
        if (opCode == null) {
            String err = String.format("Unknown instruction : %s\n", tokens[0]);
            status_code = ErrorHandler.ERR_COMP_UNDEFINED_INSTRUCTION;
            triggerProgramError(err, status_code);
        } else result.add(opCode); // tokens[0] should always be the opcode.

        // figure out which addressing mode is used.
        if (tokens.length > 1) {
            int byteIndex = 1;
            for (int i = 1; i < tokens.length; i++) {

                switch (tokens[i].charAt(0)) {
                    case REGISTER_PREFIX -> {
                        result.add(REGISTER_MODE);
                        result.add(getRegisterCode( tokens[i].substring(1) ));
                        byteIndex += 2;
                    }
                    case DIRECT_MEMORY_PREFIX ->{
                        result.add(DIRECT_MODE);
                        int addr = Integer.parseInt(tokens[i].substring(1));
                        int low = addr & 0xff;
                        int high = (addr >> 8) & 0xff;
                        result.add(high);
                        result.add(low);
                        byteIndex += 3;
                    }
                    case INDIRECT_MEMORY_PREFIX ->{
                        result.add(INDIRECT_MODE);
                        result.add( getRegisterCode( tokens[i].substring(1) ) );
                        byteIndex += 2;
                    }
                    case IMMEDIATE_PREFIX -> {
                        result.add(IMMEDIATE_MODE);
                        int imm16 = Integer.parseInt(tokens[i].substring(1));
                        int low = imm16 & 0xff;
                        int high = (imm16 >> 8) & 0xff;
                        result.add(high);
                        result.add(low);
                        byteIndex += 3;
                    }
                    case DATA_PREFIX, DATA_PREFIX_ALT -> {
                        result.add(DATA_MODE);
                        Integer addr = dataMap.get( tokens[i].substring(1) );
                        if (addr == null) {
                            String err = String.format("The variable '%s' doesn't exist in the data section.\n",
                                tokens[i].substring(1));
                            status_code = ErrorHandler.ERR_COMP_NULL_DATA_POINTER;
                            triggerProgramError(
                                err, status_code);
                        }
                        else {
                            int low = addr & 0xff;
                            int high = (addr >> 8) & 0xff;
                            result.add(high);
                            result.add(low);
                            byteIndex += 3;
                        }
                    }
                    case STRING_PREFIX ->{
                        result.add(STRING_MODE);
                        byteIndex++;
                    }
                    case MEMORY_SEGMENT_OFFSET_PREFIX ->{
                        result.add(0x0A);
                        byteIndex++;
                    }
                    case MEMORY_MODE_PREFIX -> {
                        continue;
                    }
                    default -> {
                        // if we meet a non-prefixed token
                        // we first assume it's a function definition
                        // if not found in the function table, we assume it's a symbol
                        // if neither, throw error.
                        Integer addr = functions.get( tokens[i] );
                        if (addr == null) {
                            Integer val = definitionMap.get( tokens[i] );
                            if (val == null) {
                                String err = String.format("The function '%s' doesn't exist in the ROM.\n",
                                        tokens[i].substring(1));
                                status_code = ErrorHandler.ERR_COMP_NULL_DATA_POINTER;
                                triggerProgramError(
                                        err, status_code);
                                return result;
                            }else{
                                result.add(IMMEDIATE_MODE);
                                int low = val & 0xff;
                                int high = (val >> 8) & 0xff;
                                result.add(high);
                                result.add(low);
                                byteIndex += 3;
                            }
                        }
                        else {
                            result.add(FUNCTION_MODE);
                            int low = addr & 0xff;
                            int high = (addr >> 8) & 0xff;
                            result.add(high);
                            result.add(low);
                            byteIndex += 3;
                        }
                    }
                }

                // decide if we are dealing with a byte or a word depending on the register name
                if (tokens[i].charAt(0) == REGISTER_PREFIX &&
                        tokens[i].charAt(tokens[i].length() - 1) == 'x')
                    result.set(byteIndex - 2, REGISTER_WORD_MODE);

                if (tokens[i].charAt(0) == DIRECT_MEMORY_PREFIX &&
                        tokens[i - 1].charAt(0) == REGISTER_PREFIX &&
                        tokens[i - 1].charAt(tokens[i - 1].length() - 1) == 'x')
                    result.set(byteIndex - 3, DIRECT_WORD_MODE);

                else if (tokens[i].charAt(0) == INDIRECT_MEMORY_PREFIX &&
                        tokens[i].charAt(tokens[i].length() - 1) == 'x')
                    result.set(byteIndex - 2, INDIRECT_MODE);


                // maybe the user wants to manually specify the mode. (mode overriding)
                if (tokens[i - 1].equalsIgnoreCase(MEMORY_MODE_PREFIX + "byte")){

                    switch (tokens[i].charAt(0)){
                        case DIRECT_MEMORY_PREFIX -> result.set(byteIndex - 3, DIRECT_MODE);
                        case INDIRECT_MEMORY_PREFIX -> result.set(byteIndex - 2, INDIRECT_MODE);
                        case REGISTER_PREFIX -> result.set(byteIndex - 2, REGISTER_MODE);
                    }
                }
                else if (tokens[i - 1].equalsIgnoreCase(MEMORY_MODE_PREFIX + "word")){

                    switch (tokens[i].charAt(0)){
                        case DIRECT_MEMORY_PREFIX -> result.set(byteIndex - 3, DIRECT_WORD_MODE);
                        case INDIRECT_MEMORY_PREFIX -> result.set(byteIndex - 2, INDIRECT_WORD_MODE);
                        case REGISTER_PREFIX -> result.set(byteIndex - 2, REGISTER_WORD_MODE);
                    }

                }
            }
        }
        System.out.print(instruction + " => ");
        for (int j : result) System.out.printf("0x%X ", j);
        System.out.println();
        return result;
    }

    @Override
    public int getInstructionLength(String instruction){
        String[] tokens = instruction.trim().split(" ");

        // Instruction format: opcode (1 byte) optional: operand1 (2 bytes) optional: operand2 (2 bytes)
        // NOTE: if the instruction has an address, then operand size will be 3 bytes (1 byte for mode, 2 bytes for address)
        // Output machine code: opcode operand1_addressing_mode operand1_value operand2_addressing_mode operand2_value
        int length = 1; // 1 byte for opcode
        for (int i = 1; i < tokens.length; i++) {
            //length += 2; // 2 bytes for all remaining operands
            switch (tokens[i].charAt(0)) {
                case REGISTER_PREFIX , INDIRECT_MEMORY_PREFIX -> length += 2;
                case MEMORY_MODE_PREFIX -> length += 0;
                default -> length += 3;
            }
        }
        return length;
    }


    @Override
    public int[] compileToMemoryImage(String code) {
        String[] lines = code.split("\n");
        List<Integer> memImageList = new ArrayList<>();

        // preset the list size
        for(int i = 0; i < memoryController.mem_size_B - (metadataLength - 2); i++) memImageList.add(0x00);

        StringBuilder machineCodeString = new StringBuilder();

        if (memoryController.mem_size_B > 0xffff) {
            String err = "This Maximum amount of addressable memory for this architecture is 64KB";
            triggerProgramError(err, ErrorHandler.ERR_CODE_INVALID_MEMORY_LAYOUT);
        }


        // Step 1- Calculate the function offset addresses, add .DATA variables to the data section, and build a raw code string
        String fullCode = "";
        for (int i = 0; i < lines.length; i++) {
            currentLine++;
            // Which section are we in? (is it a line of code? is it a function. and if it starts with '.' is it the data section?)
            if (lines[i].equals(".DATA")) {
                System.out.println("Data section detected.");
                int offset = 0;
                i++; // skip .DATA line

                while (!lines[i].equalsIgnoreCase("end")) {

                    String[] x = lines[i].trim().split(" ");
                    int dataStart = memoryController.dataOffset;
                    if (x[0].equals("org")) memoryController.dataOffset = Integer.parseInt(x[1].substring(1)) - offset;

                    else {
                        // store mode
                        // 1- Byte mode
                        // 2- Word mode
                        // 3- byte buffer mode
                        // 4- word buffer mode
                        // else- Undefined.
                        int storeMode = 0;
                        dataMap.put(x[0], dataStart + offset);

                        // define size of data (required)
                        if (x[1].equalsIgnoreCase("db")) storeMode = DATA_BYTE_MODE;
                        else if (x[1].equalsIgnoreCase("dw")) storeMode = DATA_WORD_MODE;

                        // alternatively reserve space for byte/word buffer
                        else if (x[1].equalsIgnoreCase("resb")){
                            int bufferSize = Integer.parseInt(x[2].substring(1));
                            memoryController.setMemory(dataStart + offset + bufferSize, ARRAY_TERMINATOR, DATA_BYTE_MODE);
                            System.out.printf("""
                                    reserved '%d' bytes for byte buffer '%s', start address: 0x%04X(%d):0x%04X(%d) -> 0x%04X(%d), end address: 0x%04X(%d):0x%04X(%d) -> 0x%04X(%d) 
                                    """, bufferSize, x[0],
                                    MemoryModule.data_start, MemoryModule.data_start,
                                    dataStart + offset, dataStart + offset,
                                    MemoryModule.data_start + dataStart + offset, MemoryModule.data_start + dataStart + offset,
                                    MemoryModule.data_start, MemoryModule.data_start,
                                    dataStart + offset + bufferSize, dataStart + offset + bufferSize,
                                    MemoryModule.data_start + dataStart + offset + bufferSize, MemoryModule.data_start + dataStart + offset + bufferSize);

                            offset += bufferSize;
                            storeMode = DATA_BUFFER_BYTE_MODE;
                        }

                        else if (x[1].equalsIgnoreCase("resw")){
                            int bufferSize = Integer.parseInt(x[2].substring(1)) * 2;
                            memoryController.setMemory(dataStart + offset + bufferSize, ARRAY_TERMINATOR, DATA_BYTE_MODE);
                            System.out.printf("""
                                    reserved '%d' bytes for word buffer '%s', start address: 0x%04X(%d):0x%04X(%d) -> 0x%04X(%d), end address: 0x%04X(%d):0x%04X(%d) -> 0x%04X(%d) 
                                    """, bufferSize, x[0],
                                    MemoryModule.data_start, MemoryModule.data_start,
                                    dataStart + offset, dataStart + offset,
                                    MemoryModule.data_start + dataStart + offset, MemoryModule.data_start + dataStart + offset,
                                    MemoryModule.data_start, MemoryModule.data_start,
                                    dataStart + offset + bufferSize, dataStart + offset + bufferSize,
                                    MemoryModule.data_start + dataStart + offset + bufferSize, MemoryModule.data_start + dataStart + offset + bufferSize);

                            offset += bufferSize;
                            storeMode = DATA_BUFFER_WORD_MODE;
                        }

                        if (storeMode != DATA_BYTE_MODE && storeMode != DATA_WORD_MODE
                         && storeMode != DATA_BUFFER_BYTE_MODE && storeMode != DATA_BUFFER_WORD_MODE) {
                            String err = "Undefined data store mode." + "'" + x[1] + "'";
                            triggerProgramError(err, ErrorHandler.ERR_COMP_UNDEFINED_DATA_MODE);
                        }

                        // We're storing a string

                        if (x[2].startsWith(String.valueOf(STRING_PREFIX))) { // 34 in decimal 0x22 in hex
                            String fullString = String.join(" ", x);

                            int startIndex = fullString.indexOf(34) + 1;
                            int endIndex = fullString.length() - 1;
                            fullString = fullString.substring(startIndex, endIndex);

                            // Handle escape characters //

                            List<Integer> string_bytes = toByteString(fullString);

                            fullString = "";
                            for(int j = 0; j < string_bytes.size(); j++){
                                fullString += (char) (int) string_bytes.get(j);
                            }


                            for (int j = 0; j < fullString.length(); j++) {

                                if (storeMode == DATA_BYTE_MODE) {
                                     System.out.printf("Setting memory location 0x%X(%d):0x%X(%d) -> 0x%X(%d) to byte char %c\n",
                                            MemoryModule.data_start, MemoryModule.data_start,
                                            dataStart + offset, dataStart + offset,
                                            MemoryModule.data_start + dataStart + offset, MemoryModule.data_start + dataStart + offset,
                                            fullString.charAt(j));
                                    memoryController.setMemory(dataStart + offset, (short) fullString.charAt(j), DATA_BYTE_MODE);
                                    offset++;

                                }else if (storeMode == DATA_WORD_MODE){
                                    int low = fullString.charAt(j) & 0xff;
                                    int high = (fullString.charAt(j) >> 8) & 0xff;
                                    System.out.printf("Setting memory location 0x%X(%d):0x%X(%d) -> 0x%X(%d) to word char %c\n",
                                            MemoryModule.data_start, MemoryModule.data_start,
                                            dataStart + offset, dataStart + offset,
                                            MemoryModule.data_start + dataStart + offset, MemoryModule.data_start + dataStart + offset
                                            , fullString.charAt(j));
                                    memoryController.setMemory(dataStart + offset, fullString.charAt(j), DATA_WORD_MODE);
                                    offset += 2;
                                }

                            }
                            memoryController.setMemory(dataStart + offset, ARRAY_TERMINATOR, DATA_BYTE_MODE);
                            offset++;

                            // We're storing an array of numbers
                        } else {
                            for (int j = 2; j < x.length; j++) {

                                if (storeMode == DATA_BYTE_MODE) {

                                    System.out.printf("Setting memory location 0x%X(%d):0x%X(%d) -> 0x%X(%d) to byte value 0x%X(%d)\n",
                                            MemoryModule.data_start, MemoryModule.data_start,
                                            dataStart + offset, dataStart + offset,
                                            MemoryModule.data_start + dataStart + offset, MemoryModule.data_start + dataStart + offset,
                                            Integer.parseInt(x[j].substring(1)), Integer.parseInt(x[j].substring(1)));

                                    memoryController.setMemory(dataStart + offset, Integer.parseInt(x[j].substring(1)), DATA_BYTE_MODE);
                                    offset++;

                                }else if (storeMode == DATA_WORD_MODE){
                                    int value = Integer.parseInt(x[j].substring(1));
                                    int low = value & 0xff;
                                    int high = (value >> 8) & 0xff;

                                    System.out.printf("Setting memory location 0x%X(%d):0x%X(%d) -> 0x%X(%d) to word value 0x%X(%d)\n",
                                            MemoryModule.data_start, MemoryModule.data_start,
                                            dataStart + offset, dataStart + offset,
                                            MemoryModule.data_start + dataStart + offset, MemoryModule.data_start + dataStart + offset,
                                            value, value);

                                    memoryController.setMemory(dataStart + offset, value, DATA_WORD_MODE);
                                    offset += 2;
                                }
                            }
                            memoryController.setMemory(dataStart + offset, ARRAY_TERMINATOR, DATA_BYTE_MODE);
                            offset++;
                        }
                    }
                    i++;
                }
            }
            else if (lines[i].startsWith("DEFINE")) { // definition declaration

                String[] tokens = lines[i].trim().split(" ");
                String symbolName = tokens[1];
                Integer symbolValue = Integer.parseInt(tokens[2].substring(1));
                definitionMap.put(symbolName, symbolValue);
                System.out.printf("set the definition for symbol '%s' to value 0x%04X(%d)\n", symbolName, symbolValue, symbolValue);
            }
            else if (lines[i].startsWith(".")) { // regular function. add the function along with the calculated offset
                functions.put(lines[i].substring(1), currentByte);
                System.out.println("Mapped function '" + lines[i].substring(1) + "' to address: 0x" +
                        Integer.toHexString(currentByte));
            } else { // code line. append the offset based on the string length.
                // in this architecture there's only 3 possible cases
                // no-operand instruction = 1 byte
                // single-operand instruction = 3 bytes
                // 2 operand instruction = 5 bytes
                if (lines[i].isEmpty() || lines[i].startsWith(COMMENT_PREFIX)) continue;
                currentByte += getInstructionLength(lines[i]);
                fullCode += lines[i] + "\n";
            }
        }
        //System.out.println(functionPointers);
        //System.out.println(dataMap);

        // Step 2- convert the raw code to machine code array.
        String[] fullLines = fullCode.split("\n");

        currentLine = 1;
        eachInstruction = new HashMap<>();
        for (int i = 0; i < fullLines.length; i++) {

            currentLine++;
            List<Integer> translatedLine = toMachineCode16(fullLines[i]);
            //String a = Arrays.toString(toMachineCode(fullLines[i])).replace("[", "").replace("]", "");
            String a = Arrays.toString(translatedLine.toArray()).replace("[", "").replace("]", "");
            //eachInstruction.put(i, toMachineCode(fullLines[i]));
            machineCodeString.append(a);
            if (i < fullLines.length - 1) machineCodeString.append(", ");
        }

        String[] eachNum = machineCodeString.toString().split(", ");

        for (int i = 0; i < eachNum.length; i++) { // The TEXT section (ROM/CODE)

            if (i >= MemoryModule.rom_end) {
                String err = String.format("""
                        The compiled machine code is too big to fit in the ROM section of memory.
                        Please optimize your code to occupy less space or increase ROM size.
                        current ROM size : 0x%X, compiled machine code size: 0x%X
                        """, MemoryModule.rom_end, eachNum.length);
                triggerProgramError(err, ErrorHandler.ERR_CODE_INSUFFICIENT_MEMORY);
                }
            if (isNumber(eachNum[i])) {

                memImageList.set(i, Integer.parseInt(eachNum[i]));
            }
        }

        memImageList.set(MemoryModule.rom_end ,(int) TEXT_SECTION_END & 0xff);

        for (int i = MemoryModule.data_start; i <= memoryController.mem_size_B - metadataLength; i++) { // The DATA and STACK sections
            memImageList.set(i, memoryController.readByteAbsolute(i) & 0xff);
        }
        memImageList.set(MemoryModule.stack_end, (int) MEMORY_SECTION_END & 0xff);


        // My signature, last release date and compiler version
        for (int i = 0; i < signature.length(); i++)
            memImageList.add((int) signature.charAt(i));

        for (int i = 0; i < lastUpdateDate.length(); i++)
            memImageList.add((int) lastUpdateDate.charAt(i));

        for(int i = 0; i < compilerVersion.length(); i++)
            memImageList.add((int) compilerVersion.charAt(i));

        memImageList.add((int) (memorySizeKB + 1)); // The memory size in KB
        memImageList.add(bit_length); // the CPU architecture flag

        // Add the program's entry point.
        int entryPoint = functions.get("MAIN");

        int entryPointLow = entryPoint & 0xff;
        int entryPointHigh = (entryPoint >> 8) & 0xff;

        memImageList.add(entryPointHigh);
        memImageList.add(entryPointLow);

        machineCode = memImageList.stream().mapToInt(Integer::intValue).toArray();

        if (stepListener != null) stepListener.updateUI();
        return machineCode;
    }

    @Override
    public void executeCompiledCode(int[] machine_code) {

        Integer mainEntryPoint = functions.get("MAIN");
        if (mainEntryPoint == null) {
            String err = "MAIN function label not found.";
            triggerProgramError(
                    err, ErrorHandler.ERR_CODE_MAIN_NOT_FOUND);
        }
        registers[PC] = mainEntryPoint;
        I = true;

        int[] metadata = new int[2];
        metadata[0] = machine_code[machine_code.length - 4]; // memory size
        metadata[1] = machine_code[machine_code.length - 3]; // architecture

        // we don't need the entry point address because it's already loaded in the functions map

        if (metadata[1] != bit_length) { // Check the architecture
            String err = String.format("This code has been compiled for %d-bit architecture." +
                            " the current CPU architecture is %d-bit.\n",
                    metadata[1], bit_length
            );

            triggerProgramError(
                    err, ErrorHandler.ERR_CODE_INCOMPATIBLE_ARCHITECTURE);
        }


        if (memoryController.mem_size_B > 0xffff) {
            String err = "This Maximum amount of addressable memory for this architecture is 64KB";
            triggerProgramError(err, ErrorHandler.ERR_CODE_INVALID_MEMORY_LAYOUT);
        }

        if (metadata[0] > (int) memorySizeKB + 1) { // Check the allocated memory
            String err = String.format("The selected binary file is generated with %sKB of memory." +
                            "The current configuration uses %sKB. make sure current CPU uses the same or bigger memory size.",
                    machine_code[machine_code.length - 4], memorySizeKB);
            triggerProgramError(
                    err, ErrorHandler.ERR_CODE_INSUFFICIENT_MEMORY);
        }



        while (!programEnd && registers[PC] != TEXT_SECTION_END) {

            if (registers[PC] >= machine_code.length) {
                String err = String.format("PC access violation detected. PC => %04X, last available ROM address: %04X",
                        registers[PC], machine_code.length);
                triggerProgramError(
                        err, ErrorHandler.ERR_CODE_PC_ACCESS_VIOLATION);
            }
            if (canExecute) {
                Logger.addLog(String.format("Executing instruction 0x%X -> %s at ROM address 0x%X",
                        machine_code[registers[PC]],
                        instructionSet.get(machine_code[registers[PC]]), registers[PC]), logDevice);

                switch (machine_code[registers[PC]]) {

                    case INS_EXT -> {
                        Logger.addLog("Terminating program.", logDevice);
                        programEnd = true;
                    }

                    // step function increments PC and returns its value
                    // we step two times for each operand. one step for mode. another step for value
                    case INS_SET -> {
                        Logger.addLog("Fetching operands.", logDevice);
                        int[] destination = getNextOperand();
                        int[] source = getNextOperand();
                        set(destination, source);
                    }
                    case INS_OUT -> {
                        int[] source = getNextOperand();
                        out(source);
                    }

                    case INS_OUTC -> {
                        int[] source = getNextOperand();
                        outc(source);
                    }

                    case INS_SHL -> {
                        int[] destination = getNextOperand();
                        int[] source = getNextOperand();
                        shift_left(destination, source);
                    }

                    case INS_SHR -> {
                        int[] destination = getNextOperand();
                        int[] source = getNextOperand();
                        shift_right(destination, source);
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
                        int start = (machine_code[registers[PC]] << 8) | machine_code[step()];
                        short len = 0;
                        while (memoryController.readByte(start) != ARRAY_TERMINATOR) {
                            start++;
                            len++;
                        }

                        switch (destination[0]) {
                            case REGISTER_MODE, REGISTER_WORD_MODE -> setRegister(destination[1], len);
                            case DIRECT_MODE -> memoryController.setMemory(destination[1], len, DATA_BYTE_MODE);
                            case DIRECT_WORD_MODE -> memoryController.setMemory(destination[1], len, DATA_WORD_MODE);
                            case INDIRECT_MODE -> memoryController.setMemory(getRegister(destination[1]), len, DATA_BYTE_MODE);
                            case INDIRECT_WORD_MODE -> memoryController.setMemory(getRegister(destination[1]), len, DATA_WORD_MODE);
                            default -> E = true;
                        }
                    }

                    case INS_LENW -> {
                        int[] destination = getNextOperand();
                        step();
                        step();
                        int start = (machine_code[registers[PC]] << 8) | machine_code[step()];
                        short len = 0;
                        while (memoryController.readWord(start)[0] != ARRAY_TERMINATOR){
                            start += 2;
                            len++;
                        }

                        switch (destination[0]) {
                            case REGISTER_MODE, REGISTER_WORD_MODE -> setRegister(destination[1], len);
                            case DIRECT_MODE -> memoryController.setMemory(destination[1], len, DATA_BYTE_MODE);
                            case DIRECT_WORD_MODE -> memoryController.setMemory(destination[1], len, DATA_WORD_MODE);
                            case INDIRECT_MODE -> memoryController.setMemory(getRegister(destination[1]), len, DATA_BYTE_MODE);
                            case INDIRECT_WORD_MODE -> memoryController.setMemory(getRegister(destination[1]), len, DATA_WORD_MODE);
                            default -> E = true;
                        }
                    }

                    case INS_OUTS -> {
                        int start = registers[SS];
                        while (memoryController.readByte(start) != ARRAY_TERMINATOR) {

                            outputString.append((char) memoryController.readByte(start));
                            output += (char) memoryController.readByte(start);
                            try {
                                System.out.print((char) memoryController.readByte(start));
                                Thread.sleep(delayAmountMilliseconds);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            start++;
                        }
                    }

                    case INS_OUTSW -> {
                        int start = registers[SS];
                        while (memoryController.readWord(start)[0] != ARRAY_TERMINATOR){
                            int[] current = memoryController.readWord(start);
                            int low = current[0];
                            int high = current[1];
                            char currentChar = (char) ((low) | (high << 8));
                            outputString.append(currentChar);
                            output += currentChar;

                            try {
                                System.out.print(currentChar);
                                Thread.sleep(delayAmountMilliseconds);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }

                            start += 2;
                        }
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
                        int address = ((machine_code[step()] << 8) | machine_code[step()]);
                        int return_address = step() - 1;
                        call(address, return_address);
                    }
                    case INS_RET -> {
                        int return_address = functionCallStack.pop();
                        Logger.addLog(String.format("Popping return address 0x%X from function call stack", return_address), logDevice);
                        registers[PC] = return_address;
                    }

                    case INS_CE -> {
                        step();
                        int address = ((machine_code[step()] << 8) | machine_code[step()]);
                        int return_address = step() - 1;
                        if (Z) call(address, return_address);
                        else registers[PC] = return_address;
                    }
                    case INS_CNE -> {
                        step();
                        int address = ((machine_code[step()] << 8) | machine_code[step()]);
                        int return_address = step() - 1;
                        if (!Z) call(address, return_address);
                        else registers[PC] = return_address;
                    }
                    case INS_CL -> {
                        step();
                        int address = ((machine_code[step()] << 8) | machine_code[step()]);
                        int return_address = step() - 1;
                        if (N) call(address, return_address);
                        else registers[PC] = return_address;
                    }
                    case INS_CLE -> {
                        step();
                        int address = ((machine_code[step()] << 8) | machine_code[step()]);
                        int return_address = step() - 1;
                        if (N || Z) call(address, return_address);
                        else registers[PC] = return_address;
                    }
                    case INS_CG -> {
                        step();
                        int address = ((machine_code[step()] << 8) | machine_code[step()]);
                        int return_address = step() - 1;
                        if (!N) call(address, return_address);
                        else registers[PC] = return_address;
                    }
                    case INS_CGE -> {
                        step();
                        int address = ((machine_code[step()] << 8) | machine_code[step()]);
                        int return_address = step() - 1;
                        if (!N || Z) call(address, return_address);
                        else registers[PC] = return_address;
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

                        registers[CX]--;
                        updateRegisterBytes();
                        if (registers[CX] > 0) {
                            jmp();
                        } else step();
                    }

                    case INS_INT -> {
                        if (I) {
                            boolean x = InterruptHandler.triggerSoftwareInterrupt(this, registers, memoryController);
                            if (!x) E = true;
                        } else Logger.addLog("Interrupt flag not set. skipping.", logDevice, true);
                    }


                    case INS_NOP ->{ // do nothing for 1 cycle
                    }


                    default -> {
                        String err = String.format(
                                "Undefined instruction at address 0x%04X(%d). please check the instruction codes : 0x%04X(%d)",
                                registers[PC], registers[PC], machine_code[registers[PC]], machine_code[registers[PC]]);
                        status_code = ErrorHandler.ERR_CODE_INVALID_INSTRUCTION_FORMAT;
                        triggerProgramError(
                                err, status_code);
                    }
                }

                if (E) {
                    status_code = ErrorHandler.ERR_CODE_PROGRAM_ERROR;
                    String err = String.format("The program triggered an error with code : %s", status_code);
                    triggerProgramError(
                            err, status_code);
                }

                canExecute = !T;
                output = "";
                step();
            }
        }

        outputString.append("Program terminated with code : ").append(status_code);
        output = "Program terminated with code : " + status_code;
        Logger.addLog("Program terminated with code : " + status_code, logDevice);

        Logger.addLog(String.format("""
                ==============================================
                %s
                %s
                ==============================================
                %s
                ==============================================
                """, dumpRegisters(), dumpFlags(), memoryController.dumpMemory()), logDevice);


        if (Launcher.appConfig.get("WriteDump").equals("true")) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd hh.mm.ss");
            LocalDateTime time = LocalDateTime.now();
            String filename = time.format(formatter);
            Logger.writeLogFile("./" + filename + ".log");
        }
    }


    public String disassembleMachineCode(int[] machine_code) {

        try {
            code = new StringBuilder();

            code.append("Disassembled by T.K.Y CPU compiler ").append(compilerVersion).append("\n");
            code.append("Target architecture: ").append(machine_code[machine_code.length - 3]).append("-bit").append("\n");
            code.append("Memory size: ").append(machine_code[machine_code.length - 4]).append("KB").append("\n");

            registers[PC] = 0;
            delayAmountMilliseconds = 0;

            Set<Integer> functionCollector = new TreeSet<>();
            Set<Integer> dataCollector = new TreeSet<>();

            for (int i = 0; machine_code[i] != (TEXT_SECTION_END & 0xff); i++) {

                if (machine_code[i] == INS_LA || machine_code[i] == INS_LLEN || machine_code[i] == INS_LENW){
                    if (
                            (machine_code[i + 1] == REGISTER_MODE || machine_code[i + 1] == REGISTER_WORD_MODE ||
                                    machine_code[i + 1] == DIRECT_MODE || machine_code[i + 1] == DIRECT_WORD_MODE ||
                                    machine_code[i + 1] == INDIRECT_MODE || machine_code[i + 1] == INDIRECT_WORD_MODE) && machine_code[i + 3] == DATA_MODE) {

                        int high = machine_code[i + 4], low = machine_code[i + 5];
                        int address = memoryController.bytePairToWordLE(low, high);
                        dataCollector.add(address);
                    }
                }

                else if (machine_code[i] >= INS_CALL && machine_code[i] <= INS_JB || machine_code[i] == INS_LOOP) {

                    if (machine_code[i + 1] == FUNCTION_MODE) {
                        int high = machine_code[i + 2], low = machine_code[i + 3];
                        int address = memoryController.bytePairToWordLE(low, high);
                        functionCollector.add(address);
                    }
                }
            }
            functionPointers = functionCollector.stream().mapToInt(Integer::intValue).toArray();
            dataPointers = dataCollector.stream().mapToInt(Integer::intValue).toArray();
            functionAddresses = new HashMap<>();
            dataAddresses = new HashMap<>();


            System.out.println("Collecting function entries...");
            for (int i = 0; i < functionPointers.length; i++) {
                functionAddresses.put(functionPointers[i], "func_" + i);
            }

            System.out.println("Collecting data entries...");
            for(int i = 0; i < dataPointers.length; i++){
                dataAddresses.put(dataPointers[i], "n" + i);
            }

            System.out.println("Calculating data offset...");
            int dataOffset = 0;
            while(machine_code[dataOffset] != (TEXT_SECTION_END & 0xff)) dataOffset++;

            System.out.println("Rebuilding the DATA section...");
            StringBuilder dataSectionRebuild = new StringBuilder();
            dataSectionRebuild.append(".DATA");

            for(int i = 0; i < dataPointers.length; i++){
                dataSectionRebuild.append("\n\t").append(dataAddresses.get(dataPointers[i])).append(" ").append("\"");
                int start_address = dataPointers[i] + dataOffset + 1; // +1 to skip the previous data terminator
                StringBuilder data = new StringBuilder();
                for(int j = start_address; machine_code[j] != ARRAY_TERMINATOR && data.length() <= MAX_STRING_LENGTH; j++){

                    if (machine_code[j] != '\n'
                            || machine_code[j] != '\t'
                            || machine_code[j] != '\0') data.append((char) machine_code[j]);

                    else data.append("\\").append("n");
                }
                dataSectionRebuild.append(data).append("\"");
            }
            dataSectionRebuild.append("\nEND");

            int mainEntryPoint = ((machine_code[machine_code.length - 2] & 0xff) | machine_code[machine_code.length - 1]);
            code.append("Program's entry point: ").append("0x").append(Integer.toHexString(mainEntryPoint).toUpperCase()).append("\n");

            if (machine_code[machine_code.length - 3] != bit_length) { // Check the architecture
                String err = String.format("This code has been compiled for %d-bit architecture." +
                                " the current CPU architecture is %d-bit.\n",
                        machine_code[machine_code.length - 3], bit_length
                );

                triggerProgramError(
                        err, ErrorHandler.ERR_CODE_INCOMPATIBLE_ARCHITECTURE);
            }


            while (!programEnd && machine_code[registers[PC]] != TEXT_SECTION_END && machine_code[registers[PC]] != 0x00) {
                if (canExecute) {

                    if (registers[PC] == mainEntryPoint)
                        code.append("\n<.MAIN @0x").append(String.format("%04X>", registers[PC])).append("\n");
                    else {
                        for (int i = 0; i < functionPointers.length; i++) {
                            if (registers[PC] == functionPointers[i])
                                code.append("\n<").append(functionAddresses.get(functionPointers[i])).
                                        append(" @0x").append(String.format("%04X", functionPointers[i])).append(">").append("\n");
                        }
                    }

                    code.append(String.format("%04X:\t",
                            registers[PC]));

                    StringBuilder byteStr = new StringBuilder();
                    int numBytes = 0;

                    switch (machine_code[registers[PC]]) {

                        // step function increments PC and returns its value
                        // we step two times for each operand. one step for mode. another step for value
                        case INS_SET -> {
                            numBytes = 5;
                            if (machine_code[registers[PC] + 1] == CPU.IMMEDIATE_MODE) numBytes += 1;
                            if (machine_code[registers[PC] + 3] == CPU.IMMEDIATE_MODE) numBytes += 1;

                            if (machine_code[registers[PC] + 1] == CPU.DIRECT_MODE ||
                            machine_code[registers[PC] + 1] == CPU.DIRECT_WORD_MODE) numBytes += 2;
                            if (machine_code[registers[PC] + 3] == CPU.DIRECT_MODE ||
                            machine_code[registers[PC] + 3] == CPU.DIRECT_WORD_MODE) numBytes += 2;

                            for (int i = 0; i < numBytes; i++)
                                byteStr.append(String.format("%02X ", machine_code[registers[PC] + i]));
                            code.append(String.format("%-25s", byteStr.toString()));
                            code.append(instructionSet.get(machine_code[registers[PC]])).append(" ");
                            int[] destination = getNextOperand();
                            code.append(getDisassembledOperand(destination)).append(" ");
                            int[] source = getNextOperand();
                            code.append(getDisassembledOperand(source));
                        }
                        case INS_OUT,
                             INS_SQRT,
                             INS_INC, INS_DEC,
                             INS_NOT, INS_PUSH, INS_POP, INS_OUTC -> {
                            numBytes = 3;
                            if (machine_code[registers[PC] + 1] == CPU.IMMEDIATE_MODE) numBytes += 1;
                            if (machine_code[registers[PC] + 1] == CPU.DIRECT_MODE ||
                            machine_code[registers[PC] + 1] == CPU.DIRECT_WORD_MODE) numBytes += 1;

                            for (int i = 0; i < numBytes; i++)
                                byteStr.append(String.format("%02X ", machine_code[registers[PC] + i]));
                            code.append(String.format("%-25s", byteStr.toString()));
                            code.append(instructionSet.get(machine_code[registers[PC]])).append(" ");
                            int[] destination = getNextOperand();
                            code.append(getDisassembledOperand(destination));
                        }


                        case INS_ADD, INS_SUB, INS_MUL, INS_DIV, INS_POW,
                             INS_RND, INS_AND, INS_OR, INS_XOR, INS_NAND, INS_NOR, INS_SHL, INS_SHR, INS_CMP -> {
                            numBytes = 5;
                            if (machine_code[registers[PC] + 1] == CPU.IMMEDIATE_MODE) numBytes += 1;
                            if (machine_code[registers[PC] + 3] == CPU.IMMEDIATE_MODE) numBytes += 1;

                            if (machine_code[registers[PC] + 1] == CPU.DIRECT_MODE ||
                            machine_code[registers[PC] + 1] == CPU.DIRECT_WORD_MODE) numBytes += 2;
                            if (machine_code[registers[PC] + 3] == CPU.DIRECT_MODE ||
                            machine_code[registers[PC] + 3] == CPU.DIRECT_WORD_MODE) numBytes += 2;

                            for (int i = 0; i < numBytes; i++)
                                byteStr.append(String.format("%02X ", machine_code[registers[PC] + i]));
                            code.append(String.format("%-25s", byteStr.toString()));
                            code.append(instructionSet.get(machine_code[registers[PC]])).append(" ");
                            int[] destination = getNextOperand();
                            code.append(getDisassembledOperand(destination)).append(" ");
                            int[] source = getNextOperand();
                            code.append(getDisassembledOperand(source));
                        }


                        case INS_LA -> {
                            // Get the destination (must be 16-bit compatible). step to the address. load into source
                            numBytes = 6;
                            for (int i = 0; i < numBytes; i++)
                                byteStr.append(String.format("%02X ", machine_code[registers[PC] + i]));
                            code.append(String.format("%-25s", byteStr.toString()));
                            code.append(instructionSet.get(machine_code[registers[PC]])).append(" ");
                            int[] destination = getNextOperand();
                            code.append(getDisassembledOperand(destination)).append(" ");
                            int[] source = new int[]{machineCode[step()], machineCode[step()], machineCode[step()]};
                            code.append(getDisassembledOperand(source));
                        }

                        case INS_LLEN, INS_LENW -> {
                            numBytes = 6;
                            for (int i = 0; i < numBytes; i++)
                                byteStr.append(String.format("%02X ", machine_code[registers[PC] + i]));
                            code.append(String.format("%-25s", byteStr.toString()));
                            code.append(instructionSet.get(machine_code[registers[PC]])).append(" ");
                            int[] destination = getNextOperand();
                            code.append(getDisassembledOperand(destination)).append(" ");
                            int[] source = new int[]{machineCode[step()], machineCode[step()], machineCode[step()]};
                            code.append(getDisassembledOperand(source));
                        }


                        case INS_CALL -> {
                            numBytes = 4;
                            for (int i = 0; i < numBytes; i++)
                                byteStr.append(String.format("%02X ", machine_code[registers[PC] + i]));
                            code.append(String.format("%-25s", byteStr.toString()));
                            code.append(instructionSet.get(machine_code[registers[PC]])).append(" ");
                            int source[] = new int[]{machineCode[step()], machineCode[step()], machineCode[step()]};
                            code.append(getDisassembledOperand(source));
                        }


                        case INS_CE, INS_CNE, INS_CL, INS_CLE, INS_CG, INS_CGE, INS_JMP, INS_JE, INS_JNE, INS_JL,
                             INS_JLE,
                             INS_JG, INS_JGE -> {
                            numBytes = 4;
                            for (int i = 0; i < numBytes; i++)
                                byteStr.append(String.format("%02X ", machine_code[registers[PC] + i]));
                            code.append(String.format("%-25s", byteStr.toString()));
                            code.append(instructionSet.get(machine_code[registers[PC]])).append(" ");
                            int[] source = new int[]{machineCode[step()], machineCode[step()], machineCode[step()]};
                            code.append(getDisassembledOperand(source));
                        }

                        case INS_LOOP -> {
                            numBytes = 4;
                            for (int i = 0; i < numBytes; i++)
                                byteStr.append(String.format("%02X ", machine_code[registers[PC] + i]));
                            code.append(String.format("%-25s", byteStr.toString()));
                            code.append(instructionSet.get(machine_code[registers[PC]])).append(" ");
                            // if RCX > 0: decrement RC and jump to the label address specified.
                            int[] source = new int[]{machineCode[step()], machineCode[step()], machineCode[step()]};
                            code.append(getDisassembledOperand(source));
                        }

                        case INS_INT, INS_OUTS, INS_OUTSW, INS_EXT, INS_RET,
                             INS_END, INS_NOP -> {
                            numBytes = 1;
                            byteStr.append(String.format("%02X ", machine_code[registers[PC]]));
                            code.append(String.format("%-25s", byteStr.toString()));
                            code.append(instructionSet.get(machine_code[registers[PC]])).append(" ");
                        }


                        case TEXT_SECTION_END & 0xff -> {
                            System.out.println("Code ends here.");
                            programEnd = true;
                            break;
                        }

                        default -> {
                            numBytes = 1;
                            byteStr.append(String.format("%02X ", machine_code[registers[PC]]));
                            code.append(String.format("%-25s", byteStr.toString()));
                        }
                    }

                    if (E) {
                        status_code = ErrorHandler.ERR_CODE_PROGRAM_ERROR;
                        String err = String.format("The program triggered an error with code : %s", status_code);
                        triggerProgramError(
                                err, status_code);
                    }

                    canExecute = !T;
                    output = "";
                    code.append("\n");
                    currentLine++;
                    step();
                }

            }
            code.append("\n").append(dataSectionRebuild);

            outputString.append("Program terminated with code : ").append(status_code);
            output = "Program terminated with code : " + status_code;
            Logger.addLog("Program terminated with code : " + status_code, logDevice);

            Logger.addLog(String.format("""
                    ==============================================
                    %s
                    %s
                    ==============================================
                    %s
                    ==============================================
                    """, dumpRegisters(), dumpFlags(), memoryController.dumpMemory()), logDevice);


            if (Launcher.appConfig.get("WriteDump").equals("true")) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd hh.mm.ss");
                LocalDateTime time = LocalDateTime.now();
                String filename = time.format(formatter);
                Logger.writeLogFile("./" + filename + ".log");
            }

            return code.toString();
        }catch (Exception e){
            e.printStackTrace();
            System.out.println(code);
            return null;
        }
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////////////////////
    /// //////////////////////////////////////////////////////////////////////////////////////////////////////
    ///
    /// ///////////////////////////// INSTRUCTION SET ////////////////////////////////////////////////////////
    ///


    public void set(int[] destination, int[] source) {

        int operandValue = getOperandValue(source);

        Logger.addLog("Fetching destination.", logDevice);
        switch (destination[0]) {

            case REGISTER_MODE, REGISTER_WORD_MODE -> setRegister(destination[1], operandValue);
            case DIRECT_MODE -> memoryController.setMemory((destination[1] << 8) | destination[2], operandValue, DATA_BYTE_MODE);
            case DIRECT_WORD_MODE -> memoryController.setMemory((destination[1] << 8) | destination[2], operandValue, DATA_WORD_MODE);
            case INDIRECT_MODE -> memoryController.setMemory(getRegister(destination[1]), operandValue, DATA_BYTE_MODE);
            case INDIRECT_WORD_MODE -> memoryController.setMemory(getRegister(destination[1]), operandValue, DATA_WORD_MODE);
            default -> E = true;

        }
    }


    public void out(int[] source) {

        Logger.addLog("Fetching operands", logDevice);
        output = String.valueOf(getOperandValue(source));
        char[] x = output.toCharArray();
        for(char c : x){
            try {
                Thread.sleep(delayAmountMilliseconds);
                System.out.print(c);
                outputString.append(c);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void outc(int[] source) {
        Logger.addLog("Fetching operands", logDevice);
        output = String.valueOf((char) getOperandValue(source));

        char[] x = output.toCharArray();
        for(char c : x){
            try {
                Thread.sleep(delayAmountMilliseconds);
                System.out.print(c);
                outputString.append(c);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void shift_left(int[] destination, int[] source) {
        int operandValue = getOperandValue(source);
        int newVal = 0;

        switch (destination[0]) {
            case REGISTER_MODE, REGISTER_WORD_MODE -> {
                newVal = getRegister(destination[1]) << operandValue;
                setRegister(destination[1], newVal);
            }
            case DIRECT_MODE -> {
                newVal = memoryController.readByte((destination[1] << 8) | destination[2]) << operandValue;
                memoryController.setMemory((destination[1] << 8) | destination[2], newVal, DATA_BYTE_MODE);
            }
            case DIRECT_WORD_MODE -> {
                newVal = memoryController.bytePairToWordLE(memoryController.readWord((destination[1] << 8) | destination[2])) << operandValue;
                memoryController.setMemory((destination[1] << 8) | destination[2], newVal, DATA_WORD_MODE);
            }
            case INDIRECT_MODE -> {
                newVal = memoryController.readByte(getRegister(destination[1])) << operandValue;
                memoryController.setMemory(getRegister(destination[1]), newVal);
            }
            case INDIRECT_WORD_MODE -> {
                newVal = memoryController.bytePairToWordLE(memoryController.readWord(getRegister(destination[1]))) << operandValue;
                memoryController.setMemory(getRegister(destination[1]), newVal);
            }
            default -> E = true;
        }
        updateFlags(newVal);
    }

    public void shift_right(int[] destination, int[] source) {
        int operandValue = getOperandValue(source);
        int newVal = 0;

        switch (destination[0]) {
            case REGISTER_MODE, REGISTER_WORD_MODE -> {
                newVal = getRegister(destination[1]) >> operandValue;
                setRegister(destination[1], newVal);
            }
            case DIRECT_MODE -> {
                newVal = memoryController.readByte((destination[1] << 8) | destination[2]) >> operandValue;
                memoryController.setMemory((destination[1] << 8) | destination[2], newVal, DATA_BYTE_MODE);
            }
            case DIRECT_WORD_MODE -> {
                newVal = memoryController.bytePairToWordLE(memoryController.readWord((destination[1] << 8) | destination[2])) >> operandValue;
                memoryController.setMemory((destination[1] << 8) | destination[2], newVal, DATA_WORD_MODE);
            }
            case INDIRECT_MODE -> {
                newVal = memoryController.readByte(getRegister(destination[1])) >> operandValue;
                memoryController.setMemory(getRegister(destination[1]), newVal);
            }
            case INDIRECT_WORD_MODE -> {
                newVal = memoryController.bytePairToWordLE(memoryController.readWord(getRegister(destination[1]))) >> operandValue;
                memoryController.setMemory(getRegister(destination[1]), newVal);
            }
            default -> E = true;
        }
        updateFlags(newVal);
    }


    public void add(int[] destination, int[] source){

        int operandValue = getOperandValue(source);
        int newVal = 0;

        Logger.addLog("Setting destination", logDevice);
        switch (destination[0]){
            case REGISTER_MODE, REGISTER_WORD_MODE ->{
                newVal = getRegister(destination[1]) + operandValue;

                setRegister( destination[1], newVal);
            }

            case DIRECT_MODE ->{
                newVal = memoryController.readByte( (destination[1] << 8) | destination[2] ) + operandValue;
                memoryController.setMemory( (destination[1] << 8) | destination[2], newVal, DATA_BYTE_MODE);
            }

            case DIRECT_WORD_MODE ->{
                newVal = memoryController.bytePairToWordLE( memoryController.readWord( (destination[1] << 8) | destination[2] ) ) + operandValue;
                memoryController.setMemory( (destination[1] << 8) | destination[2], newVal, DATA_WORD_MODE);
            }

            case INDIRECT_MODE ->{
                newVal = memoryController.readByte( getRegister( destination[1] ) ) + operandValue;
                memoryController.setMemory( getRegister( destination[1] ), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = memoryController.bytePairToWordLE( memoryController.readWord( getRegister( destination[1] ) ) ) + operandValue;
                memoryController.setMemory( getRegister( destination[1] ), newVal);
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
                newVal = memoryController.readByte( (destination[1] << 8) | destination[2] ) - operandValue;
                memoryController.setMemory((destination[1] << 8) | destination[2], newVal, DATA_BYTE_MODE);
            }

            case DIRECT_WORD_MODE ->{
                newVal = memoryController.bytePairToWordLE( memoryController.readWord( (destination[1] << 8) | destination[2] ) ) - operandValue;
                memoryController.setMemory( (destination[1] << 8) | destination[2], newVal, DATA_WORD_MODE);
            }

            case INDIRECT_MODE ->{
                newVal = memoryController.readByte( getRegister( destination[1] ) ) - operandValue;
                memoryController.setMemory( getRegister( destination[1] ), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = memoryController.bytePairToWordLE( memoryController.readWord( getRegister( destination[1] ) ) ) - operandValue;
                memoryController.setMemory( getRegister( destination[1] ), newVal);
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
                newVal = memoryController.readByte( (destination[1] << 8) | destination[2] ) * operandValue;
                memoryController.setMemory( (destination[1] << 8) | destination[2], newVal, DATA_BYTE_MODE);
            }

            case DIRECT_WORD_MODE ->{
                newVal = memoryController.bytePairToWordLE( memoryController.readWord( (destination[1] << 8) | destination[2] ) ) * operandValue;
                memoryController.setMemory( (destination[1] << 8) | destination[2], newVal, DATA_WORD_MODE);
            }

            case INDIRECT_MODE ->{
                newVal = memoryController.readByte( getRegister( destination[1] ) ) * operandValue;
                memoryController.setMemory( getRegister( destination[1] ), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = memoryController.bytePairToWordLE( memoryController.readWord( getRegister( destination[1] ) ) ) * operandValue;
                memoryController.setMemory( getRegister( destination[1] ), newVal);
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
                newVal = memoryController.readByte( (destination[1] << 8) | destination[2] ) / operandValue;
                memoryController.setMemory( (destination[1] << 8) | destination[2], newVal, DATA_BYTE_MODE);
            }

            case DIRECT_WORD_MODE ->{
                newVal = memoryController.bytePairToWordLE( memoryController.readWord( (destination[1] << 8) | destination[2] ) ) / operandValue;
                memoryController.setMemory( (destination[1] << 8) | destination[2], newVal, DATA_WORD_MODE);
            }

            case INDIRECT_MODE ->{
                newVal = memoryController.readByte( getRegister( destination[1] ) ) / operandValue;
                memoryController.setMemory( getRegister( destination[1] ), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = memoryController.bytePairToWordLE( memoryController.readWord( getRegister( destination[1] ) ) ) / operandValue;
                memoryController.setMemory( getRegister( destination[1] ), newVal);
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
                newVal = ~memoryController.readByte( (source[1] << 8) | source[2] );
                memoryController.setMemory( (source[1] << 8) | source[2], newVal, DATA_BYTE_MODE);
            }

            case DIRECT_WORD_MODE -> {
                newVal = ~memoryController.bytePairToWordLE( memoryController.readWord( (source[1] << 8) | source[2] ) );
                memoryController.setMemory( (source[1] << 8) | source[2], newVal, DATA_WORD_MODE );
            }

            case INDIRECT_MODE -> {
                newVal = ~memoryController.readByte( getRegister(source[1]) );
                memoryController.setMemory( getRegister(source[1]), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = ~memoryController.bytePairToWordLE( memoryController.readWord( getRegister( source[1] ) ) );
                memoryController.setMemory( getRegister( source[1] ), newVal );
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
                newVal = memoryController.readByte( (destination[1] << 8) | destination[2] ) & operandValue;
                memoryController.setMemory( (destination[1] << 8) | destination[2], newVal, DATA_BYTE_MODE);
            }

            case DIRECT_WORD_MODE ->{
                newVal = memoryController.bytePairToWordLE( memoryController.readWord( (destination[1] << 8) | destination[2] ) ) & operandValue;
                memoryController.setMemory( (destination[1] << 8) | destination[2], newVal, DATA_WORD_MODE);
            }

            case INDIRECT_MODE ->{
                newVal = memoryController.readByte( getRegister( destination[1] ) ) & operandValue;
                memoryController.setMemory( getRegister( destination[1] ), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = memoryController.bytePairToWordLE( memoryController.readWord( getRegister( destination[1] ) ) ) & operandValue;
                memoryController.setMemory( getRegister( destination[1] ), newVal);
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
                newVal = memoryController.readByte( (destination[1] << 8) | destination[2] ) | operandValue;
                memoryController.setMemory( (destination[1] << 8) | destination[2], newVal);
            }

            case DIRECT_WORD_MODE ->{
                newVal = memoryController.bytePairToWordLE( memoryController.readWord( (destination[1] << 8) | destination[2] ) ) | operandValue;
                memoryController.setMemory( (destination[1] << 8) | destination[2], newVal, DATA_WORD_MODE);
            }

            case INDIRECT_MODE ->{
                newVal = memoryController.readByte( getRegister( destination[1] ) ) | operandValue;
                memoryController.setMemory( getRegister( destination[1] ), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = memoryController.bytePairToWordLE( memoryController.readWord( getRegister( destination[1] ) ) ) | operandValue;
                memoryController.setMemory( getRegister( destination[1] ), newVal);
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
                newVal = memoryController.readByte( (destination[1] << 8) | destination[2] ) ^ operandValue;
                memoryController.setMemory( (destination[1] << 8) | destination[2], newVal, DATA_BYTE_MODE);
            }

            case DIRECT_WORD_MODE ->{
                newVal = memoryController.bytePairToWordLE( memoryController.readWord( (destination[1] << 8) | destination[2] ) ) ^ operandValue;
                memoryController.setMemory( (destination[1] << 8) | destination[2], newVal, DATA_WORD_MODE);
            }

            case INDIRECT_MODE ->{
                newVal = memoryController.readByte( getRegister( destination[1] ) ) ^ operandValue;
                memoryController.setMemory( getRegister( destination[1] ), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = memoryController.bytePairToWordLE( memoryController.readWord( getRegister( destination[1] ) ) ) ^ operandValue;
                memoryController.setMemory( getRegister( destination[1] ), newVal);
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
                newVal = ~(memoryController.readByte( (destination[1] << 8) | destination[2] ) & operandValue);
                memoryController.setMemory( (destination[1] << 8) | destination[2], newVal, DATA_BYTE_MODE);
            }

            case DIRECT_WORD_MODE ->{
                newVal = ~(memoryController.bytePairToWordLE( memoryController.readWord( (destination[1] << 8) | destination[2] ) ) & operandValue);
                memoryController.setMemory( (destination[1] << 8) | destination[2], newVal, DATA_WORD_MODE);
            }

            case INDIRECT_MODE ->{
                newVal = ~(memoryController.readByte( getRegister( destination[1] ) ) & operandValue);
                memoryController.setMemory( getRegister( destination[1] ), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = ~(memoryController.bytePairToWordLE( memoryController.readWord( getRegister( destination[1] ) ) ) & operandValue);
                memoryController.setMemory( getRegister( destination[1] ), newVal);
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
                newVal = ~(memoryController.readByte( (destination[1] << 8) | destination[2] ) | operandValue);
                memoryController.setMemory( (destination[1] << 8) | destination[2], newVal, DATA_BYTE_MODE);
            }

            case DIRECT_WORD_MODE ->{
                newVal = ~(memoryController.bytePairToWordLE( memoryController.readWord( (destination[1] << 8) | destination[2] ) ) | operandValue);
                memoryController.setMemory( (destination[1] << 8) | destination[2], newVal, DATA_WORD_MODE);
            }

            case INDIRECT_MODE ->{
                newVal = ~(memoryController.readByte( getRegister( destination[1] ) ) | operandValue);
                memoryController.setMemory( getRegister( destination[1] ), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = ~(memoryController.bytePairToWordLE( memoryController.readWord( getRegister( destination[1] ) ) ) | operandValue);
                memoryController.setMemory( getRegister( destination[1] ), newVal);
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
                newVal = (int) Math.pow( memoryController.readByte( (destination[1] << 8) | destination[2] ) , operandValue);
                memoryController.setMemory( (destination[1] << 8) | destination[2], newVal, DATA_BYTE_MODE);
            }

            case DIRECT_WORD_MODE ->{
                newVal = (int) Math.pow( memoryController.bytePairToWordLE( memoryController.readWord( (destination[1] << 8) | destination[2] ) ), operandValue );
                memoryController.setMemory( (destination[1] << 8) | destination[2], newVal, DATA_WORD_MODE);
            }

            case INDIRECT_MODE ->{
                newVal = (int) Math.pow( memoryController.readByte( getRegister( destination[1] ) ), operandValue );
                memoryController.setMemory( getRegister( destination[1] ), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = (int) Math.pow( memoryController.bytePairToWordLE( memoryController.readWord( getRegister( destination[1] ) ) ), operandValue );
                memoryController.setMemory( getRegister( destination[1] ), newVal);
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
                newVal = (int) Math.sqrt( memoryController.readByte( (source[1] << 8) | source[2] ) );
                memoryController.setMemory( (source[1] << 8) | source[2], newVal , DATA_BYTE_MODE);
            }

            case DIRECT_WORD_MODE -> {
                newVal = (int) Math.sqrt( memoryController.bytePairToWordLE( memoryController.readWord( (source[1] << 8) | source[2] ) ) );
                memoryController.setMemory( (source[1] << 8) | source[2], newVal , DATA_WORD_MODE);
            }

            case INDIRECT_MODE -> {
                newVal = (int) Math.sqrt( memoryController.readByte( getRegister(source[1]) ) );
                memoryController.setMemory( getRegister(source[1]), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = (int) Math.sqrt( memoryController.bytePairToWordLE( memoryController.readWord( getRegister(source[1]) ) ) );
                memoryController.setMemory( getRegister( source[1] ), newVal );
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

            case DIRECT_MODE ->{
                newVal = (int) (Math.random() * operandValue);
                memoryController.setMemory( (destination[1] << 8) | destination[2], newVal, DATA_BYTE_MODE);
            }
            case DIRECT_WORD_MODE -> {
                newVal = (int) (Math.random() * operandValue);
                memoryController.setMemory( (destination[1] << 8) | destination[2], newVal, DATA_WORD_MODE );
            }

            case INDIRECT_MODE, INDIRECT_WORD_MODE ->{
                newVal = (int) (Math.random() * operandValue);
                memoryController.setMemory( getRegister( destination[1] ), newVal );
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
                newVal = memoryController.readByte( (source[1] << 8) | source[2] ) + 1;
                memoryController.setMemory( (source[1] << 8) | source[2], newVal , DATA_BYTE_MODE);
            }

            case DIRECT_WORD_MODE -> {
                newVal = memoryController.bytePairToWordLE( memoryController.readWord( (source[1] << 8) | source[2] ) ) + 1;
                memoryController.setMemory( (source[1] << 8) | source[2], newVal, DATA_WORD_MODE );
            }

            case INDIRECT_MODE -> {
                newVal = memoryController.readByte( getRegister(source[1]) ) + 1;
                memoryController.setMemory( getRegister(source[1]), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = memoryController.bytePairToWordLE( memoryController.readWord( getRegister( source[1] ) ) ) + 1;
                memoryController.setMemory( getRegister( source[1] ), newVal );
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
                newVal = memoryController.readByte( (source[1] << 8) | source[2] ) - 1;
                memoryController.setMemory( (source[1] << 8) | source[2], newVal, DATA_BYTE_MODE );
            }

            case DIRECT_WORD_MODE -> {
                newVal = memoryController.bytePairToWordLE( memoryController.readWord( (source[1] << 8) | source[2] ) ) - 1;
                memoryController.setMemory( (source[1] << 8) | source[2], newVal , DATA_WORD_MODE);
            }

            case INDIRECT_MODE -> {
                newVal = memoryController.readByte( getRegister(source[1]) ) - 1;
                memoryController.setMemory( getRegister(source[1]), newVal );
            }

            case INDIRECT_WORD_MODE -> {
                newVal = memoryController.bytePairToWordLE( memoryController.readWord( getRegister( source[1] ) ) ) - 1;
                memoryController.setMemory( getRegister( source[1] ), newVal );
            }

            case IMMEDIATE_MODE -> newVal = source[1] - 1;

            default -> E = true;
        }
        updateFlags(newVal);
    }

    public void la(int[] source){
        int address = (machineCode[ registers[PC] ] << 8) | machineCode[step()];
        Logger.addLog(String.format("Loading address present in PC : 0x%X", address), logDevice);
        switch (source[0]){
            case REGISTER_MODE, REGISTER_WORD_MODE -> setRegister( source[1], address);
            case DIRECT_MODE, DIRECT_WORD_MODE -> memoryController.setMemory( source[1], address );
            case INDIRECT_MODE, INDIRECT_WORD_MODE -> memoryController.setMemory( getRegister(source[1]) , address );
        }
    }


    public void push(int[] source){
        switch (source[0]){

            case REGISTER_MODE ->{
                memoryController.setMemoryAbsolute( registers[SP], getRegisterByte( source[1] ), CPU.DATA_BYTE_MODE );
                registers[SP]--;
            }

            case REGISTER_WORD_MODE -> {
                int[] val = getRegisterWord( source[1] );
                for (int j : val) {
                    memoryController.setMemoryAbsolute(registers[SP], j, CPU.DATA_BYTE_MODE );
                    registers[SP]--;
                }

            }

            case DIRECT_MODE -> {
                memoryController.setMemoryAbsolute( registers[SP], memoryController.readByte( source[1] << 8 | source[2] ), CPU.DATA_BYTE_MODE );
                registers[SP]--;
            }

            case DIRECT_WORD_MODE -> {
                int[] val = memoryController.readWord( (source[1] << 8) | source[2] );
                for (int j : val) {
                    memoryController.setMemoryAbsolute(registers[SP], j, CPU.DATA_BYTE_MODE);
                    registers[SP]--;
                }
            }

            case INDIRECT_MODE -> {
                memoryController.setMemoryAbsolute( registers[SP], memoryController.readByte( getRegister(source[1]) ), DATA_BYTE_MODE);
                registers[SP]--;
            }

            case INDIRECT_WORD_MODE -> {
                int[] val = memoryController.readWord( getRegister(source[1]) );

                for(int j : val){
                    memoryController.setMemoryAbsolute( registers[SP], j, DATA_BYTE_MODE );
                    registers[SP]--;
                }
            }

            case IMMEDIATE_MODE -> {
                int val = source[1];
                if (val <= max_byte_value) {
                    memoryController.setMemoryAbsolute( registers[SP], val, DATA_BYTE_MODE );
                    registers[SP]--;
                }
                else{
                    int low = val & 0xff;
                    int high = (val >> 8) & 0xff;
                    memoryController.setMemoryAbsolute(registers[SP], low, DATA_BYTE_MODE);
                    registers[SP]--;
                    memoryController.setMemoryAbsolute(registers[SP], high, DATA_BYTE_MODE);
                    registers[SP]--;
                }
            }

        }
    }


    public void pop(int[] source){

        switch (source[0]){
            case REGISTER_MODE -> {
                registers[SP]++;
                setRegister( source[1], memoryController.readByteAbsolute(registers[SP]) );
            }

            case REGISTER_WORD_MODE -> {
                registers[SP]++;
                int val = memoryController.bytePairToWordBE(memoryController.readWordAbsolute(registers[SP]));
                registers[SP]++;

                setRegister( source[1], val );
            }

            case DIRECT_MODE -> {
                registers[SP]++;
                memoryController.setMemory( (source[1] << 8) | source[2], memoryController.readByteAbsolute(registers[SP]), DATA_BYTE_MODE );
            }

            case DIRECT_WORD_MODE -> {
                registers[SP]++;
                int[] val = memoryController.readWordAbsolute( registers[SP] );
                memoryController.setMemory( (source[1] << 8) | source[2] , val[0], DATA_BYTE_MODE);
                memoryController.setMemory(((source[1] << 8) | source[2]) + 1, val[1], DATA_BYTE_MODE);
                registers[SP]++;
            }

            case INDIRECT_MODE -> {
                registers[SP]++;
                memoryController.setMemory( getRegisterByte( source[1] ), memoryController.readByteAbsolute(registers[SP]) );
            }

            case INDIRECT_WORD_MODE -> {
                registers[SP]++;
                memoryController.setMemory( getRegister( source[1] ),
                        memoryController.bytePairToWordBE( memoryController.readWordAbsolute(registers[SP]) ) );
                registers[SP]++;
            }
        }
    }


    public void call(int address, int return_address){
        Logger.addLog(String.format("Pushing return address %X into function call stack.", return_address), logDevice);
        functionCallStack.push(return_address); // save the return address
        Logger.addLog(String.format("Updating PC to point to caller's address : %X", address), logDevice);
        registers[PC] = address - 1; // sub 1 to nullify the step() and the address byte
    }


    public void jmp(){
        Logger.addLog("Updating PC to point to caller's address : 0x" +
                Integer.toHexString( (machineCode[registers[PC]] << 8) | machineCode[registers[PC] + 1] ), logDevice);
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

        logDevice = debugSource;
        System.out.println("Starting 16-bit CPU module");
        bit_length = 16;

        memoryController = new MemoryModule(memorySizeKB, this);


        if (memoryController.stack_start < 0){
            String errMsg = "Invalid memory layout (stack). " + memoryController.stack_start;
            triggerProgramError(
                    errMsg, ErrorHandler.ERR_CODE_INVALID_MEMORY_LAYOUT);

        }
        if (memoryController.data_start < 0){
            String errMsg = "Invalid memory layout (data). " + memoryController.data_start;
            triggerProgramError(
                    errMsg, ErrorHandler.ERR_CODE_INVALID_MEMORY_LAYOUT);
        }


        Logger.addLog(memInitMsg, logDevice, true);

        Logger.addLog(String.format("CPU speed set to %s Cycles per second. With a step delay of %sMS\n",
                Launcher.appConfig.get("Cycles"), delayAmountMilliseconds), logDevice, true);

        reset();
    }


    public void reset(){
        System.out.println("Initializing memory.");

        memoryController.resetMemory();

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
        lineMap = new HashMap<>();

        Logger.resetLogs();

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

            registerNames[i] = String.valueOf(currentChar) + byteChar;
            index++;
            if (index == 2){
                index = 0;
                currentChar++;
            }
            registerPairStart++;
        }

        currentChar = 'a';
        for(int i = byteRegisterCount; i < byteRegisterCount + registerPairCount; i++){
            registerNames[i] = currentChar + "x";
            currentChar++;
        }

        registerNames[PC] = "pc";
        registerNames[SP] = "sp";
        registerNames[SS] = "ss";
        registerNames[SE] = "se";
        registerNames[DI] = "di";
        registerNames[DP] = "dp";

        CX = registerPairStart + 2;


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
        definitionMap = new HashMap<>();

        outputString = new StringBuilder();

        machineCode = new int[] {0};


        registers[SP] = memoryController.stack_end;
        registers[DP] = memoryController.dataOrigin;
        registers[PC] = 0;

    }

    /// //////////////////////////////////////////////////////////////////////////////
    /// /////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////

}