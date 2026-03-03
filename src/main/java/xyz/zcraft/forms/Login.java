package xyz.zcraft.forms;

import xyz.zcraft.util.NetworkHelper;
import xyz.zcraft.User;
import xyz.zcraft.elect.Round;

import javax.swing.*;
import java.awt.*;

public class Login {
    private static final Object LOCK = new Object();
    private JTextField uidField;
    private JButton buttonOk;
    private JPanel rootPane;
    private JComboBox<Round> roundCombo;
    private JTextArea roundDetail;
    private JLabel openStatusLabel;
    private JScrollPane detailScroll;
    private JPanel roundSelectionPane;
    private JPasswordField passwordField;
    private JLabel statusInfoLabel;

    private JFrame jFrame;

    private User user = null;

    public Login() {
        buttonOk.addActionListener(_ -> {
            uidField.setEnabled(false);
            passwordField.setEnabled(false);
            buttonOk.setEnabled(false);

            if (user == null) {
                try {
                    user = NetworkHelper.getUserFromPassword(uidField.getText(), String.copyValueOf(passwordField.getPassword()));
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(jFrame, "登录失败，请稍后再试一次", "错误", JOptionPane.ERROR_MESSAGE);
                    uidField.setEnabled(true);
                    passwordField.setEnabled(true);
                    buttonOk.setEnabled(true);
                    return;
                }

                statusInfoLabel.setText("登录成功: " + user.getName());

                try {
                    NetworkHelper.getRounds(user).forEach(roundCombo::addItem);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(jFrame, "选课数据获取失败", "错误", JOptionPane.ERROR_MESSAGE);
                    uidField.setEnabled(true);
                    buttonOk.setEnabled(true);
                    return;
                }
                roundSelectionPane.setVisible(true);

                statusInfoLabel.setText("登录至" + user.getUid() + "-" + user.getName() + " | 获取到 " + roundCombo.getItemCount() + " 个选课轮次");

                buttonOk.setText("继续");
                buttonOk.setEnabled(true);
            } else {
                synchronized (LOCK) {
                    LOCK.notifyAll();
                }
                jFrame.setVisible(false);
            }
        });

        roundCombo.addActionListener(_ -> {
            final Round round = (Round) roundCombo.getSelectedItem();
            if (round != null) {
                openStatusLabel.setText("开放: " + (round.openFlag() == 1 ? "√" : "×"));
                roundDetail.setText(String.format(
                        """
                                %s %s
                                选课开放时间：%s-%s
                                选课说明：
                                %s
                                """, round.calendarName(), round.name(), round.beginTime(), round.endTime(), round.remark()));
                detailScroll.getHorizontalScrollBar().setValue(0);
            }
        });

        roundSelectionPane.setVisible(false);

        jFrame = new JFrame();
        jFrame.setContentPane(rootPane);
        jFrame.getRootPane().setDefaultButton(buttonOk);
        jFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        jFrame.setSize(new Dimension(512, 320));
        jFrame.setLocationRelativeTo(null);
    }

    public User requestLogin() {
        jFrame.setVisible(true);

        try {
            synchronized (LOCK) {
                LOCK.wait();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return user;
    }
}
