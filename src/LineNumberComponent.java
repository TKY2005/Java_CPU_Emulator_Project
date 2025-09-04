import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
/*
 */
/* This file is generated entirely by deepseek AI
* */

public class LineNumberComponent extends JComponent implements DocumentListener, CaretListener {
    private final JTextArea textArea;
    private int currentLine = -1;
    private int manualSelection = -1;
    private Color highlightColor = new Color(180, 220, 255);
    private Color manualHighlightColor = new Color(255, 200, 150); // Different color for manual selection

    public LineNumberComponent(JTextArea textArea) {
        this.textArea = textArea;
        textArea.getDocument().addDocumentListener(this);
        textArea.addCaretListener(this);
        setFont(textArea.getFont());
        setBackground(Color.LIGHT_GRAY);
        setPreferredSize(calculatePreferredSize());
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // Fill background
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());

        FontMetrics fm = g.getFontMetrics();
        int lineHeight = fm.getHeight();
        int baseline = fm.getAscent();
        int lineCount = getLineCount();

        // Highlight manual selection (if valid)
        if (manualSelection >= 0 && manualSelection < lineCount) {
            g.setColor(manualHighlightColor);
            g.fillRect(0, manualSelection * lineHeight, getWidth(), lineHeight);
        }

        // Highlight current line (if valid)
        if (currentLine >= 0 && currentLine < lineCount) {
            g.setColor(highlightColor);
            g.fillRect(0, currentLine * lineHeight, getWidth(), lineHeight);
        }

        // Draw line numbers
        g.setColor(getForeground());
        for (int i = 0; i < lineCount; i++) {
            String number = Integer.toString(i + 1);
            int x = getWidth() - fm.stringWidth(number) - 2;
            int y = (i * lineHeight) + baseline;
            g.drawString(number, x, y);
        }
    }

    private int getLineCount() {
        return textArea.getDocument().getDefaultRootElement().getElementCount();
    }

    private Dimension calculatePreferredSize() {
        int lineCount = getLineCount();
        String maxNumber = (lineCount == 0) ? "0" : String.valueOf(lineCount);
        FontMetrics fm = getFontMetrics(getFont());
        int width = fm.stringWidth(maxNumber) + 6;
        int height = textArea.getPreferredSize().height;
        return new Dimension(width, height);
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        update();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        update();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        update();
    }

    @Override
    public void caretUpdate(CaretEvent e) {
        updateCurrentLine();
    }

    private void update() {
        setPreferredSize(calculatePreferredSize());
        revalidate();
        repaint();
    }

    private void updateCurrentLine() {
        try {
            int caretPosition = textArea.getCaretPosition();
            currentLine = textArea.getLineOfOffset(caretPosition);
            repaint();
        } catch (Exception ex) {
            currentLine = -1;
        }
    }

    /**
     * Manually highlight a specific line
     * @param lineNumber 1-based line number to highlight (-1 to clear)
     */
    public void highlightLine(int lineNumber) {
        manualSelection = lineNumber - 1; // Convert to 0-based index
        repaint();
    }

    /**
     * Clear any manual line highlight
     */
    public void clearManualHighlight() {
        manualSelection = -1;
        repaint();
    }

    public void setHighlightColor(Color color) {
        this.highlightColor = color;
        repaint();
    }

    public void setManualHighlightColor(Color color) {
        this.manualHighlightColor = color;
        repaint();
    }

    public boolean moveCaretToLine(int lineNumber) {
        int lineIndex = lineNumber - 1; // Convert to 0-based index
        try {
            int lineCount = getLineCount();
            if (lineIndex < 0 || lineIndex >= lineCount) {
                return false;
            }

            // Get the start and end offsets of the line
            int startOffset = textArea.getLineStartOffset(lineIndex);

            // Move caret to start of line
            textArea.setCaretPosition(startOffset);

            // Scroll to make the line visible
            Rectangle viewRect = textArea.modelToView(startOffset);
            if (viewRect != null) {
                textArea.scrollRectToVisible(viewRect);
            }

            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public Color getHighlightColor() {
        return highlightColor;
    }

    public Color getManualHighlightColor() {
        return manualHighlightColor;
    }
}