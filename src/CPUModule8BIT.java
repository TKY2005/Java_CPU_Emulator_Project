import java.util.*;

public class CPUModule8BIT extends CPU {

    // CPU specific Variables //
    public static final int max_value = 255;
    public static final int min_value = -127;

    public int currentLine = 1;
    /// ///////////////////////////////////////

    // Memory related Variables //
    public int data_start;
    public int stack_start;
    public int last_addressable_location;

    public int mem_size_B;

    public short[] registers;
    public String[] registerNames;
    public short[] memory;
    /// ///////////////////////////////////////
    ///
    ///
    /// Listeners /////////////////////////////
    private onStepListener stepListener;




    int memorySize = Integer.parseInt(Settings.loadSettings().get("MemSize"));
    int offsetSize = Integer.parseInt(Settings.loadSettings().get("OffsetSize"));
    int stackSize = Integer.parseInt(Settings.loadSettings().get("StackSize"));


    public CPUModule8BIT() {
        super();

        System.out.println("Starting 8 bit cpu module.");
        mem_size_B = memorySize * 1024;
        stack_start = mem_size_B - (stackSize * 1024);
        data_start = stack_start - (offsetSize * 1024);
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
                offsetSize, data_start, data_start,
                stackSize, stack_start, stack_start,
                last_addressable_location, last_addressable_location));

        System.out.printf("CPU speed set to %s Cycles per second. With a step delay of %sMS\n",
                Launcher.appConfig.get("Cycles"), delayAmountMilliseconds);

        reset();
    }


    @Override
    public void reset(){

        // 6 General purpose registers + 6 Special purpose registers
        registers = new short[REGISTER_COUNT + 6];
        registerNames = new String[registers.length];
        bit_length = 8;
        memory = new short[mem_size_B];
        currentLine = 1;
        currentByte = 0;
        status_code = 0;

        functionCallStack = new Stack<>();
        dataMap = new HashMap<>();
        functions = new HashMap<>();


        outputString = new StringBuilder();
        machineCode = new int[] {0};
        data_start = stack_start - (offsetSize * 1024);


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

        registers[SP] = (short) (stack_start + memory.length - stack_start - 1);
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
                        short[] destination = getNextOperand();
                        short[] source = getNextOperand();
                        set(destination, source);
                    }
                    case INS_OUT -> {
                        short[] destination = getNextOperand();
                        out(destination);
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
                        }
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
                step();
            }

        }

        outputString.append("Program terminated with code : ").append(status_code);
    }

    public int step() {
        registers[PC]++;
        if (stepListener != null) stepListener.updateUI();
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

    @Override
    public void set(short[] destination, short[] value){
        short operandValue;
        operandValue = switch (value[0]){
            case REGISTER_MODE -> getRegister( value[1] );
            case DIRECT_MODE -> getMemory( value[1] );
            case INDIRECT_MODE -> getMemory( getRegister( value[1] ) );
            case IMMEDIATE_MODE -> value[1];

            default -> 256;
        };
        if (operandValue == 256) triggerProgramError(new ErrorHandler.InvalidInstructionException("Invalid instruction prefix"),
                "Invalid instruction prefix", ErrorHandler.ERR_CODE_INVALID_PREFIX);

        switch (destination[0]){
            case REGISTER_MODE -> setRegister( destination[1], operandValue );
            case DIRECT_MODE -> setMemory( destination[1], operandValue );
            case INDIRECT_MODE -> setMemory( getRegister( destination[1] ), operandValue );

            default -> triggerProgramError(new ErrorHandler.InvalidInstructionException("Invalid instruction prefix"),
                "Invalid instruction prefix", ErrorHandler.ERR_CODE_INVALID_PREFIX);
        }

    }

    @Override
    public void out(short[] destination){
        switch (destination[0]){
            case REGISTER_MODE -> outputString.append( registers[ destination[1] ] ).append("\n");
            case DIRECT_MODE -> outputString.append( memory[ destination[1] ] ).append("\n");
            case INDIRECT_MODE -> outputString.append( memory[ registers[ destination[1] ] ]  ).append("\n");
            case IMMEDIATE_MODE -> outputString.append( destination[1] ).append("\n");

            default -> E = true;
        }
    }

    @Override
    public void add(short[] destination, short[] source){

        short operandValue = switch ( source[0] ){

            case REGISTER_MODE -> getRegister( source[1] );
            case DIRECT_MODE -> getMemory( source[1] );
            case INDIRECT_MODE -> getMemory( getRegister( source[1] ) );
            case IMMEDIATE_MODE -> source[1];

            default -> 256;
        };
        if (operandValue == 256) triggerProgramError(new ErrorHandler.InvalidInstructionException("Invalid instruction prefix"),
                "Invalid instruction prefix", ErrorHandler.ERR_CODE_INVALID_PREFIX);

        short newVal = 0;
        switch (destination[0]){
            case REGISTER_MODE -> {
                newVal = (short) (operandValue + getRegister( destination[1] ));
                setRegister( destination[1] , newVal);
            }
            case DIRECT_MODE -> {
                newVal = (short) ( operandValue + getMemory( destination[1] ) );
                setMemory( destination[1], newVal );
            }
            case INDIRECT_MODE -> {
                newVal = (short) ( operandValue + getMemory( getRegister( destination[1] ) ) );
                setMemory( getRegister( destination[1] ), newVal );
            }
        }
        updateFlags(newVal);

    }

    @Override
    public void sub(short[] destination, short[] source){

        short operandValue = switch ( source[0] ){

            case REGISTER_MODE -> getRegister( source[1] );
            case DIRECT_MODE -> getMemory( source[1] );
            case INDIRECT_MODE -> getMemory( getRegister( source[1] ) );
            case IMMEDIATE_MODE -> source[1];

            default -> 256;
        };
        if (operandValue == 256) triggerProgramError(new ErrorHandler.InvalidInstructionException("Invalid instruction prefix"),
                "Invalid instruction prefix", ErrorHandler.ERR_CODE_INVALID_PREFIX);

        short newVal = 0;
        switch (destination[0]){
            case REGISTER_MODE -> {
                newVal = (short) ( getRegister( destination[1] ) - operandValue );
                setRegister( destination[1] , newVal);
            }
            case DIRECT_MODE -> {
                newVal = (short) ( getMemory( destination[1] ) - operandValue);
                setMemory( destination[1], newVal );
            }
            case INDIRECT_MODE -> {
                newVal = (short) ( getMemory( getRegister( destination[1] ) ) - operandValue );
                setMemory( getRegister( destination[1] ), newVal );
            }
        }
        byte flagSetter = (byte) newVal;
        if (flagSetter == 0) Z = true;
        if (flagSetter < 0) N = true;

    }

    @Override
    public void mul(short[] destination, short[] source){

        short operandValue = switch ( source[0] ){

            case REGISTER_MODE -> getRegister( source[1] );
            case DIRECT_MODE -> getMemory( source[1] );
            case INDIRECT_MODE -> getMemory( getRegister( source[1] ) );
            case IMMEDIATE_MODE -> source[1];

            default -> 256;
        };
        if (operandValue == 256) triggerProgramError(new ErrorHandler.InvalidInstructionException("Invalid instruction prefix"),
                "Invalid instruction prefix", ErrorHandler.ERR_CODE_INVALID_PREFIX);

        short newVal = 0;
        switch (destination[0]){
            case REGISTER_MODE -> {
                newVal = (short) (operandValue * getRegister( destination[1] ));
                setRegister( destination[1] , newVal);
            }
            case DIRECT_MODE -> {
                newVal = (short) ( operandValue * getMemory( destination[1] ) );
                setMemory( destination[1], newVal );
            }
            case INDIRECT_MODE -> {
                newVal = (short) ( operandValue * getMemory( getRegister( destination[1] ) ) );
                setMemory( getRegister( destination[1] ), newVal );
            }
        }
        byte flagSetter = (byte) newVal;
        if (flagSetter == 0) Z = true;
        if (flagSetter < 0) N = true;

    }
    
    @Override
    public void div(short[] destination, short[] source){

        short operandValue = switch ( source[0] ){

            case REGISTER_MODE -> getRegister( source[1] );
            case DIRECT_MODE -> getMemory( source[1] );
            case INDIRECT_MODE -> getMemory( getRegister( source[1] ) );
            case IMMEDIATE_MODE -> source[1];

            default -> 256;
        };
        if (operandValue == 256) triggerProgramError(new ErrorHandler.InvalidInstructionException("Invalid instruction prefix"),
                "Invalid instruction prefix", ErrorHandler.ERR_CODE_INVALID_PREFIX);

        short newVal = 0;
        switch (destination[0]){
            case REGISTER_MODE -> {
                newVal = (short) (operandValue / getRegister( destination[1] ));
                setRegister( destination[1] , newVal);
            }
            case DIRECT_MODE -> {
                newVal = (short) ( operandValue / getMemory( destination[1] ) );
                setMemory( destination[1], newVal );
            }
            case INDIRECT_MODE -> {
                newVal = (short) ( operandValue / getMemory( getRegister( destination[1] ) ) );
                setMemory( getRegister( destination[1] ), newVal );
            }
        }
        byte flagSetter = (byte) newVal;
        if (flagSetter == 0) Z = true;
        if (flagSetter < 0) N = true;

    }

    @Override
    public void not(short[] source){
        short newVal = 0;
        switch (source[0]){
            case REGISTER_MODE ->{
                setRegister(source[1], (short) ~getRegister(source[1])); newVal = getRegister(source[1]);
            }
            case DIRECT_MODE -> {
                setMemory(source[1], (short) ~getMemory(source[1])); newVal = getMemory(source[1]);
            }
            case INDIRECT_MODE ->{
                setMemory( getMemory(getRegister( source[1] )), (short) ~getMemory( getRegister( source[1] )));
                newVal = getMemory( getRegister(source[1]) );
            }
            default -> E = true;
        }

        byte flagSetter = (byte) newVal;
        if (flagSetter < 0) N = true;
        if (flagSetter == 0) Z = true;
    }

    @Override
    public void and(short[] destination, short[] source){

        short operandValue = switch (source[0]){
            case REGISTER_MODE -> getRegister(source[1]);
            case DIRECT_MODE -> getMemory(source[1]);
            case INDIRECT_MODE -> getMemory(getRegister(source[1]));
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
                newVal = (short) (operandValue & getMemory(destination[1]));
                setMemory(destination[1], newVal);
            }
            case INDIRECT_MODE -> {
                newVal = (short) (operandValue & getMemory( getRegister(destination[1]) ));
                setMemory( getRegister(destination[1]), newVal );
            }
            default -> E = true;
        }
        byte flagSetter = (byte) newVal;
        if (flagSetter < 0) N = true;
        if (flagSetter == 0) Z = true;
    }

    @Override
    public void or(short[] destination, short[] source){

        short operandValue = switch (source[0]){
            case REGISTER_MODE -> getRegister(source[1]);
            case DIRECT_MODE -> getMemory(source[1]);
            case INDIRECT_MODE -> getMemory(getRegister(source[1]));
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
                newVal = (short) (operandValue | getMemory(destination[1]));
                setMemory(destination[1], newVal);
            }
            case INDIRECT_MODE -> {
                newVal = (short) (operandValue | getMemory( getRegister(destination[1]) ));
                setMemory( getRegister(destination[1]), newVal );
            }
            default -> E = true;
        }
        byte flagSetter = (byte) newVal;
        if (flagSetter < 0) N = true;
        if (flagSetter == 0) Z = true;
    }

    @Override
    public void xor(short[] destination, short[] source){

        short operandValue = switch (source[0]){
            case REGISTER_MODE -> getRegister(source[1]);
            case DIRECT_MODE -> getMemory(source[1]);
            case INDIRECT_MODE -> getMemory(getRegister(source[1]));
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
                newVal = (short) (operandValue ^ getMemory(destination[1]));
                setMemory(destination[1], newVal);
            }
            case INDIRECT_MODE -> {
                newVal = (short) (operandValue ^ getMemory( getRegister(destination[1]) ));
                setMemory( getRegister(destination[1]), newVal );
            }
            default -> E = true;
        }
        byte flagSetter = (byte) newVal;
        if (flagSetter < 0) N = true;
        if (flagSetter == 0) Z = true;
    }

    @Override
    public void nand(short[] destination, short[] source){

        short operandValue = switch (source[0]){
            case REGISTER_MODE -> getRegister(source[1]);
            case DIRECT_MODE -> getMemory(source[1]);
            case INDIRECT_MODE -> getMemory(getRegister(source[1]));
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
                newVal = (short) ~(operandValue & getMemory(destination[1]));
                setMemory(destination[1], newVal);
            }
            case INDIRECT_MODE -> {
                newVal = (short) ~(operandValue & getMemory( getRegister(destination[1]) ));
                setMemory( getRegister(destination[1]), newVal );
            }
            default -> E = true;
        }
        byte flagSetter = (byte) newVal;
        if (flagSetter < 0) N = true;
        if (flagSetter == 0) Z = true;
    }

    @Override
    public void nor(short[] destination, short[] source){

        short operandValue = switch (source[0]){
            case REGISTER_MODE -> getRegister(source[1]);
            case DIRECT_MODE -> getMemory(source[1]);
            case INDIRECT_MODE -> getMemory(getRegister(source[1]));
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
                newVal = (short) ~(operandValue | getMemory(destination[1]));
                setMemory(destination[1], newVal);
            }
            case INDIRECT_MODE -> {
                newVal = (short) ~(operandValue | getMemory( getRegister(destination[1]) ));
                setMemory( getRegister(destination[1]), newVal );
            }
            default -> E = true;
        }
        byte flagSetter = (byte) newVal;
        if (flagSetter < 0) N = true;
        if (flagSetter == 0) Z = true;
    }

    @Override
    public void pow(short[] destination, short[] source){
        short power = switch(source[0]){
            case REGISTER_MODE -> getRegister( source[1] );
            case DIRECT_MODE -> getMemory( source[1] );
            case INDIRECT_MODE ->  getMemory( getRegister( source[1] ) );
            case IMMEDIATE_MODE -> source[1];

            default -> 256;
        };
        if (power == 256) triggerProgramError(new ErrorHandler.InvalidInstructionException("Invalid instruction prefix"),
                "Invalid instruction prefix", ErrorHandler.ERR_CODE_INVALID_PREFIX);

        System.out.println("power : " + power);
        System.out.println(destination[1] + "^" + power);
        short newValue = (short) Math.pow( destination[1], power );
        System.out.println(newValue);
        switch (destination[0]){
            case REGISTER_MODE -> setRegister( destination[1], newValue );
            case DIRECT_MODE -> setMemory( destination[1], newValue );
            case INDIRECT_MODE -> setMemory( getRegister( destination[1] ), newValue );
        }
        byte flagSetter = (byte) newValue;
        if (flagSetter == 0) Z = true;
        if (flagSetter < 0) N = true;
    }

    @Override
    public void sqrt(short[] destination){

        short newValue = (short) Math.sqrt( destination[1] );
        switch (destination[0]){
            case REGISTER_MODE -> setRegister( destination[1], newValue );
            case DIRECT_MODE -> setMemory( destination[1], newValue );
            case INDIRECT_MODE -> setMemory( getRegister( destination[1] ), newValue );
            default ->{
                String err = "Invalid instruction error.";
                triggerProgramError(new ErrorHandler.InvalidInstructionException(err),
                        err, ErrorHandler.ERR_CODE_INVALID_PREFIX);
            }
        }
        byte flagSetter = (byte) newValue;
        if (flagSetter == 0) Z = true;
        if (flagSetter < 0) N = true;
    }


    @Override
    public void rnd(short[] destination, short[] source){
        short bound = switch (source[0]){
            case REGISTER_MODE -> getRegister(source[1]);
            case DIRECT_MODE -> getMemory(source[1]);
            case INDIRECT_MODE -> getMemory( getRegister(source[1]) );
            case IMMEDIATE_MODE -> source[1];
            default -> 256;
        };

        if (bound == 256) triggerProgramError(new ErrorHandler.InvalidInstructionException("Invalid instruction prefix"),
                "Invalid instruction prefix", ErrorHandler.ERR_CODE_INVALID_PREFIX);

        short newVal = (short) ( Math.random() * bound );
        switch (destination[0]){
            case REGISTER_MODE -> setRegister( destination[1],  newVal);
            case DIRECT_MODE -> setMemory(destination[1], newVal);
            case INDIRECT_MODE -> setMemory( getRegister(destination[1]), newVal );

            default -> triggerProgramError(new ErrorHandler.InvalidInstructionException("Invalid instruction prefix"),
                "Invalid instruction prefix", ErrorHandler.ERR_CODE_INVALID_PREFIX);
        }
    }

    @Override
    public void inc(short[] destination){

        switch (destination[0]){
            case REGISTER_MODE -> setRegister(destination[1], (short) (getRegister(destination[1]) + 1));
            case DIRECT_MODE -> setMemory(destination[1], (short) (getMemory(destination[1]) + 1));
            case INDIRECT_MODE -> setMemory(getMemory( getRegister(destination[1]) ),
                    (short) ( getMemory( getRegister(destination[1]) ) + 1));
        }
        byte flagSetter = (byte) destination[1];
        if (flagSetter == 0) Z = true;
        if (flagSetter < 0) N = true;
    }

    @Override
    public void dec(short[] destination){
        switch (destination[0]){
            case REGISTER_MODE -> setRegister(destination[1], (short) (getRegister(destination[1]) - 1));
            case DIRECT_MODE -> setMemory(destination[1], (short) (getMemory(destination[1]) - 1));
            case INDIRECT_MODE -> setMemory(getMemory( getRegister(destination[1]) ),
                    (short) ( getMemory( getRegister(destination[1]) ) - 1));
        }
        byte flagSetter = (byte) destination[1];
        if (flagSetter == 0) Z = true;
        if (flagSetter < 0) N = true;
    }

    @Override
    public void la(short[] source){
        short address = (short) machineCode[ registers[PC] ];
        switch (source[0]){
            case REGISTER_MODE -> setRegister( source[1], address);
            case DIRECT_MODE -> setMemory( source[1], address );
            case INDIRECT_MODE -> setMemory( getRegister(source[1]) , address );
        }
    }

    @Override
    public void push(short[] source){
        switch (source[0]){
            case REGISTER_MODE -> memory[ registers[SP] ] = getRegister(source[1]);
            case DIRECT_MODE -> memory[registers[SP]] = getMemory(source[1]);
            case INDIRECT_MODE -> memory[registers[SP]] = getMemory( getMemory( getRegister( source[1] ) ) );
            case IMMEDIATE_MODE -> memory[registers[SP]] = source[1];
        }
        registers[SP]--;
    }

    @Override
    public void pop(short[] source){
        registers[SP]++;
        switch (source[0]){
            case REGISTER_MODE -> setRegister( source[1], memory[ registers[SP] ] );
            case DIRECT_MODE -> setMemory( source[1], memory[registers[SP]] );
            case INDIRECT_MODE -> setMemory( getRegister(source[1]), memory[registers[SP]] );
            default -> E = true;
        }
        memory[ registers[SP] ] = 0;
    }

    @Override
    public void call(int address, int return_address){
        System.out.println("Pushing address : 0x" + Integer.toHexString(return_address));
        functionCallStack.push(return_address); // save the return address
        System.out.println(functionCallStack);
        registers[PC] = (short) (address - 1); // sub 1 to nullify the step()
    }

    @Override
    public void jmp(){
        registers[PC] = (short) (machineCode[registers[PC]] - 1);
    }

    @Override
    public void cmp(short[] destination, short[] source){
        short val1 = switch (destination[0]){
            case REGISTER_MODE -> getRegister(destination[1]);
            case DIRECT_MODE -> getMemory( destination[1] );
            case INDIRECT_MODE -> getMemory( getRegister(destination[1]) );
            case IMMEDIATE_MODE -> destination[1];
            default -> 256;
        };
        short val2 = switch (source[0]){
            case REGISTER_MODE -> getRegister(source[1]);
            case DIRECT_MODE -> getMemory( source[1] );
            case INDIRECT_MODE -> getMemory( getRegister(source[1]) );
            case IMMEDIATE_MODE -> source[1];
            default -> 256;
        };

        Z = val1 == val2; // true if equal false if not
        N = val1 < val2; // true if less false if not
    }


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
        for(int i = 0; i < result.length; i++) System.out.printf("0x%X ", result[i]);
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

    public short getRegister(int registerID){
        return registers[registerID];
    }
    public int getRegisterCode(String registerName){
        for(int i = 0; i < registerNames.length; i++){
            if (registerNames[i].equals(registerName)) return i;
        }
        return -1;
    }

    public short getMemory(int address){
        if (isValidAddress(address)){
            return memory[address];
        }else return 256;
    }


    public void setMemory(int address, short value){
        if (!isValidAddress(address)){
            String err = String.format("Invalid memory access at location %X(%d)\n", address, address);
            triggerProgramError(new ErrorHandler.InvalidMemoryOperationException(err),
                    err, ErrorHandler.ERR_CODE_INVALID_MEMORY_ADDRESS);

        }else{
            if (value > max_value || value < min_value){
                String err = String.format("Value %X(%d) exceeds the %d-bit limit for this CPU module.\n",
                value, value, bit_length);
                triggerProgramError(new ErrorHandler.InvalidMemoryOperationException(err),
                        err, ErrorHandler.ERR_CODE_CPU_SIZE_VIOLATION);
            }else memory[address] = value;
        }
    }



    public void setRegister(int registerID, short value){
        if (registerID < registers.length){
            if (registerID == PC && Launcher.appConfig.get("OverwritePC").equals("false")){
                 String err = "Direct modification of PC register is not allowed." +
                            " if you wish to proceed, change that in the settings.";
                    triggerProgramError(new ErrorHandler.InvalidMemoryOperationException(err),
                            err, ErrorHandler.ERR_CODE_PC_MODIFY_UNALLOWED);
            }
            // Special purpose registers are 16-bits whereas general purpose registers are 8-bits
            else if (registerID >= PC) registers[registerID] = value;
            else if (value > max_value){
                String err = String.format("The value %X(%d) exceeds the %d-bit CPU module size.", value, value, bit_length);
                triggerProgramError(new ErrorHandler.InvalidMemoryOperationException(err),
                        err, ErrorHandler.ERR_CODE_CPU_SIZE_VIOLATION);
            }
            else registers[registerID] = value;
        }
    }

    public boolean isValidAddress(int address){
        return address <= last_addressable_location && address >= 0;
    }

    @Override
    public int[] readWord(int startAddress){
        if ( isValidAddress(startAddress) && isValidAddress(startAddress + 1))
            return new int[] {memory[startAddress], memory[startAddress + 1]};
        else
            return new int [] {-1};
    }

    @Override
    public int readByte(int address){
        if (isValidAddress(address)) return memory[address];
        else return -256;
    }

    @Override
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

    @Override
    public String dumpRegisters(){
        int registersPerLine = 3;
        StringBuilder result = new StringBuilder();
        for(int i = 0; i < registers.length; i++){

            if (( i + 1 ) % registersPerLine == 0) result.append("\n");
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

    public void triggerProgramError(RuntimeException exceptionType, String errMsg, int errCode){
        status_code = errCode;
        outputString.append("line " + currentLine + " : " + errMsg);
        programEnd = true;
        exceptionType = new RuntimeException("line " + currentLine + " : " + errMsg);
        throw exceptionType;
    }

    @Override
    public void setUIupdateListener(onStepListener listener){
        this.stepListener = listener;
    }
}