import java.util.HashMap;

public class CPUModule8BIT extends CPU {
    public static final int bit_length = 8;
    public static final int max_value = 0xff;
    public static final int max_memory = 64;

    int memory_size;
    public int data_start;
    public int data_offset;
    public byte[] memory;


    public CPUModule8BIT(int registerCount){
        super(registerCount);
        data_offset = 5;
        memory_size = max_memory;
        int memsize = (memory_size * 1024);
        data_start = memsize - (data_offset * 1024);
        memory = new byte[memsize];
        String startMemory = String.format("""
                Starting with %dKB of memory with %dKB data offset. Total %d %d-bit memory locations (%d locations for data)
                data section starts at address 0x%X(%d)\n
                """, memory_size, data_offset, memsize, bit_length, memsize - data_start, data_start, data_start);
    }

    @Override
    public void initCPU(int registerCount){
        registers = new HashMap<String, Byte>();

        for(int i = 'a'; i < 'z' - registerCount; i++){
            registers.put("r" + (char) i, (byte) 0);
            System.out.println("Initialized registers.");

        }
    }
    @Override
    public void reset(){

    }
}