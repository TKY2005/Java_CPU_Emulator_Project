import javax.swing.*;
import java.util.Scanner;

public class VirtualMachine {

    private static CPU cpuModule;
    public boolean readyToExecute = false;
    public boolean UIMode = false;
    public static boolean ui = false;
    public byte compileDirection = 0;

    public String err_msg = "";

    public VirtualMachine(CPU cpuModule){
        System.out.println("Starting the virtual machine with the selected CPU module.");
        this.cpuModule = cpuModule;
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
                else {
                    newLine.append(token).append(" ");
                }
            }

            result.append(newLine.toString().trim()).append("\n");
        }

        try {
            if (compileDirection == 0) cpuModule.machineCode = cpuModule.compileCode(result.toString());
            else if (compileDirection == 1) cpuModule.machineCode = cpuModule.compileToFileBinary(result.toString());


            ui = UIMode;
        }catch (RuntimeException e) {
            e.printStackTrace();
            err_msg = e.getMessage();
            System.out.println(err_msg);
            readyToExecute = false;
            throw e;
        }
        readyToExecute = true;
    }

    public void executeCode(){
        try {
            cpuModule.executeCompiledCode(cpuModule.machineCode);
            System.out.println(cpuModule.output);
        } catch (Exception e){
            System.out.println(e.getMessage());
        }
    }

    public void resetCPU(){
        cpuModule.reset();

        cpuModule.canExecute = true;
        cpuModule.programEnd = false;
        readyToExecute = false;
    }

    // 8-BIT INTERRUPT HANDLER
    public static boolean interruptHandler(short[] registers, short[] memory){
        boolean validInterrupt = true;
        switch (registers[0]){ // interrupt register : RA

            case CPU.INT_INPUT_STR -> {

                Logger.addLog("Calling interrupt for input string");
                String input_message = getInputMessage(registers, memory);
                //System.out.println("Message is : " + input_message);
                String input = "";
                if (ui){ // temporary
                  //  System.out.println("Showing message for ui input");
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

                    memory[i] = (short) input.charAt(index);
                    index++;
                }
                memory[write_address + input.length()] = CPU.ARRAY_TERMINATOR;
                registers[9] = (short) (write_address + input.length()); // string end position stored in SE

            }

            case CPU.INT_INPUT_NUM -> {
                Logger.addLog("Calling interrupt for numeric input");
                String input_message = getInputMessage(registers, memory);

                short input;

                if (ui){
                    Logger.addLog("Showing ui input prompt");
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
                Logger.addLog("Calling debug interrupt.");
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
                            System.out.println(cpuModule.dumpMemoryDebug(address));
                        } else if (x[0].equals("g")) debugPause = false;
                        else System.out.println("Unknown command '" + x[0] + "'");
                    }
                }
            }


            default -> validInterrupt = false;
        }
        Logger.addLog("done. returning to original program.");
        return validInterrupt;
    }

    private static String getInputMessage(short[] registers, short[] memory) {
        int input_message_pointer = registers[8]; // string message stored at : SS
        String input_message = "";
        if (memory[input_message_pointer] != CPU.ARRAY_TERMINATOR) {
            //System.out.println("Message stored at : 0x" + Integer.toHexString(input_message_pointer));
            for (int i = input_message_pointer; memory[i] != CPU.ARRAY_TERMINATOR; i++) {
                //  System.out.println("adding char : " + (char) memory[i]);
                input_message += (char) memory[i];
            }
        } else input_message = "Input : "; // no message provided
        return input_message;
    }

    private static String getInputMessage(int[] registers, short[] memory) {
        int input_message_pointer = registers[20]; // string message stored at : SS
        String input_message = "";
        if (memory[input_message_pointer] != CPU.ARRAY_TERMINATOR) {
            //System.out.println("Message stored at : 0x" + Integer.toHexString(input_message_pointer));
            for (int i = input_message_pointer; memory[i] != CPU.ARRAY_TERMINATOR; i++) {
                //  System.out.println("adding char : " + (char) memory[i]);
                input_message += (char) memory[i];
            }
        } else input_message = "Input : "; // no message provided
        return input_message;
    }

    // 16-BIT INTERRUPT HANDLER
    public static boolean interruptHandler(int[] registers, short[] memory){
         boolean validInterrupt = true;
        switch (registers[1]){ // interrupt register: AH

            case CPU.INT_INPUT_STR -> {

                int mode = registers[0]; // store mode register: AL
                if (mode != CPU.DATA_BYTE_MODE && mode != CPU.DATA_WORD_MODE) mode = CPU.DATA_BYTE_MODE;

                Logger.addLog("Calling interrupt for input string");
                String input_message = getInputMessage(registers, memory);

                String input = "";
                if (ui){
                    Logger.addLog("Showing message for ui input");
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


                int write_address = registers[22]; // write position register: DI

                int index = 0;

                if (mode == CPU.DATA_BYTE_MODE) {
                    for (int i = write_address; i < write_address + input.length(); i++) {
                        memory[i] = (short) input.charAt(index);
                        index++;
                    }
                }
                else if (mode == CPU.DATA_WORD_MODE){
                    for(int i = write_address; i < write_address + input.length() * 2; i += 2){
                        short low = (short) (input.charAt(index) & 0xff);
                        short high = (short) ((input.charAt(index) >> 8) & 0xff);
                        //System.out.printf("processing word char %c -> low: 0x%X, high 0x%X\n", input.charAt(index), low, high);
                        memory[i + 1] = high;
                        memory[i] = low;
                        index++;
                    }
                }

                int endPosition = 0;
                if (mode == CPU.DATA_BYTE_MODE) endPosition = write_address + input.length();
                else if (mode == CPU.DATA_WORD_MODE ) endPosition = write_address + input.length() * 2;

                memory[endPosition] = CPU.ARRAY_TERMINATOR;

                registers[21] = endPosition; // string end position stored in SE

            }

            case CPU.INT_INPUT_NUM -> {
                Logger.addLog("Calling interrupt for numeric input");
                String input_message = getInputMessage(registers, memory);

                int input;

                if (ui){
                    Logger.addLog("Showing ui input prompt");
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
                Logger.addLog("Calling debug interrupt.");
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
                            System.out.println(cpuModule.dumpMemoryDebug(address));
                        } else if (x[0].equals("g")) debugPause = false;
                        else System.out.println("Unknown command '" + x[0] + "'");
                    }
                }
            }


            default -> validInterrupt = false;
        }
        Logger.addLog("done. returning to original program.");
        return validInterrupt;
    }

    public static boolean interruptHandler(long[] registers, short[] memory){
        return true;
    }

}
