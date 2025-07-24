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
                    String hex = token.substring(1);
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
                else {
                    newLine.append(token).append(" ");
                }
            }

            result.append(newLine.toString().trim()).append("\n");
        }

        try {
            if (compileDirection == 0) cpuModule.machineCode = cpuModule.compileCode(result.toString());
            else if (compileDirection == 1) cpuModule.machineCode = cpuModule.compileToFileBinary(result.toString());
            System.out.println(cpuModule.dumpROM());
            ui = UIMode;
        }catch (RuntimeException e) {
            e.printStackTrace();
            err_msg = e.getMessage();
            readyToExecute = false;
            throw e;
        }
        readyToExecute = true;
    }

    public void executeCode(){
        cpuModule.executeCompiledCode(cpuModule.machineCode);
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
                    System.out.print(input_message); input = new Scanner(System.in).nextLine();
                }


                int write_address = registers[11]; // write_pointer_register : DI

                int index = 0;

                for(int i = write_address; i < write_address + input.length(); i++){

                    memory[i] = (short) input.charAt(index);
                    index++;
                }
                memory[write_address + input.length() + 1] = CPU.NULL_TERMINATOR;
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
                    System.out.print(input_message);
                    input = new Scanner(System.in).nextShort();
                }

                if (input <= 255) registers[3] = input; // if input fits 8-bits place in RD
                else registers[11] = input; // otherwise place in DI
            }

            case CPU.INT_DEBUG -> {
                Logger.addLog("Calling debug interrupt.");
                System.out.println("Press Enter to continue.");
                Scanner s = new Scanner(System.in);
                s.nextLine();
            }


            default -> validInterrupt = false;
        }
        Logger.addLog("done. returning to original program.");
        return validInterrupt;
    }

    private static String getInputMessage(short[] registers, short[] memory) {
        int input_message_pointer = registers[8]; // string message stored at : SS
        String input_message = "";
        if (memory[input_message_pointer] != CPU.NULL_TERMINATOR) {
            //System.out.println("Message stored at : 0x" + Integer.toHexString(input_message_pointer));
            for (int i = input_message_pointer; memory[i] != CPU.NULL_TERMINATOR; i++) {
                //  System.out.println("adding char : " + (char) memory[i]);
                input_message += (char) memory[i];
            }
        } else input_message = "Input : "; // no message provided
        return input_message;
    }

    private static String getInputMessage(int[] registers, short[] memory) {
        int input_message_pointer = registers[20]; // string message stored at : SS
        String input_message = "";
        if (memory[input_message_pointer] != CPU.NULL_TERMINATOR) {
            //System.out.println("Message stored at : 0x" + Integer.toHexString(input_message_pointer));
            for (int i = input_message_pointer; memory[i] != CPU.NULL_TERMINATOR; i++) {
                //  System.out.println("adding char : " + (char) memory[i]);
                input_message += (char) memory[i];
            }
        } else input_message = "Input : "; // no message provided
        return input_message;
    }

    // 16-BIT INTERRUPT HANDLER
    public static boolean interruptHandler(int[] registers, short[] memory){
         boolean validInterrupt = true;
        switch (registers[1]){ // interrupt register : RAH

            case CPU.INT_INPUT_STR -> {

                Logger.addLog("Calling interrupt for input string");
                String input_message = getInputMessage(registers, memory);
                //System.out.println("Message is : " + input_message);
                String input = "";
                if (ui){
                  //  System.out.println("Showing message for ui input");
                    input = JOptionPane.showInputDialog(null, input_message,
                            "Input", JOptionPane.INFORMATION_MESSAGE);

                }else{
                    System.out.print(input_message); input = new Scanner(System.in).nextLine();
                }


                int write_address = registers[22]; // write_pointer_register : DI

                int index = 0;

                for(int i = write_address; i < write_address + input.length(); i++){

                    memory[i] = (short) input.charAt(index);
                    index++;
                }
                memory[write_address + input.length() + 1] = CPU.NULL_TERMINATOR;
                registers[21] = (short) (write_address + input.length()); // string end position stored in SE

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
                    System.out.print(input_message);
                    input = new Scanner(System.in).nextShort();
                }

                registers[15] = input; // place input in RDX
            }

            case CPU.INT_DEBUG -> {
                Logger.addLog("Calling debug interrupt.");
                System.out.println("Press Enter to continue.");
                Scanner s = new Scanner(System.in);
                s.nextLine();
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
