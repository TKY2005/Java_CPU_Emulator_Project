import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

class ParseResult {
    List<Instruction> programInstructions;
    List<ProgramData> data;
    HashMap<String, Integer> symbolTable;
    static List<ParseError> errors;
    static boolean succeeded;
    public ParseResult() {
        programInstructions = new ArrayList<>();
        symbolTable = new HashMap<>();
        data = new ArrayList<>();
        errors = new ArrayList<>();
        succeeded = true;
    }
}

class ParseError {
    int row, column;
    Token token;
    String errorMsg;
    int type;

    public static int ERR_CRITICAL = 0;
    public static int ERR_WARNING = 1;
    public ParseError(int row, int column, Token t, String e, int type) {
        this.row = row; this.column = column;
        this.token = t;
        this.errorMsg = e;
        this.type = type;
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
    List<Token> data;

    public static int MODE_BYTE = 1;
    public static int MODE_WORD = 2;
    public ProgramData(String s, int size, int start, int mode, List<Token> data) {
        this.symbol = s;
        this.size = size;
        this.startAddress = start;
        this.mode = mode;
        this.data = data;
    }

    public String toStringDebug() {
        return String.format("""
                [DATA]
                symbol: %s
                size: %d
                address: 0x%04X
                type: %s
                """, symbol, size, startAddress, (mode == MODE_BYTE) ? "byte" : "word");
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
    private final int codeOffset = MemoryModule.rom_end;

    private int dataOffset = 0;
    // This checks only the syntax for now. will add expression parsing later
    public ParseResult parseTokens(List<Token> tokenStream) {

        ParseResult result = new ParseResult();
        index = 0;
        offset = 0;
        lengthCounter = 0;

        tokens = tokenStream;

        while (index < tokens.size()) {
            Token current = tokens.get(index);

            if (current.subType == SubType.SPEC_DATA) {
                skip();
                while (tokens.get(index).subType != SubType.SPEC_END) {
                    List<Token> line = readCurrentLine();
                    if (line != null) {
                        if (line.getFirst().subType == SubType.DIR_ORG) {
                            dataOffset = 0;
                            dataBaseAddr = codeOffset + Integer.parseInt(line.get(1).lexeme);
                        } else {
                            ProgramData d = parseDataEntry(line);
                            result.data.add(d);
                        }
                    }
                }
                return result;
            }
            else continue;
        }
        return result;
    }

    private List<Token> readCurrentLine() {
        List<Token> l = new ArrayList<>();
        while (tokens.get(index).mainType != TokenType.NEWLINE){
            l.add(tokens.get(index));
            index++;
        }
        if (l.isEmpty()){
            index++;
            return null;
        }
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

    private ProgramData parseDataEntry(List<Token> l) {
        String name = "";
        int size = 0, startAddr = dataBaseAddr + dataOffset, mode = 0;
        List<Token> data = new ArrayList<>();

        // first token of a data entry must be the label/identifier //
        if (l.getFirst().mainType != TokenType.LABEL) {
            ParseResult.errors.add(new ParseError(l.getFirst().row, l.getFirst().column, l.getFirst(),
                    "Data entries must start with an identifier.", ParseError.ERR_CRITICAL));
            ParseResult.succeeded = false;
        }
        else name = l.getFirst().lexeme;

        // followed by the data size directive //
        if (!matchSubTypes(new SubType[] {SubType.DIR_DB, SubType.DIR_DW, SubType.DIR_RESB, SubType.DIR_RESW}, l.get(1))) {
            ParseResult.errors.add(new ParseError(l.get(1).row, l.get(1).column, l.get(1),
                    "You must specify the size of data.", ParseError.ERR_CRITICAL));
        }
        else mode = switch (l.get(1).subType) {
            case SubType.DIR_DB, SubType.DIR_RESB -> ProgramData.MODE_BYTE;
            case SubType.DIR_DW, SubType.DIR_RESW -> ProgramData.MODE_WORD;
            default -> 0;
        };
        // parse the data and set the offsets
        for(int i = 2; i < l.size(); i++) {
            data.add(l.get(i));
            switch (l.get(i).mainType) {
                case TokenType.COMMA -> {}
                case TokenType.STRING -> {
                    String x = l.get(i).lexeme;
                    for(int k = 0; k < x.length(); k++) size += mode;
                }
                case TokenType.NUMBER -> {
                    size += mode;
                }
                default -> ParseResult.errors.add(new ParseError(l.get(i).row, l.get(i).column, l.get(i),
                        "Unexpected data type.", ParseError.ERR_CRITICAL));
            }
        }
        dataOffset += size;
        return new ProgramData(name, size, startAddr, mode, data);
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
