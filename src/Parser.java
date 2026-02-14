import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

class ParseResult {
    List<Instruction> programInstructions;
    List<ProgramData> data;
    HashMap<String, Integer> symbolTable;
    List<ParseError> errors;
    boolean succeeded;
    public ParseResult() {
        programInstructions = new ArrayList<>();
        symbolTable = new HashMap<>();
        data = new ArrayList<>();
        errors = new ArrayList<>();
        succeeded = false;
    }
}

class ParseError {
    int row, column;
    Token token;
    String errorMsg;
    int type;
    public static int ERR_CRITICAL = 0;
    public static int ERR_WARNING = 1;
    public ParseError(int row, int column, Token t, String e) {
        this.row = row; this.column = column;
        this.token = t;
        this.errorMsg = e;
    }
    @Override
    public String toString() {
        return String.format("%s @ %d,%d -> %s: %s",
                (type == ERR_WARNING) ? "Warning" : "Error", column, row, token.toString(), errorMsg);
    }
}

class ProgramData {
    String symbol;
    int size;
    int startAddress;
    int mode;

    public static int MODE_BYTE = 0;
    public static int MODE_WORD = 1;
    public ProgramData(String s, int size, int start, int mode) {
        this.symbol = s;
        this.size = size;
        this.startAddress = start;
        this.mode = mode;
    }
}

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
    private int dataBaseAddr = MemoryModule.data_start;
    private int dataOffset = 0;
    // This checks only the syntax for now. will add expression parsing later
    public ParseResult parseTokens(List<Token> tokenStream) {

        ParseResult result = new ParseResult();
        index = 0;
        offset = 0;
        lengthCounter = 0;

        tokens = tokenStream;

        while (index < tokens.size()) {

        }
        return result;
    }

    private List<Token> readCurrentLine() {
        List<Token> l = new ArrayList<>();
        while (tokens.get(index).mainType != TokenType.NEWLINE) l.add(tokens.get(index));
        return l;
    }

    private Token moveNext() {
        return tokens.get(index++);
    }
    private Token peekNext() {
        return tokens.get(index + 1);
    }
    private void skip() {index++;}
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
    private boolean matchSubTypes(SubType[] types, Token t) {
        for(SubType type : types) if (t.subType == type) return true;
        return false;
    }
}
