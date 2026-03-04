package xyz.zcraft.forms;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.User;
import xyz.zcraft.elect.Round;
import xyz.zcraft.util.NetworkHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public class Login {
    private static final Object LOCK = new Object();
    private static final Logger LOG = LogManager.getLogger(Login.class);

    private final Path cachePath = Path.of("cache");
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
    private JCheckBox cacheCheck;
    private boolean ready = false;

    private JFrame jFrame;

    private User user = null;

    public Login() {
        setUpListeners();
        tryLoadCache();
        setUpFrame();
    }

    private void tryLoadCache() {
        if (Files.exists(cachePath)) {
            final String cookie;
            try {
                cookie = Files.readString(cachePath);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(jFrame, "缓存加载失败", "错误", JOptionPane.ERROR_MESSAGE);
                LOG.error("Failed to read cache file", e);
                return;
            }
            NetworkHelper.getUserFromCookieAsync(cookie)
                    .exceptionally((_) -> {
                        JOptionPane.showMessageDialog(jFrame, "缓存加载失败，尝试删除缓存文件", "错误", JOptionPane.ERROR_MESSAGE);
                        try {
                            Files.delete(cachePath);
                        } catch (IOException e) {
                            LOG.error("Failed to delete cache file", e);
                        }
                        return null;
                    })
                    .thenAccept(u -> {
                        user = u;
                        if (user != null) {
                            uidField.setText(String.valueOf(user.getUid()));
                            passwordField.setText(cookie);
                            cacheCheck.setSelected(true);
                            cacheCheck.setText("缓存已加载");
                            uidField.setEnabled(false);
                            passwordField.setEnabled(false);
                            cacheCheck.setEnabled(false);
                            getRoundData();
                        }
                    });
        }
    }

    private void setUpFrame() {
        roundSelectionPane.setVisible(false);

        jFrame = new JFrame();
        jFrame.setContentPane(rootPane);
        jFrame.getRootPane().setDefaultButton(buttonOk);
        jFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        jFrame.setSize(new Dimension(512, 320));
        jFrame.setLocationRelativeTo(null);
    }

    private void setUpListeners() {
        buttonOk.addActionListener(this::triggerLogin);
        roundCombo.addActionListener(this::roundSelected);
    }

    public Map.Entry<User, Integer> requestLogin() {
        jFrame.setVisible(true);

        try {
            synchronized (LOCK) {
                LOCK.wait();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return Map.entry(user, ((Round) Objects.requireNonNull(roundCombo.getSelectedItem())).id());
    }

    private void triggerLogin(ActionEvent actionEvent) {
        uidField.setEnabled(false);
        passwordField.setEnabled(false);
        cacheCheck.setEnabled(false);
        buttonOk.setEnabled(false);

        if (!ready) {
            statusInfoLabel.setText("登录中...");
            try {
                NetworkHelper.getUserFromPasswordAsync(uidField.getText(), String.copyValueOf(passwordField.getPassword()))
                        .thenAccept(u -> {
                            user = u;
                            statusInfoLabel.setText("登录成功: " + user.getName());

                            if (cacheCheck.isSelected()) {
                                try {
                                    Files.writeString(cachePath, user.getCookie());
                                    cacheCheck.setText("缓存已保存");
                                } catch (Exception e) {
                                    JOptionPane.showMessageDialog(jFrame, "缓存保存失败", "错误", JOptionPane.ERROR_MESSAGE);
                                }
                            }

                            getRoundData();
                        });
            } catch (Exception e) {
                LOG.error("Login failed", e);
                JOptionPane.showMessageDialog(jFrame, "登录失败，请稍后再试一次", "错误", JOptionPane.ERROR_MESSAGE);
                statusInfoLabel.setText("");
                uidField.setEnabled(true);
                passwordField.setEnabled(true);
                cacheCheck.setEnabled(true);
                buttonOk.setEnabled(true);
            }
        } else {
            synchronized (LOCK) {
                LOCK.notifyAll();
            }
            jFrame.setVisible(false);
        }
    }

    private void getRoundData() {
        statusInfoLabel.setText("登录至 " + user.getName() + " | 获取选课数据中...");

        try {
            NetworkHelper.getRounds(user).forEach(roundCombo::addItem);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(jFrame, "选课数据获取失败", "错误", JOptionPane.ERROR_MESSAGE);
            uidField.setEnabled(true);
            buttonOk.setEnabled(true);
            return;
        }
        roundSelectionPane.setVisible(true);

        statusInfoLabel.setText("登录至 " + user.getUid() + "-" + user.getName() + " | 获取到 " + roundCombo.getItemCount() + " 个选课轮次");

        ready = true;
        buttonOk.setText("继续");
        buttonOk.setEnabled(true);
    }

    private void roundSelected(ActionEvent actionEvent) {
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
    }
}
