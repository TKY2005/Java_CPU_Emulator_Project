import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

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

    public short[] registers;
    public String[] registerNames;
    public short[] memory;
    /// ///////////////////////////////////////


    // Flags N = negative, C = carry, O = overflow //
    boolean N, C, O;
    // T = trap
    boolean T;
    /// /////////////////////////////////////////////

    int memorySize = Integer.parseInt(Settings.loadSettings().get("MemSize"));
    int offsetSize = Integer.parseInt(Settings.loadSettings().get("OffsetSize"));
    int stackSize = Integer.parseInt(Settings.loadSettings().get("StackSize"));


    public CPUModule8BIT() {
        super();

        int mem_size_B = memorySize * 1024;
        stack_start = mem_size_B - (stackSize * 1024);
        data_start = stack_start - (offsetSize * 1024);
        last_addressable_location = stack_start - 1;

        memory = new short[mem_size_B];

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
        registers = new short[REGISTER_COUNT + 4];
        registerNames = new String[registers.length];
        bit_length = 8;


        // set register names for search
        char registerChar = 'a';
        for(int i = 0; i < registerNames.length - 2; i++){
            registerNames[i] = "r" + registerChar;
            registerChar++;
        }
        registerNames[PC] = "pc";
        registerNames[SP] = "sp";
        registerNames[SS] = "ss";
        registerNames[SE] = "se";

        /*for(int i = 0; i < registerNames.length; i++){
            System.out.printf("%s => 0x%X\n", registerNames[i], i);
        }*/

        registers[SP] = (short) stack_start;
        registers[PC] = 0;

        // Test Code

        String code = """
                .exit
                 set $ra !40
                 set $rb !0
                 int
                .MAIN
                    la $ra ~n2
                    la $rb ~n1
                    la $rc ~n3
                    set $rc &rc
                    call cipher
                    jmp exit
                end
                .cipher
                    add &ra &rb
                    inc $ra
                    inc $rb
                    
                    cmp $rb !3
                    ce reset_rb
                    
                    loop cipher
                    jmp MAIN
                end
                .reset_rb
                    set $rb !3
                    ret
                .DATA
                 n1 !10 !40 !50
                 n2 "Hello world"
                 n3 !11
                end
                """;

        compileCode(code);
        
        System.out.println(dumpROM());
        System.out.println("==================");
        System.out.println(dumpMemory());

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
                 dataMap.put(x[0], data_start + offset);
                 if (x[1].startsWith(String.valueOf(STRING_PREFIX))){ // 34 in decimal 0x22 in hex
                     String fullString = String.join(" ", x);

                     int startIndex = fullString.indexOf(34) + 1;
                     int endIndex = fullString.length() - 1;
                     fullString = fullString.substring(startIndex, endIndex);
                     for(int j = 0; j < fullString.length(); j++){
                         System.out.printf("Setting memory location 0x%X(%d) to char %c\n",
                                 data_start + offset, data_start + offset, fullString.charAt(j));
                         setMemory(data_start + offset, (short) fullString.charAt(j));
                         offset++;
                     }
                     setMemory(offset, NULL_TERMINATOR);
                     offset++;
                 }else{
                     for(int j = 1; j < x.length; j++){
                         System.out.printf("Setting memory location 0x%X(%d) to value 0x%X(%d)\n",
                                 data_start + offset, data_start + offset,
                                 Integer.parseInt(x[j].substring(1)), Integer.parseInt(x[j].substring(1)));

                         setMemory(data_start + offset, Short.parseShort(x[j].substring(1)));
                         offset++;
                     }
                     setMemory(offset, NULL_TERMINATOR);
                     offset++;
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

    public void setMemory(int address, short value){
        if (!isValidAddress(address)){
            // TODO: Trigger a program Error and toggle programEnd.
            String errCode = String.format("Invalid memory access at location %X(%d)\n", address, address);
            System.out.print(errCode);

        }else{
            if (value > max_value || value < min_value){
                invalidValueSize(value);
            }else memory[address] = value;
        }
    }

    public void invalidValueSize(short value){ // This should be an Exception.
        String errCode = String.format("Value %X(%d) exceeds the %d-bit limit for this CPU module.\n",
                value, value, bit_length);
        // TODO: Trigger a program error and toggle programEnd.
        System.out.print(errCode);
    }

    public void setRegister(int registerID, short value){
        if (registerID < registers.length){
            // Special purpose registers are 16-bits whereas general purpose registers are 8-bits
            if (registerID >= PC) registers[registerID] = value;
            else if (value > max_value) invalidValueSize(value);
            else registers[registerID] = value;
        }
    }

    public boolean isValidAddress(int address){
        return address <= last_addressable_location && address > 0;
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
}