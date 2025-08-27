public class MemoryModule {
    int mem_size_B, memorySize;

    static float ROMpercentage = (35.0f / 100), DATApercentage = (45.0f / 100), STACKpercentage = (20.0f / 100);

    int ROMsizeB, DATAsizeB, STACKsizeB;
    int rom_start, rom_end, data_start, data_end, stack_start, stack_end;

    int dataOffset;
    int last_addressable_location;

    float ROMsizeKB, DATAsizeKB, STACKsizeKB;


    public void calculateMemorySegments(){

        mem_size_B = (memorySize * 1024) + CPU.signature.length() + CPU.lastUpdateDate.length() + CPU.compilerVersion.length() + 4;

        ROMsizeKB = (ROMpercentage * memorySize);
        DATAsizeKB = (DATApercentage * memorySize);
        STACKsizeKB = (STACKpercentage * memorySize);

        ROMsizeB = (int) (ROMsizeKB * 1024);
        DATAsizeB = (int) (DATAsizeKB * 1024);
        STACKsizeB = (int) (STACKsizeKB * 1024);

        rom_start = 0;
        data_start = rom_start + ROMsizeB;
        stack_start = data_start + DATAsizeB;

        rom_end = data_start - 1;
        data_end = stack_start - 1;
        stack_end = stack_start + STACKsizeB;

        dataOffset = data_start;
        last_addressable_location = data_end;
        System.out.println("Done calculating memory segments.");
    }
}
