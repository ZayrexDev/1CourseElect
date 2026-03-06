package xyz.zcraft.forms;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.elect.*;
import xyz.zcraft.util.AsyncHelper;
import xyz.zcraft.util.NetworkHelper;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class CourseElect {
    private static final Logger LOG = LogManager.getLogger(CourseElect.class);
    private final Object LOCK = new Object();
    private final Round round;
    private final User user;
    private DefaultListModel<TeachClass> toElect = new DefaultListModel<>();
    private DefaultListModel<TeachClass> toQuit = new DefaultListModel<>();
    private JTextField courseIdField;
    private JButton searchBtn;
    private JList<TeachClass> courseClassList;
    private JList<TeachClass> electList;
    private JButton submitBtn;
    private JButton discardBtn;
    private JButton addElectBtn;
    private JButton addQuitBtn;
    private JList<TeachClass> quitList;
    private JPanel rootPane;
    private JLabel getResultLabel;
    private JButton removeBtn;
    private JTextField jumpField;
    private JButton jumpBtn;
    private JLabel requestMainLabel;
    private JButton setRequestMainBtn;
    private JButton clearBtn;
    private JFrame jFrame;
    private boolean discarding = false;
    private DefaultListModel<TeachClass> courseClass = new DefaultListModel<>();
    private TeachClass requestMainClass = null;

    public CourseElect(User user, Round round) {
        this.user = user;
        this.round = round;

        setupListeners();
        setupFrame();
    }

    private void setupListeners() {
        jumpBtn.addActionListener(this::jumpToCourse);
        searchBtn.addActionListener(this::getCourse);
        courseClassList.addListSelectionListener(this::courseSelected);
        removeBtn.addActionListener(this::removeSelected);
        addElectBtn.addActionListener(this::addSelectedToElect);
        addQuitBtn.addActionListener(this::addSelectedToQuit);
        discardBtn.addActionListener(this::discardAndQuit);
        electList.addListSelectionListener(this::electSelected);
        quitList.addListSelectionListener(this::quitSelected);
        submitBtn.addActionListener(this::submit);
        setRequestMainBtn.addActionListener(this::setRequestMain);
        clearBtn.addActionListener(this::clearSearch);
    }

    private void setupFrame() {
        jFrame = new JFrame();
        jFrame.setTitle("编辑选课请求 - " + user.getUid() + " - " + round.getRoundData().calendarName());
        jFrame.setContentPane(rootPane);
        jFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        jFrame.pack();
        jFrame.setLocationRelativeTo(null);
    }

    private void submit(ActionEvent e) {
        synchronized (LOCK) {
            LOCK.notifyAll();
        }
    }

    private void getCourse(ActionEvent e) {
        searchBtn.setEnabled(false);
        courseIdField.setEnabled(false);
        String courseId = courseIdField.getText().toUpperCase().trim();
        if (courseId.isBlank() || !courseId.matches("^[A-Z]{3}\\d{4}(\\d{2})?$")) {
            getResultLabel.setText("课程编号格式不正确");
            searchBtn.setEnabled(true);
            courseIdField.setEnabled(true);
            return;
        }

        if (courseId.length() == 9) {
            jumpField.setText(courseId);
            courseIdField.setText(courseId.substring(0, 7));
            courseId = courseIdField.getText();
        }

        String finalCourseId = courseId;
        round.getCourseList().stream()
                .filter(c -> Objects.equals(c.getCourseData().newCourseCode(), finalCourseId))
                .findFirst()
                .ifPresentOrElse(c -> {
                    final CourseData courseData = c.getCourseData();
                    getResultLabel.setText(courseData.courseCode() + " - " + courseData.courseName() + " | 获取课程信息中...");
                    AsyncHelper.supplyAsync(() -> NetworkHelper.getTeachClasses(user, round, courseData.courseCode()))
                            .thenAccept(classes -> {
                                courseClass = new DefaultListModel<>();
                                courseClass.addAll(classes);
                                courseClassList.setModel(courseClass);
                                searchBtn.setEnabled(true);
                                courseIdField.setEnabled(true);
                                getResultLabel.setText(courseData.courseCode() + " - " + courseData.courseName() + " | 共 " + classes.size() + " 个教学班");
                            }).exceptionally(ex -> {
                                LOG.error("Exception threw when getting teach classes", ex);
                                getResultLabel.setText("获取课程信息失败，请重试");
                                searchBtn.setEnabled(true);
                                courseIdField.setEnabled(true);
                                return null;
                            });
                }, () -> {
                    getResultLabel.setText("未找到课程，请重试");
                    searchBtn.setEnabled(true);
                    courseIdField.setEnabled(true);
                });
    }

    private void removeSelected(ActionEvent e) {
        TeachClass selected = courseClassList.getSelectedValue();
        if (selected == null) selected = electList.getSelectedValue();
        if (selected == null) selected = quitList.getSelectedValue();
        if (selected == null) return;

        if (requestMainClass != null && selected.newTeachClassCode().equals(requestMainClass.newTeachClassCode())) {
            requestMainClass = null;
            requestMainLabel.setText("未设置");
        }

        if (toElect.contains(selected)) {
            toElect.removeElement(selected);
            removeBtn.setEnabled(false);
            addElectBtn.setEnabled(true);
            addQuitBtn.setEnabled(true);
        } else if (toQuit.contains(selected)) {
            toQuit.removeElement(selected);
            removeBtn.setEnabled(false);
            addElectBtn.setEnabled(true);
            addQuitBtn.setEnabled(true);
        }

        if (courseClass.contains(selected)) {
            addElectBtn.setEnabled(true);
            addQuitBtn.setEnabled(true);
        }
    }

    private void addSelectedToElect(ActionEvent e) {
        TeachClass selected = courseClassList.getSelectedValue();
        if (selected == null) selected = quitList.getSelectedValue();
        if (selected != null && !toElect.contains(selected)) {
            if (toQuit.contains(selected)) {
                toQuit.removeElement(selected);
                quitList.clearSelection();
            }
            toElect.addElement(selected);
            electList.setSelectedValue(selected, true);
            removeBtn.setEnabled(true);
            addElectBtn.setEnabled(false);
            addQuitBtn.setEnabled(true);
        }
    }

    private void addSelectedToQuit(ActionEvent e) {
        TeachClass selected = courseClassList.getSelectedValue();
        if (selected == null) selected = electList.getSelectedValue();
        if (selected != null && !toQuit.contains(selected)) {
            if (toElect.contains(selected)) {
                toElect.removeElement(selected);
                electList.clearSelection();
            }
            toQuit.addElement(selected);
            quitList.setSelectedValue(selected, true);
            removeBtn.setEnabled(true);
            addElectBtn.setEnabled(true);
            addQuitBtn.setEnabled(false);
        }
    }

    private void discardAndQuit(ActionEvent e) {
        discarding = true;
        jFrame.dispose();
        synchronized (LOCK) {
            LOCK.notifyAll();
        }
    }

    private void electSelected(ListSelectionEvent e) {
        quitList.clearSelection();
        if (courseClass.contains(electList.getSelectedValue())) {
            courseClassList.setSelectedValue(electList.getSelectedValue(), true);
        } else {
            courseClassList.clearSelection();
        }

        removeBtn.setEnabled(true);
        addElectBtn.setEnabled(false);
        addQuitBtn.setEnabled(true);
    }

    private void quitSelected(ListSelectionEvent e) {
        electList.clearSelection();
        if (courseClass.contains(quitList.getSelectedValue())) {
            courseClassList.setSelectedValue(quitList.getSelectedValue(), true);
        } else {
            courseClassList.clearSelection();
        }

        removeBtn.setEnabled(true);
        addElectBtn.setEnabled(true);
        addQuitBtn.setEnabled(false);
    }

    private void courseSelected(ListSelectionEvent e) {
        final TeachClass selected = courseClassList.getSelectedValue();
        addElectBtn.setEnabled(selected != null);
        addQuitBtn.setEnabled(selected != null);

        if (selected != null) {
            if (toElect.contains(selected)) {
                removeBtn.setEnabled(true);
                addElectBtn.setEnabled(false);
                addQuitBtn.setEnabled(true);

                electList.setSelectedValue(selected, true);
                quitList.clearSelection();
            } else if (toQuit.contains(selected)) {
                removeBtn.setEnabled(true);
                addElectBtn.setEnabled(true);
                addQuitBtn.setEnabled(false);

                quitList.setSelectedValue(selected, true);
                electList.clearSelection();
            } else {
                removeBtn.setEnabled(false);
                addElectBtn.setEnabled(true);
                addQuitBtn.setEnabled(true);

                electList.clearSelection();
                quitList.clearSelection();
            }
        }
    }

    private void jumpToCourse(ActionEvent e) {
        final String jumpText = jumpField.getText();
        courseClass.elements().asIterator()
                .forEachRemaining(c -> {
                    if (c.newTeachClassCode().equals(jumpText)) {
                        courseClassList.setSelectedValue(c, true);
                    }
                });
    }

    public ElectRequest openEdit(ElectRequest originalRequest) {
        toElect = new DefaultListModel<>();
        toQuit = new DefaultListModel<>();

        ElectRequest request;
        if (originalRequest == null) {
            request = new ElectRequest();
            request.setStudentId(String.valueOf(user.getUid()));
            request.setCalendarId(String.valueOf(round.getRoundData().calendarId()));
            request.setRoundId(round.getRoundData().id());
        } else {
            request = originalRequest;
        }

        if (request.getElectClasses() != null) toElect.addAll(request.getElectClasses());
        if (request.getWithdrawClasses() != null) toQuit.addAll(request.getWithdrawClasses());

        this.electList.setModel(toElect);
        this.quitList.setModel(toQuit);

        jFrame.setVisible(true);

        synchronized (LOCK) {
            try {
                LOCK.wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        jFrame.dispose();

        if (discarding) return null;

        List<TeachClass> toElectResult = new LinkedList<>();
        toElect.elements().asIterator().forEachRemaining(toElectResult::add);

        List<TeachClass> toQuitResult = new LinkedList<>();
        toQuit.elements().asIterator().forEachRemaining(toQuitResult::add);

        request.setElectClasses(toElectResult);
        request.setWithdrawClasses(toQuitResult);
        request.setMainClass(requestMainClass);

        return request;
    }

    private void setRequestMain(ActionEvent e) {
        TeachClass selected = courseClassList.getSelectedValue();
        if (selected == null) selected = electList.getSelectedValue();
        if (selected == null) selected = quitList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(jFrame, "请先选择一个课程", "错误", JOptionPane.ERROR_MESSAGE);
        } else {
            requestMainClass = selected;
            requestMainLabel.setText(requestMainClass.newTeachClassCode() + " - " + requestMainClass.courseName());
        }
    }

    private void clearSearch(ActionEvent e) {
        courseIdField.setText("");
        jumpField.setText("");
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
        rootPane.setLayout(new GridLayoutManager(6, 1, new Insets(10, 10, 10, 10), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 8, new Insets(0, 0, 0, 0), -1, -1));
        rootPane.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("课程编码：");
        panel1.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        courseIdField = new JTextField();
        panel1.add(courseIdField, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, new Dimension(100, -1), new Dimension(100, -1), new Dimension(100, -1), 0, false));
        searchBtn = new JButton();
        searchBtn.setText("查找");
        panel1.add(searchBtn, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("课号：");
        panel1.add(label2, new GridConstraints(0, 5, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        jumpField = new JTextField();
        panel1.add(jumpField, new GridConstraints(0, 6, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, new Dimension(100, -1), new Dimension(100, -1), new Dimension(100, -1), 0, false));
        jumpBtn = new JButton();
        jumpBtn.setText("跳转");
        panel1.add(jumpBtn, new GridConstraints(0, 7, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        clearBtn = new JButton();
        clearBtn.setText("清除");
        panel1.add(clearBtn, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        getResultLabel = new JLabel();
        getResultLabel.setText("输入课程编码查找...");
        rootPane.add(getResultLabel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        rootPane.add(scrollPane1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        courseClassList = new JList();
        scrollPane1.setViewportView(courseClassList);
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        rootPane.add(panel2, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel2.setBorder(BorderFactory.createTitledBorder(null, "当前请求", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel2.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(250, -1), null, 0, false));
        panel3.setBorder(BorderFactory.createTitledBorder(null, "待选课列表", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JScrollPane scrollPane2 = new JScrollPane();
        panel3.add(scrollPane2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        electList = new JList();
        final DefaultListModel defaultListModel1 = new DefaultListModel();
        electList.setModel(defaultListModel1);
        scrollPane2.setViewportView(electList);
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel2.add(panel4, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(250, -1), null, 0, false));
        panel4.setBorder(BorderFactory.createTitledBorder(null, "待退课列表", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JScrollPane scrollPane3 = new JScrollPane();
        panel4.add(scrollPane3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        quitList = new JList();
        scrollPane3.setViewportView(quitList);
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(1, 6, new Insets(0, 0, 0, 0), -1, -1));
        rootPane.add(panel5, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        submitBtn = new JButton();
        submitBtn.setText("保存更改");
        panel5.add(submitBtn, new GridConstraints(0, 5, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel5.add(spacer2, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        discardBtn = new JButton();
        discardBtn.setText("放弃更改");
        panel5.add(discardBtn, new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("请求主体：");
        panel5.add(label3, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        requestMainLabel = new JLabel();
        requestMainLabel.setText("未设置");
        panel5.add(requestMainLabel, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        setRequestMainBtn = new JButton();
        setRequestMainBtn.setText("设置主体");
        panel5.add(setRequestMainBtn, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        rootPane.add(panel6, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        addElectBtn = new JButton();
        addElectBtn.setEnabled(false);
        addElectBtn.setText("添加到待选");
        panel6.add(addElectBtn, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, 1, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        addQuitBtn = new JButton();
        addQuitBtn.setEnabled(false);
        addQuitBtn.setText("添加到待退");
        panel6.add(addQuitBtn, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, 1, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        removeBtn = new JButton();
        removeBtn.setEnabled(false);
        removeBtn.setText("移除");
        panel6.add(removeBtn, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return rootPane;
    }

}
