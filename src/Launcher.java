import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.Writer;
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
            OffsetPercentage=65
            StackPercentage=35
            Architecture=16
            WriteDump=false
            Cycles=200
            OverwritePC=false
            UiUpdateInterval=50
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
            String architecture;
            if (args.length == 3){
                architecture = args[2];
                new CLI(filePath, architecture);
            }
            else new CLI(filePath);
            System.exit(0);
        }

        else if (args[0].equalsIgnoreCase("compile")){

            if (args[1] == null || args[2] == null){
                System.out.println("Please provide the source code file path and the output binary path");
                System.exit(1);
            }


            String sourceCodeFilePath = args[1];
            String outputPath = args[2];
            if (args.length == 4) {
                String architecture = args[3];
                System.out.println("Compiling for " + architecture + "-bit module");
                new CLICompiler(sourceCodeFilePath, outputPath, architecture);
            }

            else if (args.length == 3){
                System.out.println("Compiling for architecture present in config file.");
                new CLICompiler(sourceCodeFilePath, outputPath);
            }

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
                    CLI path/to/binary_file.tky -> execute a binary file with the CPU config in the config file.
                    COMPILE -> /path/to/source_code_file.ast /path/to/output_file.tky (optional)architecture -> compile source code to binary file.
                    DECOMPILE /path/to/binary_file.tky /path/to/output_file.ast -> disassemble the given binary file.
                    """);
        }
    }
}