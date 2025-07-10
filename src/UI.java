import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

public class UI extends JFrame implements onStepListener {
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

    private JButton[] executionButtons = new JButton[]{
            executeCodeButton,
            compileCodeButton,
            loadCodeFromFileButton,
            compileCodeToFileButton,
            settingsButton,
            resetMemoryButton
    };

    private CPU cpuModule;
    private VirtualMachine vm;

    public UI(String title){
        super(title);
        this.setContentPane(panel1);
        this.setDefaultCloseOperation(EXIT_ON_CLOSE);

        this.pack();
        this.setVisible(true);

        HashMap<String, String> settings = Settings.loadSettings();

        String architecture = settings.get("Architecture");
        //if (architecture.equals("8")) cpuModule = new CPUModule8BIT();
        /*else if (architecture.equals("16")) cpuModule = new CPUModule16BIT();
        else if (architecture.equals("32")) cpuModule = new CPUModule32BIT();
        else if (architecture.equals("64")) cpuModule = new CPUModule64BIT();*/


        cpuModule = new CPUModule8BIT();
        vm = new VirtualMachine(cpuModule);

        CodeArea.setText(".MAIN\next");
        cpuModule.setUIupdateListener(this);
        updateUI();

        if (!vm.readyToExecute) executeCodeButton.setEnabled(false);


        compileCodeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String code = CodeArea.getText();
                try {
                    vm.sendCode(code);
                }catch (RuntimeException err){
                    JOptionPane.showMessageDialog(panel1, vm.err_msg, "Compilation Error", JOptionPane.ERROR_MESSAGE);
                }

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
                toggleButtons(false);
                vm.readyToExecute = false;

                SwingWorker<Void, Void> executionThread = new SwingWorker<Void, Void>() {
                    @Override
                    protected Void doInBackground() throws Exception {
                        vm.executeCode();

                        return null;
                    }

                    @Override
                    protected void done(){
                        updateUI();
                    }
                };

                executionThread.execute();
                toggleButtons(true);
            }
        });

        resetMemoryButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                vm.resetCPU();
                updateUI();
            }
        });

        settingsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                new Settings("Settings.");
            }
        });
    }

    @Override
    public void updateUI(){
        SwingUtilities.invokeLater( () -> {
            RegisterDumpArea.setText(cpuModule.dumpRegisters() + "\n\n" + cpuModule.dumpFlags());
            MemoryDumpArea.setText(cpuModule.dumpMemory());
            MemoryDumpArea.setCaretPosition(0);
            OutputDumpArea.setText(cpuModule.outputString.toString());
        });
    }

    public void toggleButtons(boolean enabled){
        for(JButton button : executionButtons) button.setEnabled(enabled);
    }
}
