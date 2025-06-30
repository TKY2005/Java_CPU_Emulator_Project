import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.HashMap;

public class Settings extends JFrame {
    private JPanel panel1;
    private JSlider MemorySlider;
    private JSlider DataOffsetSlider;
    private JRadioButton a8BitRadioButton;
    private JRadioButton a16BitRadioButton;
    private JRadioButton a32BitRadioButton;
    private JCheckBox writeLogsAndProgramCheckBox;
    private JButton saveButton;
    private JLabel MemorySizeLabel;
    private JLabel OffsetSizeLabel;
    private JRadioButton a64BitRadioButton;
    private JSlider CycleSpeedSlider;
    private JLabel CylceLabel;
    private JLabel DevInfo;
    private JSlider StackSizeSlider;
    private JLabel StackSizeLabel;

    public Settings(String title){
        super(title);
        this.setContentPane(panel1);
        this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        this.setLocationRelativeTo(null);
        this.pack();
        this.setVisible(true);

        // Apparently, the only way to show newlines in JLabel is to surround with html tags and break with br tags
        // Interesting Property.
        DevInfo.setText(String.format("""
                <html>
                Custom CPU Emulators Project<br>
                V%s<br>
                Youssef Mohamed Ahmed @2025<br>
                yousoftec@hotmail.com<br>
                https://github.com/TKY2005
                </html>
                """, Launcher.version));

        // Load program settings

        ButtonGroup radioButtons = new ButtonGroup();
        radioButtons.add(a8BitRadioButton);
        radioButtons.add(a16BitRadioButton);
        radioButtons.add(a32BitRadioButton);
        radioButtons.add(a64BitRadioButton);

        HashMap<String, String> settings = loadSettings();

        MemorySlider.setValue(Integer.parseInt(settings.get("MemSize")));
        MemorySizeLabel.setText(settings.get("MemSize") + "KB");

        DataOffsetSlider.setValue(Integer.parseInt(settings.get("OffsetSize")));
        OffsetSizeLabel.setText(settings.get("OffsetSize") + "KB");

        StackSizeSlider.setValue(Integer.parseInt(settings.get("StackSize")));
        StackSizeLabel.setText(settings.get("StackSize") + "KB");

        switch (settings.get("Architecture")){
            case "8" -> a8BitRadioButton.setSelected(true);
            case "16" -> a16BitRadioButton.setSelected(true);
            case "32" -> a32BitRadioButton.setSelected(true);
            case "64" -> a64BitRadioButton.setSelected(true);
        }

        writeLogsAndProgramCheckBox.setSelected(Boolean.parseBoolean(settings.get("WriteDump")));

        CycleSpeedSlider.setValue(Integer.parseInt(settings.get("Cycles")));
        CylceLabel.setText(settings.get("Cycles") + " Instructions/Second");


        MemorySlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                MemorySizeLabel.setText( MemorySlider.getValue() + "KB" );
                DataOffsetSlider.setMaximum(MemorySlider.getValue() - 1);
            }
        });


        DataOffsetSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                OffsetSizeLabel.setText( DataOffsetSlider.getValue() + "KB" );
                StackSizeSlider.setMaximum( DataOffsetSlider.getValue() - 1 );
            }
        });


        CycleSpeedSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                CylceLabel.setText( CycleSpeedSlider.getValue() + " Instructions/Second" );
            }
        });


        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                writeSettings();
            }
        });


        StackSizeSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent changeEvent) {
                StackSizeLabel.setText( StackSizeSlider.getValue() + "KB" );
            }
        });
    }

    public static HashMap<String, String> loadSettings(){
        HashMap<String, String> settings = new HashMap<>();

        try{
            File file = new File(Launcher.configFilePath);
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null){
                String settingParam = line.split("=")[0];
                String settingValue = line.split("=")[1];
                settings.put(settingParam, settingValue);
            }
        }catch (Exception e) {e.printStackTrace();}

        return settings;
    }

    public void writeSettings(){
        try{
            String successMSG = "Successfully saved new Settings. The program will exit to apply them.";

            File file = new File(Launcher.configFilePath);
            FileWriter writer = new FileWriter(file);
            PrintWriter printer = new PrintWriter(writer);

            printer.println("Version=" + Launcher.version);
            printer.println("MemSize=" + MemorySlider.getValue());
            printer.println("OffsetSize=" + DataOffsetSlider.getValue());
            printer.println("StackSize=" + StackSizeSlider.getValue());

            if (a8BitRadioButton.isSelected()) printer.println("Architecture=8");
            if (a16BitRadioButton.isSelected()) printer.println("Architecture=16");
            if (a32BitRadioButton.isSelected()) printer.println("Architecture=32");
            if (a64BitRadioButton.isSelected()) printer.println("Architecture=64");

            printer.println("WriteDump=" + writeLogsAndProgramCheckBox.isSelected());
            printer.print("Cycles=" + CycleSpeedSlider.getValue());

            printer.close();
            writer.close();

            JOptionPane.showMessageDialog(panel1, successMSG, "Success", JOptionPane.INFORMATION_MESSAGE);
            System.exit(0);
        }catch (Exception e) {
            e.printStackTrace();
            String failMSG = "Failed to write setting to new file.\n" + e.getMessage();
            JOptionPane.showMessageDialog(panel1, failMSG, "Success", JOptionPane.ERROR_MESSAGE);
        }
    }
}
