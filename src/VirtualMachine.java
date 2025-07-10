import java.lang.reflect.Array;
import java.util.Arrays;

public class VirtualMachine {

    private CPU cpuModule;
    public boolean readyToExecute = false;
    public boolean UIMode = false;

    public String err_msg = "";

    public VirtualMachine(CPU cpuModule){
        System.out.println("Starting the virtual machine with the selected CPU module.");
        this.cpuModule = cpuModule;
        System.out.println("Welcome");

        // Temporary testing codes.
        /*String code = """
                .MAIN
                    la $dp ~n1
                    set $ra &dp
                    
                    la $ss ~n2
                    llen $rc ~n2
                    call cipher
                    
                    la $ss ~n2
                    outs
                ext
                
                .cipher
                    add &ss $ra
                    inc $ss
                    loop cipher
                ret
                
                .DATA
                n1 !5
                n2 "Hello world"
                end
                """;

        String code2 = """
                .MAIN
                    set $ra @a
                    set $rb !0
                    .set_loop
                        set &rb $ra
                        inc $ra
                        inc $rb
                        cmp $ra @z
                        jle set_loop
                        set &rb !0
                    set $ss !0
                    outs
                ext
                """;*/

        /*String code3 = """
                .MAIN
                 call func1
                 la $ss ~n1
                 outs
                ext
                
                .func1
                 la $ss ~n2
                 outs
                 call func2
                ret
                
                .func2
                 la $ss ~n3
                 outs
                ret
                
                .DATA
                 org #14
                 n1 "This is the end of MAIN"
                 n2 "This is func1"
                 n3 "This is func2"
                end
                """;

        String code4 = """
                .MAIN
                la $dp ~n1
                set $ra &dp
                la $ss ~n2
                llen $rc ~n2
                call cipher
                outs
                ext
                
                .cipher
                add &ss $ra
                inc $ss
                loop cipher
                ret
                .DATA
                org !10
                n1 !5
                n2 "Hello world"
                end
                """;
                */
    }

    public void sendCode(String code){

       StringBuilder result = new StringBuilder();
        String[] lines = code.split("\n");
        err_msg = "";
        cpuModule.reset();

        for (String line : lines) {
            String[] tokens = line.trim().split("\\s+");
            StringBuilder newLine = new StringBuilder();

            for (String token : tokens) {
                if (token.startsWith(CPU.HEX_PREFIX)) {
                    // Hexadecimal to decimal conversion
                    String hex = token.substring(1);
                    int decimal = Integer.parseInt(hex, 16);
                    newLine.append("!").append(decimal).append(" ");
                } else if (token.startsWith(CPU.CHAR_PREFIX)) {
                    // Character to ASCII decimal conversion
                    char ch = token.charAt(1);
                    int ascii = (int) ch;
                    newLine.append("!").append(ascii).append(" ");
                }
                else if (token.startsWith(CPU.HEX_MEMORY)){
                    String hex = token.substring(1);
                    int decimal = Integer.parseInt(hex, 16);
                    newLine.append("%").append(decimal).append(" ");
                }
                else {
                    newLine.append(token).append(" ");
                }
            }

            result.append(newLine.toString().trim()).append("\n");
        }

        try {
            cpuModule.compileCode(result.toString());
        }catch (RuntimeException e) {
            e.printStackTrace();
            err_msg = e.getMessage();
            throw e;
        }
        readyToExecute = true;
    }

    public void executeCode(){
        // Implement delay and stepping logic and update UI components
        cpuModule.executeCompiledCode(cpuModule.machineCode);
    }

    public void resetCPU(){
        cpuModule.reset();
        cpuModule.canExecute = true;
        cpuModule.programEnd = false;
    }
}
