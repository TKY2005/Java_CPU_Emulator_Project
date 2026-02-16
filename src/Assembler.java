import java.util.List;

public class Assembler {
    private static String source = "ASSEMBLER";
    public static void triggerCompilationError(String error) {
        Logger.addLog(error, source, true);
        System.exit(0);
    }

    public void compileToMemoryImage(String source, CPU target, String[] registerList) {
        Tokenizer t = new Tokenizer();
        List<Token> tokens = t.tokenize(source, target, registerList);
        //for(Token tok : tokens) System.out.println(tok.toStringDebug());
        Parser p = new Parser();
        ParseResult result = p.parseTokens(tokens);

        for(ProgramData d : result.data) System.out.println(d.toStringDebug());
    }
}
