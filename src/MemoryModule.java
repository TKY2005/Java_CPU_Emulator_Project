public class MemoryModule {

    private CPU cpu;

    int mem_size_B;

    static float memorySizeKB = (Float.parseFloat(Launcher.appConfig.get("MemSize")));
    static float ROMpercentage = (Float.parseFloat(Launcher.appConfig.get("ROMPercentage")) / 100);
    static float DATApercentage = (Float.parseFloat(Launcher.appConfig.get("DataPercentage")) / 100);
    static float STACKpercentage = (Float.parseFloat(Launcher.appConfig.get("StackPercentage")) / 100);


    int ROMsizeB, DATAsizeB, STACKsizeB;
    static int rom_start, rom_end, data_start, data_end, stack_start, stack_end;

    float originStartPercentage = 0.25f;
    int dataOffset, dataOrigin;
    int last_addressable_location;

    float ROMsizeKB, DATAsizeKB, STACKsizeKB;

    private short[] memory;
    static int file_offset = 0;

    static final int max_byte_value = 0xff;
    static final int max_pair_value = 0xffff;

    String logDevice = "MEMORY_CONTROLLER";

    public MemoryModule(int sizeB, CPU cpu){
        this.cpu = cpu;
        mem_size_B = sizeB;
        memorySizeKB = sizeB / 1024f;
        calculateMemorySegments();
    }
    public MemoryModule(float sizeKB, CPU cpu){
        this.cpu = cpu;
        memorySizeKB = sizeKB;
        mem_size_B = (int) (sizeKB * 1024);
        calculateMemorySegments();
    }

    public void calculateMemorySegments(){

        // 4 additional bytes for architecture, memory size, entry point address
        mem_size_B = mem_size_B + CPU.signature.length() + CPU.lastUpdateDate.length() + CPU.compilerVersion.length() + 4;

        ROMsizeKB = (ROMpercentage * memorySizeKB);
        DATAsizeKB = (DATApercentage * memorySizeKB);
        STACKsizeKB = (STACKpercentage * memorySizeKB);

        ROMsizeB = (int) (ROMsizeKB * 1024);
        DATAsizeB = (int) (DATAsizeKB * 1024);
        STACKsizeB = (int) (STACKsizeKB * 1024);

        rom_start = 0;
        data_start = rom_start + ROMsizeB;
        stack_start = data_start + DATAsizeB;

        rom_end = data_start - 1;
        data_end = stack_start - 1;
        stack_end = stack_start + STACKsizeB;

        dataOrigin = data_start + (int) (originStartPercentage * DATAsizeB);
        dataOffset = dataOrigin;
        last_addressable_location = data_end;

        String memInitMsg = String.format("""
                Starting with %sKB of memory and %s Bytes for metadata. Total of %d locations
                ROM section size: %.3fKB(%dB), start address: 0x%X(%d) -> end address: 0x%X(%d)
                DATA section size: %.3fKB(%dB), start address: 0x%X(%d) -> end address: 0x%X(%d)
                STACK section size: %.3fKB(%dB), start address: 0x%X(%d) -> end address: 0x%X(%d)
                last addressable location: 0x%X(%d)
                data offset location: 0x%X(%d)
                """,
                memorySizeKB, CPU.signature.length() + CPU.lastUpdateDate.length() + CPU.compilerVersion.length() + 4, mem_size_B,
                ROMsizeKB, ROMsizeB, rom_start, rom_start, rom_end, rom_end,
                DATAsizeKB, DATAsizeB, data_start, data_start, data_end, data_end,
                STACKsizeKB, STACKsizeB, stack_start, stack_start, stack_end, stack_end,
                last_addressable_location, last_addressable_location,
                dataOffset, dataOffset
                );

        resetMemory();
        Logger.addLog(memInitMsg, logDevice, true);
        Logger.addLog("Done calculating memory segments.", logDevice, true);
    }

    public void resetMemory(){
        memory = new short[mem_size_B];
        dataOffset = dataOrigin;
    }

    public int getMemorySize(){
        return memory.length;
    }

    public short getMemory(short address){

        short actualAddress = (short) (data_start - file_offset + address);
        if (!isValidMemoryAddress(actualAddress)){
            String err = String.format("0x%X(%d):0x%X(%d) is an invalid memory address.",
                            data_start, data_start
                            ,actualAddress, actualAddress);
            cpu.triggerProgramError(err, ErrorHandler.ERR_CODE_INVALID_MEMORY_ADDRESS);
        }
        else return memory[actualAddress];

        return -1;
    }

    public int readByte(int address){

        int actualAddress = data_start + address;
        if (!isValidMemoryAddress(actualAddress)){
            String err = String.format("0x%X(%d):0x%X(%d) -> 0x%X(%d) is an invalid memory address.",
                            data_start, data_start,
                            address, address
                            ,actualAddress, actualAddress);
            cpu.triggerProgramError(
                    err, ErrorHandler.ERR_CODE_INVALID_MEMORY_ADDRESS);
        }
        Logger.addLog(String.format("Reading a byte from address : 0x%04X -> 0x%02X",
                        actualAddress, memory[actualAddress]),
                logDevice);
        return memory[actualAddress];
    }

    public int[] readWord(int startAddress){

        int actualAddress = data_start + startAddress;
        if (!isValidMemoryAddress(actualAddress)){
            String err = String.format("0x%X(%d):0x%X(%d) -> 0x%X(%d) is an invalid memory address.",
                            data_start, data_start,
                            startAddress, startAddress
                            ,actualAddress, actualAddress);
            cpu.triggerProgramError(
                    err, ErrorHandler.ERR_CODE_INVALID_MEMORY_ADDRESS);
        }
        if (!isValidMemoryAddress(actualAddress + 1)){
            String err = String.format("0x%X(%d):0x%X(%d) -> 0x%X(%d) is an invalid memory address.",
                            data_start, data_start,
                            startAddress + 1, startAddress + 1
                            ,actualAddress, actualAddress);
            cpu.triggerProgramError(
                    err, ErrorHandler.ERR_CODE_INVALID_MEMORY_ADDRESS);
        }

        Logger.addLog(String.format("Reading a word from address : 0x%04X -> 0x%02X, 0x%02X",
                        actualAddress, memory[actualAddress], memory[actualAddress + 1]),
                logDevice);
        return new int[] {memory[actualAddress], memory[actualAddress + 1]};
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

        int actualAddress = data_start + address;
        if (isValidMemoryAddress(actualAddress)){

            if (mode == CPU.DATA_BYTE_MODE) memory[actualAddress] = (short) value;
            else if (mode == CPU.DATA_WORD_MODE){
                if (!isValidMemoryAddress(actualAddress + 1)){
                    String err = String.format("0x%X(%d):0x%X(%d) -> 0x%X(%d) is an invalid memory address.",
                            data_start - file_offset, data_start - file_offset,
                            address + 1, address + 1
                            ,actualAddress, actualAddress);
                    cpu.triggerProgramError(
                            err, ErrorHandler.ERR_CODE_INVALID_MEMORY_ADDRESS);
                }
                else{
                    int low = value & 0xff;
                    int high = (value >> 8) & 0xff;
                    memory[actualAddress] = (short) low;
                    memory[actualAddress + 1] = (short) high;
                }
            }

        }else{
            String err = String.format("0x%X(%d):0x%X(%d) -> 0x%X(%d) is an invalid memory address.",
                            data_start - file_offset, data_start - file_offset,
                            address, address
                            ,actualAddress, actualAddress);
            cpu.triggerProgramError(
                    err, ErrorHandler.ERR_CODE_INVALID_MEMORY_ADDRESS);
        }
    }

    public void setMemory(int address, int value){


        int actualAddress = data_start + address;
        if (isValidMemoryAddress(actualAddress)){

            if (value <= max_byte_value) memory[actualAddress] = (short) value;
            else {
                if (!isValidMemoryAddress(actualAddress + 1)){
                    String err = String.format("0x%X(%d):0x%X(%d) -> 0x%X(%d) is an invalid memory address.",
                            data_start - file_offset, data_start - file_offset,
                            address + 1, address + 1
                            ,actualAddress, actualAddress);
                    cpu.triggerProgramError(
                            err, ErrorHandler.ERR_CODE_INVALID_MEMORY_ADDRESS);
                }
                else{
                    int low = value & 0xff;
                    int high = (value >> 8) & 0xff;
                    memory[actualAddress] = (short) low;
                    memory[actualAddress + 1] = (short) high;
                }
            }

        }else{
            String err = String.format("0x%X(%d):0x%X(%d) -> 0x%X(%d) is an invalid memory address.",
                            data_start - file_offset, data_start - file_offset,
                            address, address
                            ,actualAddress, actualAddress);
            cpu.triggerProgramError(
                    err, ErrorHandler.ERR_CODE_INVALID_MEMORY_ADDRESS);
        }
    }


    public void setMemoryAbsolute(int address, int value, int mode){

        if (isValidAbsoluteAddress(address)) {

            if (mode == CPU.DATA_BYTE_MODE) memory[address] = (short) value;
            else if (mode == CPU.DATA_WORD_MODE) {
                int low = value & 0xff;
                int high = (value >> 8) & 0xff;
                memory[address] = (short) low;
                if (address + 1 < memory.length) memory[address + 1] = (short) high;
                else{
                    String err = String.format("0x%04X(%d) is not a valid memory address.", address + 1, address + 1);
                    cpu.triggerProgramError(err, ErrorHandler.ERR_CODE_INVALID_MEMORY_ADDRESS);
                }
            }

        } else{
            String err = String.format("0x%04X(%d) is not a valid memory address.", address, address);
            cpu.triggerProgramError(err, ErrorHandler.ERR_CODE_INVALID_MEMORY_ADDRESS);
        }
    }
    
    public int readByteAbsolute(int address){
        
        if (!isValidAbsoluteAddress(address)){
            String err = String.format("0x%X(%d) is an invalid memory address.",
                    address, address);
            cpu.triggerProgramError(
                    err, ErrorHandler.ERR_CODE_INVALID_MEMORY_ADDRESS);
        }
        Logger.addLog(String.format("Reading a byte from address : 0x%04X -> 0x%02X",
                        address, memory[address]),
                logDevice);
        return memory[address];
    }
    
    public int[] readWordAbsolute(int startAddress){
        
        if (!isValidAbsoluteAddress(startAddress)){
            String err = String.format("0x%X(%d) is an invalid memory address."
                            ,startAddress, startAddress);
            cpu.triggerProgramError(
                    err, ErrorHandler.ERR_CODE_INVALID_MEMORY_ADDRESS);
        }
        if (!isValidAbsoluteAddress(startAddress + 1)){
            String err = String.format("0x%X(%d) is an invalid memory address.",
                            startAddress + 1, startAddress + 1);
            cpu.triggerProgramError(
                    err, ErrorHandler.ERR_CODE_INVALID_MEMORY_ADDRESS);
        }

        Logger.addLog(String.format("Reading a word from address : 0x%04X -> 0x%02X, 0x%02X",
                        startAddress, memory[startAddress], memory[startAddress + 1]),
                logDevice);
        return new int[] {memory[startAddress], memory[startAddress + 1]};
    }

    private boolean isValidAbsoluteAddress(int startAddress) {
        return startAddress >= 0 && startAddress < memory.length;
    }

    public boolean isValidMemoryAddress(int address){
        return address <= last_addressable_location && address > rom_end;
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

    public String dumpROM(){
        StringBuilder hexDump = new StringBuilder();

        for(int i = 0; i != rom_end; i++){
            if (i % 5 == 0){
                hexDump.append("\n");
                hexDump.append(String.format("%04X : \t", i));
            }

            hexDump.append(String.format("0x%02X" , memory[i])).append(" ");
        }
        return hexDump.toString();
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
}
