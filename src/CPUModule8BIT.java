import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CPUModule8BIT extends CPU {

    // CPU specific Variables //
    public static final int max_value = 255;
    public static final int min_value = -127;

    public int currentLine = 0;
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


    // Flags N = negative, C = carry, O = overflow, Z = zero //
    boolean N, C, O, Z;
    // T = trap, E = Error
    boolean T, E;
    /// /////////////////////////////////////////////

    int memorySize = Integer.parseInt(Settings.loadSettings().get("MemSize"));
    int offsetSize = Integer.parseInt(Settings.loadSettings().get("OffsetSize"));
    int stackSize = Integer.parseInt(Settings.loadSettings().get("StackSize"));


    public CPUModule8BIT() {
        super();

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

        reset();
    }


    @Override
    public void reset(){

        // 6 General purpose registers + 4 Special purpose registers
        registers = new short[REGISTER_COUNT + 6];
        registerNames = new String[registers.length];
        bit_length = 8;
        memory = new short[mem_size_B];

        outputString = new StringBuilder();

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


    }

    public void executeCompiledCode(int[] machine_code){
        int mainEntryPoint = functions.get("MAIN");
        registers[PC] = (short) mainEntryPoint;
        while (!programEnd || registers[PC] < machine_code.length){
            switch (machine_code[ registers[PC] ]){
                case INS_EXT ->{
                    programEnd = true;
                    break;
                }

                // step function increments PC and returns its value
                // we step two times for each operand. one step for mode. another step for value
                case INS_SET -> {
                    short[] destination = new short[] {(short) machine_code[ step() ], (short) machine_code[ step() ]};
                    short[] source = new short[] {(short) machine_code[ step() ], (short) machine_code[ step() ]};
                    set(destination, source);
                }
                case INS_OUT -> {
                    short[] destination = new short[] { (short) machine_code[ step() ], (short) machine_code[ step() ] };
                    out(destination);
                }

                case INS_ADD -> {
                    short[] destination = new short[] { (short) machine_code[ step() ], (short) machine_code[ step() ] };
                    short[] source = new short[] {  (short) machine_code[ step() ], (short) machine_code[ step() ]  };
                    add(destination, source);
                }
                case INS_SUB -> {
                    short[] destination = new short[] { (short) machine_code[ step() ], (short) machine_code[ step() ] };
                    short[] source = new short[] {  (short) machine_code[ step() ], (short) machine_code[ step() ]  };
                    sub(destination, source);
                }
                case INS_MUL -> {
                    short[] destination = new short[] { (short) machine_code[ step() ], (short) machine_code[ step() ] };
                    short[] source = new short[] {  (short) machine_code[ step() ], (short) machine_code[ step() ]  };
                    mul(destination, source);
                }
                case INS_DIV -> {
                    short[] destination = new short[] { (short) machine_code[ step() ], (short) machine_code[ step() ] };
                    short[] source = new short[] {  (short) machine_code[ step() ], (short) machine_code[ step() ]  };
                    div(destination, source);
                }

                case INS_POW -> {
                    short[] destination = new short[] { (short) machine_code[ step() ], (short) machine_code[ step() ] };
                    short[] source = new short[] {  (short) machine_code[ step() ], (short) machine_code[ step() ]  };
                    pow(destination, source);
                }

                case INS_SQRT -> {
                    short[] destination = new short[] { (short) machine_code[ step() ], (short) machine_code[ step() ] };
                    sqrt(destination);
                }

                case INS_RND -> {
                    short[] destination = new short[] { (short) machine_code[ step() ], (short) machine_code[ step() ] };
                    short[] source = new short[] { (short) machine_code[ step() ], (short) machine_code[ step() ]};
                    rnd(destination, source);
                }

                case INS_INC -> {
                    short[] destination = new short[] { (short) machine_code[ step() ], (short) machine_code[ step() ] };
                    inc(destination);
                }
                case INS_DEC -> {
                    short[] destination = new short[] { (short) machine_code[ step() ], (short) machine_code[ step() ] };
                    dec(destination);
                }

                case INS_NOT -> {
                    short[] source = new short[] {(short) machine_code[step()], (short) machine_code[step()]};
                    not(source);
                }

                case INS_AND -> {
                    short[] destination = new short[] {(short) machine_code[step()], (short) machine_code[step()]};
                    short[] source = new short[] {(short) machine_code[step()], (short) machine_code[step()]};
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
                    short[] source = new short[] { (short) machine_code[ step() ], (short) machine_code[ step() ]};
                    step();
                    step();
                    la(source);
                }

                case INS_LLEN -> {
                    short[] destination = new short[] {(short) machine_code[ step() ], (short) machine_code[ step() ]};
                    step();
                    step();
                    int start = machine_code[registers[PC]];
                    short len = 0;
                    while (getMemory( start ) != NULL_TERMINATOR){
                        start++;
                        len++;
                    }

                    switch (destination[0]){
                        case REGISTER_MODE -> setRegister(destination[1], len);
                        case DIRECT_MODE -> setMemory(destination[1], len);
                        case INDIRECT_MODE -> setMemory( getRegister( destination[1] ), len );
                        default -> E = true;
                    }
                }

                case INS_OUTS -> {
                    int start = registers[SS];
                    while (getMemory( start ) != NULL_TERMINATOR){
                        outputString.append( (char) getMemory(start));
                        start++;
                    }
                    outputString.append("\n");
                }

                case INS_PUSH -> {
                    short[] source = new short[] { (short) machine_code[ step() ], (short) machine_code[ step() ] };
                    push(source);
                }

                case INS_POP ->{
                    short[] source = new short[] { (short) machine_code[ step() ], (short) machine_code[ step() ] };
                    pop(source);
                }

                case INS_CALL -> {
                    int mode = machine_code[step()];
                    int address = machine_code[step()];
                    call(address);
                }
                case INS_RET -> {
                    System.out.println("Popping the function call stack.");
                    if (!functionCallStack.isEmpty()) {
                        int return_address = functionCallStack.pop() - 1;
                        System.out.println("Popping address : 0x" + Integer.toHexString(return_address) + ". Stack size : " + functionCallStack.size());
                        registers[PC] = (short) (return_address);
                    }else{
                        E = true;
                        System.out.println("The function stack is empty.");
                        continue;
                    }
                }

                case INS_CE -> {
                    step();
                    int address = machine_code[step()];
                    if (Z) call(address);
                }
                case INS_CNE -> {
                    step();
                    int address = machine_code[step()];
                    if (!Z) call(address);
                }
                case INS_CL -> {
                    step();
                    int address = machine_code[step()];
                    if (N) call(address);
                }
                case INS_CLE -> {
                    step();
                    int address = machine_code[step()];
                    if (N || Z) call(address);
                }
                case INS_CG -> {
                    step();
                    int address = machine_code[step()];
                    if (!N) call(address);
                }
                case INS_CGE -> {
                    step();
                    int address = machine_code[step()];
                    if (!N || Z) call(address);
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
                    short[] destination = new short[] {(short) machine_code[ step() ], (short) machine_code[ step() ]};
                    short[] source = new short[] { (short) machine_code[ step() ], (short) machine_code[ step() ]};
                    cmp(destination, source);
                }

                case INS_LOOP -> {
                    // if RC > 0: decrement RC and jump to the label address specified.
                    step();
                    step();
                    //short address = (short) machine_code[ registers[PC] ];

                    registers[2]--;
                    if (registers[2] > 0){
                        jmp();
                    }
                }

                default -> {
                    String err = "Undefined instruction. please check the instruction codes : " + machine_code[ registers[PC] ];
                    status_code = ErrorHandler.ERR_CODE_INVALID_INSTRUCTION_FORMAT;
                    triggerProgramError(new ErrorHandler.InvalidInstructionException(err),
                            err, status_code);
                }
            }

            if (E){
                status_code = ErrorHandler.ERR_CODE_PROGRAM_ERROR;
                String err = String.format("The program triggered an error with code : %s", status_code);
                triggerProgramError(new ErrorHandler.ProgramErrorException(err),
                        err, status_code);
            }

            step();
        }

        outputString.append("Program terminated with code : ").append(status_code);
    }

    public int step() {registers[PC]++; return registers[PC];}

    public short[] getNextOperand(){
        return new short[] {(short) machineCode[step()], (short) machineCode[step()]};
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
        byte flagSetter = (byte) newVal;
        if (flagSetter == 0) Z = true;
        else Z = false;
        if (flagSetter < 0) N = true;
        else N = false;

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
                newVal = (short) (operandValue - getRegister( destination[1] ));
                setRegister( destination[1] , newVal);
            }
            case DIRECT_MODE -> {
                newVal = (short) ( operandValue - getMemory( destination[1] ) );
                setMemory( destination[1], newVal );
            }
            case INDIRECT_MODE -> {
                newVal = (short) ( operandValue - getMemory( getRegister( destination[1] ) ) );
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

        short newValue = (short) Math.pow( destination[1], power );
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
    public void call(int address){
        System.out.println("Pushing address : 0x" + Integer.toHexString(machineCode[registers[PC]]));
        functionCallStack.push(machineCode[ registers[PC] ]); // save the return address
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
        result[0] = translationMap.get(tokens[0]); // tokens[0] should always be the opcode.

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
                    result[i + 1] = dataMap.get(tokens[tokenIndex].substring(1));
                    tokenIndex++;
                    break;
                }
                if (result[i] == 0x6){
                    result[i + 1] = functions.get(tokens[tokenIndex]);
                    tokenIndex++;
                    break;
                }

                if (isNumber(tokens[tokenIndex].substring(1))){
                    result[i + 1] = Integer.parseInt(tokens[tokenIndex].substring(1));
                }else result[i + 1] = getRegisterCode(tokens[tokenIndex].substring(1));
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

            // Which section are we in? (is it a line of code? is it a function. and if it starts with '.' is it the data section?)
            if (lines[i].equals(".DATA")){
                System.out.println("Data section detected.");
                int offset = 0;
                i++; // skip .DATA line

             while (!lines[i].equals("end")) {

                 String[] x = lines[i].trim().split(" ");
                 if (x[0].equals("org")) data_start = Integer.parseInt(x[1].substring(1));

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
                if (lines[i].isEmpty()) continue;
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

        for(int i = 0; i < fullLines.length; i++){

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
            // Special purpose registers are 16-bits whereas general purpose registers are 8-bits
            if (registerID >= PC) registers[registerID] = value;
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
                result.append("\t\t\t").append("|").append(charSet).append("|").append("\n");
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
        result.append(String.format("N = %d\tO = %d\tC = %d\tZ = %d\tT = %d\tE = %d\n",
                N ? 1 : 0,
                O ? 1 : 0,
                C ? 1 : 0,
                Z ? 1 : 0,
                T ? 1 : 0,
                E ? 1 : 0));

        return result.toString();
    }

    public void triggerProgramError(RuntimeException exceptionType, String errMsg, int errCode){
        status_code = errCode;
        outputString.append(errMsg);
        programEnd = true;
        exceptionType = new RuntimeException(errMsg);
        throw exceptionType;
    }
}