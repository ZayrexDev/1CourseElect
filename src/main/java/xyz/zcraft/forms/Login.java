package xyz.zcraft.forms;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.elect.User;
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

    private final Path cachePath = Path.of("cache/cookie");

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

        buttonOk.setEnabled(false);
        cacheCheck.setEnabled(false);
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
                    cacheCheck.setEnabled(true);
                    buttonOk.setEnabled(true);
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
                        statusInfoLabel.setText("登录至 " + user.getUid() + "-" + user.getName());
                        buttonOk.setText("获取选课轮次");
                        buttonOk.setEnabled(true);

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
                        statusInfoLabel.setText("登录至 " + user.getUid() + "-" + user.getName());

                        if (cacheCheck.isSelected()) {
                            try {
                                Files.createDirectories(cachePath.getParent());
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
            statusInfoLabel.setText("登录至 " + user.getUid() + "-" + user.getName() + " | 获取选课轮次中...");
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

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        rootPane = new JPanel();
        rootPane.setLayout(new GridLayoutManager(4, 3, new Insets(10, 10, 10, 10), -1, -1));
        buttonOk = new JButton();
        buttonOk.setText("获取选课信息");
        rootPane.add(buttonOk, new GridConstraints(3, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        roundSelectionPane = new JPanel();
        roundSelectionPane.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        rootPane.add(roundSelectionPane, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        roundSelectionPane.add(panel1, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        roundCombo = new JComboBox();
        final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
        roundCombo.setModel(defaultComboBoxModel1);
        panel1.add(roundCombo, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("可用的选课：");
        panel1.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        openStatusLabel = new JLabel();
        openStatusLabel.setText("开放：×");
        panel1.add(openStatusLabel, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        detailScroll = new JScrollPane();
        roundSelectionPane.add(detailScroll, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        roundDetail = new JTextArea();
        roundDetail.setEditable(false);
        roundDetail.setLineWrap(true);
        roundDetail.setText("");
        detailScroll.setViewportView(roundDetail);
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        rootPane.add(panel2, new GridConstraints(0, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        uidField = new JTextField();
        panel2.add(uidField, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("学号：");
        panel2.add(label2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("密码：");
        panel2.add(label3, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        passwordField = new JPasswordField();
        panel2.add(passwordField, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        statusInfoLabel = new JLabel();
        statusInfoLabel.setText("");
        rootPane.add(statusInfoLabel, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        rootPane.add(spacer1, new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        cacheCheck = new JCheckBox();
        cacheCheck.setText("缓存登录凭据");
        rootPane.add(cacheCheck, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return rootPane;
    }
}
