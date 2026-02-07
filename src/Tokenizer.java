import java.util.ArrayList;
import java.util.List;
import java.util.Map;

enum TokenType {
    NUMBER,
    STRING,
    DIRECTIVE,
    INSTRUCTION,
    SYMBOL,
    LABEL,
    REGISTER,
    OPERATOR,
    LBRACE, RBRACE,
    LPAREN, RPAREN,
    DOUBLEQUOTE,
    NEWLINE,
    EOF,
    DOT,
    COMMA,
    SPECIAL,
    UNDEFINED
}

enum SubType {
    LABEL_FUNC, LABEL_DATA, // Label subtypes
    SPEC_MAIN, SPEC_DATA, SPEC_END,
    DIR_BYTE, DIR_WORD, DIR_ORG, DIR_RESB, DIR_RESW, DIR_DB, DIR_DW, DIR_DEFINE, // Directive subtypes
    OPER_ADD, OPER_SUB, OPER_MUL, OPER_DIV, // Operator subtypes
    REG_8, REG_16,
    UNDEFINED
}

class Token {
    String lexeme;
    TokenType mainType;
    SubType subType;

    public Token(String lexeme, TokenType mainType, SubType subType) {
        this.lexeme = lexeme;
        this.mainType = mainType;
        this.subType = subType;
    }

    @Override
    public String toString() {
        return String.format("""
                [
                    token: %s
                    supertype: %s
                    subtype: %s
                ]
                """, this.lexeme, this.mainType, this.subType);
    }
}

public class Tokenizer {
    private int index;
    private char[] chars;
    private StringBuilder tokenBuff;
    private boolean isReadingQuotedString = false;
    private boolean isReadingFunctionLabel = false;
    private boolean isReadingData = false;
    private boolean special = false;

    private String[] directives =  {
      "ORG",
        "BYTE",
        "WORD",
        "RESB",
        "RESW",
        "DB",
        "DW",
        "DEFINE"
    };

    public List<Token> tokenize(String source, CPU target, String[] registerList) {
        index = 0;
        tokenBuff = new StringBuilder();
        chars = source.toCharArray();
        List<Token> result = new ArrayList<>();

        while (index < chars.length) {

            // '!' and '#' are temporary and are added because of the preprocessor automatically adding prefixes
            if (chars[index] == ' ' || chars[index] == '!' || chars[index] == '#') next(); // skip whitespace (except for when reading strings)
            else if (chars[index] == '\n'){
                consume();
                result.add(new Token(tokenBuff.toString(), TokenType.NEWLINE, null));
            }
            else if (chars[index] == '\"') { // signal the tokenizer to keep reading until next double quote is found
                isReadingQuotedString = !isReadingQuotedString;
                next();
            }
            // signal to the tokenizer that the following string is a label declaration
            else if (chars[index] == '.' && !isReadingQuotedString) {
                isReadingFunctionLabel = true;
                next();
            }

            else if (chars[index] == '[')
            {
                consume();
                result.add(new Token(tokenBuff.toString(), TokenType.LBRACE, null));
            }
            else if (chars[index] == ']')
            {
                consume();
                result.add(new Token(tokenBuff.toString(), TokenType.RBRACE, null));
            }
            else if (chars[index] == '(')
            {
                consume();
                result.add(new Token(tokenBuff.toString(), TokenType.LPAREN, null));
            }
            else if (chars[index] == ')')
            {
                consume();
                result.add(new Token(tokenBuff.toString(), TokenType.RPAREN, null));
            }
            else if (chars[index] == ',')
            {
                consume();
                result.add(new Token(tokenBuff.toString(), TokenType.COMMA, null));
            }

            else if (isOperator(chars[index]))
            {
                consume();
                SubType type = getOperatorSubType(chars[index - 1]);
                result.add(new Token(tokenBuff.toString(), TokenType.OPERATOR, type));
            }

            else if (isDigit(chars[index])) // keep reading until there's no more digits
            {
                while (index < chars.length && isDigit(chars[index])) consume();
                result.add(new Token(tokenBuff.toString(), TokenType.NUMBER, null));
            }

            else if (isCharOrSep(chars[index]))
            {
                if (isReadingQuotedString) while (index < chars.length && chars[index] != '\"') consume();
                else while (index < chars.length && isCharOrSep(chars[index]) || isDigit(chars[index])) consume();

                if (tokenBuff.toString().equals("MAIN")) {
                    special = true;
                }
                else if (tokenBuff.toString().equals("DATA")){
                    special = true;
                    isReadingData = true;
                    next();
                }
                else if (tokenBuff.toString().equalsIgnoreCase("end")){
                    special = true;
                    isReadingData = false;
                    next();
                }

                TokenType mainType = getStringType(tokenBuff.toString(), registerList, target);
                SubType sub = null;

                if (mainType == TokenType.SPECIAL) {
                    special = false;
                    if (isReadingData) sub = SubType.SPEC_DATA;
                    else if (isReadingFunctionLabel) sub = SubType.SPEC_MAIN;
                    else if (tokenBuff.toString().equalsIgnoreCase("end")) sub = SubType.SPEC_END;
                }

                else if (mainType == TokenType.DIRECTIVE) sub = getDirectiveType(tokenBuff.toString());

                else if (mainType == TokenType.LABEL){
                    if (isReadingData) sub = SubType.LABEL_DATA;
                    else if (isReadingFunctionLabel) sub = SubType.LABEL_FUNC;
                }

                else if (mainType == TokenType.REGISTER) {
                    sub = getRegisterWidth(tokenBuff.toString(), target);
                }

                isReadingFunctionLabel = false; // so we don't treat everything as labels
                result.add(new Token(tokenBuff.toString(), mainType, sub));
            }

            else {
                consume();
                System.out.printf("Undefined token '%s'.", tokenBuff);
                System.exit(255);
            }

            tokenBuff.setLength(0);
        }
        result.add(new Token("", TokenType.EOF, null));
        return result;
    }

