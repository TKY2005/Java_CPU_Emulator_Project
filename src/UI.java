import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
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

    //private LineNumberComponent lineNumbers;

    private JButton[] executionButtons = new JButton[]{
            executeCodeButton,
            compileCodeButton,
            loadCodeFromFileButton,
            settingsButton,
            resetMemoryButton
    };

    private CPU cpuModule;
    private VirtualMachine vm;

    public UI(String title){
        super(title);
        this.setContentPane(panel1);
        this.setDefaultCloseOperation(EXIT_ON_CLOSE);

        this.setExtendedState(JFrame.MAXIMIZED_BOTH);

        this.pack();
        this.setVisible(true);

        HashMap<String, String> settings = Settings.loadSettings();
        //lineNumbers = new LineNumberComponent(CodeArea);

        String architecture = settings.get("Architecture");
        if (architecture.equals("8")) cpuModule = new CPUModule8BIT();
        else if (architecture.equals("16")) cpuModule = new CPUModule16BIT();
        //else if (architecture.equals("32")) cpuModule = new CPUModule32BIT();
        //else if (architecture.equals("64")) cpuModule = new CPUModule64BIT();


        vm = new VirtualMachine(cpuModule);
        vm.UIMode = true;

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
                executeCodeButton.setEnabled(false);
                updateUI();
            }
        });

        settingsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                new Settings("Settings.");
            }
        });


        loadCodeFromFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                try{
                    JFileChooser chooser = new JFileChooser(System.getenv("HOME"));
                    chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

                    int save = chooser.showSaveDialog(panel1);
                    String code = "";

                    if (save == JFileChooser.APPROVE_OPTION){
                        File codeFile = chooser.getSelectedFile();

                        BufferedReader reader = new BufferedReader(new FileReader(codeFile));
                        String line;
                        while ( (line = reader.readLine()) != null ){
                            code += line + "\n";
                        }
                    }

                    CodeArea.setText(code);
                }catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(panel1, "Error reading from file.", "Error", JOptionPane.ERROR_MESSAGE);
                }
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
