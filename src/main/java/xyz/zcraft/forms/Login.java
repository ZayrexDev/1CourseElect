package xyz.zcraft.forms;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.User;
import xyz.zcraft.elect.Course;
import xyz.zcraft.elect.Round;
import xyz.zcraft.elect.RoundData;
import xyz.zcraft.util.AsyncHelper;
import xyz.zcraft.util.NetworkHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class Login {
    private static final Object LOCK = new Object();
    private static final Logger LOG = LogManager.getLogger(Login.class);

    private final Path cachePath = Path.of("cache");

    private JTextField uidField;
    private JButton buttonOk;
    private JPanel rootPane;
    private JComboBox<RoundData> roundCombo;
    private JTextArea roundDetail;
    private JLabel openStatusLabel;
    private JScrollPane detailScroll;
    private JPanel roundSelectionPane;
    private JPasswordField passwordField;
    private JLabel statusInfoLabel;
    private JCheckBox cacheCheck;

    private boolean loginReady = false;
    private boolean roundDataReady = false;
    private boolean courseListReady = false;
    private List<Course> courses = null;

    private JFrame jFrame;

    private User user = null;
    private RoundData roundData = null;

    public Login() {
        setUpListeners();
        tryLoadCache();
        setUpFrame();
    }

    private void tryLoadCache() {
        if (!Files.exists(cachePath)) {
            return;
        }

        statusInfoLabel.setText("尝试加载缓存...");

        uidField.setEnabled(false);
        passwordField.setEnabled(false);
        AsyncHelper.supplyAsync(() -> {
                    final String cookie;
                    try {
                        cookie = Files.readString(cachePath);
                        uidField.setText("加载缓存中...");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return NetworkHelper.getUserFromCookie(cookie);
                })
                .exceptionally((e) -> {
                    LOG.error("Failed to load cache", e);
                    JOptionPane.showMessageDialog(jFrame, "缓存加载失败，尝试删除缓存文件", "错误", JOptionPane.ERROR_MESSAGE);
                    try {
                        Files.delete(cachePath);
                    } catch (IOException ex) {
                        LOG.error("Failed to delete cache file", ex);
                    }
                    statusInfoLabel.setText("缓存加载失败");
                    uidField.setText("");
                    passwordField.setText("");
                    uidField.setEnabled(true);
                    passwordField.setEnabled(true);
                    return null;
                })
                .thenAccept(u -> {
                    user = u;
                    if (user != null) {
                        loginReady = true;

                        uidField.setText(String.valueOf(user.getUid()));
                        cacheCheck.setSelected(true);

                        cacheCheck.setText("缓存已加载");
                        statusInfoLabel.setText("登录成功: " + user.getName());
                        buttonOk.setText("获取选课轮次");

                        uidField.setEnabled(false);
                        passwordField.setEnabled(false);
                        cacheCheck.setEnabled(false);
                    }
                });
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
        buttonOk.addActionListener(this::proceed);
        roundCombo.addActionListener(this::roundSelected);
    }

    public Map.Entry<User, Round> requestLogin() {
        jFrame.setVisible(true);

        try {
            synchronized (LOCK) {
                LOCK.wait();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return Map.entry(user, new Round(roundData, courses));
    }

    private void proceed(ActionEvent actionEvent) {
        buttonOk.setEnabled(false);

        if (!loginReady) {
            uidField.setEnabled(false);
            passwordField.setEnabled(false);
            cacheCheck.setEnabled(false);

            statusInfoLabel.setText("登录中...");
            AsyncHelper.supplyAsync(() -> NetworkHelper.getUserFromPassword(uidField.getText(), String.copyValueOf(passwordField.getPassword())))
                    .thenAccept(u -> {
                        user = u;
                        statusInfoLabel.setText("登录成功: " + user.getName());

                        if (cacheCheck.isSelected()) {
                            try {
                                Files.writeString(cachePath, user.getCookie());
                                cacheCheck.setText("缓存已保存");
                            } catch (Exception e) {
                                LOG.error("Failed to save cache", e);
                                JOptionPane.showMessageDialog(jFrame, "缓存保存失败", "错误", JOptionPane.ERROR_MESSAGE);
                            }
                        }

                        loginReady = true;
                        buttonOk.setText("获取选课轮次");
                        buttonOk.setEnabled(true);
                    }).exceptionally((e) -> {
                        LOG.error("Login failed", e);
                        JOptionPane.showMessageDialog(jFrame, "登录失败，请稍后再试一次", "错误", JOptionPane.ERROR_MESSAGE);
                        statusInfoLabel.setText("登录失败");
                        uidField.setEnabled(true);
                        passwordField.setEnabled(true);
                        cacheCheck.setEnabled(true);
                        buttonOk.setEnabled(true);
                        return null;
                    });
        } else if (!roundDataReady) {
            statusInfoLabel.setText("登录至 " + user.getName() + " | 获取选课轮次中...");
            AsyncHelper.supplyAsync(() -> NetworkHelper.getRounds(user))
                    .thenAccept(rounds -> {
                        rounds.forEach(roundCombo::addItem);
                        roundSelectionPane.setVisible(true);

                        statusInfoLabel.setText("登录至 " + user.getUid() + "-" + user.getName() + " | 获取到 " + roundCombo.getItemCount() + " 个选课轮次");

                        roundDataReady = true;
                        buttonOk.setText("获取课程数据");
                        buttonOk.setEnabled(true);
                    }).exceptionally((e) -> {
                        LOG.error("Failed to get round data", e);
                        JOptionPane.showMessageDialog(jFrame, "选课轮次获取失败", "错误", JOptionPane.ERROR_MESSAGE);
                        statusInfoLabel.setText("登录至 " + user.getUid() + "-" + user.getName() + " | 选课轮次获取失败");
                        buttonOk.setText("获取选课轮次");
                        buttonOk.setEnabled(true);
                        return null;
                    });
        } else if (!courseListReady) {
            roundCombo.setEnabled(false);

            statusInfoLabel.setText("登录至 " + user.getUid() + "-" + user.getName() + " | 获取课程数据中");
            this.roundData = (RoundData) roundCombo.getSelectedItem();

            if (roundData == null) {
                JOptionPane.showMessageDialog(jFrame, "请选择一个选课轮次", "错误", JOptionPane.ERROR_MESSAGE);
                buttonOk.setEnabled(true);
                roundCombo.setEnabled(true);
                return;
            }

            AsyncHelper.supplyAsync(() -> NetworkHelper.getRoundCourses(user, roundData.id()))
                    .thenAccept(courses -> {
                        this.courses = courses;
                        courseListReady = true;
                        statusInfoLabel.setText("登录至 " + user.getUid() + "-" + user.getName() + " | 获取到" + courses.size() + "个课程");
                        buttonOk.setText("完成");
                        buttonOk.setEnabled(true);
                    }).exceptionally((e) -> {
                        LOG.error("Failed to get course list", e);
                        JOptionPane.showMessageDialog(jFrame, "选课数据获取失败", "错误", JOptionPane.ERROR_MESSAGE);
                        statusInfoLabel.setText("登录至 " + user.getUid() + "-" + user.getName() + " | 课程数据获取失败");
                        buttonOk.setText("获取课程数据");
                        buttonOk.setEnabled(true);
                        roundCombo.setEnabled(true);
                        return null;
                    });
        } else {
            synchronized (LOCK) {
                LOCK.notifyAll();
            }
            jFrame.setVisible(false);
        }
    }

    private void roundSelected(ActionEvent actionEvent) {
        final RoundData roundData = (RoundData) roundCombo.getSelectedItem();
        if (roundData != null) {
            openStatusLabel.setText("开放: " + (roundData.openFlag() == 1 ? "√" : "×"));
            roundDetail.setText(String.format(
                    """
                            %s %s
                            选课开放时间：%s-%s
                            选课说明：
                            %s
                            """, roundData.calendarName(), roundData.name(), roundData.beginTime(), roundData.endTime(), roundData.remark()));
            detailScroll.getHorizontalScrollBar().setValue(0);
        }
    }
}
