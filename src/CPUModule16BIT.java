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
        Logger.addLog("Fetching source");
        return switch (source[0]) {
            case REGISTER_MODE, REGISTER_WORD_MODE -> getRegister(source[1]);
            case DIRECT_MODE -> readByte( ( source[1] << 8 ) | source[2]);
            case DIRECT_WORD_MODE -> bytePairToWordLE((readWord( ( source[1] << 8 ) | source[2])));
            case INDIRECT_MODE -> readByte(getRegister(source[1]));
            case INDIRECT_WORD_MODE -> bytePairToWordLE(readWord(getRegister(source[1])));
            case IMMEDIATE_MODE -> (source[1] << 8) | source[2];
            default -> max_pair_value + 1;
        };
    }

    public String getDisassembledOperand(int[] operand) {
        return switch (operand[0]) {
            case REGISTER_MODE, REGISTER_WORD_MODE -> {
                String mode = (operand[0] == REGISTER_MODE && operand[1] < CPUModule16BIT.PC) ? "BYTE" : "WORD";
                yield mode + " " + CPU.REGISTER_PREFIX + getRegisterName(operand[1], false);
            }
            case DIRECT_MODE, DIRECT_WORD_MODE ->{
                String mode = (operand[0] == DIRECT_MODE) ? "BYTE" : "WORD";
                yield mode + " " + CPU.HEX_MEMORY + Integer.toHexString((operand[1] << 8) | operand[2]);
            }
            case INDIRECT_MODE, INDIRECT_WORD_MODE ->{
                String mode = (operand[0] == INDIRECT_MODE && operand[1] < CPUModule16BIT.PC) ? "BYTE" : "WORD";
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

    public int getMemory(int address) {
        if (isValidMemoryAddress(address)) return memory[address];
        else return max_pair_value + 1;
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


    public String dumpMemory() {
        int chunkSize = 10;
        StringBuilder result = new StringBuilder();
        StringBuilder charSet = new StringBuilder();
        for (int i = 0; i < memory.length; i++) {

            if (i % chunkSize == 0) result.append(String.format("%05X :\t", i));

            result.append(String.format("0x%02X\t", memory[i] & 0xff));
            charSet.append((Character.isLetterOrDigit(memory[i])) ? (char) memory[i] : ".");

            if ((i + 1) % chunkSize == 0) {
                result.append("\t\t").append("|").append(charSet).append("|").append("\n");
                charSet.setLength(0);
            }
        }
        return result.toString();
    }


    public String dumpMemory(int start, int end) {
        int chunkSize = 10;
        StringBuilder result = new StringBuilder();
        StringBuilder charSet = new StringBuilder();
        for (int i = start; i <= end; i++) {

            if (i % chunkSize == 0) result.append(String.format("%05X :\t", i));

            result.append(String.format("0x%02X", memory[i]));
            charSet.append((Character.isLetterOrDigit(memory[i])) ? (char) memory[i] : ".");

            if ((i + 1) % chunkSize == 0) {
                result.append("\t\t").append("|").append(charSet).append("|").append("\n");
                charSet.setLength(0);
            }
        }
        return result.toString();
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
        Logger.addLog("Updating flags.");
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
                        result.add(FUNCTION_MODE);
                        Integer addr = functions.get( tokens[i] );
                        if (addr == null) {
                            String err = String.format("The function '%s' doesn't exist in the ROM.\n",
                                tokens[i].substring(1));
                            status_code = ErrorHandler.ERR_COMP_NULL_DATA_POINTER;
                            triggerProgramError(
                                err, status_code);

                            return result;
                        }
                        else {
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


                // maybe the user wants to manually specify the mode.
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
    public int[] compileCode(String code) {
        String[] lines = code.split("\n");
        List<Integer> machineCodeList = new ArrayList<>();

        StringBuilder machineCodeString = new StringBuilder();
        List<Integer> instructionStartAddresses = new ArrayList<>();
        List<Integer> codeLines = new ArrayList<>();
        instructionStartAddresses.add(0);

        if (mem_size_B > 0xffff) {
            String err = "This Maximum amount of addressable memory for this architecture is 64KB";
            triggerProgramError(err, ErrorHandler.ERR_CODE_INVALID_MEMORY_LAYOUT);
        }


        // Step 1- Calculate the function offset addresses, add .DATA variables to the data section, and build a raw code string
        String fullCode = "";
        currentLine = 0;
        for (int i = 0; i < lines.length; i++) {
            currentLine++;
            // Which section are we in? (is it a line of code? is it a function. and if it starts with '.' is it the data section?)
            if (lines[i].equals(".DATA")) {
                System.out.println("Data section detected.");
                int offset = 0;
                i++; // skip .DATA line

                while (!lines[i].equalsIgnoreCase("end")) {
                    currentLine++;

                    String[] x = lines[i].trim().split(" ");
                    int dataStart = dataOffset;
                    if (x[0].equals("org")) dataOffset = Integer.parseInt(x[1].substring(1)) - offset;

                    else {
                        // store mode
                        // 1- Byte mode
                        // 2- Word mode
                        // else- Undefined.
                        int storeMode = 0;
                        dataMap.put(x[0], dataStart + offset);

                        if (x[1].equalsIgnoreCase("db")) storeMode = DATA_BYTE_MODE;
                        else if (x[1].equalsIgnoreCase("dw")) storeMode = DATA_WORD_MODE;

                        if (storeMode != DATA_BYTE_MODE && storeMode != DATA_WORD_MODE) {
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
                                    System.out.printf("Setting memory location 0x%X(%d) to byte char %c\n",
                                            dataStart + offset, dataStart + offset, fullString.charAt(j));
                                    setMemory(dataStart + offset, (short) fullString.charAt(j), DATA_BYTE_MODE);
                                    offset++;

                                }else if (storeMode == DATA_WORD_MODE){
                                    int low = fullString.charAt(j) & 0xff;
                                    int high = (fullString.charAt(j) >> 8) & 0xff;
                                    System.out.printf("Setting memory location 0x%X(%d) to word char %c\n",
                                            dataStart + offset, dataStart + offset, fullString.charAt(j));
                                    setMemory(dataStart + offset, fullString.charAt(j), DATA_WORD_MODE);
                                    offset += 2;
                                }

                            }
                            setMemory(dataStart + offset, ARRAY_TERMINATOR, DATA_BYTE_MODE);
                            offset++;

                            // We're storing an array of numbers
                        } else {
                            for (int j = 2; j < x.length; j++) {

                                if (storeMode == DATA_BYTE_MODE) {

                                    System.out.printf("Setting memory location 0x%X(%d) to byte value 0x%X(%d)\n",
                                            dataStart + offset, dataStart + offset,
                                            Integer.parseInt(x[j].substring(1)), Integer.parseInt(x[j].substring(1)));

                                    setMemory(dataStart + offset, Integer.parseInt(x[j].substring(1)), DATA_BYTE_MODE);
                                    offset++;

                                }else if (storeMode == DATA_WORD_MODE){
                                    int value = Integer.parseInt(x[j].substring(1));
                                    int low = value & 0xff;
                                    int high = (value >> 8) & 0xff;

                                    System.out.printf("Setting memory location 0x%X(%d) to word value 0x%X(%d)\n",
                                            dataStart + offset, dataStart + offset,
                                            value, value);

                                    setMemory(dataStart + offset, value, DATA_WORD_MODE);
                                    offset += 2;
                                }
                            }
                            setMemory(dataStart + offset, ARRAY_TERMINATOR, DATA_BYTE_MODE);
                            offset++;
                        }
                    }
                    i++;
                }
                currentLine++;
            } else if (lines[i].startsWith(".")) { // regular function. add the function along with the calculated offset
                functions.put(lines[i].substring(1), currentByte);
                System.out.println("Mapped function '" + lines[i].substring(1) + "' to address: 0x" +
                        Integer.toHexString(currentByte));
            } else { // code line. append the offset based on the string length.
                // in this architecture there's only 3 possible cases
                // no-operand instruction = 1 byte
                // single-operand instruction = 3 bytes
                // 2 operand instruction = 5 bytes
                // if the instruction includes loading addresses or word operations, then 2 more bytes will be added.
                if (lines[i].isEmpty() || lines[i].startsWith(COMMENT_PREFIX)) continue;

                currentByte += getInstructionLength(lines[i]);
                instructionStartAddresses.add(currentByte);
                codeLines.add(currentLine);

                fullCode += lines[i] + "\n";
            }
        }
        //System.out.println(functionPointers);
        //System.out.println(dataMap);

        // Step 2- convert the raw code to machine code array.
        String[] fullLines = fullCode.split("\n");

        eachInstruction = new HashMap<>();
        for (int i = 0; i < fullLines.length; i++) {

            currentLine++;
            List<Integer> translatedLine = toMachineCode16(fullLines[i]);

            //String a = Arrays.toString(translatedLine).replace("[", "").replace("]", "");
            String a = Arrays.toString(translatedLine.toArray()).replace("[", "").replace("]", "");
            //eachInstruction.put(i, toMachineCode(fullLines[i]));
            machineCodeString.append(a);
            if (i < fullLines.length - 1) machineCodeString.append(", ");
        }

        String[] eachNum = machineCodeString.toString().split(", ");
        for (int i = 0; i < eachNum.length; i++) {
            if (isNumber(eachNum[i])) {
                machineCodeList.add(Integer.parseInt(eachNum[i]));
            }
        }
        machineCodeList.add((int) TEXT_SECTION_END & 0xff);

        for (int i = 0; i < signature.length(); i++) // My signature, last release date and compiler version
            machineCodeList.add((int) signature.charAt(i));

        for (int i = 0; i < lastUpdateDate.length(); i++)
            machineCodeList.add((int) lastUpdateDate.charAt(i));

        for (int i = 0; i < compilerVersion.length(); i++)
            machineCodeList.add((int) compilerVersion.charAt(i));

        machineCodeList.add((int) (memorySizeKB + 1)); // The memory size in KB
        machineCodeList.add(bit_length); // the CPU architecture flag

        // The program's entry point.
        int entryPoint = functions.get("MAIN");

        int entryPointLow = entryPoint & 0xff;
        int entryPointHigh = (entryPoint >> 8) & 0xff;

        machineCodeList.add(entryPointHigh);
        machineCodeList.add(entryPointLow);

        machineCode = machineCodeList.stream().mapToInt(Integer::intValue).toArray();
        stepListener.updateUI();
        instructionStartAddresses.removeLast();

        for(int i = 0; i < instructionStartAddresses.size(); i++){
            lineMap.put(instructionStartAddresses.get(i), codeLines.get(i));
        }
        return machineCode;
    }


    @Override
    public int[] compileToFileBinary(String code) {
        String[] lines = code.split("\n");
        List<Integer> machineCodeList = new ArrayList<>();

        StringBuilder machineCodeString = new StringBuilder();

        if (mem_size_B > 0xffff) {
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
                    int dataStart = dataOffset;
                    if (x[0].equals("org")) dataOffset = Integer.parseInt(x[1].substring(1)) - offset;

                    else {
                        // store mode
                        // 1- Byte mode
                        // 2- Word mode
                        // else- Undefined.
                        int storeMode = 0;
                        dataMap.put(x[0], dataStart + offset);

                        if (x[1].equalsIgnoreCase("db")) storeMode = DATA_BYTE_MODE;
                        else if (x[1].equalsIgnoreCase("dw")) storeMode = DATA_WORD_MODE;

                        if (storeMode != DATA_BYTE_MODE && storeMode != DATA_WORD_MODE) {
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
                                    System.out.printf("Setting memory location 0x%X(%d) to byte char %c\n",
                                            dataStart + offset, dataStart + offset, fullString.charAt(j));
                                    setMemory(dataStart + offset, (short) fullString.charAt(j), DATA_BYTE_MODE);
                                    offset++;

                                }else if (storeMode == DATA_WORD_MODE){
                                    int low = fullString.charAt(j) & 0xff;
                                    int high = (fullString.charAt(j) >> 8) & 0xff;
                                    System.out.printf("Setting memory location 0x%X(%d) to word char %c\n",
                                            dataStart + offset, dataStart + offset, fullString.charAt(j));
                                    setMemory(dataStart + offset, fullString.charAt(j), DATA_WORD_MODE);
                                    offset += 2;
                                }

                            }
                            setMemory(dataStart + offset, ARRAY_TERMINATOR, DATA_BYTE_MODE);
                            offset++;

                            // We're storing an array of numbers
                        } else {
                            for (int j = 2; j < x.length; j++) {

                                if (storeMode == DATA_BYTE_MODE) {

                                    System.out.printf("Setting memory location 0x%X(%d) to byte value 0x%X(%d)\n",
                                            dataStart + offset, dataStart + offset,
                                            Integer.parseInt(x[j].substring(1)), Integer.parseInt(x[j].substring(1)));

                                    setMemory(dataStart + offset, Integer.parseInt(x[j].substring(1)), DATA_BYTE_MODE);
                                    offset++;

                                }else if (storeMode == DATA_WORD_MODE){
                                    int value = Integer.parseInt(x[j].substring(1));
                                    int low = value & 0xff;
                                    int high = (value >> 8) & 0xff;

                                    System.out.printf("Setting memory location 0x%X(%d) to word value 0x%X(%d)\n",
                                            dataStart + offset, dataStart + offset,
                                            value, value);

                                    setMemory(dataStart + offset, value, DATA_WORD_MODE);
                                    offset += 2;
                                }
                            }
                            setMemory(dataStart + offset, ARRAY_TERMINATOR, DATA_BYTE_MODE);
                            offset++;
                        }
                    }
                    i++;
                }
            } else if (lines[i].startsWith(".")) { // regular function. add the function along with the calculated offset
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

        for (int i = 0; i < eachNum.length; i++) { // The TEXT section
            if (isNumber(eachNum[i])) {
                machineCodeList.add(Integer.parseInt(eachNum[i]));
            }
        }
        machineCodeList.add((int) TEXT_SECTION_END & 0xff);

        for (int i = 0; i < memory.length; i++) { // The DATA and STACK sections
            machineCodeList.add((int) memory[i] & 0xff);
        }
        machineCodeList.add((int) MEMORY_SECTION_END & 0xff);

        for (int i = 0; i < signature.length(); i++) // My signature, last release date and compiler version
            machineCodeList.add((int) signature.charAt(i));

        for (int i = 0; i < lastUpdateDate.length(); i++)
            machineCodeList.add((int) lastUpdateDate.charAt(i));

        for (int i = 0; i < compilerVersion.length(); i++)
            machineCodeList.add((int) compilerVersion.charAt(i));

        machineCodeList.add((int) (memorySizeKB + 1)); // The memory size in KB
        machineCodeList.add(bit_length); // the CPU architecture flag

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
    public void executeCompiledCode(int[] machine_code) {

        Integer mainEntryPoint = functions.get("MAIN");
        if (mainEntryPoint == null) {
            String err = "MAIN function label not found.";
            triggerProgramError(
                    err, ErrorHandler.ERR_CODE_MAIN_NOT_FOUND);
        }
        registers[PC] = mainEntryPoint;
        I = true;

        if (machine_code[machine_code.length - 3] != bit_length) { // Check the architecture
            String err = String.format("This code has been compiled for %d-bit architecture." +
                            " the current CPU architecture is %d-bit.\n",
                    machine_code[machine_code.length - 3], bit_length
            );

            triggerProgramError(
                    err, ErrorHandler.ERR_CODE_INCOMPATIBLE_ARCHITECTURE);
        }


        if (mem_size_B > 0xffff) {
            String err = "This Maximum amount of addressable memory for this architecture is 64KB";
            triggerProgramError(err, ErrorHandler.ERR_CODE_INVALID_MEMORY_LAYOUT);
        }

        if (machine_code[machine_code.length - 4] > (int) memorySizeKB + 1) { // Check the allocated memory
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
                        instructionSet.get(machine_code[registers[PC]]), registers[PC]));

                switch (machine_code[registers[PC]]) {

                    case INS_EXT -> {
                        Logger.addLog("Terminating program.");
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
                        while (getMemory(start) != ARRAY_TERMINATOR) {
                            start++;
                            len++;
                        }

                        switch (destination[0]) {
                            case REGISTER_MODE, REGISTER_WORD_MODE -> setRegister(destination[1], len);
                            case DIRECT_MODE -> setMemory(destination[1], len, DATA_BYTE_MODE);
                            case DIRECT_WORD_MODE -> setMemory(destination[1], len, DATA_WORD_MODE);
                            case INDIRECT_MODE -> setMemory(getRegister(destination[1]), len, DATA_BYTE_MODE);
                            case INDIRECT_WORD_MODE -> setMemory(getRegister(destination[1]), len, DATA_WORD_MODE);
                            default -> E = true;
                        }
                    }

                    case INS_LENW -> {
                        int[] destination = getNextOperand();
                        step();
                        step();
                        int start = (machine_code[registers[PC]] << 8) | machine_code[step()];
                        short len = 0;
                        while (readWord(start)[0] != ARRAY_TERMINATOR){
                            start += 2;
                            len++;
                        }

                        switch (destination[0]) {
                            case REGISTER_MODE, REGISTER_WORD_MODE -> setRegister(destination[1], len);
                            case DIRECT_MODE -> setMemory(destination[1], len, DATA_BYTE_MODE);
                            case DIRECT_WORD_MODE -> setMemory(destination[1], len, DATA_WORD_MODE);
                            case INDIRECT_MODE -> setMemory(getRegister(destination[1]), len, DATA_BYTE_MODE);
                            case INDIRECT_WORD_MODE -> setMemory(getRegister(destination[1]), len, DATA_WORD_MODE);
                            default -> E = true;
                        }
                    }

                    case INS_OUTS -> {
                        int start = registers[SS];
                        while (readByte(start) != ARRAY_TERMINATOR) {
                            outputString.append((char) memory[start]);
                            output += (char) memory[start];
                            try {
                                System.out.print((char) memory[start]);
                                Thread.sleep(delayAmountMilliseconds);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            start++;
                        }
                    }

                    case INS_OUTSW -> {
                        int start = registers[SS];
                        while (readWord(start)[0] != ARRAY_TERMINATOR){
                            int[] current = readWord(start);
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
                        Logger.addLog(String.format("Popping return address 0x%X from function call stack", return_address));
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
                            boolean x = VirtualMachine.interruptHandler(registers, memory);
                            if (!x) E = true;
                        } else System.out.println("Interrupt flag not set. skipping.");
                    }


                    case INS_NOP ->{ // do nothing for 1 cycle
                    }


                    default -> {
                        String err = "Undefined instruction. please check the instruction codes : " + machine_code[registers[PC]];
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
        Logger.addLog("Program terminated with code : " + status_code);

        Logger.addLog(String.format("""
                ==============================================
                %s
                %s
                ==============================================
                %s
                ==============================================
                """, dumpRegisters(), dumpFlags(), dumpMemory()));


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
                        int address = bytePairToWordLE(low, high);
                        dataCollector.add(address);
                    }
                }

                else if (machine_code[i] >= INS_CALL && machine_code[i] <= INS_JB || machine_code[i] == INS_LOOP) {

                    if (machine_code[i + 1] == FUNCTION_MODE) {
                        int high = machine_code[i + 2], low = machine_code[i + 3];
                        int address = bytePairToWordLE(low, high);
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


            while (!programEnd && machine_code[registers[PC]] != TEXT_SECTION_END) {
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
            Logger.addLog("Program terminated with code : " + status_code);

            Logger.addLog(String.format("""
                    ==============================================
                    %s
                    %s
                    ==============================================
                    %s
                    ==============================================
                    """, dumpRegisters(), dumpFlags(), dumpMemory()));


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

        Logger.addLog("Fetching destination.");
        switch (destination[0]) {

            case REGISTER_MODE, REGISTER_WORD_MODE -> setRegister(destination[1], operandValue);
            case DIRECT_MODE ->setMemory((destination[1] << 8) | destination[2], operandValue, DATA_BYTE_MODE);
            case DIRECT_WORD_MODE -> setMemory((destination[1] << 8) | destination[2], operandValue, DATA_WORD_MODE);
            case INDIRECT_MODE -> setMemory(getRegister(destination[1]), operandValue, DATA_BYTE_MODE);
            case INDIRECT_WORD_MODE -> setMemory(getRegister(destination[1]), operandValue, DATA_WORD_MODE);
            default -> E = true;

        }
    }


    public void out(int[] source) {

        Logger.addLog("Fetching operands");
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
        Logger.addLog("Fetching operands");
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
                newVal = readByte((destination[1] << 8) | destination[2]) << operandValue;
                setMemory((destination[1] << 8) | destination[2], newVal, DATA_BYTE_MODE);
            }
            case DIRECT_WORD_MODE -> {
                newVal = bytePairToWordLE(readWord((destination[1] << 8) | destination[2])) << operandValue;
                setMemory((destination[1] << 8) | destination[2], newVal, DATA_WORD_MODE);
            }
            case INDIRECT_MODE -> {
                newVal = readByte(getRegister(destination[1])) << operandValue;
                setMemory(getRegister(destination[1]), newVal);
            }
            case INDIRECT_WORD_MODE -> {
                newVal = bytePairToWordLE(readWord(getRegister(destination[1]))) << operandValue;
                setMemory(getRegister(destination[1]), newVal);
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
                newVal = readByte((destination[1] << 8) | destination[2]) >> operandValue;
                setMemory((destination[1] << 8) | destination[2], newVal, DATA_BYTE_MODE);
            }
            case DIRECT_WORD_MODE -> {
                newVal = bytePairToWordLE(readWord((destination[1] << 8) | destination[2])) >> operandValue;
                setMemory((destination[1] << 8) | destination[2], newVal, DATA_WORD_MODE);
            }
            case INDIRECT_MODE -> {
                newVal = readByte(getRegister(destination[1])) >> operandValue;
                setMemory(getRegister(destination[1]), newVal);
            }
            case INDIRECT_WORD_MODE -> {
                newVal = bytePairToWordLE(readWord(getRegister(destination[1]))) >> operandValue;
                setMemory(getRegister(destination[1]), newVal);
            }
            default -> E = true;
        }
        updateFlags(newVal);
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
                newVal = readByte( (destination[1] << 8) | destination[2] ) + operandValue;
                setMemory( (destination[1] << 8) | destination[2], newVal, DATA_BYTE_MODE);
            }

            case DIRECT_WORD_MODE ->{
                newVal = bytePairToWordLE( readWord( (destination[1] << 8) | destination[2] ) ) + operandValue;
                setMemory( (destination[1] << 8) | destination[2], newVal, DATA_WORD_MODE);
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
                newVal = readByte( (destination[1] << 8) | destination[2] ) - operandValue;
                setMemory((destination[1] << 8) | destination[2], newVal, DATA_BYTE_MODE);
            }

            case DIRECT_WORD_MODE ->{
                newVal = bytePairToWordLE( readWord( (destination[1] << 8) | destination[2] ) ) - operandValue;
                setMemory( (destination[1] << 8) | destination[2], newVal, DATA_WORD_MODE);
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
                newVal = readByte( (destination[1] << 8) | destination[2] ) * operandValue;
                setMemory( (destination[1] << 8) | destination[2], newVal, DATA_BYTE_MODE);
            }

            case DIRECT_WORD_MODE ->{
                newVal = bytePairToWordLE( readWord( (destination[1] << 8) | destination[2] ) ) * operandValue;
                setMemory( (destination[1] << 8) | destination[2], newVal, DATA_WORD_MODE);
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
                newVal = readByte( (destination[1] << 8) | destination[2] ) / operandValue;
                setMemory( (destination[1] << 8) | destination[2], newVal, DATA_BYTE_MODE);
            }

            case DIRECT_WORD_MODE ->{
                newVal = bytePairToWordLE( readWord( (destination[1] << 8) | destination[2] ) ) / operandValue;
                setMemory( (destination[1] << 8) | destination[2], newVal, DATA_WORD_MODE);
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
                newVal = ~readByte( (source[1] << 8) | source[2] );
                setMemory( (source[1] << 8) | source[2], newVal, DATA_BYTE_MODE);
            }

            case DIRECT_WORD_MODE -> {
                newVal = ~bytePairToWordLE( readWord( (source[1] << 8) | source[2] ) );
                setMemory( (source[1] << 8) | source[2], newVal, DATA_WORD_MODE );
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
                newVal = readByte( (destination[1] << 8) | destination[2] ) & operandValue;
                setMemory( (destination[1] << 8) | destination[2], newVal, DATA_BYTE_MODE);
            }

            case DIRECT_WORD_MODE ->{
                newVal = bytePairToWordLE( readWord( (destination[1] << 8) | destination[2] ) ) & operandValue;
                setMemory( (destination[1] << 8) | destination[2], newVal, DATA_WORD_MODE);
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
                newVal = readByte( (destination[1] << 8) | destination[2] ) | operandValue;
                setMemory( (destination[1] << 8) | destination[2], newVal);
            }

            case DIRECT_WORD_MODE ->{
                newVal = bytePairToWordLE( readWord( (destination[1] << 8) | destination[2] ) ) | operandValue;
                setMemory( (destination[1] << 8) | destination[2], newVal, DATA_WORD_MODE);
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
                newVal = readByte( (destination[1] << 8) | destination[2] ) ^ operandValue;
                setMemory( (destination[1] << 8) | destination[2], newVal, DATA_BYTE_MODE);
            }

            case DIRECT_WORD_MODE ->{
                newVal = bytePairToWordLE( readWord( (destination[1] << 8) | destination[2] ) ) ^ operandValue;
                setMemory( (destination[1] << 8) | destination[2], newVal, DATA_WORD_MODE);
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
                newVal = ~(readByte( (destination[1] << 8) | destination[2] ) & operandValue);
                setMemory( (destination[1] << 8) | destination[2], newVal, DATA_BYTE_MODE);
            }

            case DIRECT_WORD_MODE ->{
                newVal = ~(bytePairToWordLE( readWord( (destination[1] << 8) | destination[2] ) ) & operandValue);
                setMemory( (destination[1] << 8) | destination[2], newVal, DATA_WORD_MODE);
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
                newVal = ~(readByte( (destination[1] << 8) | destination[2] ) | operandValue);
                setMemory( (destination[1] << 8) | destination[2], newVal, DATA_BYTE_MODE);
            }

            case DIRECT_WORD_MODE ->{
                newVal = ~(bytePairToWordLE( readWord( (destination[1] << 8) | destination[2] ) ) | operandValue);
                setMemory( (destination[1] << 8) | destination[2], newVal, DATA_WORD_MODE);
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
                newVal = (int) Math.pow( readByte( (destination[1] << 8) | destination[2] ) , operandValue);
                setMemory( (destination[1] << 8) | destination[2], newVal, DATA_BYTE_MODE);
            }

            case DIRECT_WORD_MODE ->{
                newVal = (int) Math.pow( bytePairToWordLE( readWord( (destination[1] << 8) | destination[2] ) ), operandValue );
                setMemory( (destination[1] << 8) | destination[2], newVal, DATA_WORD_MODE);
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
                newVal = (int) Math.sqrt( readByte( (source[1] << 8) | source[2] ) );
                setMemory( (source[1] << 8) | source[2], newVal , DATA_BYTE_MODE);
            }

            case DIRECT_WORD_MODE -> {
                newVal = (int) Math.sqrt( bytePairToWordLE( readWord( (source[1] << 8) | source[2] ) ) );
                setMemory( (source[1] << 8) | source[2], newVal , DATA_WORD_MODE);
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

            case DIRECT_MODE ->{
                newVal = (int) (Math.random() * operandValue);
                setMemory( (destination[1] << 8) | destination[2], newVal, DATA_BYTE_MODE);
            }
            case DIRECT_WORD_MODE -> {
                newVal = (int) (Math.random() * operandValue);
                setMemory( (destination[1] << 8) | destination[2], newVal, DATA_WORD_MODE );
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
                newVal = readByte( (source[1] << 8) | source[2] ) + 1;
                setMemory( (source[1] << 8) | source[2], newVal , DATA_BYTE_MODE);
            }

            case DIRECT_WORD_MODE -> {
                newVal = bytePairToWordLE( readWord( (source[1] << 8) | source[2] ) ) + 1;
                setMemory( (source[1] << 8) | source[2], newVal, DATA_WORD_MODE );
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
                newVal = readByte( (source[1] << 8) | source[2] ) - 1;
                setMemory( (source[1] << 8) | source[2], newVal, DATA_BYTE_MODE );
            }

            case DIRECT_WORD_MODE -> {
                newVal = bytePairToWordLE( readWord( (source[1] << 8) | source[2] ) ) - 1;
                setMemory( (source[1] << 8) | source[2], newVal , DATA_WORD_MODE);
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
                memory[ registers[SP] ] = (short) readByte( (source[1] << 8) | source[2] );
                registers[SP]--;
            }

            case DIRECT_WORD_MODE -> {
                int[] val = readWord( (source[1] << 8) | source[2] );
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
                memory[(source[1] << 8) | source[2]] = memory[registers[SP]];
                memory[registers[SP]] = 0;
            }

            case DIRECT_WORD_MODE -> {
                registers[SP]++;
                int[] val = new int[] {memory[registers[SP]], memory[registers[SP] + 1]};
                memory[ (source[1] << 8) | source[2] ] = (short) val[0];
                memory[((source[1] << 8) | source[2]) + 1] = (short) val[1];

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
        registers[PC] = address - 1; // sub 1 to nullify the step() and the address byte
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

        Logger.source = debugSource;
        System.out.println("Starting 16-bit CPU module");
        bit_length = 16;

        calculateMemorySegments();

        memory = new short[mem_size_B];


        if (stack_start < 0){
            String errMsg = "Invalid memory layout (stack). " + stack_start;
            triggerProgramError(
                    errMsg, ErrorHandler.ERR_CODE_INVALID_MEMORY_LAYOUT);

        }
        if (data_start < 0){
            String errMsg = "Invalid memory layout (data). " + data_start;
            triggerProgramError(
                    errMsg, ErrorHandler.ERR_CODE_INVALID_MEMORY_LAYOUT);
        }

        System.out.println(memInitMsg);

        System.out.printf("CPU speed set to %s Cycles per second. With a step delay of %sMS\n",
                Launcher.appConfig.get("Cycles"), delayAmountMilliseconds);

        Logger.addLog(memInitMsg);


        Logger.addLog(String.format("CPU speed set to %s Cycles per second. With a step delay of %sMS\n",
                Launcher.appConfig.get("Cycles"), delayAmountMilliseconds));

        reset();
    }


    public void reset(){
        System.out.println("Initializing memory.");
        memory = new short[mem_size_B];
        dataOffset = dataOrigin;

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

        outputString = new StringBuilder();

        machineCode = new int[] {0};


        registers[SP] = stack_end;
        registers[DP] = dataOrigin;
        registers[PC] = 0;

    }

    /// //////////////////////////////////////////////////////////////////////////////
    /// /////////////////////////////////////////////////////////////////////////////
    /// ////////////////////////////////////////////////////////////////////////////

}