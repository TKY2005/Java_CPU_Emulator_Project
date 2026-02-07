import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

class Instruction {
    List<Token> parts;
    int length;
    int address;
    public Instruction(List<Token> parts, int length, int address) {
        this.parts = parts;
        this.length = length;
        this.address = address;
    }
    public Instruction(List<Token> parts) {
        this.parts = parts;
    }
}

public class Parser {
    private List<Token> tokens;
    private int index;
    List<Token> instructionTokens;
    private int offset = 0;
    private int lengthCounter = 0;
    // This checks only the syntax for now. will add expression parsing later
    public List<Instruction> parseTokens(List<Token> tokenStream) {
        List<Instruction> result = new ArrayList<>();

        index = 0;
        offset = 0;
        lengthCounter = 0;

        tokens = tokenStream;
        instructionTokens = new ArrayList<>();
        HashMap<String, Integer> symbolMap = new HashMap<>();

        while (index < tokens.size()) {

            offset += lengthCounter;
        }
        return result;
    }

    private Token moveNext() {
        return tokens.get(index++);
    }
    private Token peekNext() {
        return tokens.get(index + 1);
    }
    private void skipNext() {index++;}
    private void consumeToken() {instructionTokens.add(tokens.get(index++));}

    private void parseInstruction(List<Token> insTokens) {
        // an instruction can have from 0 to 2 operands that are separated by a comma
        // a memory address can consist of a variable amount of tokens surrounded in blocky braces []
        // the destination operand shall be a register or a memory address and nothing else
        // none of the operands can be a string literal
        // instruction opcodes are 1 byte long
        // register operands, immediate operands, and label operands are 2 bytes long
        // memory addressing can have variable length operands
    }

    private boolean matchTypes(TokenType[] types, Token t) {
        for(TokenType type : types) if (t.mainType == type) return true;
        return false;
    }
}
