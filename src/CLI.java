import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class CLI {
    private VirtualMachine vm;
    private CPU cpuModule;

    String architecture = Launcher.appConfig.get("Architecture");
    File binFile;

    public CLI(String filePath){

        binFile = new File(filePath);
        if (!binFile.exists()){
            System.out.println("Couldn't find the specified file.");
            System.exit(-1);
        }

        if (architecture.equals("8")) cpuModule = new CPUModule8BIT();
        else if (architecture.equals("16")) cpuModule = new CPUModule16BIT();
        // implement 32 and 64 bit when done.

        vm = new VirtualMachine(cpuModule);
        vm.UIMode = false;
        loadBinaryFile();

        vm.readyToExecute = true;
        int entryPointLow = cpuModule.machineCode[ cpuModule.machineCode.length - 1 ];
        int entryPointHigh = cpuModule.machineCode[ cpuModule.machineCode.length - 2 ];

        cpuModule.functions.put("MAIN",  (entryPointHigh << 8) | entryPointLow );
        vm.executeCode();
    }

    public void loadBinaryFile(){

        try {
            System.out.println("Reading file content.");
            byte[] machineCode = Files.readAllBytes(binFile.toPath());


            System.out.println("Loading the TEXT section of the file into CPU ROM");
            int ROMsize = 0, MEMsize = 0;
            for(int i = 0; machineCode[i] != CPU.TEXT_SECTION_END; i++) ROMsize++;

            for(int i = ROMsize + 1; machineCode[i] != CPU.MEMORY_SECTION_END; i++) MEMsize++;


            List<Integer> machineCodeList = new ArrayList<>();
            for(int i = 0; i <= ROMsize; i++) machineCodeList.add(machineCode[i] & 0xff);
            System.out.println("Adding the file metadata");
            for(int i = ROMsize + MEMsize + 2; i < machineCode.length; i++) machineCodeList.add(machineCode[i] & 0xff);

            cpuModule.machineCode = machineCodeList.stream().mapToInt(Integer::intValue).toArray();


            System.out.println("Loading the DATA and STACK sections into memory.");
            int index = 0;
            for(int i = ROMsize + 1; machineCode[i] != CPU.MEMORY_SECTION_END; i++) {
                cpuModule.memory[index] = machineCode[i];
                index++;
            }
            System.out.println("Done. Starting execution.\n");

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
