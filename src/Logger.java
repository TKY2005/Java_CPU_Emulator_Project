import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    static String singleLog;
    static StringBuilder logString = new StringBuilder();


    public static void addLog(String log){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("[yyyy/MM/dd HH:mm:ss]");
        LocalDateTime time = LocalDateTime.now();
        String timeNow = time.format(formatter);

        singleLog = timeNow + " " + log;
        logString.append(singleLog).append("\n");
    }

    public static void writeLogFile(String filepath){

        try {
            File file = new File(filepath);
            FileWriter writer = new FileWriter(file);
            PrintWriter printer = new PrintWriter(writer);

            printer.print(logString);
            printer.close();
            writer.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static void resetLogs(){
        logString = new StringBuilder();
        singleLog = "";
    }
}
