package com.fabricmanagement.testsupport;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.LoggerFactory;

/** Captures DEBUG/TRACE too, including throwable causes and suppressed exception text. */
public final class SensitiveLogCapture implements AutoCloseable {
  private final Logger parent = (Logger) LoggerFactory.getLogger("com.fabricmanagement");
  private final Map<Logger, Level> previous = new LinkedHashMap<>();
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  public SensitiveLogCapture(Class<?>... sources) {
    previous.put(parent, parent.getLevel());
    parent.setLevel(Level.TRACE);
    for (Class<?> source : sources) {
      Logger logger = (Logger) LoggerFactory.getLogger(source);
      previous.putIfAbsent(logger, logger.getLevel());
      logger.setLevel(Level.TRACE);
    }
    appender.start();
    parent.addAppender(appender);
  }

  public String text() {
    return appender.list.stream()
        .map(
            event ->
                event.getFormattedMessage()
                    + "\n"
                    + (event.getThrowableProxy() == null
                        ? ""
                        : ThrowableProxyUtil.asString(event.getThrowableProxy())))
        .collect(Collectors.joining("\n"));
  }

  @Override
  public void close() {
    parent.detachAppender(appender);
    appender.stop();
    previous.forEach(Logger::setLevel);
  }
}
