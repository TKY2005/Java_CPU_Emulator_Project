import java.util.ArrayList;
import java.util.List;

class Instruction {
    List<Token> parts;
}

public class Parser {
    private List<Token> tokens;
    private int index;
    List<Token> instructionTokens;
    // This checks only the syntax for now. will add expression parsing later
    public List<Instruction> parseTokens(List<Token> tokenStream) {
        index = 0;
        tokens = tokenStream;
        instructionTokens = new ArrayList<>();
        while (index < tokens.size()) {
            Token current = tokens.get(index);

            if (current.mainType == TokenType.INSTRUCTION) {

            }
        }
    }

    private Token moveNext() {
        return tokens.get(index++);
    }
    private Token peekNext() {
        return tokens.get(index + 1);
    }
    private void consumeToken() {instructionTokens.add(tokens.get(index++));}
}
