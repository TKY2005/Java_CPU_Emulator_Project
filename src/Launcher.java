import javax.swing.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import org.apache.commons.cli.*;

/*
    Java simple CPU emulator
    T.K.Y
    last updated: October 17 2025
 */

public class Launcher{
    static String configFilePath = "./myEmulator.conf";
    static String version = "3.7";
    static HashMap<String, String> appConfig;

    static Option architectureOption =
                    Option.builder("a")
                            .longOpt("architecture")
                            .hasArg(true)
                            .argName("ARCHITECTURE")
                            .required(false)
                            .desc("Decide which CPU architecture to run the current process.").get();

    static Option memOption =
            Option.builder("m")
                    .longOpt("memory")
                    .argName("MEMORYKB")
                    .hasArg(true)
                    .required(false)
                    .desc("Specify the running memory size in Kilobytes.")
                    .get();

    static Option memByteOption =
            Option.builder("b")
                    .longOpt("bytes")
                    .argName("MEMORYBYTES")
                    .hasArg(true)
                    .required(false)
                    .desc("Specify the running memory size in bytes")
                    .get();

    static Option helpOption =
            Option.builder("h")
                    .longOpt("help")
                    .desc("Display this help message.")
                    .get();

    static String configDefaultTemplate = String.format("""
            Version=%s
            MemSize=8
            ROMPercentage=35
            DataPercentage=55
            StackPercentage=10
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

    static void checkFlags(Options options, CommandLine cmd, HelpFormatter formatter) throws IOException {
        if (cmd.hasOption("m"))
        {
            System.out.println();
            appConfig.replace("MemSize", cmd.getOptionValue("m"));
            System.out.println("Starting with custom size in KB: " + appConfig.get("MemSize"));
        }
        if (cmd.hasOption("h"))
        {
            formatter.printHelp("cli-example", "TKY CPU EMULATOR", options, null, false);
        }
        if (cmd.hasOption("b"))
        {
            int sizeB = Integer.parseInt(cmd.getOptionValue("b"));
            float sizeKB = (sizeB / 1024f);
            appConfig.replace("MemSize", Float.toString(sizeKB));
            System.out.println("Starting with custom size in bytes: " + sizeB);
        }
        if (cmd.hasOption("a"))
        {
            appConfig.replace("Architecture", cmd.getOptionValue("a"));
            System.out.println("Starting with defined architecture: " + appConfig.get("Architecture"));
        }
    }

    public static void main(String[] args) throws IOException {
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

        Options options = new Options();
        options.addOption(architectureOption);
        options.addOption(memOption);
        options.addOption(memByteOption);
        options.addOption(helpOption);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = HelpFormatter.builder().get();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            e.printStackTrace();
            formatter.printHelp("cli-example", "TKY CPU EMULATOR", options, null, false);
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
            checkFlags(options, cmd, formatter);
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
            checkFlags(options, cmd, formatter);
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
                    CLI path/to/binary_file.tky
                    COMPILE -> /path/to/source_code_file.ast /path/to/output_file.tky
                    DECOMPILE /path/to/binary_file.tky /path/to/output_file.ast -> disassemble the given binary file.
                    """);
            formatter.printHelp("cli-example", "TKY CPU EMULATOR", options, null, false);
        }
    }
}