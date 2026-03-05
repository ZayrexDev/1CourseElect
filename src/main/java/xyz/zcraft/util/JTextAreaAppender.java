package xyz.zcraft.util;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import javax.swing.*;
import java.io.Serializable;

@Plugin(name = "JTextAreaAppender", category = "Core", elementType = "appender", printObject = true)
public class JTextAreaAppender extends AbstractAppender {
    private static JTextArea textArea;

    protected JTextAreaAppender(String name, Filter filter, Layout<? extends Serializable> layout) {
        super(name, filter, layout);
    }

    @PluginFactory
    public static JTextAreaAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Layout") Layout<? extends Serializable> layout) {
        return new JTextAreaAppender(name, null, layout);
    }

    public static void setTextArea(JTextArea textArea) {
        JTextAreaAppender.textArea = textArea;
    }

    @Override
    public void append(LogEvent event) {
        final String message = new String(getLayout().toByteArray(event));
        SwingUtilities.invokeLater(() -> textArea.append(message));
    }
}
