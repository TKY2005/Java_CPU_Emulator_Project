import java.util.HashMap;
import java.util.Map;

public abstract class CPU {

    protected int register_count;
    protected Map registers;
    protected Object memory;

    public CPU(int registerCount){
        System.out.println("Setting up CPU.");
        initCPU(registerCount);
    }

    public abstract void initCPU(int registerCount);
    public abstract void reset();
}
