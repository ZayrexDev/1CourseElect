package xyz.zcraft.forms;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.elect.*;
import xyz.zcraft.util.AsyncHelper;
import xyz.zcraft.util.JTextAreaLoggerFactory;
import xyz.zcraft.util.NetworkHelper;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ElectRequester {
    private final Logger LOG = LogManager.getLogger(ElectRequester.class);
    private final User user;
    private final Round round;
    private final DefaultListModel<ElectRequest> requests = new DefaultListModel<>();
    private ScheduledExecutorService scheduler = null;
    private Logger ELECT_LOG = null;
    private Logger RESULT_LOG = null;
    private JCheckBox onFixedTimeCheck;
    private JFormattedTextField fixedTimeCountField;
    private JFormattedTextField fixedTimeDelayField;
    private JButton manualRequestBtn;
    private JTextArea logArea;
    private JFormattedTextField emptyDelayField;
    private JButton editRequestBtn;
    private JCheckBox onEmptyCheck;
    private JLabel countdownLabel;
    private JLabel fixedTimeLabel;
    private JFormattedTextField manualCountField;
    private JList<ElectRequest> electRequestJList;
    private JButton addRequestBtn;
    private JButton removeRequestBtn;
    private JPanel rootPane;
    private JFormattedTextField manualDelayField;
    private JButton manualStopBtn;
    private JFormattedTextField resultLookUpDelayField;
    private JButton lookUpDelayUpdateBtn;
    private JTextArea resultLogArea;
    private JButton resultLookUpButton;
    private JLabel resultLookUpStatus;
    private JButton checkCapacityBtn;
    private JButton sortUpBtn;
    private JButton sortDownBtn;
    private JFormattedTextField requestMidDelayField;
    private JList<TeachClass> electList;
    private JList<TeachClass> quitList;
    private JPanel requestInfoPane;
    private JLabel awaLable;
    private JFrame jFrame;
    private boolean resultReady = false;
    private volatile boolean manualStarted = false;
    private boolean lastIsProcessing = false;
    private final Timer resultTimer = new Timer(500, this::updateCountdown);
    private Duration countdown = Duration.ZERO;
    private boolean checkCapacityRunning = false;
    private final Timer checkCapacityTimer = new Timer(1000, this::checkCapacityCountdown);

    public ElectRequester(User user, Round round) {
        this.user = user;
        this.round = round;

        setupComponents();
        setupListeners();
        setupFormatters();
        setupFrame();
        setupLogger();
    }

    private void checkCapacityCountdown(ActionEvent actionEvent) {
        if (!checkCapacityRunning) return;
        if (countdown.isPositive()) {
            countdownLabel.setText(String.format("%d:%02d:%02d", countdown.toHours(), countdown.toMinutesPart(), countdown.toSecondsPart()));
            countdown = countdown.minusSeconds(1);
        } else {
            checkCapacityNow();
        }
    }

    private void checkCapacityNow() {
        LOG.info("Checking capacity...");
        ELECT_LOG.info("[容量检测]正在检查选课容量...");
        countdown = Duration.ofSeconds(Integer.parseInt(emptyDelayField.getText()) * 60L);
        AsyncHelper.supplyAsync(() -> {
            List<TeachClass> checkList = new LinkedList<>();
            requests.elements().asIterator().forEachRemaining(r -> checkList.addAll(r.getElectClasses()));

            final boolean[] capacityAvailable = {false};
            for (TeachClass teachClass : checkList) {
                NetworkHelper.getTeachClasses(user, round, teachClass.courseCode())
                        .stream()
                        .filter(c -> Objects.equals(c.teachClassId(), teachClass.teachClassId()))
                        .findFirst()
                        .ifPresent(c -> {
                            if (c.maxNumber() > c.currentNumber()) {
                                capacityAvailable[0] = true;
                            }
                        });
                if (capacityAvailable[0]) break;
            }

            if (capacityAvailable[0]) {
                ELECT_LOG.info("[容量检测]检测到选课容量，正在发送选课请求...");
                requests.elements().asIterator().forEachRemaining(this::sendRequest);
            } else {
                ELECT_LOG.info("[容量检测]未检测到选课容量，继续等待...");
            }
            return null;
        });
    }

    private void setupComponents() {
        electRequestJList.setModel(requests);
    }

    private void setupListeners() {
        editRequestBtn.addActionListener(this::openEditRequest);
        addRequestBtn.addActionListener(this::openAddRequest);
        removeRequestBtn.addActionListener(this::removeRequest);
        manualRequestBtn.addActionListener(this::sendManualRequest);
        manualStopBtn.addActionListener(_ -> manualStarted = false);
        lookUpDelayUpdateBtn.addActionListener(_ -> resultTimer.setDelay(Integer.parseInt(resultLookUpDelayField.getText())));
        onFixedTimeCheck.addActionListener(this::setTimeSchedule);
        resultLookUpButton.addActionListener(_ ->
                AsyncHelper.supplyAsync(() -> NetworkHelper.getElectResult(user, round))
                        .thenAccept(this::handleResult)
        );
        checkCapacityBtn.addActionListener(_ -> checkCapacityNow());
        onEmptyCheck.addActionListener(_ -> {
            if (onEmptyCheck.isSelected()) {
                emptyDelayField.setEnabled(false);
                checkCapacityRunning = true;
                countdown = Duration.ofSeconds(Integer.parseInt(emptyDelayField.getText()) * 60L);
                checkCapacityTimer.start();
            } else {
                checkCapacityRunning = false;
                countdownLabel.setText("--:--:--");
                checkCapacityTimer.stop();
                emptyDelayField.setEnabled(true);
            }
        });
        sortUpBtn.addActionListener(_ -> {
            int index = electRequestJList.getSelectedIndex();
            if (index > 0) {
                ElectRequest selected = requests.get(index);
                requests.remove(index);
                requests.add(index - 1, selected);
                electRequestJList.setSelectedIndex(index - 1);
            }
        });
        sortDownBtn.addActionListener(_ -> {
            int index = electRequestJList.getSelectedIndex();
            if (index >= 0 && index < requests.size() - 1) {
                ElectRequest selected = requests.get(index);
                requests.remove(index);
                requests.add(index + 1, selected);
                electRequestJList.setSelectedIndex(index + 1);
            }
        });
        electRequestJList.addListSelectionListener(_ -> refreshRequestDetail());
        awaLable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                new AboutDialog().setVisible(true);
            }
        });
    }

    private void refreshRequestDetail() {
        final ElectRequest selected = electRequestJList.getSelectedValue();
        if (selected != null) {
            editRequestBtn.setEnabled(true);
            removeRequestBtn.setEnabled(true);
            sortDownBtn.setEnabled(true);
            sortUpBtn.setEnabled(true);

            electList.setListData(selected.getElectClasses().toArray(new TeachClass[0]));
            quitList.setListData(selected.getWithdrawClasses().toArray(new TeachClass[0]));
            requestInfoPane.setVisible(true);
        } else {
            editRequestBtn.setEnabled(false);
            removeRequestBtn.setEnabled(false);
            sortDownBtn.setEnabled(false);
            sortUpBtn.setEnabled(false);

            requestInfoPane.setVisible(false);
            electList.setListData(new TeachClass[0]);
            quitList.setListData(new TeachClass[0]);
        }
    }

    private void setupFormatters() {
        final var tf = new JFormattedTextField.AbstractFormatterFactory() {
            @Override
            public JFormattedTextField.AbstractFormatter getFormatter(JFormattedTextField tf) {
                NumberFormat format = NumberFormat.getInstance();
                format.setGroupingUsed(false);
                format.setParseIntegerOnly(true);
                NumberFormatter formatter = new NumberFormatter(format);
                formatter.setValueClass(Integer.class);
                formatter.setMinimum(0);
                formatter.setMaximum(Integer.MAX_VALUE);
                formatter.setAllowsInvalid(false);
                formatter.setCommitsOnValidEdit(true);
                return formatter;
            }
        };

        manualDelayField.setFormatterFactory(tf);
        manualCountField.setFormatterFactory(tf);
        fixedTimeDelayField.setFormatterFactory(tf);
        fixedTimeCountField.setFormatterFactory(tf);
        emptyDelayField.setFormatterFactory(tf);
        resultLookUpDelayField.setFormatterFactory(tf);
        requestMidDelayField.setFormatterFactory(tf);

        manualDelayField.setValue(500);
        manualCountField.setValue(5);
        fixedTimeDelayField.setValue(500);
        fixedTimeCountField.setValue(5);
        emptyDelayField.setValue(5);
        resultLookUpDelayField.setValue(200);
        requestMidDelayField.setValue(50);
    }

    private void handleResult(ElectResult electResult) {
        if (Objects.equals(electResult.status(), "Loading")
                || Objects.equals(electResult.status(), "Processing")
        ) {
            resultReady = false;
            resultLookUpStatus.setText("√");
            if (!lastIsProcessing) RESULT_LOG.info("服务器处理选课中...");
            lastIsProcessing = true;
        } else if (Objects.equals(electResult.status(), "Ready")) {
            lastIsProcessing = false;
            final List<TeachClass> classes = new LinkedList<>();
            requests.elements().asIterator().forEachRemaining(r -> {
                classes.addAll(r.getElectClasses());
                classes.addAll(r.getWithdrawClasses());
            });
            resultReady = true;
            resultLookUpStatus.setText("×");

            for (int i = 0; i < electResult.successCourses().size(); i++) {
                long courseId = electResult.successCourses().getLongValue(i);
                classes.stream()
                        .filter(c -> Objects.equals(c.teachClassId(), courseId))
                        .findFirst()
                        .ifPresent(c ->
                                RESULT_LOG.info("选/退课成功: {}-{}", c.newTeachClassCode(), c.courseName())
                        );
            }
            electResult.failedReasons().forEach((key, value) ->
                    RESULT_LOG.info("选课失败: {}-{}", key, value)
            );
        }
    }

    private void updateCountdown(ActionEvent actionEvent) {
        if (resultReady) {
            resultLookUpStatus.setText("×");
            return;
        }

        final ElectResult electResult = NetworkHelper.getElectResult(user, round);
        handleResult(electResult);
    }

    private void sendRequest(ElectRequest request) {
        AsyncHelper.supplyAsync(() -> NetworkHelper.sendElectRequest(user, request))
                .thenAccept(this::handleResult);
        resultReady = false;
        if (!resultTimer.isRunning()) {
            resultTimer.start();
        }
    }

    private void sendManualRequest(ActionEvent e) {
        manualStarted = true;
        AsyncHelper.supplyAsync(() -> {
            ELECT_LOG.info("手动请求开始执行");
            int count = Integer.parseInt(manualCountField.getText());
            long delay = Long.parseLong(manualDelayField.getText());
            for (int i = 0; i < count; i++) {
                if (!manualStarted) {
                    ELECT_LOG.info("手动请求已停止：被用户中途停止");
                    break;
                }
                int finalI = i;
                requests.elements().asIterator().forEachRemaining(request -> {
                    try {
                        Thread.sleep(Long.parseLong(requestMidDelayField.getText()));
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                    sendRequest(request);
                    ELECT_LOG.info("[手动]已发送选课请求({}/{}): {}", finalI + 1, count, request);
                });
                try {
                    Thread.sleep(Duration.ofMillis(delay));
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
            ELECT_LOG.info("手动请求发送完成");
            return null;
        });
    }

    private void setupLogger() {
        ELECT_LOG = JTextAreaLoggerFactory.create(logArea);
        RESULT_LOG = JTextAreaLoggerFactory.create(resultLogArea);
    }

    private void openAddRequest(ActionEvent actionEvent) {
        AsyncHelper.supplyAsync(() -> {
            CourseElect courseElect = new CourseElect(user, round);
            return courseElect.openEdit(null);
        }).thenAccept(e -> {
            if (e != null) {
                requests.addElement(e);
            }
        }).exceptionally(e -> {
            LOG.error("Exception threw when adding request", e);
            return null;
        });
    }

    private void openEditRequest(ActionEvent actionEvent) {
        AsyncHelper.supplyAsync(() -> {
            CourseElect courseElect = new CourseElect(user, round);
            return courseElect.openEdit(electRequestJList.getSelectedValue());
        }).thenAccept(_ -> refreshRequestDetail()).exceptionally(e -> {
            LOG.error("Exception threw when editing request", e);
            return null;
        });
    }

    private void removeRequest(ActionEvent e) {
        requests.removeElement(electRequestJList.getSelectedValue());
    }

    private void setupFrame() {
        jFrame = new JFrame("选课 - " + user.getUid() + " - " + round.getRoundData().calendarName());
        jFrame.setContentPane(rootPane);
        jFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jFrame.setSize(new Dimension(800, 600));
        jFrame.setLocationRelativeTo(null);
    }

    public void show() {
        jFrame.setVisible(true);
    }

    private void setTimeSchedule(ActionEvent e) {
        if (onFixedTimeCheck.isSelected()) {
            fixedTimeCountField.setEnabled(false);
            fixedTimeDelayField.setEnabled(false);

            String timeStr = JOptionPane.showInputDialog(jFrame,
                    "请输入定时请求的开始时间（24小时制），格式: ⌈HH:mm:ss⌋",
                    "设置定时请求", JOptionPane.PLAIN_MESSAGE);

            if (timeStr == null) {
                onFixedTimeCheck.setSelected(false);
                return;
            }

            if (timeStr.matches("^([01]?\\d|2[0-3]):[0-5]\\d:[0-5]\\d$")) {
                String[] parts = timeStr.split(":");
                int targetHour = Integer.parseInt(parts[0]);
                int targetMinute = Integer.parseInt(parts[1]);
                int targetSecond = Integer.parseInt(parts[2]);

                fixedTimeLabel.setText(String.format("%02d:%02d:%02d", targetHour, targetMinute, targetSecond));

                LocalDateTime now = LocalDateTime.now();
                LocalDateTime targetTime = now.withHour(targetHour).withMinute(targetMinute).withSecond(targetSecond).withNano(0);

                long delayInSeconds = Duration.between(now, targetTime).getSeconds();

                if (scheduler != null) scheduler.shutdownNow();
                scheduler = Executors.newScheduledThreadPool(1);
                scheduler.schedule(() -> {
                    ELECT_LOG.info("定时请求开始执行");
                    int count = Integer.parseInt(fixedTimeCountField.getText());
                    long delay = Long.parseLong(fixedTimeDelayField.getText());
                    for (int i = 0; i < count; i++) {
                        if (!onFixedTimeCheck.isSelected()) {
                            ELECT_LOG.info("定时请求已停止：被用户中途停止");
                            break;
                        }
                        int finalI = i;
                        requests.elements().asIterator().forEachRemaining(request -> {
                            try {
                                Thread.sleep(Long.parseLong(requestMidDelayField.getText()));
                            } catch (InterruptedException ex) {
                                throw new RuntimeException(ex);
                            }
                            sendRequest(request);
                            ELECT_LOG.info("[定时]已发送选课请求({}/{}): {}", finalI + 1, count, request);
                        });
                        try {
                            Thread.sleep(Duration.ofMillis(delay));
                        } catch (InterruptedException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                    ELECT_LOG.info("定时请求发送完成");
                    onFixedTimeCheck.setSelected(false);
                    fixedTimeLabel.setText("--:--:--");
                    fixedTimeCountField.setEnabled(true);
                    fixedTimeDelayField.setEnabled(true);

                    scheduler.shutdown();
                }, delayInSeconds, TimeUnit.SECONDS);

                LOG.info("Scheduled time-based request at {} (in {} seconds)", timeStr, delayInSeconds);
            } else {
                JOptionPane.showMessageDialog(jFrame, "时间格式错误，请输入 HH:mm:ss 格式的时间", "错误", JOptionPane.ERROR_MESSAGE);
                onFixedTimeCheck.setSelected(false);
                fixedTimeLabel.setText("--:--:--");
                fixedTimeCountField.setEnabled(true);
                fixedTimeDelayField.setEnabled(true);
            }
        } else {
            fixedTimeCountField.setEnabled(true);
            fixedTimeDelayField.setEnabled(true);
            fixedTimeLabel.setText("--:--:--");

            final List<Runnable> r = scheduler.shutdownNow();
            LOG.info("Terminated time schedule, stopped {} tasks", r.size());
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
        rootPane.setLayout(new GridLayoutManager(2, 2, new Insets(10, 10, 10, 10), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(3, 1, new Insets(5, 5, 5, 5), -1, -1));
        rootPane.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(500, 1000), null, 0, false));
        panel1.setBorder(BorderFactory.createTitledBorder(null, "选课请求", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JScrollPane scrollPane1 = new JScrollPane();
        panel1.add(scrollPane1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        electRequestJList = new JList();
        scrollPane1.setViewportView(electRequestJList);
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel2.add(spacer1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        panel2.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        addRequestBtn = new JButton();
        addRequestBtn.setText("添加");
        panel3.add(addRequestBtn, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        editRequestBtn = new JButton();
        editRequestBtn.setEnabled(false);
        editRequestBtn.setText("编辑");
        panel3.add(editRequestBtn, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        removeRequestBtn = new JButton();
        removeRequestBtn.setEnabled(false);
        removeRequestBtn.setText("删除");
        panel3.add(removeRequestBtn, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), 0, 0));
        panel2.add(panel4, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        sortUpBtn = new JButton();
        sortUpBtn.setEnabled(false);
        sortUpBtn.setText("↑");
        panel4.add(sortUpBtn, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        sortDownBtn = new JButton();
        sortDownBtn.setEnabled(false);
        sortDownBtn.setText("↓");
        panel4.add(sortDownBtn, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        requestInfoPane = new JPanel();
        requestInfoPane.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        requestInfoPane.setVisible(false);
        panel1.add(requestInfoPane, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        requestInfoPane.setBorder(BorderFactory.createTitledBorder(null, "", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        requestInfoPane.add(panel5, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(250, -1), null, 0, false));
        panel5.setBorder(BorderFactory.createTitledBorder(null, "待选课列表", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JScrollPane scrollPane2 = new JScrollPane();
        panel5.add(scrollPane2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        electList = new JList();
        final DefaultListModel defaultListModel1 = new DefaultListModel();
        electList.setModel(defaultListModel1);
        scrollPane2.setViewportView(electList);
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        requestInfoPane.add(panel6, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(250, -1), null, 0, false));
        panel6.setBorder(BorderFactory.createTitledBorder(null, "待退课列表", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JScrollPane scrollPane3 = new JScrollPane();
        panel6.add(scrollPane3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        quitList = new JList();
        scrollPane3.setViewportView(quitList);
        final JPanel panel7 = new JPanel();
        panel7.setLayout(new GridLayoutManager(2, 1, new Insets(5, 5, 5, 5), -1, -1));
        rootPane.add(panel7, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(1000, 1000), null, 0, false));
        panel7.setBorder(BorderFactory.createTitledBorder(null, "日志", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JScrollPane scrollPane4 = new JScrollPane();
        panel7.add(scrollPane4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setText("");
        scrollPane4.setViewportView(logArea);
        final JScrollPane scrollPane5 = new JScrollPane();
        panel7.add(scrollPane5, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(114514, 114514), null, 0, false));
        resultLogArea = new JTextArea();
        resultLogArea.setEditable(false);
        resultLogArea.setText("");
        scrollPane5.setViewportView(resultLogArea);
        final JPanel panel8 = new JPanel();
        panel8.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        rootPane.add(panel8, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel9 = new JPanel();
        panel9.setLayout(new GridLayoutManager(4, 1, new Insets(5, 5, 5, 5), -1, -1));
        panel8.add(panel9, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel9.setBorder(BorderFactory.createTitledBorder(null, "发送选项", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JPanel panel10 = new JPanel();
        panel10.setLayout(new GridLayoutManager(1, 8, new Insets(5, 5, 5, 5), -1, -1));
        panel9.add(panel10, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel10.setBorder(BorderFactory.createTitledBorder(null, "定时", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        onFixedTimeCheck = new JCheckBox();
        onFixedTimeCheck.setText("定时发送");
        panel10.add(onFixedTimeCheck, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        fixedTimeCountField = new JFormattedTextField();
        fixedTimeCountField.setEditable(true);
        fixedTimeCountField.setEnabled(true);
        fixedTimeCountField.setText("1");
        panel10.add(fixedTimeCountField, new GridConstraints(0, 5, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(50, -1), null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("发送次数:");
        panel10.add(label1, new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("发送间隔(ms):");
        panel10.add(label2, new GridConstraints(0, 6, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        fixedTimeDelayField = new JFormattedTextField();
        fixedTimeDelayField.setEditable(true);
        fixedTimeDelayField.setEnabled(true);
        fixedTimeDelayField.setText("500");
        panel10.add(fixedTimeDelayField, new GridConstraints(0, 7, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(100, -1), null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel10.add(spacer2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        fixedTimeLabel = new JLabel();
        fixedTimeLabel.setText("--:--:--");
        panel10.add(fixedTimeLabel, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("设定时间:");
        panel10.add(label3, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel11 = new JPanel();
        panel11.setLayout(new GridLayoutManager(1, 7, new Insets(5, 5, 5, 5), -1, -1));
        panel9.add(panel11, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel11.setBorder(BorderFactory.createTitledBorder(null, "手动", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        manualRequestBtn = new JButton();
        manualRequestBtn.setText("发送！！");
        panel11.add(manualRequestBtn, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        manualCountField = new JFormattedTextField();
        manualCountField.setText("1");
        panel11.add(manualCountField, new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(50, -1), null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("发送次数:");
        panel11.add(label4, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("发送间隔(ms):");
        panel11.add(label5, new GridConstraints(0, 5, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        manualDelayField = new JFormattedTextField();
        manualDelayField.setText("500");
        panel11.add(manualDelayField, new GridConstraints(0, 6, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(100, -1), null, 0, false));
        final Spacer spacer3 = new Spacer();
        panel11.add(spacer3, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        manualStopBtn = new JButton();
        manualStopBtn.setText("停止");
        panel11.add(manualStopBtn, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel12 = new JPanel();
        panel12.setLayout(new GridLayoutManager(1, 7, new Insets(5, 5, 5, 5), -1, -1));
        panel9.add(panel12, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel12.setBorder(BorderFactory.createTitledBorder(null, "空容监测", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        onEmptyCheck = new JCheckBox();
        onEmptyCheck.setText("检测到有容量时自动发送");
        panel12.add(onEmptyCheck, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer4 = new Spacer();
        panel12.add(spacer4, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JLabel label6 = new JLabel();
        label6.setText("检测间隔(min):");
        panel12.add(label6, new GridConstraints(0, 5, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        emptyDelayField = new JFormattedTextField();
        emptyDelayField.setEditable(true);
        emptyDelayField.setEnabled(true);
        emptyDelayField.setText("5");
        panel12.add(emptyDelayField, new GridConstraints(0, 6, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(100, -1), null, 0, false));
        final JLabel label7 = new JLabel();
        label7.setText("距下次检测:");
        panel12.add(label7, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        countdownLabel = new JLabel();
        countdownLabel.setText("--:--:--");
        panel12.add(countdownLabel, new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        checkCapacityBtn = new JButton();
        checkCapacityBtn.setText("立刻检测");
        panel12.add(checkCapacityBtn, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel13 = new JPanel();
        panel13.setLayout(new GridLayoutManager(1, 4, new Insets(0, 0, 0, 0), -1, -1));
        panel9.add(panel13, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label8 = new JLabel();
        label8.setText("请求间发送间隔(ms):");
        panel13.add(label8, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer5 = new Spacer();
        panel13.add(spacer5, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        requestMidDelayField = new JFormattedTextField();
        requestMidDelayField.setText("50");
        panel13.add(requestMidDelayField, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(100, -1), null, 0, false));
        final JLabel label9 = new JLabel();
        label9.setText("注意：短时间发送过多请求可能会被服务器忽略。");
        panel13.add(label9, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel14 = new JPanel();
        panel14.setLayout(new GridLayoutManager(3, 1, new Insets(5, 5, 5, 5), -1, -1));
        panel8.add(panel14, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_VERTICAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel14.setBorder(BorderFactory.createTitledBorder(null, "其他选项", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        awaLable = new JLabel();
        awaLable.setText("awa");
        panel14.add(awaLable, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer6 = new Spacer();
        panel14.add(spacer6, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JPanel panel15 = new JPanel();
        panel15.setLayout(new GridLayoutManager(5, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel14.add(panel15, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel15.setBorder(BorderFactory.createTitledBorder(null, "结果查询", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        resultLookUpDelayField = new JFormattedTextField();
        resultLookUpDelayField.setText("200");
        panel15.add(resultLookUpDelayField, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(100, -1), null, 0, false));
        final JLabel label10 = new JLabel();
        label10.setText("自动查询间隔:");
        panel15.add(label10, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        resultLookUpButton = new JButton();
        resultLookUpButton.setText("立即查询");
        panel15.add(resultLookUpButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel16 = new JPanel();
        panel16.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel15.add(panel16, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label11 = new JLabel();
        label11.setText("自动查询状态:");
        panel16.add(label11, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        resultLookUpStatus = new JLabel();
        resultLookUpStatus.setText("×");
        panel16.add(resultLookUpStatus, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        lookUpDelayUpdateBtn = new JButton();
        lookUpDelayUpdateBtn.setText("更新间隔");
        panel15.add(lookUpDelayUpdateBtn, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return rootPane;
    }

}
