import javax.swing.*;
import java.io.*;
import java.util.Scanner;

public class VirtualMachine {

    private static CPU cpuModule;
    public boolean readyToExecute = false;
    public boolean UIMode = false;
    public static boolean ui = false;
    public byte compileDirection = 0;

    public String err_msg = "";

    private static HardDiskDriver diskDriver;
    private int[] memImage;

    static String logDevice = "VIRTUAL_MACHINE";

    public VirtualMachine(CPU cpuModule){
        Logger.addLog("Starting the virtual machine with the selected CPU module.", logDevice, true);
        this.cpuModule = cpuModule;
        //diskDriver = new HardDiskDriver("./disk0.img");
        System.out.println("Welcome");
    }

    public void sendCode(String code){

       StringBuilder result = new StringBuilder();
        String[] lines = code.split("\n");
        err_msg = "";
        cpuModule.reset();


        for (String line : lines) {
            String[] tokens = line.trim().split("\\s+");
            StringBuilder newLine = new StringBuilder();

            for (String token : tokens) {
                if (token.startsWith(CPU.HEX_PREFIX)) {
                    // Hexadecimal to decimal conversion
                    String hex = token.substring(2);
                    int decimal = Integer.parseInt(hex, 16);
                    newLine.append("!").append(decimal).append(" ");
                } else if (token.startsWith(CPU.CHAR_PREFIX)) {
                    // Character to ASCII decimal conversion
                    char ch = token.charAt(1);
                    int ascii = (int) ch;
                    newLine.append("!").append(ascii).append(" ");
                }
                else if (token.startsWith(CPU.HEX_MEMORY)){
                    String hex = token.substring(1);
                    int decimal = Integer.parseInt(hex, 16);
                    newLine.append("%").append(decimal).append(" ");
                }
                else if (token.startsWith(CPU.SIGNAL_PREFIX)){
                    String signal = String.valueOf(cpuModule.programSignals.get(token.substring(1)));
                    byte numeric = Byte.parseByte(signal);
                    newLine.append("!").append(numeric);
                }
                else if (token.equalsIgnoreCase("byte") || token.equalsIgnoreCase("word")){
                    newLine.append(CPU.MEMORY_MODE_PREFIX).append(token).append(" ");
                }
                else if (token.startsWith(CPU.COMMENT_PREFIX)) break;
                else {
                    newLine.append(token).append(" ");
                }
            }

            result.append(newLine.toString().trim()).append("\n");
        }

        try {
            //if (compileDirection == 0) cpuModule.machineCode = cpuModule.compileCode(result.toString());
            //else if (compileDirection == 1) cpuModule.machineCode = cpuModule.compileToFileBinary(result.toString());
            loadImageToMemory(result, cpuModule.memoryController);

            ui = UIMode;
        }catch (RuntimeException e) {
            try {
                File file = new File("./CompileError.log");
                BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                err_msg = e.getMessage();
                System.out.println(err_msg);
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                writer.write(sw.toString());
                writer.close();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            readyToExecute = false;
            throw e;
        }
        readyToExecute = true;
    }

    private void loadImageToMemory(StringBuilder result, MemoryModule memory) {
        memImage = cpuModule.compileToMemoryImage(result.toString());
        for(int i = 0; i < memory.getMemorySize(); i++)
            memory.setMemoryAbsolute(i, (short) (memImage[i] & 0xff), CPU.DATA_BYTE_MODE);
    }

    public void loadImageToMemory(int[] memImage, MemoryModule memory){
        for(int i = 0; i < memory.getMemorySize(); i++)
            memory.setMemoryAbsolute(i, (short) (memImage[i] & 0xff), CPU.DATA_BYTE_MODE);
    }
    public void setMemImage(int[] machineCode){
        memImage = machineCode;
    }

    public void executeCode(){
        try {
            diskDriver = new HardDiskDriver("./disk0.img");
            cpuModule.executeCompiledCode(memImage);
            System.out.println(cpuModule.output);
            diskDriver.closeDrive();
        } catch (Exception e){
            try {
                File file = new File("./RuntimeError.log");
                BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                err_msg = e.getMessage();
                System.out.println(err_msg);
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                writer.write(sw.toString());
                writer.close();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            diskDriver.closeDrive();
        }
    }

    public void resetCPU(){
        cpuModule.reset();

        cpuModule.canExecute = true;
        cpuModule.programEnd = false;
        readyToExecute = false;
    }

    // 8-BIT INTERRUPT HANDLER
    public static boolean interruptHandler(short[] registers, MemoryModule memory){
        boolean validInterrupt = true;
        switch (registers[0]){ // interrupt register : RA

            case CPU.INT_INPUT_STR -> {

                Logger.addLog("Calling interrupt for input string", logDevice);
                String input_message = getInputMessage(registers, memory);
                //System.out.println("Message is : " + input_message);
                String input = "";
                if (ui){
                    Logger.addLog("Showing message for ui input", logDevice);
                    input = JOptionPane.showInputDialog(null, input_message,
                            "Input", JOptionPane.INFORMATION_MESSAGE);

                }else{
                    //System.out.print(input_message);
                    for(int i = 0; i < input_message.length(); i++) {
                        try {
                            System.out.print(input_message.charAt(i));
                            Thread.sleep(cpuModule.delayAmountMilliseconds);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    input = new Scanner(System.in).nextLine();
                }


                int write_address = registers[11]; // write_pointer_register : DI

                int index = 0;

                for(int i = write_address; i < write_address + input.length(); i++){

                    //memory[i] = (short) input.charAt(index);
                    memory.setMemory(i, input.charAt(index), CPU.DATA_BYTE_MODE);
                    index++;
                }
                //memory[write_address + input.length()] = CPU.ARRAY_TERMINATOR;
                memory.setMemory(write_address + input.length(), CPU.ARRAY_TERMINATOR, CPU.DATA_BYTE_MODE);
                registers[9] = (short) (write_address + input.length()); // string end position stored in SE

            }

            case CPU.INT_INPUT_NUM -> {
                Logger.addLog("Calling interrupt for numeric input", logDevice);
                String input_message = getInputMessage(registers, memory);

                short input;

                if (ui){
                    Logger.addLog("Showing ui input prompt", logDevice);
                    input = Short.parseShort(JOptionPane.showInputDialog(null, input_message, "Numeric input : ",
                            JOptionPane.INFORMATION_MESSAGE));
                }else{
                    for(int i = 0; i < input_message.length(); i++) {
                        try {
                            Thread.sleep(cpuModule.delayAmountMilliseconds);
                            System.out.print(input_message.charAt(i));
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    input = new Scanner(System.in).nextShort();
                }

                if (input <= 255) registers[3] = input; // if input fits 8-bits place in RD
                else registers[11] = input; // otherwise place in DI
            }

            case CPU.INT_DEBUG -> {
                Logger.addLog("Calling debug interrupt.", logDevice);
                System.out.println(cpuModule.dumpRegisters());
                Scanner s = new Scanner(System.in);

                if (ui){
                    JOptionPane.showMessageDialog(null, "Debug interrupts are not supported in UI mode.");
                }
                else {
                    boolean debugPause = true;

                    while (debugPause) {
                        System.out.print(">> ");
                        String[] x = s.nextLine().trim().split(" ");
                        if (x[0].equals("d")) {
                            int address = 0;
                            if (x[1].charAt(x[1].length() - 1) == 'h') address = Integer.parseInt(
                                    x[1].substring(0, x[1].length() - 1), 16
                            );
                            else address = Integer.parseInt(x[1]);
                            System.out.println(cpuModule.memoryController.dumpMemoryDebug(address));
                        }
                        else if (x[0].equals("ds")){

                            if (cpuModule.functionCallStack.isEmpty()){
                                System.out.println("The function call stack is currently empty.");
                                continue;
                            }
                            for(int i = cpuModule.functionCallStack.size() - 1; i >= 0; i--){
                                System.out.printf("[%d] => 0x%04X\n", i, cpuModule.functionCallStack.get(i));
                            }

                        }
                        else if (x[0].equals("g")) debugPause = false;
                        else System.out.println("Unknown command '" + x[0] + "'");
                    }
                }
            }

            case CPU.INT_STRING_CONCAT -> {
                int string1_address = registers[11]; // first string address: DI
                int string2_address = registers[10]; // second string address: DP
                int copy_address = registers[8]; // result will be copied to address: SS
                String concat = "";
                for(int i = string1_address; memory.readByte(i) != CPU.ARRAY_TERMINATOR; i++){
                    //System.out.println("Concatenating: 0x" + Integer.toHexString(i) + " => " + (char) memory[i]);
                    concat += (char) memory.readByte(i);
                }
                for(int i = string2_address; memory.readByte(i) != CPU.ARRAY_TERMINATOR; i++){
                    //System.out.println("Concatenating: 0x" + Integer.toHexString(i) + " => " + (char) memory[i]);
                    concat += (char) memory.readByte(i);
                }

                int index = 0;
                for(int i = copy_address; i < copy_address + concat.length(); i++){
                    //memory[i] = (short) concat.charAt(index); index++;
                    //memory[i + 1] = CPU.ARRAY_TERMINATOR;
                    memory.setMemory(i, concat.charAt(index), CPU.DATA_BYTE_MODE); index++;
                    memory.setMemory(i + 1, CPU.ARRAY_TERMINATOR, CPU.DATA_BYTE_MODE);
                }
            }

            case CPU.INT_STR_CPY -> {
                int strAddr = registers[8]; // original string address at : SS
                int strDest = registers[9]; // copy destination address at : SE

                for(int i = strAddr; memory.readByte(i) != CPU.ARRAY_TERMINATOR; i++){
                    int destination = strDest + (i - strAddr);
                    //memory[destination] = memory[i];
                    //memory[destination + 1] = CPU.ARRAY_TERMINATOR;
                    memory.setMemory(destination, memory.readByte(i));
                    memory.setMemory(destination + 1, CPU.ARRAY_TERMINATOR, CPU.DATA_BYTE_MODE);
                }
            }

            case CPU.INT_MEM_CPY -> {
                int startAddress = registers[11]; // start address at: DI
                int destinationAddress = registers[10]; // destination address at: DP
                int numBytes = registers[3]; // number of bytes to copy at: RD

                for(int i = startAddress; i < startAddress + numBytes; i++){
                    //memory[destinationAddress + (i - startAddress)] = memory[i];
                    memory.setMemory( destinationAddress + (i - startAddress), memory.readByte(i), CPU.DATA_BYTE_MODE );
                }
            }

            case CPU.INT_FILE -> {

                int read_write_addr = registers[11]; // the address where the file will be loaded / fetched : DI
                int file_path_addr = registers[8]; // the address of the file path : SS

                int operation = registers[1]; // the operation to perform : RB


                // for write operations
                // RB = 0x1 for CPU.FILE_WRITE
                // SS : file path
                // DI : the beginning of the file
                // DP : the number of bytes to write

                // for read operations
                // RB = 0x0 for CPU.FILE_READ
                // SS : file path
                // DI : the location the file will be loaded to
                // DP : the total number of bytes that were read will be placed here

                // append data to a file
                // RA = 0x2 for CPU.FILE_APPEND
                // SS : file path
                // DI : the beginning of the data to be appended
                // DP : the number of bytes to append

                // delete a file
                // RB = 0x3 for CPU.DELETE_FILE
                // SS : file path

                String fileName = "";
                for(int i = file_path_addr; memory.readByte(i) != CPU.ARRAY_TERMINATOR; i++) {
                    fileName += (char) memory.readByte(i);
                }

                if (operation == CPU.FILE_READ) {
                    byte[] file_data = diskDriver.readFile(fileName);
                    for(int i = 0; i < file_data.length; i++)
                        memory.setMemory(read_write_addr + i, file_data[i], CPU.DATA_BYTE_MODE);

                    registers[10] = (short) file_data.length; // the total number of bytes that are read from the disk
                }

                else if (operation == CPU.FILE_WRITE) {
                    int file_length = registers[10]; // the number of bytes to copy : DP
                    byte[] file_data = new byte[file_length];

                    for(int i = 0; i < file_data.length; i++) {
                        file_data[i] = (byte) memory.readByte( read_write_addr + i );
                    }
                    diskDriver.saveFile(fileName, file_data);
                }

                else if (operation == CPU.FILE_APPEND){
                    int file_length = registers[10];
                    byte[] file_data = new byte[file_length];
                    for(int i = 0; i < file_data.length; i++){
                        file_data[i] = (byte) memory.readByte( read_write_addr + i );
                    }
                    diskDriver.appendFile(fileName, file_data);
                }

                else if (operation == CPU.FILE_DELETE){

                    diskDriver.deleteFile(fileName);
                }
                else {
                    validInterrupt = false;
                }

            }

            default -> validInterrupt = false;
        }
        Logger.addLog("done. returning to original program.", logDevice);
        return validInterrupt;
    }


    // 16-BIT INTERRUPT HANDLER
    public static boolean interruptHandler(int[] registers, MemoryModule memory){
         boolean validInterrupt = true;
        switch (registers[1]){ // interrupt register: AH

            case CPU.INT_INPUT_STR -> {

                int mode = registers[0]; // store mode register: AL
                if (mode != CPU.DATA_BYTE_MODE && mode != CPU.DATA_WORD_MODE) mode = CPU.DATA_BYTE_MODE;

                Logger.addLog("Calling interrupt for input string", logDevice);
                String input_message = getInputMessage(registers, memory);

                String input = "";
                if (ui){
                    Logger.addLog("Showing message for ui input", logDevice);
                    input = JOptionPane.showInputDialog(null, input_message,
                            "Input", JOptionPane.INFORMATION_MESSAGE);

                }else{
                    //System.out.print(input_message);
                    for(int i = 0; i < input_message.length(); i++) {
                        try {
                            System.out.print(input_message.charAt(i));
                            Thread.sleep(cpuModule.delayAmountMilliseconds);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    input = new Scanner(System.in).nextLine();
                }


                int write_address = registers[22]; // write position register: data_start:DI

                int index = 0;

                if (mode == CPU.DATA_BYTE_MODE) {
                    for (int i = write_address; i < write_address + input.length(); i++) {
                        memory.setMemory(i, input.charAt(index), CPU.DATA_BYTE_MODE);
                        index++;
                    }
                }
                else if (mode == CPU.DATA_WORD_MODE){
                    for(int i = write_address; i < write_address + input.length() * 2; i += 2){
                        short low = (short) (input.charAt(index) & 0xff);
                        short high = (short) ((input.charAt(index) >> 8) & 0xff);

                        memory.setMemory(i + 1, high, CPU.DATA_BYTE_MODE);
                        memory.setMemory(i, low, CPU.DATA_BYTE_MODE);
                        index++;
                    }
                }

                int endPosition = 0;
                if (mode == CPU.DATA_BYTE_MODE) endPosition = write_address + input.length();
                else if (mode == CPU.DATA_WORD_MODE ) endPosition = write_address + input.length() * 2;

                memory.setMemory(endPosition, CPU.ARRAY_TERMINATOR, CPU.DATA_BYTE_MODE);

                registers[21] = endPosition; // string end position stored in SE

            }

            case CPU.INT_INPUT_NUM -> {
                Logger.addLog("Calling interrupt for numeric input", logDevice);
                String input_message = getInputMessage(registers, memory);

                int input;

                if (ui){
                    Logger.addLog("Showing ui input prompt", logDevice);
                    input = Short.parseShort(JOptionPane.showInputDialog(null, input_message, "Numeric input : ",
                            JOptionPane.INFORMATION_MESSAGE));
                }else{
                    //System.out.print(input_message);
                    for(int i = 0; i < input_message.length(); i++) {
                        try {
                            System.out.print(input_message.charAt(i));
                            Thread.sleep(cpuModule.delayAmountMilliseconds);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    input = new Scanner(System.in).nextShort();
                }

                registers[15] = input; // place input in DX
                // update DL, DH
                registers[6] = registers[15] & 0xff;
                registers[7] = (registers[15] >> 8) & 0xff;
            }

            case CPU.INT_DEBUG -> {
                Logger.addLog("Calling debug interrupt.", logDevice);
                System.out.println(cpuModule.dumpRegisters());
                Scanner s = new Scanner(System.in);

                if (ui){
                    JOptionPane.showMessageDialog(null, "Debug interrupts are not supported in UI mode.");
                }
                else {
                    boolean debugPause = true;

                    while (debugPause) {
                        System.out.print(">> ");
                        String[] x = s.nextLine().trim().split(" ");
                        if (x[0].equals("d")) {
                            int address = 0;
                            if (x[1].charAt(x[1].length() - 1) == 'h') address = Integer.parseInt(
                                    x[1].substring(0, x[1].length() - 1), 16
                            );
                            else address = Integer.parseInt(x[1]);
                            System.out.println(cpuModule.memoryController.dumpMemoryDebug(address));

                        } else if (x[0].equals("g")) debugPause = false;

                        else if (x[0].equals("ds")){

                            if (cpuModule.functionCallStack.isEmpty()){
                                System.out.println("The function call stack is currently empty.");
                                continue;
                            }
                            for(int i = cpuModule.functionCallStack.size() - 1; i >= 0; i--){
                                System.out.printf("[%d] => 0x%04X\n", i, cpuModule.functionCallStack.get(i));
                            }

                        }
                        else System.out.println("Unknown command '" + x[0] + "'");
                    }
                }
            }

            case CPU.INT_STRING_CONCAT -> {
                int string1_address = registers[22]; // first string address: DI
                int string2_address = registers[23]; // second string address: DP
                int copy_address = registers[20]; // result will be copied to address: SS
                String concat = "";
                for(int i = string1_address; memory.readByte(i) != CPU.ARRAY_TERMINATOR; i++){
                    //System.out.println("Concatenating: 0x" + Integer.toHexString(i) + " => " + (char) memory[i]);
                    concat += (char) memory.readByte(i);
                }
                for(int i = string2_address; memory.readByte(i) != CPU.ARRAY_TERMINATOR; i++){
                    //System.out.println("Concatenating: 0x" + Integer.toHexString(i) + " => " + (char) memory[i]);
                    concat += (char) memory.readByte(i);
                }

                int index = 0;
                for(int i = copy_address; i < copy_address + concat.length(); i++){
                    memory.setMemory(i, concat.charAt(index), CPU.DATA_BYTE_MODE); index++;
                    memory.setMemory(i + 1, CPU.ARRAY_TERMINATOR, CPU.DATA_BYTE_MODE);
                }
            }

            case CPU.INT_STR_CPY -> {
                int strAddr = registers[20]; // original string address at : SS
                int strDest = registers[21]; // copy destination address at : SE

                for(int i = strAddr; memory.readByte(i) != CPU.ARRAY_TERMINATOR; i++){
                    int destination = strDest + (i - strAddr);
                    memory.setMemory(destination, memory.readByte(i), CPU.DATA_BYTE_MODE);
                    memory.setMemory(destination + 1, CPU.ARRAY_TERMINATOR, CPU.DATA_BYTE_MODE);
                }
            }

            case CPU.INT_MEM_CPY -> {
                int startAddress = registers[22]; // start address at: DI
                int destinationAddress = registers[23]; // destination address at: DP
                int numBytes = registers[15]; // number of bytes to copy at: DX

                for(int i = startAddress; i < startAddress + numBytes; i++){
                    memory.setMemory(destinationAddress + (i - startAddress), memory.readByte(i), CPU.DATA_BYTE_MODE);
                }
            }

            case CPU.INT_FILE -> {

                int read_write_addr = registers[22]; // the address where the file will be loaded / fetched : DI
                int file_path_addr = registers[20]; // the address of the file path : SS

                int operation = registers[0]; // the operation to perform : AL


                // for write operations
                // AL = 0x1 for CPU.FILE_WRITE
                // SS : file path
                // DI : the beginning of the file
                // DX : the number of bytes to write

                // for read operations
                // AL = 0x0 for CPU.FILE_READ
                // SS : file path
                // DI : the location the file will be loaded to
                // DX : the total number of bytes that were read will be placed here

                // append data to a file
                // AL = 0x2 for CPU.FILE_APPEND
                // SS : file path
                // DI : the beginning of the data to be appended
                // DX : the number of bytes to append

                // delete a file
                // AL = 0x3 for CPU.DELETE_FILE
                // SS : file path

                String fileName = "";
                for(int i = file_path_addr; memory.readByte(i) != CPU.ARRAY_TERMINATOR; i++) {
                    fileName += (char) memory.readByte(i);
                }

                if (operation == CPU.FILE_READ) {
                    byte[] file_data = diskDriver.readFile(fileName);

                    for(int i = 0; i < file_data.length; i++)
                        memory.setMemory(read_write_addr + i, file_data[i], CPU.DATA_BYTE_MODE);

                    registers[15] = file_data.length;
                }

                else if (operation == CPU.FILE_WRITE) {
                    int file_length = registers[15]; // the number of bytes to copy : DX
                    byte[] file_data = new byte[file_length];

                    for(int i = 0; i < file_data.length; i++) {
                        file_data[i] = (byte) memory.readByte(read_write_addr + i);
                    }
                    diskDriver.saveFile(fileName, file_data);
                }

                else if (operation == CPU.FILE_APPEND){
                    int file_length = registers[15];
                    byte[] file_data = new byte[file_length];
                    for(int i = 0; i < file_data.length; i++){
                        file_data[i] = (byte) memory.readByte(read_write_addr + i);
                    }
                    diskDriver.appendFile(fileName, file_data);
                }

                else if (operation == CPU.FILE_DELETE){

                    diskDriver.deleteFile(fileName);
                }
                else {
                    validInterrupt = false;
                }

            }

            default -> validInterrupt = false;
        }
        Logger.addLog("done. returning to original program.", logDevice);
        return validInterrupt;
    }

    public static boolean interruptHandler(long[] registers, short[] memory){
        return true;
    }



    // Get string prompt for 8-bit module
    private static String getInputMessage(short[] registers, MemoryModule memory) {
        int input_message_pointer = registers[8]; // string message stored at : SS
        String input_message = "";
        if (memory.readByte( input_message_pointer ) != CPU.ARRAY_TERMINATOR) {

            for (int i = input_message_pointer;
                 memory.readByte(i) != CPU.ARRAY_TERMINATOR || i - input_message_pointer >= cpuModule.MAX_STRING_LENGTH;
                 i++) {
                input_message += (char) memory.readByte(i);
            }
        } else input_message = "Input : "; // no message provided
        return input_message;
    }

    // Get string prompt for 16-bit module
    private static String getInputMessage(int[] registers, MemoryModule memory) {
        int input_message_pointer = registers[20]; // string message stored at : SS
        String input_message = "";
        if (memory.readByte(input_message_pointer) != CPU.ARRAY_TERMINATOR) {
            //System.out.println("Message stored at : 0x" + Integer.toHexString(input_message_pointer));
            for (int i = input_message_pointer;
                 memory.readByte(i) != CPU.ARRAY_TERMINATOR || i - input_message_pointer >= cpuModule.MAX_STRING_LENGTH;
                 i++) {
                //  System.out.println("adding char : " + (char) memory[i]);
                input_message += (char) memory.readByte(i);
            }
        } else input_message = "Input : "; // no message provided
        return input_message;
    }

}
