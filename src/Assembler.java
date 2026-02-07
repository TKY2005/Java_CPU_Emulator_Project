public class Assembler {
    private static String source = "ASSEMBLER";
    public static void triggerCompilationError(String error) {
        Logger.addLog(error, source, true);
        System.exit(0);
    }
}
