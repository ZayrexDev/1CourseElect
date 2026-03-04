package xyz.zcraft;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.forms.Login;


public class Main {
    private static final Logger LOG = LogManager.getLogger(Main.class);
    static void main() {
        com.formdev.flatlaf.FlatDarculaLaf.setup();

        LOG.info("Starting application");
        final var session = new Login().requestLogin();

        final User user = session.getKey();
        final var roundId = session.getValue();
        LOG.info("Login successful: user.uid={}, courses.size={}", user.getUid(), roundId.size());

    }
}
