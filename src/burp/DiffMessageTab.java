package burp;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.util.Arrays;

public class DiffMessageTab implements IMessageEditorTab {
    private final JPanel diffyContainer = new JPanel(new BorderLayout());
    private RSyntaxTextArea textEditor = new RSyntaxTextArea(20, 60);
    private RTextScrollPane scrollPane = new RTextScrollPane(textEditor);
    private String red = "#dc3545";
    private String green = "#28a745";
    private String blue = "#0d6efd";

    private byte[] currentMessage;
    private byte[] lastMessage;
    private Boolean componentShown = false;
    private final int MAX_BYTES = 1000000;

    public DiffMessageTab() {
        diffyContainer.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                if(componentShown) {
                    return;
                }
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        diffyContainer.removeAll();
                        textEditor.setLineWrap(true);
                        textEditor.setEditable(false);
                        textEditor.setAntiAliasingEnabled(false);
                        scrollPane.setAutoscrolls(true);
                        DefaultCaret caret = (DefaultCaret) textEditor.getCaret();
                        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
                        try {
                            Theme theme = Theme.load(getClass().getResourceAsStream(
                                    "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"));
                            theme.apply(textEditor);
                        } catch (IOException ioe) {
                        }
                        diffyContainer.add(scrollPane);
                    }
                });
                componentShown = true;
            }
        });
    }

    @Override
    public String getTabCaption() {
        return "Diff";
    }

    @Override
    public Component getUiComponent() {
        return diffyContainer;
    }

    @Override
    public boolean isEnabled(byte[] content, boolean isRequest) {
        return !isRequest;
    }

    @Override
    public void setMessage(byte[] content, boolean isRequest) {
        if(isRequest) {
           return;
        }
        if (content != null && content.length > 0) {
            if(currentMessage != content) {

                if(content.length > MAX_BYTES) {
                    textEditor.setText("Response is too large to diff");
                    return;
                }

                textEditor.setText(Utilities.helpers.bytesToString(content));
                textEditor.removeAllLineHighlights();
                if(lastMessage != null && lastMessage != content && lastMessage.length > 0) {
                    java.util.List<String> currentResponse = Arrays.asList(Utilities.helpers.bytesToString(content).split("\\n"));
                    java.util.List<String> previousResponse  = Arrays.asList(Utilities.helpers.bytesToString(lastMessage).split("\\n"));
                    Highlighter highlighter = textEditor.getHighlighter();

                    Patch<String> patch = DiffUtils.diff(previousResponse, currentResponse);
                    for (AbstractDelta<String> delta : patch.getDeltas()) {
                        switch (delta.getType()) {
                            case INSERT:
                            case CHANGE:
                                java.util.List<String> sourceLines = delta.getSource().getLines();
                                java.util.List<String> targetLines = delta.getTarget().getLines();
                                int linePos = delta.getTarget().getPosition();
                                int pos = 0;
                                for(int i=0;i<linePos;i++) {
                                    pos += currentResponse.get(i).length() + 1;
                                }
                                for(int i=0;i<targetLines.size();i++) {
                                    Patch<String> linePatch = DiffUtils.diffInline(String.join("\n", sourceLines), String.join("\n", targetLines));
                                    for (AbstractDelta<String> lineDelta : linePatch.getDeltas()) {
                                        if (lineDelta.getTarget().getLines() == null) {
                                            continue;
                                        }
                                        if (lineDelta.getTarget().getLines().size() == 0) {
                                            continue;
                                        }
                                        int startPos = pos + lineDelta.getTarget().getPosition();
                                        int endPos = startPos + lineDelta.getTarget().getLines().get(0).length();
                                        if (lineDelta.getType() == DeltaType.CHANGE) {
                                            addHighlight(Color.decode(blue), startPos, endPos, highlighter);
                                        } else if (lineDelta.getType() == DeltaType.INSERT) {
                                            addHighlight(Color.decode(green), startPos, endPos, highlighter);
                                        }
                                    }
                                }
                                break;
                        }
                    }
                }
            }
            lastMessage = currentMessage;
        }
        currentMessage = content;
    }
    @Override
    public byte[] getMessage() {
        return currentMessage;
    }

    private void addHighlight(Color colour, int startPos, int endPos, Highlighter highlighter) {
        try {
            Highlighter.HighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(colour);
            highlighter.addHighlight(startPos, endPos, painter);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public byte[] getSelectedData() {
        return null;
    }
}