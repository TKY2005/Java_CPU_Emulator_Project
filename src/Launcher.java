import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.HashMap;

public class Launcher{
    static String configFilePath = "./myEmulator.conf";
    static String version = "3.0";
    static HashMap<String, String> appConfig;
    static String configDefaultTemplate = String.format("""
            Version=%s
            MemSize=8
            OffsetSize=3
            StackSize=2
            Architecture=16
            WriteDump=false
            Cycles=200
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

    public static void main(String[] args) throws InstantiationException, IllegalAccessException {
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
                }
            }

        }catch (Exception e){
            e.printStackTrace();
        }
        CPU cpu = new CPUModule8BIT();
        Settings settings = new Settings("Hello wrold");
    }
}