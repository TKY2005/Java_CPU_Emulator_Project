import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.swing.*;
import java.io.*;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Scanner;

public class VirtualMachine {

    private static CPU cpuModule;
    public boolean readyToExecute = false;
    public boolean UIMode = false;
    public static boolean ui = false;
    public byte compileDirection = 0;

    public String err_msg = "";

    static HardDiskDriver diskDriver;
    private int[] memImage;

    static int[] beepError = {950, 150, 100};
    static int[] beepOverflow = {900, 500};
    static int[] beepSuccess = {900, 150};

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

        StringBuilder fullCode = new StringBuilder();
        for(String line : lines){
            if (line.trim().split(" ")[0].equalsIgnoreCase("INCLUDE")){
                StringBuilder path = new StringBuilder();
                int path_start = line.indexOf('"');
                while (line.charAt(++path_start) != '"') path.append(line.charAt(path_start));

                try {
                    System.out.println("Adding file: " + path);
                    File file = new File(path.toString());
                    BufferedReader reader = new BufferedReader(new FileReader(file));
                    String x;
                    while ( (x = reader.readLine()) != null ){
                        if (x.equalsIgnoreCase(".MAIN")) {
                            String err = "Included files cannot have cannot have a MAIN function label.";
                            cpuModule.triggerProgramError(err, ErrorHandler.ERR_COMP_COMPILATION_ERROR);
                        }
                        else fullCode.append(x).append("\n");
                    }
                }catch (FileNotFoundException e) {
                    String err = String.format("INCLUDE statement failed. file '%s' not found.", path);
                    cpuModule.triggerProgramError(err, ErrorHandler.ERR_COMP_COMPILATION_ERROR);
                }
                catch (IOException e) {
                    String err = "Cannot read file...";
                    cpuModule.triggerProgramError(err, ErrorHandler.ERR_COMP_COMPILATION_ERROR);
                }
            } else fullCode.append(line).append("\n");
        }

        lines = fullCode.toString().split("\n");
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
                else if (token.startsWith(CPU.BIN_PREFIX)){
                    String bin = token.substring(2);
                    int decimal = Integer.parseInt(bin, 2);
                    newLine.append("!").append(decimal).append(" ");
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
            } catch (Exception ex) {
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

    public static void beep(int hz, int msecs){ // written by chatgpt
        try {
            float SAMPLE_RATE = 44100;
            byte[] buf = new byte[(int) SAMPLE_RATE * msecs / 1000];
            for (int i = 0; i < buf.length; i++) {
                double angle = i / (SAMPLE_RATE / hz) * 2.0 * Math.PI;
                buf[i] = (byte)(Math.sin(angle) * 127);
            }

            AudioFormat af = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
            try (SourceDataLine sdl = AudioSystem.getSourceDataLine(af)) {
                sdl.open(af);
                sdl.start();
                sdl.write(buf, 0, buf.length);
                sdl.drain();
                sdl.stop();
            }
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }
    }
}
