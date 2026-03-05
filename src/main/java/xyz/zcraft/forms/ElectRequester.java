package xyz.zcraft.forms;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.User;
import xyz.zcraft.elect.ElectRequest;
import xyz.zcraft.elect.ElectResult;
import xyz.zcraft.elect.Round;
import xyz.zcraft.elect.TeachClass;
import xyz.zcraft.util.AsyncHelper;
import xyz.zcraft.util.JTextAreaAppender;
import xyz.zcraft.util.NetworkHelper;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class ElectRequester {
    private static final Logger LOG = LogManager.getLogger(ElectRequester.class);
    private static final Logger ELECT_LOG = LogManager.getLogger("ELECT_LOG");
    final boolean[] isAtBottom = {true};
    private final User user;
    private final Round round;
    private final DefaultListModel<ElectRequest> requests = new DefaultListModel<>();
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
    private JPanel requestEditPane;
    private JButton textLogBtn;
    private JFormattedTextField manualDelayField;
    private JScrollPane logScroll;
    private JButton manualStopBtn;
    private JFrame jFrame;
    private boolean resultReady = false;
    private final Timer resultTimer = new Timer(100, this::updateCountdown);
    private volatile boolean manualStarted = false;

    public ElectRequester(User user, Round round) {
        this.user = user;
        this.round = round;

        setupComponents();
        setupListeners();
        setupFormatters();
        setupFrame();
        setupLogger();
    }

    private void setupComponents() {
        electRequestJList.setModel(requests);

        final JScrollBar verticalBar = logScroll.getVerticalScrollBar();
        verticalBar.addAdjustmentListener(e -> {
            isAtBottom[0] = (verticalBar.getValue() + verticalBar.getVisibleAmount()) == verticalBar.getMaximum();
        });
    }

    private void setupListeners() {
        editRequestBtn.addActionListener(this::openEditRequest);
        addRequestBtn.addActionListener(this::openAddRequest);
        removeRequestBtn.addActionListener(this::removeRequest);
        textLogBtn.addActionListener(_ -> ELECT_LOG.info("Test log message"));
        manualRequestBtn.addActionListener(this::sendManualRequest);
        manualStopBtn.addActionListener(_ -> manualStarted = false);
        logArea.getDocument().addDocumentListener(new DocumentListener() {
            private void update() {
                if (isAtBottom[0]) logArea.setCaretPosition(logArea.getDocument().getLength());
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                update();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                update();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                update();
            }
        });
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
    }

    private void handleResult(ElectResult electResult) {
        if (electResult.status().equals("Loading")) {
            resultReady = false;
            ELECT_LOG.info("服务器处理选课中...");
        } else if (electResult.status().equals("Ready")) {
            final List<TeachClass> classes = new LinkedList<>();
            requests.elements().asIterator().forEachRemaining(r -> {
                classes.addAll(r.getElectClasses());
                classes.addAll(r.getWithdrawClasses());
            });
            resultReady = true;
            for (int i = 0; i < electResult.successCourses().size(); i++) {
                long courseId = electResult.successCourses().getLongValue(i);
                classes.stream()
                        .filter(c -> Objects.equals(c.teachClassId(), courseId))
                        .findFirst()
                        .ifPresent(c -> {
                            ELECT_LOG.info("选课成功: {} - {}",
                                    c.newTeachClassCode(), c.courseName()
                            );
                        });
            }
            electResult.failedReasons().forEach((key, value) ->
                    ELECT_LOG.info("选课失败: {} - {}", key, value)
            );
        }
    }

    private void updateCountdown(ActionEvent actionEvent) {
        if (resultReady) return;

        final ElectResult electResult = NetworkHelper.getElectResult(user, round);
        handleResult(electResult);
    }

    private void sendRequest(ElectRequest request) {
        AsyncHelper.supplyAsync(() -> NetworkHelper.sendElectRequest(user, request))
                .thenAccept(this::handleResult);
        resultReady = false;
        if (resultTimer.isRunning()) {
            resultTimer.restart();
        } else {
            resultTimer.start();
        }
    }

    private void sendManualRequest(ActionEvent e) {
        manualStarted = true;
        AsyncHelper.supplyAsync(() -> {
            int count = Integer.parseInt(manualCountField.getText());
            long delay = Long.parseLong(manualDelayField.getText());
            for (int i = 0; i < count; i++) {
                if (!manualStarted) return null;
                requests.elements().asIterator().forEachRemaining(request -> {
                    sendRequest(request);
                    ELECT_LOG.info("已发送选课请求: {}", request);
                });
                try {
                    Thread.sleep(Duration.ofMillis(delay));
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
            return null;
        });
    }

    private void setupLogger() {
        JTextAreaAppender.setTextArea(logArea);
    }

    private void openAddRequest(ActionEvent actionEvent) {
        requestEditPane.setEnabled(false);
        AsyncHelper.supplyAsync(() -> {
            CourseElect courseElect = new CourseElect(user, round);
            return courseElect.openEdit(null);
        }).thenAccept(e -> {
            if (e != null) {
                requests.addElement(e);
            }
            requestEditPane.setEnabled(true);
        }).exceptionally(e -> {
            LOG.error("Exception threw when adding request", e);
            requestEditPane.setEnabled(true);
            return null;
        });
    }

    private void openEditRequest(ActionEvent actionEvent) {
        requestEditPane.setEnabled(false);
        AsyncHelper.supplyAsync(() -> {
            CourseElect courseElect = new CourseElect(user, round);
            final ElectRequest electRequest = courseElect.openEdit(electRequestJList.getSelectedValue());
            requestEditPane.setEnabled(true);
            return electRequest;
        }).exceptionally(e -> {
            LOG.error("Exception threw when editing request", e);
            requestEditPane.setEnabled(true);
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
}
