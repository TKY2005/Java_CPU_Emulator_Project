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
    private JSlider DataPercentageSlider;
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
    private JSlider StackPercentageSlider;
    private JLabel StackSizeLabel;
    private JCheckBox allowDirectManipulationOfCheckBox;
    private JSlider UIintervalSlider;
    private JLabel UIintervalLabel;
    private JSlider ROMPercentageSlider;
    private JLabel ROMSizeLabel;
    private JPanel panel;
    private JCheckBox OverFlowCheckBox;

    private boolean adjustingSliders = false;
    private int romValue, dataValue, stackValue;

    public Settings(String title){
        super(title);
        this.setContentPane(panel1);
        this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

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

        a32BitRadioButton.setEnabled(false);
        a64BitRadioButton.setEnabled(false);

        HashMap<String, String> settings = loadSettings();

        MemorySlider.setValue((int) Float.parseFloat(settings.get("MemSize")));
        MemorySizeLabel.setText(settings.get("MemSize") + "KB");

        // Ensure initial values sum to 100
        dataValue = Integer.parseInt(settings.get("DataPercentage"));
        stackValue = Integer.parseInt(settings.get("StackPercentage"));
        romValue = Integer.parseInt(settings.get("ROMPercentage"));

        //System.out.println(dataValue);
        //System.out.println(stackValue);
        if (romValue + dataValue + stackValue != 100) {
            System.out.println("Detected faulty ROM, DATA and STACK sizes. Readjusting...");
            // Distribute the remaining percentage equally
            int remaining = 100 - romValue;
            dataValue = remaining / 2;
            stackValue = remaining - dataValue;

            ROMPercentageSlider.setValue(romValue);
            DataPercentageSlider.setValue(dataValue);
            StackPercentageSlider.setValue(stackValue);
        }
        ROMSizeLabel.setText(romValue + "%");
        OffsetSizeLabel.setText(dataValue + "%");
        StackSizeLabel.setText(stackValue + "%");

        switch (settings.get("Architecture")){
            case "8" -> a8BitRadioButton.setSelected(true);
            case "16" -> a16BitRadioButton.setSelected(true);
            case "32" -> a32BitRadioButton.setSelected(true);
            case "64" -> a64BitRadioButton.setSelected(true);
        }

        writeLogsAndProgramCheckBox.setSelected(Boolean.parseBoolean(settings.get("WriteDump")));
        allowDirectManipulationOfCheckBox.setSelected( Boolean.parseBoolean( settings.get("OverwritePC") ) );
        OverFlowCheckBox.setSelected( Boolean.parseBoolean(settings.get("OverFlowProtection")) );

        CycleSpeedSlider.setValue(Integer.parseInt(settings.get("Cycles")));
        CylceLabel.setText(settings.get("Cycles") + " Cycles/Second");

        UIintervalSlider.setValue(Integer.parseInt(settings.get("UiUpdateInterval")));
        UIintervalLabel.setText(settings.get("UiUpdateInterval") + "ms");

        MemorySlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                MemorySizeLabel.setText( MemorySlider.getValue() + "KB" );
            }
        });

        ROMPercentageSlider.addChangeListener(e -> {
                if (adjustingSliders) return;
            adjustingSliders = true;

            int newRomValue = ROMPercentageSlider.getValue();
            adjustOtherSliders(newRomValue, 0); // 0 means ROM changed

            adjustingSliders = false;
        });


        DataPercentageSlider.addChangeListener(e -> {
            if (adjustingSliders) return;
            adjustingSliders = true;

            int newDataValue = DataPercentageSlider.getValue();
            adjustOtherSliders(newDataValue, 1); // 1 means Data changed

            adjustingSliders = false;
        });

        StackPercentageSlider.addChangeListener(e -> {
            if (adjustingSliders) return;
            adjustingSliders = true;

            int newStackValue = StackPercentageSlider.getValue();
            adjustOtherSliders(newStackValue, 2); // 2 means Stack changed

            adjustingSliders = false;
        });



        CycleSpeedSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                CylceLabel.setText( CycleSpeedSlider.getValue() + " Cycles/Second" );
            }
        });


        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                writeSettings();
            }
        });


        UIintervalSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent changeEvent) {
                UIintervalLabel.setText(UIintervalSlider.getValue() + "ms");
            }
        });
    }


    private void adjustOtherSliders(int changedValue, int changedSlider) {
        int remaining = 100 - changedValue;

        // Get current values of the other two sliders
        int other1, other2;
        if (changedSlider == 0) { // ROM changed
            other1 = dataValue;
            other2 = stackValue;
        } else if (changedSlider == 1) { // Data changed
            other1 = romValue;
            other2 = stackValue;
        } else { // Stack changed
            other1 = romValue;
            other2 = dataValue;
        }

        int totalOther = other1 + other2;

        if (totalOther == 0) {
            // If both others are 0, split remaining equally
            other1 = remaining / 2;
            other2 = remaining - other1;
        } else {
            // Distribute remaining proportionally
            other1 = (other1 * remaining) / totalOther;
            other2 = remaining - other1;

            // Ensure no negative values and handle rounding errors
            if (other1 < 0) other1 = 0;
            if (other2 < 0) other2 = 0;

            // Re-adjust if there's still remaining due to rounding
            int actualTotal = other1 + other2;
            if (actualTotal != remaining) {
                if (changedSlider == 0) {
                    other1 += (remaining - actualTotal);
                } else if (changedSlider == 1) {
                    other2 += (remaining - actualTotal);
                } else {
                    other1 += (remaining - actualTotal);
                }
            }
        }

        // Update the appropriate variables and sliders
        if (changedSlider == 0) { // ROM changed
            romValue = changedValue;
            dataValue = other1;
            stackValue = other2;

            DataPercentageSlider.setValue(dataValue);
            StackPercentageSlider.setValue(stackValue);
            OffsetSizeLabel.setText(dataValue + "%");
            StackSizeLabel.setText(stackValue + "%");
        } else if (changedSlider == 1) { // Data changed
            dataValue = changedValue;
            romValue = other1;
            stackValue = other2;

            ROMPercentageSlider.setValue(romValue);
            StackPercentageSlider.setValue(stackValue);
            ROMSizeLabel.setText(romValue + "%");
            StackSizeLabel.setText(stackValue + "%");
        } else { // Stack changed
            stackValue = changedValue;
            romValue = other1;
            dataValue = other2;

            ROMPercentageSlider.setValue(romValue);
            DataPercentageSlider.setValue(dataValue);
            ROMSizeLabel.setText(romValue + "%");
            OffsetSizeLabel.setText(dataValue + "%");
        }

        ROMSizeLabel.setText(romValue + "%");
        OffsetSizeLabel.setText(dataValue + "%");
        StackSizeLabel.setText(stackValue + "%");
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
            //System.out.println(romValue);
            //System.out.println(dataValue);
            //System.out.println(stackValue);
            printer.println("ROMPercentage=" + romValue);
            printer.println("DataPercentage=" + dataValue);
            printer.println("StackPercentage=" + stackValue);

            if (a8BitRadioButton.isSelected()) printer.println("Architecture=8");
            if (a16BitRadioButton.isSelected()) printer.println("Architecture=16");
            if (a32BitRadioButton.isSelected()) printer.println("Architecture=32");
            if (a64BitRadioButton.isSelected()) printer.println("Architecture=64");

            printer.println("WriteDump=" + writeLogsAndProgramCheckBox.isSelected());
            printer.println("Cycles=" + CycleSpeedSlider.getValue());
            printer.println("OverwritePC=" + allowDirectManipulationOfCheckBox.isSelected());
            printer.println("OverFlowProtection=" + OverFlowCheckBox.isSelected());
            printer.print("UiUpdateInterval=" + UIintervalSlider.getValue());

            printer.close();
            writer.close();

            JOptionPane.showMessageDialog(panel1, successMSG, "Success", JOptionPane.INFORMATION_MESSAGE);
            System.exit(0);
        }catch (Exception e) {
            e.printStackTrace();
            String failMSG = "Failed to write setting to new file.\n" + e.getMessage();
            JOptionPane.showMessageDialog(panel1, failMSG, "Failure", JOptionPane.ERROR_MESSAGE);
        }
    }
}
