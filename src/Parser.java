import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

class ParseResult {
    List<Instruction> instructions;
    List<ProgramData> data;
    HashMap<String, Integer> symbolTable;
    static List<ParseError> errors;
    boolean succeeded;
    public ParseResult() {
        instructions = new ArrayList<>();
        symbolTable = new HashMap<>();
        data = new ArrayList<>();
        errors = new ArrayList<>();
        succeeded = true;
    }
}

class ParseError {
    int row, column;
    Token token;
    List<Token> line;
    String errorMsg;
    int type;

    public static int ERR_CRITICAL = 0;
    public static int ERR_WARNING = 1;
    public ParseError(Token t, String e, int type) {
        this.token = t;
        this.row = t.row; this.column = t.column;
        this.errorMsg = e;
        this.type = type;
    }
    public ParseError(List<Token> line, Token t, String e, int type) {
        this.line = line;
        this.token = t;
        this.row = t.row;
        this.column = t.column;
        this.errorMsg = e;
        this.type = type;
    }
    @Override
    public String toString() {
        return String.format("%s @ %d,%d -> %s (%s): %s",
                (type == ERR_WARNING) ? "Warning" : "Error", column, row, lineToString(), token.toString(), errorMsg);
    }
    public String lineToString() {
        if (line == null || line.isEmpty()) return "";
        else {
            StringBuilder s = new StringBuilder();
            for(Token t : line) s.append(t.lexeme).append(" ");
            return s.toString();
        }
    }
}

class ProgramData {
    String symbol;
    int size;
    int physicalAddress;
    int relativeAddress;
    int mode;
    List<Token> data;

    public static int MODE_BYTE = 1;
    public static int MODE_WORD = 2;
    public ProgramData(String s, int size, int physical, int relative, int mode, List<Token> data) {
        this.symbol = s;
        this.size = size;
        this.physicalAddress = physical;
        this.relativeAddress = relative;
        this.mode = mode;
        this.data = data;
    }

    public String toStringDebug() {
        return String.format("""
                [DATA]
                symbol: %s
                size: %d
                relative address: 0x%04X
                physical address: 0x%04X
                type: %s
                data: %s
                """, symbol, size, relativeAddress, physicalAddress, (mode == MODE_BYTE) ? "byte" : "word", dataToString());
    }
    public String dataToString() {
        if (data == null || data.isEmpty()) return "No data.";
        else {
            StringBuilder s = new StringBuilder();
            for(Token t : data) s.append("\"").append(t.lexeme).append("\"").append(" ");
            return s.toString();
        }
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

    public String toStringDebug() {
        return String.format("""
                [INSTRUCTION]
                instruction length: %d
                instruction address: 0x%04X
                instruction: %s
                """, length, address, instructionToString());
    }
    public String instructionToString() {
        StringBuilder s = new StringBuilder();
        for (Token part : parts) s.append(part.lexeme).append(" ");
        return s.toString();
    }
}

public class Parser {
    private List<Token> tokens;
    private int index;
    List<Token> instructionTokens;
    private int offset = 0;
    private int lengthCounter = 0;

    private final int dataBaseAddr = MemoryModule.data_start; // dataBaseAddr points to the beginning of the data section
    private final int codeOffset = MemoryModule.rom_end;

    private int dataAddr; // dataAddr points to the address of data relative to the data section start (relative address)
    private int dataOffset = 0; // dataOffset is how far we are from the dataAddr pointer

