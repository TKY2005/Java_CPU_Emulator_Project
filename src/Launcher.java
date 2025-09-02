import javax.swing.*;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.HashMap;

/*
    Java simple CPU emulator
    T.K.Y
    last updated: August 22 2025
 */

public class Launcher{
    static String configFilePath = "./myEmulator.conf";
    static String version = "3.2";
    static HashMap<String, String> appConfig;
    static String configDefaultTemplate = String.format("""
            Version=%s
            MemSize=8
            DataPercentage=65
            StackPercentage=35
            Architecture=16
            WriteDump=false
            Cycles=200
            OverwritePC=false
            UiUpdateInterval=35
            """, version);

    static void createConfigFile(){
        try{
            File file = new File(configFilePath);
            FileWriter writer = new FileWriter(file);
            PrintWriter printer = new PrintWriter(writer);
            printer.print(configDefaultTemplate);

            printer.close();
            writer.close();

        }catch (Exception e) {e.printStackTrace();}
    }

    static void validateSettings(){
        try{
            Float.parseFloat(appConfig.get("MemSize"));
        }catch (Exception e) {
            triggerLaunchError(String.format("Invalid memory size: %s", appConfig.get("MemSize")));
        }

        int dataSize = 0, stackSize = 0;
        String[] current = {"DATA", ""};
        try{
            current[1] = appConfig.get("DataPercentage");
            dataSize = Integer.parseInt(appConfig.get("DataPercentage"));
            current[0] = "STACK"; current[1] = appConfig.get("StackPercentage");
            stackSize = Integer.parseInt(appConfig.get("StackPercentage"));
        }catch (Exception e){
            triggerLaunchError(String.format("Invalid %s percentage %s%%", current[0], current[1]));
        }

        if (dataSize + stackSize > 100) triggerLaunchError(String.format("Invalid data and stack percentages: %s%%",
                dataSize + stackSize));

        String architecture = appConfig.get("Architecture");
        boolean valid = switch (architecture){
            case "8", "16", "32", "64" -> true;
            default -> false;
        };

        if (!valid) triggerLaunchError("Invalid architecture: " + architecture);

        String dump = appConfig.get("WriteDump");
        valid = switch (dump.toLowerCase()) {
            case "true", "false" -> true;
            default -> false;
        };

        if (!valid) triggerLaunchError("Invalid option for WriteDump=" + appConfig.get("WriteDump"));

        try {
            Integer.parseInt(appConfig.get("Cycles"));
        }catch (Exception e) {triggerLaunchError("Invalid cycle count: " + appConfig.get("Cycles"));}

        String overwrite = appConfig.get("OverwritePC");
        valid = switch (overwrite.toLowerCase()){
            case "true", "false" -> true;
            default -> false;
        };

        if (!valid) triggerLaunchError("Invalid option for OverwritePC=" + appConfig.get("OverwritePC"));

        try {
            Integer.parseInt(appConfig.get("UiUpdateInterval"));
        }catch (Exception e) {triggerLaunchError("Invalid ui interval count : " + appConfig.get("UiUpdateInterval"));}
    }

    static void triggerLaunchError(String errMsg){
        try{
            JOptionPane.showMessageDialog(null, errMsg, "Error", JOptionPane.ERROR_MESSAGE);
        }catch (Exception e){ System.out.println(errMsg);}
        System.exit(-1);
    }

    public static void main(String[] args) {
        appConfig = new HashMap<>();
        try{
            File file = new File(configFilePath);

            if (!file.exists()){
                System.out.println("Config file not found. Creating new one.");

                createConfigFile();
                appConfig = Settings.loadSettings();
            }
            else{
                appConfig = Settings.loadSettings();
                if (!appConfig.get("Version").equals(version)){
                    System.out.println("App Versions don't match. Rewriting the config file");
                    createConfigFile();
                    appConfig = Settings.loadSettings();
                }
            }
            validateSettings();

        }catch (Exception e){
            e.printStackTrace();
        }

        if (args.length == 0 ){
            System.out.println("No arguments. Going into UI mode.");
            new UI("T.K.Y CPU Emulator V" + Launcher.version);
        }


        else if (args[0].equalsIgnoreCase("cli")){
            System.out.println("Starting in CLI mode.");
            if (args[1] == null){
                System.out.println("Please enter the binary file path.");
                System.exit(-1);
            }
            String filePath = args[1];
            if (args.length >= 3){
                appConfig.put("Architecture", args[2]);
            }
            if (args.length >= 4){
                appConfig.put("MemSize", args[3]);
            }
            validateSettings();
            new CLI(filePath);
            System.exit(0);
        }

        else if (args[0].equalsIgnoreCase("compile")){

            if (args[1] == null || args[2] == null){
                System.out.println("Please provide the source code file path and the output binary path");
                System.exit(1);
            }



            String sourceCodeFilePath = args[1];
            String outputPath = args[2];
            if (args.length >= 4) {
                String architecture = args[3];
                appConfig.put("Architecture", architecture);
                System.out.println("Compiling for " + architecture + "-bit module");
            }

            if (args.length >= 5){
                String memSize = args[4];
                appConfig.put("MemSize", memSize);
                System.out.println("Compiling with " + memSize + "KB of memory.");
            }

            else if (args.length == 3){
                System.out.println("Compiling for architecture present in config file.");
            }

            validateSettings();
            new CLICompiler(sourceCodeFilePath, outputPath);
            System.exit(0);
        }

        else if (args[0].equalsIgnoreCase("decompile")){
            if (args[1] == null || args[2] == null){
                System.out.println("Please provide the path to the binary file and the output file.");
                System.exit(1);
            }

            String binaryFilePath = args[1];
            String outputFilePath = args[2];

            new Disassembler(binaryFilePath, outputFilePath);
            System.exit(0);
        }
        else{
            System.out.println("""
                    Available commands:
                    None -> go into UI
                    CLI path/to/binary_file.tky architecture(optional) memsize(optional) -> execute a binary file with the CPU config in the config file.
                    COMPILE -> /path/to/source_code_file.ast /path/to/output_file.tky architecture(optional) memsize(optional) -> compile source code to binary file.
                    DECOMPILE /path/to/binary_file.tky /path/to/output_file.ast -> disassemble the given binary file.
                    """);
        }
    }
}