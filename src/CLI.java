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

        System.out.println("Running the file using the architecture present in the config file.");
        if (architecture.equals("8")) cpuModule = new CPUModule8BIT();
        else if (architecture.equals("16")) cpuModule = new CPUModule16BIT();
        // implement 32 and 64 bit when done.

        vm = new VirtualMachine(cpuModule);
        vm.UIMode = false;
        loadBinaryFile();



        if (cpuModule.machineCode[ cpuModule.machineCode.length - 3 ] != cpuModule.bit_length){
            System.out.printf("This code has been compiled for %d-bit architecture." +
                    " the current CPU architecture is %d-bit.\n",
                    cpuModule.machineCode[cpuModule.machineCode.length - 3], cpuModule.bit_length);

            System.exit(-1);
        }
        vm.readyToExecute = true;
        int entryPointLow = cpuModule.machineCode[ cpuModule.machineCode.length - 1 ];
        int entryPointHigh = cpuModule.machineCode[ cpuModule.machineCode.length - 2 ];

        cpuModule.functions.put("MAIN",  (entryPointHigh << 8) | entryPointLow );
        VirtualMachine.beep(VirtualMachine.beepSuccess[0], VirtualMachine.beepSuccess[1]);
        vm.executeCode();
    }

    public CLI(String filePath, String architecture){

        binFile = new File(filePath);
        if (!binFile.exists()){
            System.out.println("Couldn't find the specified file.");
            System.exit(-1);
        }

        System.out.println("Running the file using the " + architecture + "-bit module.");
        if (architecture.equals("8")) cpuModule = new CPUModule8BIT();
        else if (architecture.equals("16")) cpuModule = new CPUModule16BIT();
        // implement 32 and 64 bit when done.

        vm = new VirtualMachine(cpuModule);
        vm.UIMode = false;
        loadBinaryFile();



        if (cpuModule.machineCode[ cpuModule.machineCode.length - 3 ] != cpuModule.bit_length){
            System.out.printf("This code has been compiled for %d-bit architecture." +
                    " the current CPU architecture is %d-bit.\n",
                    cpuModule.machineCode[cpuModule.machineCode.length - 3], cpuModule.bit_length);

            System.exit(-1);
        }
        vm.readyToExecute = true;
        int entryPointLow = cpuModule.machineCode[ cpuModule.machineCode.length - 1 ];
        int entryPointHigh = cpuModule.machineCode[ cpuModule.machineCode.length - 2 ];

        cpuModule.functions.put("MAIN",  (entryPointHigh << 8) | entryPointLow );
        vm.executeCode();
    }

    public void loadBinaryFile(){

        try {
            System.out.println("READING FILE CONTENT.\n");

            byte[] fileBin = Files.readAllBytes(binFile.toPath());

            byte[] metadata = new byte[CPU.metadataLength];
            for(int i = 0; i < metadata.length; i++){
                metadata[i] = fileBin[ fileBin.length - metadata.length + i ];
            }
            int Fver = metadata[ metadata.length - 5] + metadata[ metadata.length - 6 ] + metadata[ metadata.length - 7 ];

            String fileVersion = String.valueOf((char) metadata[metadata.length - 8]) +
                    (char) metadata[metadata.length - 7] +
                    (char) metadata[metadata.length - 6] +
                    (char) metadata[metadata.length - 5];

            int Cver = (int) CPU.compilerVersion.charAt(2) +
                    (int) CPU.compilerVersion.charAt(3) +
                    (int) CPU.compilerVersion.charAt(4);

            if (Cver != Fver && !Launcher.ignoreVersionCheck){
                String err = String.format("""
                        THE COMPILED BINARY FILE VERSION DOESN'T MATCH WITH THE CURRENT COMPILER VERSION.
                        RUNNING THE FILE WITH NON-MATCHING VERSIONS CAN CAUSE UNDEFINED BEHAVIOUR AND CRASHES.
                        PLEASE RECOMPILE THE SOURCE CODE FILE TO UPDATE IT TO THE LATEST VERSION.
                        CURRENT COMPILER VERSION: %s(%d), FILE VERSION: %s(%d)
                        YOU CAN ALSO TURN OFF VERSION CHECKING BY INCLUDING THE -ivc FLAG
                        """, CPU.compilerVersion, Cver, fileVersion, Fver);

                cpuModule.triggerProgramError(err, ErrorHandler.ERR_CODE_INCOMPATIBLE_ARCHITECTURE);
            }

            System.out.println("CHECKING MEMORY.");

            if (fileBin.length > cpuModule.memoryController.mem_size_B) {
                String err = String.format("""
                        THE CURRENT MEMORY CONFIGURATION USES %.3fKB OF MEMORY.
                        THE COMPILED BINARY FILE USES %.3fKB OF MEMORY.
                        PLEASE INCREASE MEMORY SIZE.
                        """, cpuModule.memoryController.mem_size_B / 1024f, fileBin.length / 1024f);

                cpuModule.triggerProgramError(err, ErrorHandler.ERR_CODE_INSUFFICIENT_MEMORY);
            }
            System.out.printf("BINARY FILE SIZE: %.3fKB, MEMORY SIZE: %.3fKB. MEMORY OK\n",
                    fileBin.length / 1024f, cpuModule.memoryController.mem_size_B / 1024f);

            int index_file = 0, index_memory = 0;
            int file_rom_size = 0, file_data_size = 0;

            for(file_rom_size = 0; fileBin[file_rom_size] != CPU.TEXT_SECTION_END; file_rom_size++);
            for(int i = file_rom_size + 1; fileBin[i] != CPU.MEMORY_SECTION_END; i++) file_data_size++;

            int mem_rom_size = MemoryModule.rom_end - MemoryModule.rom_start;
            int mem_data_size = MemoryModule.stack_end - MemoryModule.data_start;

            if (file_rom_size > mem_rom_size) {
                String err = String.format("""
                        THE ROM SIZE OF THE COMPILED BINARY EXCEEDS THE ROM SIZE IN THE CURRENT MEMORY CONFIGURATION.
                        PLEASE INCREASE ROM SIZE.
                        BINARY FILE ROM SIZE: %dB, CURRENT ROM SIZE: %dB
                        """, file_rom_size, MemoryModule.rom_end);

                cpuModule.triggerProgramError(err, ErrorHandler.ERR_CODE_INSUFFICIENT_MEMORY);
            }
            System.out.printf("BINARY FILE ROM SIZE: %dB, CURRENT ROM SIZE: %dB. ROM OK.\n", file_rom_size, mem_rom_size);

            if (file_data_size > mem_data_size) {
                String err = String.format("""
                        THE DATA SECTION SIZE OF THE BINARY FILE EXCEEDS THE DATA SECTION SIZE OF THE CURRENT MEMORY CONFIGURATION.
                        PLEASE INCREASE DATA AND STACK SIZE
                        BINARY FILE DATA SECTION SIZE: %dB, CURRENT DATA SECTION SIZE: %dB
                        """, file_data_size, mem_data_size);
            }
            System.out.printf("BINARY FILE DATA SIZE: %dB, CURRENT DATA SIZE: %dB. DATA OK.\n", file_data_size, mem_data_size);

            System.out.println("ADDING AND ALIGNING THE BINARY DATA TO MEMORY\n");

            List<Integer> machineCode = new ArrayList<>();
            for(int i = 0; i < cpuModule.memoryController.mem_size_B; i++) machineCode.add(0x00);


            System.out.println("COPYING ROM DATA TO MEMORY IMAGE.");
            for(int i = 0; i < file_rom_size; i++){
                machineCode.set(index_file, fileBin[index_file] & 0xff);
                index_file++;
            }
            machineCode.set(mem_rom_size + 1, CPU.TEXT_SECTION_END & 0xff);

            System.out.println("COPYING DATA SECTION TO MEMORY IMAGE.");

            // we have to align the contents of the file to the memory.
            index_memory = MemoryModule.data_start - 1;
            for(int i = 0; i < file_data_size; i++){
                machineCode.set(index_memory, fileBin[index_file] & 0xff);
                index_file++;
                index_memory++;
            }
            machineCode.set(mem_rom_size + mem_data_size, CPU.MEMORY_SECTION_END & 0xff);


            System.out.println("COPYING METADATA TO MEMORY IMAGE.");

            int copyIndex = fileBin.length - 1;
            for(int i = 0; i <= CPU.metadataLength; i++){
                machineCode.set( machineCode.size() - i - 1, (int) fileBin[copyIndex]);
                copyIndex--;
            }

            System.out.println("ASSEMBLING THE MEMORY.");
            int[] image = machineCode.stream().mapToInt(Integer::intValue).toArray();
            cpuModule.machineCode = image;
            vm.setMemImage(image);

            for(int i = 0; i < cpuModule.memoryController.mem_size_B; i++)
                cpuModule.memoryController.setMemoryAbsolute(i, (short) image[i], CPU.DATA_BYTE_MODE);

            System.out.println("DONE. STARTING EXECUTION.\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
