package xyz.zcraft;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.elect.User;
import xyz.zcraft.forms.ElectRequester;
import xyz.zcraft.forms.Login;

import java.io.InputStream;
import java.util.Properties;


public class Main {
    private static final Logger LOG = LogManager.getLogger(Main.class);

    static void main() {
        com.formdev.flatlaf.FlatDarculaLaf.setup();

        LOG.info("Starting application");
        final var session = new Login().requestLogin();

        final User user = session.getKey();
        final var round = session.getValue();
        LOG.info("Login successful: user.uid={}, round.id={}", user.getUid(), round.getRoundData().id());

        ElectRequester electRequester = new ElectRequester(user, round);
        electRequester.show();
    }

    public static String getAppProperty(String key) {
        String propStr = null;
        try (InputStream input = Main.class.getResourceAsStream("/application.properties")) {
            if (input != null) {
                Properties prop = new Properties();
                prop.load(input);
                propStr = prop.getProperty(key, "Unknown Version");
            }
        } catch (Exception ex) {
            System.err.println("Could not load version properties.");
        }
        return propStr;
    }

    public static String getVersion() {
        return getAppProperty("app.version");
    }

    public static String getUrl() {
        return getAppProperty("app.url");
    }
}
