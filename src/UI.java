import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

public class UI extends JFrame {
    private JPanel panel1;
    private JTextArea CodeArea;
    private JButton compileCodeButton;
    private JButton executeCodeButton;
    private JButton loadCodeFromFileButton;
    private JButton compileCodeToFileButton;
    private JTextArea MemoryDumpArea;
    private JTextArea OutputDumpArea;
    private JTextArea RegisterDumpArea;
    private JButton settingsButton;
    private JButton resetMemoryButton;

    private CPU cpuModule;
    private VirtualMachine vm;

    public UI(String title){
        super(title);
        this.setContentPane(panel1);
        this.setDefaultCloseOperation(EXIT_ON_CLOSE);

        this.pack();
        this.setVisible(true);

        HashMap<String, String> settings = Settings.loadSettings();
        cpuModule = null;

        String architecture = settings.get("Architecture");
        if (architecture.equals("8")) cpuModule = new CPUModule8BIT();
        /*else if (architecture.equals("16")) cpuModule = new CPUModule16BIT();
        else if (architecture.equals("32")) cpuModule = new CPUModule32BIT();
        else if (architecture.equals("64")) cpuModule = new CPUModule64BIT();*/

        assert cpuModule != null;
        vm = new VirtualMachine(cpuModule);

        CodeArea.setText(".MAIN\next");
        updateUI();

        if (!vm.readyToExecute) executeCodeButton.setEnabled(false);


        compileCodeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String code = CodeArea.getText();
                vm.sendCode(code);

                if (vm.readyToExecute){
                    String c = "Code compiled successfully.";
                    JOptionPane.showMessageDialog(panel1, c, "Success", JOptionPane.INFORMATION_MESSAGE);
                    executeCodeButton.setEnabled(true);
                }
                else{
                    String c = "Code compilation failed.";
                    JOptionPane.showMessageDialog(panel1, c, "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        executeCodeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                vm.executeCode();
                updateUI();
                executeCodeButton.setEnabled(false);
                vm.readyToExecute = false;
            }
        });

        resetMemoryButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cpuModule.reset();
                updateUI();
            }
        });
    }

    public void updateUI(){
        RegisterDumpArea.setText(cpuModule.dumpRegisters() + "\n\n" + cpuModule.dumpFlags());
        MemoryDumpArea.setText(cpuModule.dumpMemory());
        OutputDumpArea.setText(cpuModule.outputString.toString());
    }
}
