package xyz.zcraft.forms;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.elect.*;
import xyz.zcraft.util.AsyncHelper;
import xyz.zcraft.util.JTextAreaLoggerFactory;
import xyz.zcraft.util.NetworkHelper;

import javax.swing.*;
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
        }).thenAccept(_ ->refreshRequestDetail()).exceptionally(e -> {
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
}
