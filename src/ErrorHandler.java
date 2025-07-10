public class ErrorHandler{
    public static final int ERR_CODE_SUCCESS = 0;
    public static final int ERR_CODE_INVALID_MEMORY_ADDRESS = 1;
    public static final int ERR_CODE_INVALID_MEMORY_LAYOUT = 2;
    public static final int ERR_CODE_CPU_SIZE_VIOLATION = 3;
    public static final int ERR_CODE_INVALID_PREFIX = 4;
    public static final int ERR_CODE_INVALID_INSTRUCTION_FORMAT = 5;
    public static final int ERR_CODE_PROGRAM_ERROR = 6;
    public static final int ERR_CODE_NULL_POINTER = 7;
    public static final int ERR_COMP_COMPILATION_ERROR = 8;
    public static final int ERR_COMP_NULL_DATA_POINTER = 9;
    public static final int ERR_COMP_NULL_FUNCTION_POINTER = 10;
    public static final int ERR_COMP_INVALID_CPU_CODE = 11;
    public static final int ERR_COMP_UNDEFINED_INSTRUCTION = 12;
    public static final int ERR_CODE_MAIN_NOT_FOUND = 13;

    static class InvalidMemoryLayoutException extends RuntimeException{
        public InvalidMemoryLayoutException(String err) {super(err);}
    }

    static class InvalidMemoryOperationException extends RuntimeException{
        public InvalidMemoryOperationException(String err){super(err);}
    }
    static class InvalidInstructionException extends RuntimeException{
        public InvalidInstructionException(String err) {super(err);}
    }

    static class ProgramErrorException extends RuntimeException{
        public ProgramErrorException(String err) {super(err);}
    }

    static class InvalidDataPointerException extends RuntimeException{
        public InvalidDataPointerException(String err) {super(err);}
    }

    static class CodeCompilationError extends RuntimeException {
        public CodeCompilationError(String err) {super(err);}
    }
}
