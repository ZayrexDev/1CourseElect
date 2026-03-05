package xyz.zcraft.forms;

import org.apache.logging.log4j.Logger;
import xyz.zcraft.User;
import xyz.zcraft.elect.CourseData;
import xyz.zcraft.elect.ElectRequest;
import xyz.zcraft.elect.Round;
import xyz.zcraft.elect.TeachClass;
import xyz.zcraft.util.AsyncHelper;
import xyz.zcraft.util.NetworkHelper;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import java.awt.event.ActionEvent;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class CourseElect {
    private static final Logger LOG = org.apache.logging.log4j.LogManager.getLogger(CourseElect.class);
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
        if(courseId.isBlank() || !courseId.matches("^[A-Z]{3}\\d{4}(\\d{2})?$")) {
            getResultLabel.setText("课程编号格式不正确");
            searchBtn.setEnabled(true);
            courseIdField.setEnabled(true);
            return;
        }

        if(courseId.length() == 9) {
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
                    getResultLabel.setText(courseData.courseCode() + " - " + courseData.courseName());
                    AsyncHelper.supplyAsync(() -> NetworkHelper.getTeachClasses(user, round, courseData.courseCode()))
                            .thenAccept(classes -> {
                                courseClass = new DefaultListModel<>();
                                courseClass.addAll(classes);
                                courseClassList.setModel(courseClass);
                                searchBtn.setEnabled(true);
                                courseIdField.setEnabled(true);
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
        final TeachClass selected = courseClassList.getSelectedValue();
        if (selected != null && !toElect.contains(selected) && !toQuit.contains(selected)) {
            toElect.addElement(selected);
            electList.setSelectedValue(selected, true);
            removeBtn.setEnabled(true);
            addElectBtn.setEnabled(false);
            addQuitBtn.setEnabled(true);
        }
    }

    private void addSelectedToQuit(ActionEvent e) {
        final TeachClass selected = courseClassList.getSelectedValue();
        if (selected != null && !toElect.contains(selected) && !toQuit.contains(selected)) {
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
        if(courseClass.contains(electList.getSelectedValue())) {
            courseClassList.setSelectedValue(electList.getSelectedValue(), true);
        } else {
            courseClassList.clearSelection();
        }

        removeBtn.setEnabled(true);
        addElectBtn.setEnabled(false);
        addQuitBtn.setEnabled(false);
    }

    private void quitSelected(ListSelectionEvent e) {
        electList.clearSelection();
        if(courseClass.contains(quitList.getSelectedValue())) {
            courseClassList.setSelectedValue(quitList.getSelectedValue(), true);
        } else {
            courseClassList.clearSelection();
        }

        removeBtn.setEnabled(true);
        addElectBtn.setEnabled(false);
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
}
