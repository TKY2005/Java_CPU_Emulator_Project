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
    static String version = "3.8";
    static HashMap<String, String> appConfig;

    static boolean ignoreVersionCheck = false;

    private static final Option[] optionList = {
            Option.builder("a")
                            .longOpt("architecture")
                            .hasArg(true)
                            .argName("ARCHITECTURE")
                            .required(false)
                            .desc("Decide which CPU architecture to run the current process.").get(),

            Option.builder("m")
                    .longOpt("memory")
                    .argName("MEMORYKB")
                    .hasArg(true)
                    .required(false)
                    .desc("Specify the running memory size in Kilobytes.")
                    .get(),

            Option.builder("b")
                    .longOpt("bytes")
                    .argName("MEMORYBYTES")
                    .hasArg(true)
                    .required(false)
                    .desc("Specify the running memory size in bytes")
                    .get(),

            Option.builder("r")
                    .longOpt("rom")
                    .argName("ROMSIZE")
                    .hasArg(true)
                    .required(false)
                    .desc("Specify the starting rom size.")
                    .get(),

            Option.builder("d")
                    .longOpt("data")
                    .argName("DATASIZE")
                    .hasArg(true)
                    .required(false)
                    .desc("Specify the starting data size.")
                    .get(),

            Option.builder("s")
                    .longOpt("stack")
                    .argName("STACKSIZE")
                    .hasArg(true)
                    .required(false)
                    .desc("Specify the starting stack size.")
                    .get(),

            Option.builder("o")
                    .longOpt("overflow-protection")
                    .hasArg(true)
                    .desc("Enable the overflow protection mechanism for the CPU")
                    .required(false)
                    .get(),

            Option.builder("rb")
                            .longOpt("rom-bytes")
                            .hasArg(true)
                            .required(false)
                            .desc("define rom size in bytes")
                            .get(),

            Option.builder("db")
                            .longOpt("data-bytes")
                            .hasArg(true)
                            .required(false)
                            .desc("define data size in bytes")
                            .get(),
            Option.builder("sb")
                            .longOpt("stack-bytes")
                            .hasArg(true)
                            .required(false)
                            .desc("define stack size in bytes")
                            .get(),



            Option.builder("ivc")
                            .longOpt("ignore-version-check")
                            .hasArg(false)
                            .required(false)
                            .desc("Ignore version checks when running files")
                            .get(),

            Option.builder("h")
                    .longOpt("help")
                    .desc("Display this help message.")
                    .get()
    };

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
            OverFlowProtection=true
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

        float dataSize = 0, stackSize = 0;
        String[] current = {"DATA", ""};
        try{
            current[1] = appConfig.get("DataPercentage");
            dataSize = Float.parseFloat(appConfig.get("DataPercentage"));
            current[0] = "STACK"; current[1] = appConfig.get("StackPercentage");
            stackSize = Float.parseFloat(appConfig.get("StackPercentage"));
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

        String overflow = appConfig.get("OverFlowProtection");
        valid = switch (overflow.toLowerCase()){
            case "true", "false" -> true;
            default -> false;
        };

        if (!valid) triggerLaunchError("Invalid option for OverFlowProtection=" + appConfig.get("OverFlowProtection"));

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
            int sizeB = getParsedInt(cmd.getOptionValue("b"));
            float sizeKB = (sizeB / 1024f);
            appConfig.replace("MemSize", Float.toString(sizeKB));
            System.out.println("Starting with custom size in bytes: " + sizeB);
        }

        if (cmd.hasOption("r")){
            appConfig.replace("ROMPercentage", cmd.getOptionValue("r"));
            System.out.println("Starting with custom ROM percentage:" + cmd.getOptionValue("r"));
        }
        if (cmd.hasOption("s")){
            appConfig.replace("StackPercentage", cmd.getOptionValue("s"));
            System.out.println("Starting with custom STACK percentage:" + cmd.getOptionValue("s"));
        }
        if (cmd.hasOption("d")){
            appConfig.replace("DataPercentage", cmd.getOptionValue("d"));
            System.out.println("Starting with custom DATA percentage:" + cmd.getOptionValue("d"));
        }

        if (cmd.hasOption("rb")){
            int memsizeB = (int) (Float.parseFloat(appConfig.get("MemSize")) * 1024);
            float percentage =
                    ( (float) getParsedInt(cmd.getOptionValue("rb")) / memsizeB ) * 100;
            appConfig.replace("ROMPercentage", Float.toString(percentage));
            System.out.println("Starting with custom rom byte size: " + cmd.getOptionValue("rb") + " percentage: " + percentage);
        }
        if (cmd.hasOption("db")){
            int memsizeB = (int) (Float.parseFloat(appConfig.get("MemSize")) * 1024);
            float percentage = ( (float) getParsedInt(cmd.getOptionValue("db")) / memsizeB ) * 100;
            appConfig.replace("DataPercentage", Float.toString(percentage));
            System.out.println("Starting with custom data byte size: " + cmd.getOptionValue("db") + " percentage: " + percentage);
        }
        if (cmd.hasOption("sb")){
            int memsizeB = (int) (Float.parseFloat(appConfig.get("MemSize")) * 1024);
            float percentage = ( (float) getParsedInt(cmd.getOptionValue("sb")) / memsizeB ) * 100;
            appConfig.replace("StackPercentage", Float.toString(percentage));
            System.out.println("Starting with custom stack byte size: " + cmd.getOptionValue("sb") + " percentage: " + percentage);
        }

        if (cmd.hasOption("a"))
        {
            appConfig.replace("Architecture", cmd.getOptionValue("a"));
            System.out.println("Starting with defined architecture: " + appConfig.get("Architecture"));
        }
        if (cmd.hasOption("o")){
            String state = cmd.getOptionValue("o");
            appConfig.replace("OverFlowProtection", state);
            System.out.println("Setting overflow protection to: " + state);
        }

        if (cmd.hasOption("ivc")){
            ignoreVersionCheck = true;
        }
    }

    private static int getParsedInt(String numString) {
        if (numString.endsWith("h")) return Integer.parseInt(numString.substring(0, numString.length() - 1), 16);
        else return Integer.parseInt(numString);
    }

    private void adjustOtherSliders(int changedValue, int changedSlider) {
        int remaining = 100 - changedValue;

        // Get current values of the other two sliders
        int other1, other2;
        if (changedSlider == 0) { // ROM changed
            other1 = Integer.parseInt(appConfig.get("DataPercentage"));
            other2 = Integer.parseInt(appConfig.get("StackPercentage"));
        } else if (changedSlider == 1) { // Data changed
            other1 = Integer.parseInt(appConfig.get("ROMPercentage"));
            other2 = Integer.parseInt(appConfig.get("StackPercentage"));
        } else { // Stack changed
            other1 = Integer.parseInt(appConfig.get("ROMPercentage"));
            other2 = Integer.parseInt(appConfig.get("DataPercentage"));
        }

        int totalOther = other1 + other2;

        if (totalOther == 0) {
            // If both others are 0, split remaining equally
            other1 = remaining / 2;
            other2 = remaining - other1;
        } else {
            // Distribute remaining proportionally
            other1 = (other1 * remaining) / totalOther;
            other2 = remaining - other1;

            // Ensure no negative values and handle rounding errors
            if (other1 < 0) other1 = 0;
            if (other2 < 0) other2 = 0;

            // Re-adjust if there's still remaining due to rounding
            int actualTotal = other1 + other2;
            if (actualTotal != remaining) {
                if (changedSlider == 0) {
                    other1 += (remaining - actualTotal);
                } else if (changedSlider == 1) {
                    other2 += (remaining - actualTotal);
                } else {
                    other1 += (remaining - actualTotal);
                }
            }
        }

        if (changedSlider == 0){
            appConfig.replace("ROMPercentage", Integer.toString(changedValue));
            appConfig.replace("DataPercentage", Integer.toString(other1));
            appConfig.replace("StackPercentage", Integer.toString(other2));
        }
        else if (changedSlider == 1){
            appConfig.replace("DataPercentage", Integer.toString(changedValue));
            appConfig.replace("ROMPercentage", Integer.toString(other1));
            appConfig.replace("StackPercentage", Integer.toString(other2));
        }
        else{
            appConfig.replace("StackPercentage", Integer.toString(changedValue));
            appConfig.replace("ROMPercentage", Integer.toString(other1));
            appConfig.replace("DataPercentage", Integer.toString(other2));
        }
    }

    public static void main(String[] args) throws IOException {

        try {
            appConfig = new HashMap<>();
            try {
                File file = new File(configFilePath);

                if (!file.exists()) {
                    System.out.println("Config file not found. Creating new one.");

                    createConfigFile();
                    appConfig = Settings.loadSettings();
                } else {
                    appConfig = Settings.loadSettings();
                    if (!appConfig.get("Version").equals(version)) {
                        System.out.println("App Versions don't match. Rewriting the config file");
                        createConfigFile();
                        appConfig = Settings.loadSettings();
                    }
                }
                validateSettings();

            } catch (Exception e) {
                e.printStackTrace();
            }

            Options options = new Options();
            for (int i = 0; i < optionList.length; i++) options.addOption(optionList[i]);

            CommandLineParser parser = new DefaultParser();
            HelpFormatter formatter = HelpFormatter.builder().get();
            CommandLine cmd = null;

            try {
                cmd = parser.parse(options, args);
            } catch (ParseException e) {
                e.printStackTrace();
                formatter.printHelp("cli-example", "TKY CPU EMULATOR", options, null, false);
            }

            if (args.length == 0) {
                System.out.println("No arguments. Going into UI mode.");
                new UI("T.K.Y CPU Emulator V" + Launcher.version);
            } else if (args[0].equalsIgnoreCase("cli")) {
                System.out.println("Starting in CLI mode.");
                if (args[1] == null) {
                    System.out.println("Please enter the binary file path.");
                    System.exit(-1);
                }
                String filePath = args[1];
                checkFlags(options, cmd, formatter);
                validateSettings();
                new CLI(filePath);
                System.exit(0);
            } else if (args[0].equalsIgnoreCase("compile")) {

                if (args[1] == null || args[2] == null) {
                    System.out.println("Please provide the source code file path and the output binary path");
                    System.exit(1);
                }

                String sourceCodeFilePath = args[1];
                String outputPath = args[2];
                checkFlags(options, cmd, formatter);
                validateSettings();

                new CLICompiler(sourceCodeFilePath, outputPath);
                System.exit(0);
            } else if (args[0].equalsIgnoreCase("decompile")) {
                if (args[1] == null || args[2] == null) {
                    System.out.println("Please provide the path to the binary file and the output file.");
                    System.exit(1);
                }

                String binaryFilePath = args[1];
                String outputFilePath = args[2];

                new Disassembler(binaryFilePath, outputFilePath);
                System.exit(0);
            } else {
                System.out.println("""
                        Available commands:
                        None -> go into UI
                        CLI path/to/binary_file.tky
                        COMPILE -> /path/to/source_code_file.ast /path/to/output_file.tky
                        DECOMPILE /path/to/binary_file.tky /path/to/output_file.ast -> disassemble the given binary file.
                        """);
                formatter.printHelp("cli-example", "TKY CPU EMULATOR", options, null, false);
            }
        } catch (Exception e) {e.printStackTrace();}
        finally {
            try {
                InterruptHandler.shutdownKeyboardListener();
                InterruptHandler.restoreTTYCanonical();
            }catch (Exception e) {e.printStackTrace();}
        }
    }
}