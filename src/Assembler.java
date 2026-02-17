import java.util.List;
import java.util.Map;

public class Assembler {
    private static String source = "ASSEMBLER";
    public static void triggerCompilationError(String error) {
        Logger.addLog(error, source, true);
        System.exit(0);
    }

    public void compileToMemoryImage(String source, CPU target, String[] registerList, MemoryModule memory) {
        Tokenizer t = new Tokenizer();
        List<Token> tokens = t.tokenize(source, target, registerList);
        //for(Token tok : tokens) System.out.println(tok.toStringDebug());
        Parser p = new Parser();
        ParseResult result = p.parseTokens(tokens, memory);

        for(ProgramData d : result.data) System.out.println(d.toStringDebug());
        if (!ParseResult.errors.isEmpty()){
            System.out.println(ParseResult.errors.size() + " problem(s) has been detected.");
            for (ParseError e : ParseResult.errors) System.out.println(e.toString());
        }
        else System.out.println("No errors detected.");

        System.out.println("============Symbol table entries============");
        for(Map.Entry<String, Integer> entry : result.symbolTable.entrySet()) {
            System.out.printf("%s -> 0x%04X\n", entry.getKey(), entry.getValue());
        }
    }
}
