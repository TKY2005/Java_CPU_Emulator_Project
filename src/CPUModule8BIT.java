import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class CPUModule8BIT extends CPU {

    // CPU specific Variables //
    public static final int max_value = 255;
    public static final int min_value = -127;


    /// ///////////////////////////////////////


    public short[] registers;
    public String[] registerNames;

    /// ///////////////////////////////////////
    ///
    ///
    /// Listeners /////////////////////////////
    private onStepListener stepListener;

    String debugSource = "CPU_MODULE_8_BIT";

    StringBuilder code;
    int[] functionPointers;
    int[] dataPointers;

    HashMap<Integer, String> functionAddresses;
    HashMap<Integer, String> dataAddresses;


    public Integer getPC(){
        return (int) registers[PC];
    }

    public CPUModule8BIT() {
        super();
        logDevice = debugSource;

        bit_length = 8;

        System.out.println("Starting 8 bit cpu module.");

        memoryController = new MemoryModule(memoryController.memorySizeKB, this);

        if (memoryController.stack_start < 0){
            String errMsg = "Invalid memoryController.memory layout (stack). " + memoryController.stack_start;
            triggerProgramError(
                    errMsg, ErrorHandler.ERR_CODE_INVALID_MEMORY_LAYOUT);

        }
        if (memoryController.data_start < 0){
            String errMsg = "Invalid memoryController.memory layout (data). " + memoryController.data_start;
            triggerProgramError(
                    errMsg, ErrorHandler.ERR_CODE_INVALID_MEMORY_LAYOUT);
        }

        Logger.addLog(memInitMsg, logDevice, true);

        Logger.addLog(String.format("CPU speed set to %s Cycles per second. With a step delay of %sMS\n",
                Launcher.appConfig.get("Cycles"), delayAmountMilliseconds), logDevice, true);

        reset();
    }

    public String getRegisterName(int registerID, boolean toUpperCase){
        if(!toUpperCase) return registerNames[registerID];
        else return registerNames[registerID].toUpperCase();
    }

    public String getDisassembledOperand(short[] operand){
        return switch (operand[0]){
            case REGISTER_MODE -> CPU.REGISTER_PREFIX + getRegisterName(operand[1], false);
            case DIRECT_MODE -> CPU.HEX_MEMORY + Integer.toHexString(operand[1]);
            case INDIRECT_MODE -> CPU.INDIRECT_MEMORY_PREFIX + getRegisterName(operand[1], false);
            case IMMEDIATE_MODE -> CPU.HEX_PREFIX + Integer.toHexString(operand[1]).toUpperCase();

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

    @Override
    public String disassembleMachineCode(int[] machineCode){

    code = new StringBuilder();

    code.append("Disassembled by T.K.Y CPU compiler ").append(compilerVersion).append("\n");
    code.append("Target architecture: ").append(machineCode[machineCode.length - 3]).append("-bit").append("\n");
    code.append("Memory size: ").append(machineCode[machineCode.length - 4]).append("KB").append("\n");
    registers[PC] = 0;
    delayAmountMilliseconds = 0;

    Set<Integer> functionCollector = new TreeSet<>();
    Set<Integer> dataCollector = new TreeSet<>();

    for (int i = 0; machineCode[i] != (TEXT_SECTION_END & 0xff); i++) {

        if (machineCode[i] >= INS_CALL && machineCode[i] <= INS_JB || machineCode[i] == INS_LOOP) {
            if (machineCode[i + 1] == FUNCTION_MODE) {
                int high = machineCode[i + 2], low = machineCode[i + 3];
                int address = memoryController.bytePairToWordLE(low, high);
                functionCollector.add(address);
            }
        }

        if (machineCode[i] == INS_LA || machineCode[i] == INS_LLEN || machineCode[i] == INS_LENW){
                if (machineCode[i + 3] == DATA_MODE) {
                    int high = machineCode[i + 4], low = machineCode[i + 5];
                    int address = memoryController.bytePairToWordLE(low, high);
                    dataCollector.add(address);
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
    while(machineCode[dataOffset] != (TEXT_SECTION_END & 0xff)) dataOffset++;

    System.out.println("Rebuilding the DATA section...");
    StringBuilder dataSectionRebuild = new StringBuilder();
    dataSectionRebuild.append(".DATA");

    for(int i = 0; i < dataPointers.length; i++){
        dataSectionRebuild.append("\n\t").append(dataAddresses.get(dataPointers[i])).append(" ").append("\"");
        int start_address = dataPointers[i] + dataOffset + 1; // +1 to skip the previous data terminator
        String data = "";
        for(int j = start_address; machineCode[j] != ARRAY_TERMINATOR; j++){
            if (machineCode[j] != '\n') data += (char) machineCode[j];
            else data += "\\n";
        }
        dataSectionRebuild.append(data).append("\"");
    }
    dataSectionRebuild.append("\nEND");

    int mainEntryPoint = ((machineCode[machineCode.length - 2] & 0xff) | machineCode[machineCode.length - 1]);
    code.append("Program's entry point: ").append("0x").append(Integer.toHexString(mainEntryPoint).toUpperCase()).append("\n");

    if (machineCode[machineCode.length - 3] != bit_length) { // Check the architecture
        String err = String.format("This code has been compiled for %d-bit architecture." +
                        " the current CPU architecture is %d-bit.\n",
                machineCode[machineCode.length - 3], bit_length
        );

        triggerProgramError(
                err, ErrorHandler.ERR_CODE_INCOMPATIBLE_ARCHITECTURE);
    }


    while (!programEnd && machineCode[registers[PC]] != TEXT_SECTION_END && machineCode[registers[PC]] != 0x00) {
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

            switch (machineCode[registers[PC]]) {

                // step function increments PC and returns its value
                // we step two times for each operand. one step for mode. another step for value
                case INS_SET -> {
                    numBytes = 5;
                    for (int i = 0; i < numBytes; i++) byteStr.append(String.format("%02X ", machineCode[registers[PC] + i]));
                    code.append(String.format("%-20s", byteStr.toString()));
                    code.append(instructionSet.get(machineCode[registers[PC]])).append(" ");
                    short[] destination = getNextOperand();
                    code.append(getDisassembledOperand(destination)).append(" ");
                    short[] source = getNextOperand();
                    code.append(getDisassembledOperand(source));
                }
                case INS_OUT,
                     INS_SQRT,
                     INS_INC, INS_DEC,
                     INS_NOT, INS_PUSH, INS_POP, INS_OUTC -> {
                    numBytes = 3;
                    for (int i = 0; i < numBytes; i++) byteStr.append(String.format("%02X ", machineCode[registers[PC] + i]));
                    code.append(String.format("%-20s", byteStr.toString()));
                    code.append(instructionSet.get(machineCode[registers[PC]])).append(" ");
                    short[] destination = getNextOperand();
                    code.append(getDisassembledOperand(destination));
                }


                case INS_ADD, INS_SUB, INS_MUL, INS_DIV, INS_POW,
                        INS_RND, INS_AND, INS_OR, INS_XOR, INS_NAND, INS_NOR, INS_SHL, INS_SHR -> {
                    numBytes = 5;
                    for (int i = 0; i < numBytes; i++) byteStr.append(String.format("%02X ", machineCode[registers[PC] + i]));
                    code.append(String.format("%-20s", byteStr.toString()));
                    code.append(instructionSet.get(machineCode[registers[PC]])).append(" ");
                    short[] destination = getNextOperand();
                    code.append(getDisassembledOperand(destination)).append(" ");
                    short[] source = getNextOperand();
                    code.append(getDisassembledOperand(source));
                }


                case INS_LA -> {
                    // Get the destination (must be 16-bit compatible). step to the address. load into source
                    numBytes = 6;
                    for (int i = 0; i < numBytes; i++) byteStr.append(String.format("%02X ", machineCode[registers[PC] + i]));
                    code.append(String.format("%-20s", byteStr.toString()));
                    code.append(instructionSet.get(machineCode[registers[PC]])).append(" ");
                    short[] destination = getNextOperand();
                    code.append(getDisassembledOperand(destination)).append(" ");
                    short[] source = new short[]{(short) machineCode[step()], (short) machineCode[step()], (short) machineCode[step()]};
                    code.append(getDisassembledOperand(source));
                }

                case INS_LLEN -> {
                    numBytes = 6;
                    for (int i = 0; i < numBytes; i++) byteStr.append(String.format("%02X ", machineCode[registers[PC] + i]));
                    code.append(String.format("%-20s", byteStr.toString()));
                    code.append(instructionSet.get(machineCode[registers[PC]])).append(" ");
                    short[] destination = getNextOperand();
                    code.append(getDisassembledOperand(destination)).append(" ");
                    short[] source = new short[]{(short) machineCode[step()], (short) machineCode[step()], (short) machineCode[step()]};
                    code.append(getDisassembledOperand(source));
                }


                case INS_CALL -> {
                    numBytes = 4;
                    for (int i = 0; i < numBytes; i++) byteStr.append(String.format("%02X ", machineCode[registers[PC] + i]));
                    code.append(String.format("%-20s", byteStr.toString()));
                    code.append(instructionSet.get(machineCode[registers[PC]])).append(" ");
                    short source[] = new short[]{(short) machineCode[step()], (short) machineCode[step()], (short) machineCode[step()]};
                    code.append(getDisassembledOperand(source));
                }


                case INS_CE, INS_CNE, INS_CL, INS_CLE, INS_CG, INS_CGE, INS_JMP, INS_JE, INS_JNE, INS_JL, INS_JLE,
                        INS_JG, INS_JGE -> {
                    numBytes = 4;
                    for (int i = 0; i < numBytes; i++) byteStr.append(String.format("%02X ", machineCode[registers[PC] + i]));
                    code.append(String.format("%-20s", byteStr.toString()));
                    code.append(instructionSet.get(machineCode[registers[PC]])).append(" ");
                    short[] source = new short[]{(short) machineCode[step()], (short) machineCode[step()], (short) machineCode[step()]};
                    code.append(getDisassembledOperand(source));
                }

                case INS_CMP -> {
                    numBytes = 5;
                    for (int i = 0; i < numBytes; i++) byteStr.append(String.format("%02X ", machineCode[registers[PC] + i]));
                    code.append(String.format("%-20s", byteStr.toString()));
                    code.append(instructionSet.get(machineCode[registers[PC]])).append(" ");
                    short[] destination = getNextOperand();
                    code.append(getDisassembledOperand(destination)).append(" ");
                    short[] source = getNextOperand();
                    code.append(getDisassembledOperand(source));
                }

                case INS_LOOP -> {
                    numBytes = 4;
                    for (int i = 0; i < numBytes; i++) byteStr.append(String.format("%02X ", machineCode[registers[PC] + i]));
                    code.append(String.format("%-20s", byteStr.toString()));
                    code.append(instructionSet.get(machineCode[registers[PC]])).append(" ");
                    // if RCX > 0: decrement RC and jump to the label address specified.
                    short[] source = new short[]{(short) machineCode[step()], (short) machineCode[step()], (short) machineCode[step()]};
                    code.append(getDisassembledOperand(source));
                }

                case INS_INT, INS_OUTS, INS_EXT, INS_RET,
                        INS_END, INS_NOP -> {
                    numBytes = 1;
                    byteStr.append(String.format("%02X ", machineCode[registers[PC]]));
                    code.append(String.format("%-20s", byteStr.toString()));
                    code.append(instructionSet.get(machineCode[registers[PC]])).append(" ");
                }


                case TEXT_SECTION_END & 0xff -> {
                    System.out.println("Code ends here.");
                    programEnd = true;
                    break;
                }
                default -> {
                    numBytes = 1;
                    byteStr.append(String.format("%02X ", machineCode[registers[PC]]));
                    code.append(String.format("%-20s", byteStr.toString()));
                    String err = "Undefined instruction. please check the instruction codes : " + machineCode[registers[PC]];
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
    }

    @Override
    public int[] compileToMemoryImage(String code){
        String[] lines = code.split("\n");
        List<Integer> memImageList = new ArrayList<>();

        // preset the list size
        for(int i = 0; i < memoryController.mem_size_B; i++) memImageList.add(0x00);

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
            if (lines[i].equals(".DATA")){
                System.out.println("Data section detected.");
                int offset = 0;
                i++; // skip .DATA line

             while (!lines[i].equalsIgnoreCase("end")) {

                 String[] x = lines[i].trim().split(" ");
                 int dataStart = memoryController.dataOffset;
                 if (x[0].equals("org")) memoryController.dataOffset = Integer.parseInt(x[1].substring(1)) - offset;

                 else {
                     dataMap.put(x[0], dataStart + offset);
                     if (x[1].startsWith(String.valueOf(STRING_PREFIX))) { // 34 in decimal 0x22 in hex
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
                             System.out.printf("Setting memoryController.memory location 0x%X(%d) to char %c\n",
                                     dataStart + offset, dataStart + offset, fullString.charAt(j));
                             memoryController.setMemory(dataStart + offset, (short) fullString.charAt(j));
                             offset++;
                         }
                         memoryController.setMemory(dataStart + offset, ARRAY_TERMINATOR);
                         offset++;
                     } else {
                         for (int j = 1; j < x.length; j++) {
                             System.out.printf("Setting memoryController.memory location 0x%X(%d) to value 0x%X(%d)\n",
                                     dataStart + offset, dataStart + offset,
                                     Integer.parseInt(x[j].substring(1)), Integer.parseInt(x[j].substring(1)));

                             memoryController.setMemory(dataStart + offset, (short) Integer.parseInt(x[j].substring(1)));
                             offset++;
                         }
                         memoryController.setMemory(dataStart + offset, ARRAY_TERMINATOR);
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
            int[] translatedLine = toMachineCode(fullLines[i]);
            //String a = Arrays.toString(toMachineCode(fullLines[i])).replace("[", "").replace("]", "");
            String a = Arrays.toString(translatedLine).replace("[", "").replace("]", "");
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

        for (int i = MemoryModule.data_start; i < memoryController.getMemorySize(); i++) { // The DATA and STACK sections
            memImageList.set(i, memoryController.readByteAbsolute(i) & 0xff);
        }
        memImageList.set(MemoryModule.stack_end, (int) MEMORY_SECTION_END & 0xff);

        int write_addr = MemoryModule.stack_end + 1;
        for (int i = 0; i < signature.length(); i++){
            // My signature, last release date and compiler version
            memImageList.set(write_addr ,(int) signature.charAt(i));
            write_addr++;
        }

        for (int i = 0; i < lastUpdateDate.length(); i++) {
            memImageList.set(write_addr, (int) lastUpdateDate.charAt(i));
            write_addr++;
        }

        for (int i = 0; i < compilerVersion.length(); i++) {
            memImageList.set(write_addr, (int) compilerVersion.charAt(i));
            write_addr++;
        }

        write_addr = memoryController.getMemorySize() - 1;
        memImageList.set(write_addr - 3, (int) (memorySizeKB + 1)); // The memory size in KB
        memImageList.set(write_addr - 2, bit_length); // the CPU architecture flag

        // Add the program's entry point.
        int entryPoint = functions.get("MAIN");

        int entryPointLow = entryPoint & 0xff;
        int entryPointHigh = (entryPoint >> 8) & 0xff;

        memImageList.set(write_addr - 1, entryPointHigh);
        memImageList.set(write_addr, entryPointLow);

        machineCode = memImageList.stream().mapToInt(Integer::intValue).toArray();
        if (stepListener != null) stepListener.updateUI();
        return machineCode;
    }

    @Override
    public void reset(){

        // 6 General purpose registers + 6 Special purpose registers

        Logger.resetLogs();

        registers = new short[REGISTER_COUNT + 6];
        registerNames = new String[registers.length];
        bit_length = 8;

        memoryController.resetMemory();

        memoryController.dataOffset = memoryController.dataOrigin;
        currentLine = 1;
        currentByte = 0;
        status_code = 0;

        functionCallStack = new Stack<>();
        dataMap = new HashMap<>();
        functions = new HashMap<>();


        outputString = new StringBuilder();
        machineCode = new int[] {0};


        // set register names for search
        char registerChar = 'a';
        for(int i = 0; i < registerNames.length - 4; i++){
            registerNames[i] = "r" + registerChar;
            registerChar++;
        }
        registerNames[PC] = "pc";
        registerNames[SP] = "sp";
        registerNames[SS] = "ss";
        registerNames[SE] = "se";
        registerNames[DP] = "dp";
        registerNames[DI] = "di";

        /*for(int i = 0; i < registerNames.length; i++){
            System.out.printf("%s => 0x%X\n", registerNames[i], i);
        }*/

        registers[SP] = (short) memoryController.stack_start;
        registers[DP] = (short) memoryController.dataOrigin;
        registers[PC] = 0;

        Z = false;
        N = false;
        E = false;
        I = false;
        T = false;
        C = false;
        O = false;

        programEnd = false;
    }

    public void executeCompiledCode(int[] machine_code){

        Integer mainEntryPoint = functions.get("MAIN");
        if (mainEntryPoint == null){
            String err = "MAIN function label not found.";
            triggerProgramError(
                    err, ErrorHandler.ERR_CODE_MAIN_NOT_FOUND);
        }
        registers[PC] = (short) (int) mainEntryPoint;
        I = true;

        if (memoryController.mem_size_B > 0xffff) {
            String err = "This Maximum amount of addressable memoryController.memory for this architecture is 64KB";
            triggerProgramError(err, ErrorHandler.ERR_CODE_INVALID_MEMORY_LAYOUT);
        }

        while (!programEnd && registers[PC] < machine_code.length){

            if (canExecute) {
                switch (machine_code[registers[PC]]) {
                    case INS_EXT -> {
                        programEnd = true;
                    }

                    // step function increments PC and returns its value
                    // we step two times for each operand. one step for mode. another step for value
                    case INS_SET -> {
                        short[] destination = getNextOperand();
                        short[] source = getNextOperand();
                        set(destination, source);
                    }
                    case INS_OUT -> {
                        short[] destination = getNextOperand();
                        out(destination);
                    }

                    case INS_OUTC -> {
                        short[] source = getNextOperand();
                        outc(source);
                    }

                    case INS_SHL -> {
                        short[] destination = getNextOperand();
                        short[] source = getNextOperand();
                        shift_left(destination, source);
                    }
                    case INS_SHR -> {
                        short[] destination = getNextOperand();
                        short[] source = getNextOperand();
                        shift_right(destination, source);
                    }

                    case INS_ADD -> {
                        short[] destination = getNextOperand();
                        short[] source = getNextOperand();
                        add(destination, source);
                    }
                    case INS_SUB -> {
                        short[] destination = getNextOperand();
                        short[] source = getNextOperand();
                        sub(destination, source);
                    }
                    case INS_MUL -> {
                        short[] destination = getNextOperand();
                        short[] source = getNextOperand();
                        mul(destination, source);
                    }
                    case INS_DIV -> {
                        short[] destination = getNextOperand();
                        short[] source = getNextOperand();
                        div(destination, source);
                    }

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
                        int low = machineCode[registers[PC]];
                        int high = machineCode[step()];
                        int start = (low << 8) | high;
                        short len = 0;
                        while (memoryController.getMemory((short) start) != ARRAY_TERMINATOR) {
                            start++;
                            len++;
                        }

                        switch (destination[0]) {
                            case REGISTER_MODE -> setRegister(destination[1], len);
                            case DIRECT_MODE -> memoryController.setMemory(destination[1], len);
                            case INDIRECT_MODE -> memoryController.setMemory(getRegister(destination[1]), len);
                            default -> E = true;
                        }
                    }

                    case INS_OUTS -> {
                        int start = registers[SS];
                        while (memoryController.getMemory((short) start) != ARRAY_TERMINATOR) {
                            outputString.append((char) memoryController.getMemory((short) start));
                            output += (char) memoryController.getMemory((short) start);
                            try {
                                Thread.sleep(delayAmountMilliseconds);
                                System.out.print((char)memoryController.getMemory((short) start));
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            start++;
                        }
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
                        int address = ( ( machine_code[step()] << 8 ) | machine_code[step()] );
                        int return_address = step() - 1;
                        call(address, return_address);
                    }
                    case INS_RET -> {
                        int return_address = functionCallStack.pop();
                        registers[PC] = (short) return_address;
                    }

                    case INS_CE -> {
                        step();
                        int address = ( ( machine_code[step()] << 8 ) | machine_code[step()] );
                        int return_address = step() - 1;
                        if (Z) call(address, return_address);
                        else registers[PC] = (short) return_address;
                    }
                    case INS_CNE -> {
                        step();
                        int address = ( ( machine_code[step()] << 8 ) | machine_code[step()] );
                        int return_address = step() - 1;
                        if (!Z) call(address, return_address);
                        else registers[PC] = (short) return_address;
                    }
                    case INS_CL -> {
                        step();
                        int address = ( ( machine_code[step()] << 8 ) | machine_code[step()] );
                        int return_address = step() - 1;
                        if (N) call(address, return_address);
                        else registers[PC] = (short) return_address;
                    }
                    case INS_CLE -> {
                        step();
                        int address = ( ( machine_code[step()] << 8 ) | machine_code[step()] );
                        int return_address = step() - 1;
                        if (N || Z) call(address, return_address);
                        else registers[PC] = (short) return_address;
                    }
                    case INS_CG -> {
                        step();
                        int address = ( ( machine_code[step()] << 8 ) | machine_code[step()] );
                        int return_address = step() - 1;
                        if (!N) call(address, return_address);
                        else registers[PC] = (short) return_address;
                    }
                    case INS_CGE -> {
                        step();
                        int address = ( ( machine_code[step()] << 8 ) | machine_code[step()] );
                        int return_address = step() - 1;
                        if (!N || Z) call(address, return_address);
                        else registers[PC] = (short) return_address;
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
                        short[] destination = getNextOperand();
                        short[] source = getNextOperand();
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
                        }else step();
                    }

                    case INS_INT -> {
                        if (I) {
                            boolean x = InterruptHandler.triggerSoftwareInterrupt(this, registers, memoryController);
                            if (!x) E = true;
                        } else System.out.println("Interrupt flag not set. skipping.");
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
                //timeout.cancel();
                step();
            }

        }

        outputString.append("Program terminated with code : ").append(status_code);
        output = "Program terminated with code : " + status_code;
    }

    public int step() {
        long currentTime = System.currentTimeMillis();
        registers[PC]++;
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

    public short[] getNextOperand(){
        return new short[] {(short) machineCode[step()], (short) machineCode[step()]};
    }

    public void updateFlags(short value){
        byte flagSetter = (byte) value;

        // N = the value of MSB
        N = ( (flagSetter >>> 7) & 1 ) == 1;

        // C for unsigned arithmetic, O for signed arithmetic.
        if (N) O =  value > 127 || value < -127;
        else C = value > 255;

        Z = value == 0; // Z = is value = 0
    }


    public void set(short[] destination, short[] value){
        short operandValue;
        operandValue = switch (value[0]){
            case REGISTER_MODE -> getRegister( value[1] );
            case DIRECT_MODE -> memoryController.getMemory( value[1] );
            case INDIRECT_MODE -> memoryController.getMemory( getRegister( value[1] ) );
            case IMMEDIATE_MODE -> value[1];

            default -> 256;
        };
        if (operandValue == 256) triggerProgramError(
                "Invalid instruction prefix", ErrorHandler.ERR_CODE_INVALID_PREFIX);

        switch (destination[0]){
            case REGISTER_MODE -> setRegister( destination[1], operandValue );
            case DIRECT_MODE -> memoryController.setMemory( destination[1], operandValue );
            case INDIRECT_MODE -> memoryController.setMemory( getRegister( destination[1] ), operandValue );

            default -> triggerProgramError(
                    "Invalid instruction prefix", ErrorHandler.ERR_CODE_INVALID_PREFIX);
        }

    }


    public void out(short[] destination){

        switch (destination[0]){
            case REGISTER_MODE ->{
                output = String.valueOf(registers[destination[1]]);
            }
            case DIRECT_MODE ->{
                output = String.valueOf(memoryController.readByte(destination[1]));
            }
            case INDIRECT_MODE ->{
                output = String.valueOf(memoryController.readByte( registers[ destination[1] ] ));
            }
            case IMMEDIATE_MODE ->{
                output = String.valueOf(destination[1]);
            }
            default -> E = true;
        }
        if (!E){
            char[] x = output.toCharArray();

            for (char c : x) {
                try {
                    Thread.sleep(delayAmountMilliseconds);
                    outputString.append(c);
                    System.out.print(c);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public void outc(short[] source){
        switch(source[0]) {
            case REGISTER_MODE -> output = String.valueOf((char) registers[source[1]]);
            case DIRECT_MODE -> output = String.valueOf((char) memoryController.readByte(source[1]));
            case INDIRECT_MODE -> output = String.valueOf((char) memoryController.readByte(registers[source[1]]));
            case IMMEDIATE_MODE -> output = String.valueOf((char) source[1]);
        }

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


    public void shift_left(short[] destination, short[] source){
        short value = 0;
        short operandValue = switch (source[0]){
            case REGISTER_MODE -> getRegister(source[1]);
            case DIRECT_MODE -> memoryController.getMemory(source[1]);
            case INDIRECT_MODE -> memoryController.getMemory(getRegister(source[1]));
            case IMMEDIATE_MODE -> source[1];

            default -> 256;
        };
        if (operandValue == 256) E = true;

        switch (destination[0]){
            case REGISTER_MODE -> {
                value = (short) (getRegister(destination[1]) << operandValue);
                setRegister(destination[1], value);
            }

            case DIRECT_MODE -> {
                value = (short) (memoryController.getMemory( destination[1] ) << operandValue);
                memoryController.setMemory( destination[1], value );
            }

            case INDIRECT_MODE -> {
                value = (short) (memoryController.getMemory( getRegister( destination[1]) ) << operandValue);
                memoryController.setMemory( getRegister( destination[1] ), value );
            }
            default -> E = true;
        }
        updateFlags(value);
    }

    public void shift_right(short[] destination, short[] source){
        short value = 0;
        short operandValue = switch (source[0]){
            case REGISTER_MODE -> getRegister(source[1]);
            case DIRECT_MODE -> memoryController.getMemory(source[1]);
            case INDIRECT_MODE -> memoryController.getMemory(getRegister(source[1]));
            case IMMEDIATE_MODE -> source[1];

            default -> 256;
        };
        if (operandValue == 256) E = true;

        switch (destination[0]){
            case REGISTER_MODE -> {
                value = (short) (getRegister(destination[1]) >> operandValue);
                setRegister(destination[1], value);
            }

            case DIRECT_MODE -> {
                value = (short) (memoryController.getMemory( destination[1] ) >> operandValue);
                memoryController.setMemory( destination[1], value );
            }

            case INDIRECT_MODE -> {
                value = (short) (memoryController.getMemory( getRegister( destination[1]) ) >> operandValue);
                memoryController.setMemory( getRegister( destination[1] ), value );
            }

            default -> E = true;
        }

        updateFlags(value);
    }

    public void add(short[] destination, short[] source){

        short operandValue = switch ( source[0] ){

            case REGISTER_MODE -> getRegister( source[1] );
            case DIRECT_MODE -> memoryController.getMemory( source[1] );
            case INDIRECT_MODE -> memoryController.getMemory( getRegister( source[1] ) );
            case IMMEDIATE_MODE -> source[1];

            default -> 256;
        };
        if (operandValue == 256) triggerProgramError(
                "Invalid instruction prefix", ErrorHandler.ERR_CODE_INVALID_PREFIX);

        short newVal = 0;
        switch (destination[0]){
            case REGISTER_MODE -> {
                newVal = (short) (operandValue + getRegister( destination[1] ));
                setRegister( destination[1] , newVal);
            }
            case DIRECT_MODE -> {
                newVal = (short) ( operandValue + memoryController.getMemory( destination[1] ) );
                memoryController.setMemory( destination[1], newVal );
            }
            case INDIRECT_MODE -> {
                newVal = (short) ( operandValue + memoryController.getMemory( getRegister( destination[1] ) ) );
                memoryController.setMemory( getRegister( destination[1] ), newVal );
            }
        }
        updateFlags(newVal);

    }


    public void sub(short[] destination, short[] source){

        short operandValue = switch ( source[0] ){

            case REGISTER_MODE -> getRegister( source[1] );
            case DIRECT_MODE -> memoryController.getMemory( source[1] );
            case INDIRECT_MODE -> memoryController.getMemory( getRegister( source[1] ) );
            case IMMEDIATE_MODE -> source[1];

            default -> 256;
        };
        if (operandValue == 256) triggerProgramError(
                "Invalid instruction prefix", ErrorHandler.ERR_CODE_INVALID_PREFIX);

        short newVal = 0;
        switch (destination[0]){
            case REGISTER_MODE -> {
                newVal = (short) ( getRegister( destination[1] ) - operandValue );
                setRegister( destination[1] , newVal);
            }
            case DIRECT_MODE -> {
                newVal = (short) ( memoryController.getMemory( destination[1] ) - operandValue);
                memoryController.setMemory( destination[1], newVal );
            }
            case INDIRECT_MODE -> {
                newVal = (short) ( memoryController.getMemory( getRegister( destination[1] ) ) - operandValue );
                memoryController.setMemory( getRegister( destination[1] ), newVal );
            }
        }
        byte flagSetter = (byte) newVal;
        if (flagSetter == 0) Z = true;
        if (flagSetter < 0) N = true;

    }


    public void mul(short[] destination, short[] source){

        short operandValue = switch ( source[0] ){

            case REGISTER_MODE -> getRegister( source[1] );
            case DIRECT_MODE -> memoryController.getMemory( source[1] );
            case INDIRECT_MODE -> memoryController.getMemory( getRegister( source[1] ) );
            case IMMEDIATE_MODE -> source[1];

            default -> 256;
        };
        if (operandValue == 256) triggerProgramError(
                "Invalid instruction prefix", ErrorHandler.ERR_CODE_INVALID_PREFIX);

        short newVal = 0;
        switch (destination[0]){
            case REGISTER_MODE -> {
                newVal = (short) (operandValue * getRegister( destination[1] ));
                setRegister( destination[1] , newVal);
            }
            case DIRECT_MODE -> {
                newVal = (short) ( operandValue * memoryController.getMemory( destination[1] ) );
                memoryController.setMemory( destination[1], newVal );
            }
            case INDIRECT_MODE -> {
                newVal = (short) ( operandValue * memoryController.getMemory( getRegister( destination[1] ) ) );
                memoryController.setMemory( getRegister( destination[1] ), newVal );
            }
        }
        byte flagSetter = (byte) newVal;
        if (flagSetter == 0) Z = true;
        if (flagSetter < 0) N = true;

    }
    

    public void div(short[] destination, short[] source){

        short operandValue = switch ( source[0] ){

            case REGISTER_MODE -> getRegister( source[1] );
            case DIRECT_MODE -> memoryController.getMemory( source[1] );
            case INDIRECT_MODE -> memoryController.getMemory( getRegister( source[1] ) );
            case IMMEDIATE_MODE -> source[1];

            default -> 256;
        };
        if (operandValue == 256) triggerProgramError(
                "Invalid instruction prefix", ErrorHandler.ERR_CODE_INVALID_PREFIX);

        short newVal = 0;
        switch (destination[0]){
            case REGISTER_MODE -> {
                newVal = (short) (operandValue / getRegister( destination[1] ));
                setRegister( destination[1] , newVal);
            }
            case DIRECT_MODE -> {
                newVal = (short) ( operandValue / memoryController.getMemory( destination[1] ) );
                memoryController.setMemory( destination[1], newVal );
            }
            case INDIRECT_MODE -> {
                newVal = (short) ( operandValue / memoryController.getMemory( getRegister( destination[1] ) ) );
                memoryController.setMemory( getRegister( destination[1] ), newVal );
            }
        }
        byte flagSetter = (byte) newVal;
        if (flagSetter == 0) Z = true;
        if (flagSetter < 0) N = true;

    }


    public void not(short[] source){
        short newVal = 0;
        switch (source[0]){
            case REGISTER_MODE ->{
                setRegister(source[1], (short) ~getRegister(source[1])); newVal = getRegister(source[1]);
            }
            case DIRECT_MODE -> {
                memoryController.setMemory(source[1], (short) ~memoryController.getMemory(source[1])); newVal = memoryController.getMemory(source[1]);
            }
            case INDIRECT_MODE ->{
                memoryController.setMemory( memoryController.getMemory(getRegister( source[1] )), (short) ~memoryController.getMemory( getRegister( source[1] )));
                newVal = memoryController.getMemory( getRegister(source[1]) );
            }
            default -> E = true;
        }

        byte flagSetter = (byte) newVal;
        if (flagSetter < 0) N = true;
        if (flagSetter == 0) Z = true;
    }


    public void and(short[] destination, short[] source){

        short operandValue = switch (source[0]){
            case REGISTER_MODE -> getRegister(source[1]);
            case DIRECT_MODE -> memoryController.getMemory(source[1]);
            case INDIRECT_MODE -> memoryController.getMemory(getRegister(source[1]));
            case IMMEDIATE_MODE -> source[1];
            default -> 256;
        };
        if (operandValue == 256) E = true;

        short newVal = 0;
        switch (destination[0]){
            case REGISTER_MODE -> {
                newVal = (short) (operandValue & getRegister(destination[1]));
                setRegister(destination[1], newVal);
            }
            case DIRECT_MODE -> {
                newVal = (short) (operandValue & memoryController.getMemory(destination[1]));
                memoryController.setMemory(destination[1], newVal);
            }
            case INDIRECT_MODE -> {
                newVal = (short) (operandValue & memoryController.getMemory( getRegister(destination[1]) ));
                memoryController.setMemory( getRegister(destination[1]), newVal );
            }
            default -> E = true;
        }
        byte flagSetter = (byte) newVal;
        if (flagSetter < 0) N = true;
        if (flagSetter == 0) Z = true;
    }


    public void or(short[] destination, short[] source){

        short operandValue = switch (source[0]){
            case REGISTER_MODE -> getRegister(source[1]);
            case DIRECT_MODE -> memoryController.getMemory(source[1]);
            case INDIRECT_MODE -> memoryController.getMemory(getRegister(source[1]));
            case IMMEDIATE_MODE -> source[1];
            default -> 256;
        };
        if (operandValue == 256) E = true;

        short newVal = 0;
        switch (destination[0]){
            case REGISTER_MODE -> {
                newVal = (short) (operandValue | getRegister(destination[1]));
                setRegister(destination[1], newVal);
            }
            case DIRECT_MODE -> {
                newVal = (short) (operandValue | memoryController.getMemory(destination[1]));
                memoryController.setMemory(destination[1], newVal);
            }
            case INDIRECT_MODE -> {
                newVal = (short) (operandValue | memoryController.getMemory( getRegister(destination[1]) ));
                memoryController.setMemory( getRegister(destination[1]), newVal );
            }
            default -> E = true;
        }
        byte flagSetter = (byte) newVal;
        if (flagSetter < 0) N = true;
        if (flagSetter == 0) Z = true;
    }


    public void xor(short[] destination, short[] source){

        short operandValue = switch (source[0]){
            case REGISTER_MODE -> getRegister(source[1]);
            case DIRECT_MODE -> memoryController.getMemory(source[1]);
            case INDIRECT_MODE -> memoryController.getMemory(getRegister(source[1]));
            case IMMEDIATE_MODE -> source[1];
            default -> 256;
        };
        if (operandValue == 256) E = true;

        short newVal = 0;
        switch (destination[0]){
            case REGISTER_MODE -> {
                newVal = (short) (operandValue ^ getRegister(destination[1]));
                setRegister(destination[1], newVal);
            }
            case DIRECT_MODE -> {
                newVal = (short) (operandValue ^ memoryController.getMemory(destination[1]));
                memoryController.setMemory(destination[1], newVal);
            }
            case INDIRECT_MODE -> {
                newVal = (short) (operandValue ^ memoryController.getMemory( getRegister(destination[1]) ));
                memoryController.setMemory( getRegister(destination[1]), newVal );
            }
            default -> E = true;
        }
        byte flagSetter = (byte) newVal;
        if (flagSetter < 0) N = true;
        if (flagSetter == 0) Z = true;
    }


    public void nand(short[] destination, short[] source){

        short operandValue = switch (source[0]){
            case REGISTER_MODE -> getRegister(source[1]);
            case DIRECT_MODE -> memoryController.getMemory(source[1]);
            case INDIRECT_MODE -> memoryController.getMemory(getRegister(source[1]));
            case IMMEDIATE_MODE -> source[1];
            default -> 256;
        };
        if (operandValue == 256) E = true;

        short newVal = 0;
        switch (destination[0]){
            case REGISTER_MODE -> {
                newVal = (short) ~(operandValue & getRegister(destination[1]));
                setRegister(destination[1], newVal);
            }
            case DIRECT_MODE -> {
                newVal = (short) ~(operandValue & memoryController.getMemory(destination[1]));
                memoryController.setMemory(destination[1], newVal);
            }
            case INDIRECT_MODE -> {
                newVal = (short) ~(operandValue & memoryController.getMemory( getRegister(destination[1]) ));
                memoryController.setMemory( getRegister(destination[1]), newVal );
            }
            default -> E = true;
        }
        byte flagSetter = (byte) newVal;
        if (flagSetter < 0) N = true;
        if (flagSetter == 0) Z = true;
    }


    public void nor(short[] destination, short[] source){

        short operandValue = switch (source[0]){
            case REGISTER_MODE -> getRegister(source[1]);
            case DIRECT_MODE -> memoryController.getMemory(source[1]);
            case INDIRECT_MODE -> memoryController.getMemory(getRegister(source[1]));
            case IMMEDIATE_MODE -> source[1];
            default -> 256;
        };
        if (operandValue == 256) E = true;

        short newVal = 0;
        switch (destination[0]){
            case REGISTER_MODE -> {
                newVal = (short) ~(operandValue | getRegister(destination[1]));
                setRegister(destination[1], newVal);
            }
            case DIRECT_MODE -> {
                newVal = (short) ~(operandValue | memoryController.getMemory(destination[1]));
                memoryController.setMemory(destination[1], newVal);
            }
            case INDIRECT_MODE -> {
                newVal = (short) ~(operandValue | memoryController.getMemory( getRegister(destination[1]) ));
                memoryController.setMemory( getRegister(destination[1]), newVal );
            }
            default -> E = true;
        }
        byte flagSetter = (byte) newVal;
        if (flagSetter < 0) N = true;
        if (flagSetter == 0) Z = true;
    }


    public void pow(short[] destination, short[] source){
        short power = switch(source[0]){
            case REGISTER_MODE -> getRegister( source[1] );
            case DIRECT_MODE -> memoryController.getMemory( source[1] );
            case INDIRECT_MODE ->  memoryController.getMemory( getRegister( source[1] ) );
            case IMMEDIATE_MODE -> source[1];

            default -> 256;
        };
        if (power == 256) triggerProgramError(
                "Invalid instruction prefix", ErrorHandler.ERR_CODE_INVALID_PREFIX);

        short newValue = 0;
        switch (destination[0]){
            case REGISTER_MODE ->{
                newValue = (short) Math.pow( getRegister(destination[1]), power );
                setRegister( destination[1], newValue );
            }
            case DIRECT_MODE ->{
                newValue = (short) Math.pow( memoryController.getMemory( destination[1] ), power );
                memoryController.setMemory( destination[1], newValue );
            }
            case INDIRECT_MODE ->{
                newValue = (short) Math.pow( memoryController.getMemory( getRegister(destination[1]) ), power );
                memoryController.setMemory( getRegister( destination[1] ), newValue );
            }
        }
        byte flagSetter = (byte) newValue;
        if (flagSetter == 0) Z = true;
        if (flagSetter < 0) N = true;
    }


    public void sqrt(short[] destination){

        short newValue = 0;
        switch (destination[0]){
            case REGISTER_MODE ->{
                newValue = (short) Math.sqrt( getRegister(destination[1]) );
                setRegister( destination[1], newValue );
            }
            case DIRECT_MODE ->{
                newValue = (short) Math.sqrt( memoryController.getMemory(destination[1]) );
                memoryController.setMemory( destination[1], newValue );
            }
            case INDIRECT_MODE ->{
                newValue = (short) Math.sqrt( memoryController.getMemory( getRegister( destination[1] ) ) );
                memoryController.setMemory( getRegister( destination[1] ), newValue );
            }
            default ->{
                String err = "Invalid instruction error.";
                triggerProgramError(
                        err, ErrorHandler.ERR_CODE_INVALID_PREFIX);
            }
        }
        byte flagSetter = (byte) newValue;
        if (flagSetter == 0) Z = true;
        if (flagSetter < 0) N = true;
    }



    public void rnd(short[] destination, short[] source){
        short bound = switch (source[0]){
            case REGISTER_MODE -> getRegister(source[1]);
            case DIRECT_MODE -> memoryController.getMemory(source[1]);
            case INDIRECT_MODE -> memoryController.getMemory( getRegister(source[1]) );
            case IMMEDIATE_MODE -> source[1];
            default -> 256;
        };

        if (bound == 256) triggerProgramError(
                "Invalid instruction prefix", ErrorHandler.ERR_CODE_INVALID_PREFIX);

        short newVal = (short) ( Math.random() * bound );
        switch (destination[0]){
            case REGISTER_MODE -> setRegister( destination[1],  newVal);
            case DIRECT_MODE -> memoryController.setMemory(destination[1], newVal);
            case INDIRECT_MODE -> memoryController.setMemory( getRegister(destination[1]), newVal );

            default -> triggerProgramError(
                    "Invalid instruction prefix", ErrorHandler.ERR_CODE_INVALID_PREFIX);
        }
    }


    public void inc(short[] destination){

        switch (destination[0]){
            case REGISTER_MODE -> setRegister(destination[1], (short) (getRegister(destination[1]) + 1));
            case DIRECT_MODE -> memoryController.setMemory(destination[1], (short) (memoryController.getMemory(destination[1]) + 1));
            case INDIRECT_MODE -> memoryController.setMemory(memoryController.getMemory( getRegister(destination[1]) ),
                    (short) ( memoryController.getMemory( getRegister(destination[1]) ) + 1));
        }
        byte flagSetter = (byte) destination[1];
        if (flagSetter == 0) Z = true;
        if (flagSetter < 0) N = true;
    }


    public void dec(short[] destination){
        switch (destination[0]){
            case REGISTER_MODE -> setRegister(destination[1], (short) (getRegister(destination[1]) - 1));
            case DIRECT_MODE -> memoryController.setMemory(destination[1], (short) (memoryController.getMemory(destination[1]) - 1));
            case INDIRECT_MODE -> memoryController.setMemory(memoryController.getMemory( getRegister(destination[1]) ),
                    (short) ( memoryController.getMemory( getRegister(destination[1]) ) - 1));
        }
        byte flagSetter = (byte) destination[1];
        if (flagSetter == 0) Z = true;
        if (flagSetter < 0) N = true;
    }


    public void la(short[] source){
        int low = machineCode[registers[PC]];
        int high = machineCode[step()];
        short address = (short) ((low << 8) | high);
        switch (source[0]){
            case REGISTER_MODE -> setRegister( source[1], address);
            case DIRECT_MODE -> memoryController.setMemory( source[1], address );
            case INDIRECT_MODE -> memoryController.setMemory( getRegister(source[1]) , address );
        }
    }


    public void push(short[] source){
        switch (source[0]){
            case REGISTER_MODE -> memoryController.setMemoryAbsolute(registers[SP], getRegister(source[1]), DATA_BYTE_MODE);
            case DIRECT_MODE -> memoryController.setMemoryAbsolute(registers[SP], memoryController.readByte(source[1]), DATA_BYTE_MODE);
            case INDIRECT_MODE -> memoryController.setMemoryAbsolute(registers[SP], memoryController.readByte( getRegister(source[1]) ), DATA_BYTE_MODE);
            case IMMEDIATE_MODE -> memoryController.setMemoryAbsolute(registers[SP], source[1], DATA_BYTE_MODE);
        }
        registers[SP]--;
    }


    public void pop(short[] source){
        registers[SP]++;
        switch (source[0]){
            case REGISTER_MODE -> setRegister( source[1], (short) memoryController.readByteAbsolute(registers[SP]) );
            case DIRECT_MODE -> memoryController.setMemory( source[1], memoryController.readByteAbsolute(registers[SP]) );
            case INDIRECT_MODE -> memoryController.setMemory( getRegister(source[1]), memoryController.readByteAbsolute(registers[SP]) );
            default -> E = true;
        }
    }


    public void call(int address, int return_address){
        //System.out.println("Pushing address : 0x" + Integer.toHexString(return_address));
        functionCallStack.push(return_address); // save the return address
        //System.out.println(functionCallStack);
        registers[PC] = (short) (address - 1); // sub 1 to nullify the step()
    }


    public void jmp(){
        int low = machineCode[registers[PC]];
        int high = machineCode[step()];
        int address = (low << 8) | high;
        //System.out.printf("Jumping to address : %04X(%d)\n", address - 1, address - 1);
        registers[PC] = (short) (address - 1);
    }


    public void cmp(short[] destination, short[] source){
        short val1 = switch (destination[0]){
            case REGISTER_MODE -> getRegister(destination[1]);
            case DIRECT_MODE -> memoryController.getMemory( destination[1] );
            case INDIRECT_MODE -> memoryController.getMemory( getRegister(destination[1]) );
            case IMMEDIATE_MODE -> destination[1];
            default -> 256;
        };
        short val2 = switch (source[0]){
            case REGISTER_MODE -> getRegister(source[1]);
            case DIRECT_MODE -> memoryController.getMemory( source[1] );
            case INDIRECT_MODE -> memoryController.getMemory( getRegister(source[1]) );
            case IMMEDIATE_MODE -> source[1];
            default -> 256;
        };

        Z = val1 == val2; // true if equal false if not
        N = val1 < val2; // true if less false if not
    }


    @Override
    public int[] toMachineCode(String instruction){
        String[] commentedTokens = instruction.trim().split(COMMENT_PREFIX);
        String[] tokens = commentedTokens[0].trim().split(" ");

        
        // Instruction format: opcode (1 byte) optional: operand1 (2 bytes) optional: operand2 (2 bytes)
        // Output machine code: opcode operand1_addressing_mode operand1_value operand2_addressing_mode operand2_value
        int length = 1; // 1 byte for opcode
        for(int i = 1; i < tokens.length; i++){
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
            triggerProgramError(err, status_code);
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
                    case DATA_PREFIX, DATA_PREFIX_ALT -> 0x4;
                    case STRING_PREFIX -> 0x5;
                    default -> 0x6;
                };


                if (result[i] == 0x4){
                    Integer dataPointer = dataMap.get(tokens[tokenIndex].substring(1));

                    if (dataPointer == null){
                        String err = String.format("The variable '%s' doesn't exist in the data section.\n",
                                tokens[tokenIndex].substring(1));
                        status_code = ErrorHandler.ERR_COMP_NULL_DATA_POINTER;
                        triggerProgramError(
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
                        triggerProgramError(
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
                        triggerProgramError(err, status_code);
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
    public int getInstructionLength(String instruction){
        String[] commentedTokens = instruction.trim().split(COMMENT_PREFIX);
        String[] tokens = commentedTokens[0].trim().split(" ");


        // Instruction format: opcode (1 byte) optional: operand1 (2 bytes) optional: operand2 (2 bytes)
        // NOTE: if the instruction has an address, then operand size will be 3 bytes (1 byte for mode, 2 bytes for address)
        // Output machine code: opcode operand1_addressing_mode operand1_value operand2_addressing_mode operand2_value
        int length = 1; // 1 byte for opcode
        for (int i = 1; i < tokens.length; i++) {
            //length += 2; // 2 bytes for all remaining operands
            switch (tokens[i].charAt(0)) {
                case REGISTER_PREFIX, DIRECT_MEMORY_PREFIX, INDIRECT_MEMORY_PREFIX, IMMEDIATE_PREFIX -> length += 2;
                default -> length += 3;
            }
        }
        return length;
    }

    public short getRegister(int registerID){
        return registers[registerID];
    }
    public int getRegisterCode(String registerName){
        for(int i = 0; i < registerNames.length; i++){
            if (registerNames[i].equalsIgnoreCase(registerName)) return i;
        }
        return -1;
    }




    public void setRegister(int registerID, short value){
        if (registerID < registers.length){
            if (registerID == PC && Launcher.appConfig.get("OverwritePC").equals("false")){
                 String err = "Direct modification of PC register is not allowed." +
                            " if you wish to proceed, change that in the settings.";
                    triggerProgramError(
                            err, ErrorHandler.ERR_CODE_PC_MODIFY_UNALLOWED);
            }
            // Special purpose registers are 16-bits whereas general purpose registers are 8-bits
            else if (registerID >= PC) registers[registerID] = value;
            else if (value > max_value){
                String err = String.format("The value %X(%d) exceeds the %d-bit CPU module size.", value, value, bit_length);
                triggerProgramError(
                        err, ErrorHandler.ERR_CODE_CPU_SIZE_VIOLATION);
            }
            else registers[registerID] = value;
        }
    }


    @Override
    public String dumpRegisters(){
        int registersPerLine = 3;
        StringBuilder result = new StringBuilder();
        for(int i = 0; i < registers.length; i++){

            if ( i % registersPerLine == 0) result.append("\n");
            //result.append(registerNames[i].toUpperCase()).append(": ").append(String.format("0x%04X", registers[i])).append("\t");
            result.append( String.format("%-16s",
                    String.format("%s: 0x%04X", registerNames[i].toUpperCase(), registers[i])) );
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


    @Override
    public void setUIupdateListener(onStepListener listener){
        this.stepListener = listener;
    }
}