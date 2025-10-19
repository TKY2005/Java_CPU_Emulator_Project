import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class Disassembler {

    private String architecture;
    private VirtualMachine vm;
    private CPU cpuModule;

    public Disassembler(String binaryFilePath, String outputFilePath){

        try {

            byte[] x = Files.readAllBytes(Path.of(binaryFilePath));
            int[] machine_code = new int[x.length];

            for(int i = 0; i < machine_code.length; i++) machine_code[i] = x[i] & 0xff;

            architecture = Integer.toString(machine_code[machine_code.length - 3]);
            System.out.println("This has been compiled for " + architecture + "-bit module.");

            if (architecture.equals("8")) cpuModule = new CPUModule8BIT();
            else if (architecture.equals("16")) cpuModule = new CPUModule16BIT();
            // implement 32 and 64 bit when done.


            vm = new VirtualMachine(cpuModule);
            cpuModule.machineCode = machine_code;


            String output = cpuModule.disassembleMachineCode(machine_code);
            File file = new File(outputFilePath);
            FileWriter writer = new FileWriter(file);
            PrintWriter printer = new PrintWriter(writer);

            printer.print(output);
            printer.close();
            writer.close();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
