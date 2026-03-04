package xyz.zcraft.forms;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.User;
import xyz.zcraft.elect.ElectRequest;
import xyz.zcraft.elect.Round;
import xyz.zcraft.util.AsyncHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class ElectRequester {
    private static final Logger LOG = LogManager.getLogger(ElectRequester.class);
    private final User user;
    private final Round round;
    private final DefaultListModel<ElectRequest> requests = new DefaultListModel<>();
    private JCheckBox onFixedTimeCheck;
    private JSpinner fixedTimeCountSpin;
    private JSpinner fixedTimeDelaySpin;
    private JButton manualRequestBtn;
    private JTextArea logArea;
    private JSpinner emptyDelaySpin;
    private JButton editRequestBtn;
    private JCheckBox deleteOnSuccessCheck;
    private JCheckBox onEmptyCheck;
    private JLabel countdownLabel;
    private JLabel fixedTimeLabel;
    private JSpinner manualCountSpin;
    private JLabel manualDelaySpin;
    private JList<ElectRequest> electRequestJList;
    private JButton addRequestBtn;
    private JButton removeRequestBtn;
    private JPanel rootPane;
    private JPanel requestEditPane;
    private JFrame jFrame;

    public ElectRequester(User user, Round round) {
        this.user = user;
        this.round = round;

        electRequestJList.setModel(requests);

        editRequestBtn.addActionListener(this::openEditRequest);
        addRequestBtn.addActionListener(this::openAddRequest);
        removeRequestBtn.addActionListener(this::removeRequest);

        setupFrame();
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

    public void show() {
        jFrame.setVisible(true);
    }

    private void setupFrame() {
        jFrame = new JFrame("选课 - " + user.getUid() + " - " + round.getRoundData().calendarName());
        jFrame.setContentPane(rootPane);
        jFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jFrame.setSize(new Dimension(800, 600));
        jFrame.setLocationRelativeTo(null);
    }
}