    // This checks only the syntax for now. will add expression parsing later
    public ParseResult parseTokens(List<Token> tokenStream, MemoryModule memory) {

        dataAddr = memory.dataOffset;
        ParseResult result = new ParseResult();
        index = 0;
        offset = 0;
        lengthCounter = 0;

        tokens = tokenStream;

        while (index < tokens.size()) {
            Token current = tokens.get(index);
            if (current.mainType == TokenType.NEWLINE) {
                index++;
                continue;
            }
            if (current.subType == SubType.SPEC_DATA) {
                skip();
                while (tokens.get(index).subType != SubType.SPEC_END) {
                    List<Token> line = readCurrentLine();
                    if (line != null) {
                        if (line.getFirst().subType == SubType.DIR_ORG) {
                            if (line.get(1).mainType == TokenType.NUMBER) {
                                dataOffset = 0;
                                dataAddr = Integer.parseInt(line.get(1).lexeme);
                            }
                            else ParseResult.errors.add(new ParseError(line, line.get(1),
                                    "ORG directives can accept only numbers.", ParseError.ERR_CRITICAL));

                        } else {
                            ProgramData d = parseDataEntry(line);
                            result.symbolTable.put(d.symbol, d.relativeAddress);
                            result.data.add(d);
                        }
                    }
                }
            }
            else if (current.subType == SubType.LABEL_FUNC || current.subType == SubType.SPEC_MAIN) {
                String name = current.lexeme;
                result.symbolTable.put(name, offset);
                System.out.printf("Mapped function '%s' to address: 0x%04X\n", name, offset);
            }
            else {
                List<Token> line = readCurrentLine();

                if (line != null) {
                    Instruction i = parseInstruction(line);
                    result.instructions.add(i);
                }
            }
            index++;
        }
        return result;
    }

