package xyz.zcraft.forms;

import javax.swing.*;

import static xyz.zcraft.Main.getUrl;
import static xyz.zcraft.Main.getVersion;

public class AboutDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JLabel verLabel;
    private JLabel githubLabel;

    public AboutDialog() {
        setContentPane(contentPane);
        setModal(true);
        setResizable(false);
        getRootPane().setDefaultButton(buttonOK);
        setTitle("关于");
        pack();
        setLocationRelativeTo(null);
        verLabel.setText(getVersion());
        buttonOK.addActionListener(_ -> dispose());
        githubLabel.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        githubLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                try {
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(getUrl()));
                } catch (java.io.IOException ignored) {
                }
            }
        });
    }
}
