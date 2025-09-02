import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;

public class CLICompiler {

    private File sourceCodeFile;
    private VirtualMachine vm;
    private CPU cpuModule;

    public CLICompiler(String sourceCodeFilePath, String outputFilePath){
        String architecture = Launcher.appConfig.get("Architecture");

        try {
            sourceCodeFile = new File(sourceCodeFilePath);

            if (!sourceCodeFile.exists()){
                System.out.println("The file specified doesn't exist.");
                System.exit(-1);
            }

            if (architecture.equals("8")) cpuModule = new CPUModule8BIT();
            else if (architecture.equals("16")) cpuModule = new CPUModule16BIT();

            vm = new VirtualMachine(cpuModule);

            StringBuilder code = new StringBuilder();
            String line;
            BufferedReader reader = new BufferedReader(new FileReader(sourceCodeFile));

            while ( (line = reader.readLine()) != null ) code.append(line).append("\n");

            vm.compileDirection = 1;
            vm.sendCode(code.toString());

            byte[] binaryCode = new byte[cpuModule.machineCode.length];
            for(int i = 0; i < binaryCode.length; i++) binaryCode[i] = (byte) (cpuModule.machineCode[i] & 0xff);

            Files.write(Path.of(outputFilePath), binaryCode);

        }catch (Exception e) {e.printStackTrace();}
    }
}
