package xyz.zcraft.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

import javax.swing.*;
import java.util.UUID;

public class JTextAreaLoggerFactory {
    public static Logger create(JTextArea textArea) {
        String uniqueLoggerName = "JTextAreaLogger-" + UUID.randomUUID();

        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration config = context.getConfiguration();

        PatternLayout layout = PatternLayout.newBuilder()
                .withPattern("%d{HH:mm:ss.SS} [%level{length=1}] - %msg%n")
                .build();
        JTextAreaAppender appender = new JTextAreaAppender(
                "Appender-" + uniqueLoggerName,
                null, layout, false, textArea
        );
        appender.start();
        config.addAppender(appender);

        LoggerConfig loggerConfig = new LoggerConfig(uniqueLoggerName, Level.ALL, false);
        loggerConfig.addAppender(appender, Level.ALL, null);

        config.addLogger(uniqueLoggerName, loggerConfig);
        context.updateLoggers();

        return LogManager.getLogger(uniqueLoggerName);
    }

    private static class JTextAreaAppender extends AbstractAppender {
        private final JTextArea textArea;

        public JTextAreaAppender(String name, Filter filter, Layout<?> layout, boolean ignoreExceptions, JTextArea textArea) {
            super(name, filter, layout, ignoreExceptions, Property.EMPTY_ARRAY);
            this.textArea = textArea;
        }

        @Override
        public void append(LogEvent event) {
            final String message = new String(getLayout().toByteArray(event));

            SwingUtilities.invokeLater(() -> {
                if (textArea != null) {
                    textArea.append(message);
                    textArea.setCaretPosition(textArea.getDocument().getLength());
                }
            });
        }
    }
}