    private boolean isOperator(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/';
    }
    private boolean isDigit(char c) {return c >= '0' && c <= '9';}
    private boolean isCharOrSep(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c == '_');
    }

    private boolean isDirective(String str) {

        for (String directive : directives) if (str.equalsIgnoreCase(directive)) return true;
        return false;
    }

    private boolean isInstruction(String str, CPU target) {

        for(Map.Entry<Integer, String> entry : target.instructionSet.entrySet()) {
            if (str.equalsIgnoreCase(entry.getValue())) return true;
        }
        return false;
    }

    private boolean isRegisterName(String str, String[] registerList) {

        for (String s : registerList) if (str.equalsIgnoreCase(s)) return true;
        return false;
    }
    private SubType getRegisterWidth(String registerName, CPU target) {
        if (target instanceof CPUModule16BIT)
        {
            char last = registerName.charAt(registerName.length() - 1);
            if (last == 'l' || last == 'L') return SubType.REG_8;
            else return SubType.REG_16;
        }
        // this is wrong. will fix later
        else if (target instanceof CPUModule8BIT) return SubType.REG_8;
        else return null;
    }

    private SubType getOperatorSubType(char c) {
        switch (c) {
            case '+' -> {
                return SubType.OPER_ADD;
            }
            case '-' -> {
                return SubType.OPER_SUB;
            }
            case '*' -> {
                return SubType.OPER_MUL;
            }
            case '/' -> {
                return SubType.OPER_DIV;
            }
            default -> {
                return SubType.UNDEFINED;
            }
        }
    }

    private TokenType getStringType(String str, String[] registerList, CPU target) {

        if (special) return TokenType.SPECIAL;
        else if (isReadingQuotedString) return TokenType.STRING;
        else if (isReadingFunctionLabel) return TokenType.LABEL;

        else if ( isDirective(str) ) return TokenType.DIRECTIVE;
        else if (isInstruction(str, target)) return TokenType.INSTRUCTION;
        else if (isRegisterName(str, registerList)) return TokenType.REGISTER;

        else if (isReadingData) return TokenType.LABEL;

        else return TokenType.SYMBOL;
    }

    private SubType getDirectiveType(String str) {
        if (str.equalsIgnoreCase(directives[0])) return SubType.DIR_ORG;
        else if (str.equalsIgnoreCase(directives[1])) return SubType.DIR_BYTE;
        else if (str.equalsIgnoreCase(directives[2])) return SubType.DIR_WORD;
        else if (str.equalsIgnoreCase(directives[3])) return SubType.DIR_RESB;
        else if (str.equalsIgnoreCase(directives[4])) return SubType.DIR_RESW;
        else if (str.equalsIgnoreCase(directives[5])) return SubType.DIR_DB;
        else if (str.equalsIgnoreCase(directives[6])) return SubType.DIR_DW;
        else if (str.equalsIgnoreCase(directives[7])) return SubType.DIR_DEFINE;

        else return SubType.UNDEFINED;
    }

    private void next() {index++;}
    private char peek() {return chars[index + 1];}
    private void consume() {tokenBuff.append(chars[index++]);}
}