    private List<Token> readCurrentLine() {
        List<Token> l = new ArrayList<>();
        while (tokens.get(index).mainType != TokenType.NEWLINE && tokens.get(index).mainType != TokenType.EOF){
            l.add(tokens.get(index));
            index++;
        }
        if (l.isEmpty()){ // we probably collected a newline or an EOF token. we should skip it.
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

    private Instruction parseInstruction(List<Token> l) {
        // an instruction can have from 0 to 2 operands that are separated by a comma
        // a memory address can consist of a variable amount of tokens surrounded in blocky braces []
        // the destination operand shall be a register or a memory address and nothing else
        // none of the operands can be a string literal
        // instruction opcodes are 1 byte long
        // register operands, immediate operands, and label operands are 2 bytes long
        // memory addressing can have variable length operands
        Instruction r = new Instruction(l);
        r.address = offset;

        if (l.getFirst().mainType != TokenType.INSTRUCTION) {
            ParseResult.errors.add(new ParseError(l, l.getFirst(), "Expected an instruction. instead got: " + l.getFirst().mainType, ParseError.ERR_CRITICAL));
        }
        else lengthCounter += 1;

        // single-operand instruction.
        if (l.size() == 2) {
            switch (l.get(1).mainType) {
                case REGISTER -> lengthCounter += 2;
                case NUMBER -> lengthCounter += 2;
                case SYMBOL -> lengthCounter += 3;
                case SPECIAL -> lengthCounter += 3;
                case MEMORY -> {
                    if (l.get(1).subType == SubType.MEM_REGISTER) lengthCounter += 2;
                    else ParseResult.errors.add(new ParseError(l, l.get(1), "Unknown operand type: " + l.get(1).mainType, ParseError.ERR_CRITICAL));
                }
                default -> ParseResult.errors.add(new ParseError(l, l.get(1), "Unknown operand type: " + l.get(1).mainType, ParseError.ERR_CRITICAL));
            }
        }
        // 2-operand instruction
        else if (l.size() == 3) {
            switch (l.get(1).mainType) {
                case REGISTER -> lengthCounter += 2;
                case MEMORY -> {
                    if (l.get(1).subType == SubType.MEM_REGISTER) lengthCounter += 2;
                    else ParseResult.errors.add(new ParseError(l, l.get(1), "Unknown operand type: " + l.get(1).mainType, ParseError.ERR_CRITICAL));
                }
                default -> ParseResult.errors.add(new ParseError(l, l.get(1), "Unexpected operand type: " + l.get(1).mainType, ParseError.ERR_CRITICAL));
            }
            switch (l.get(2).mainType) {
                case REGISTER -> lengthCounter += 2;
                case NUMBER -> lengthCounter += 2;
                case SYMBOL -> lengthCounter += 3;
                case SPECIAL -> lengthCounter += 3;
                case MEMORY -> {
                    if (l.get(1).subType == SubType.MEM_REGISTER) lengthCounter += 2;
                    else ParseResult.errors.add(new ParseError(l, l.get(1), "Unknown operand type: " + l.get(1).mainType, ParseError.ERR_CRITICAL));
                }
                default -> ParseResult.errors.add(new ParseError(l, l.get(1), "Unknown operand type: " + l.get(1).mainType, ParseError.ERR_CRITICAL));
            }
        }
        else if (l.size() > 3) ParseResult.errors.add(new ParseError(l, l.get(4), "The extra operand will be ignored.", ParseError.ERR_WARNING));

        r.length = lengthCounter;
        offset += lengthCounter;
        lengthCounter = 0;
        return r;
    }

    private ProgramData parseDataEntry(List<Token> l) {
        // Data must follow this format: data_identifier data_type data1 data2 ...
        // Data can be either a string or a number.
        // Data types can be either a byte, a word, a byte buffer, a word buffer
        // if the buffer types are selected then the next token must be a number and nothing else.
        String name = "";
        int size = 0, mode = 0;
        int relative = dataAddr + dataOffset;
        int physical = relative + MemoryModule.data_start;

        List<Token> data = new ArrayList<>();
        ProgramData d = new ProgramData(null, 0, physical, relative, 0, null);

        // first token of a data entry must be the label/identifier //
        if (l.getFirst().mainType != TokenType.LABEL) {
            ParseResult.errors.add(new ParseError(l, l.getFirst(),
                    "Data entries must start with an identifier/label. instead got: " + l.getFirst().mainType,
                    ParseError.ERR_CRITICAL));

        } else d.symbol = l.getFirst().lexeme;

        // followed by the data size directive //
        if (!matchSubTypes(new SubType[]{SubType.DIR_DB, SubType.DIR_DW, SubType.DIR_RESB, SubType.DIR_RESW}, l.get(1))) {
            ParseResult.errors.add(new ParseError(l, l.get(1),
                    "You must specify the size and type of data.", ParseError.ERR_CRITICAL));
        } else mode = switch (l.get(1).subType) {
            case SubType.DIR_DB, SubType.DIR_RESB -> ProgramData.MODE_BYTE;
            case SubType.DIR_DW, SubType.DIR_RESW -> ProgramData.MODE_WORD;
            default -> 0;
        };

        // Parse a buffer
        if (matchSubTypes(new SubType[]{SubType.DIR_RESB, SubType.DIR_RESW}, l.get(1))) {

            if (l.get(2).mainType != TokenType.NUMBER) {
                ParseResult.errors.add(new ParseError(
                        l, l.get(2), "Buffers can only accept numbers to define the size.", ParseError.ERR_CRITICAL));
            } else {
                size += Integer.parseInt(l.get(2).lexeme) * mode;
            }
        } else {
            // parse the data and set the offsets
            for (int i = 2; i < l.size(); i++) {
                data.add(l.get(i));
                switch (l.get(i).mainType) {
                    case TokenType.COMMA -> {
                    }
                    case TokenType.STRING -> {
                        String x = l.get(i).lexeme;
                        for (int k = 0; k < x.length(); k++) size += mode;
                    }
                    case TokenType.NUMBER -> {
                        size += mode;
                    }
                    default -> ParseResult.errors.add(new ParseError(l, l.get(i),
                            "Unexpected data type.", ParseError.ERR_CRITICAL));
                }
            }
        }
        d.mode = mode;
        d.size = size;
        d.data = data;
        dataOffset += size;
        return d;
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
