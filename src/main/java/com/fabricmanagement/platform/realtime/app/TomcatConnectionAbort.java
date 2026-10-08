package com.fabricmanagement.platform.realtime.app;

import jakarta.servlet.ServletResponse;
import jakarta.servlet.ServletResponseWrapper;
import java.io.IOException;
import java.lang.reflect.Field;
import org.apache.catalina.connector.Response;
import org.apache.catalina.connector.ResponseFacade;
import org.apache.coyote.ActionCode;

/**
 * Closes a Tomcat connection without the final flush (CEDIT-05 R1). The Servlet API has no way to
 * end a response without writing what the container still buffers: completing one whose client
 * stopped reading makes Tomcat flush the rest in blocking mode on a connector thread, up to its
 * write timeout. Tomcat's own {@code CLOSE_NOW} action marks the response finished and forbids
 * further I/O, so the request ends and the socket is closed with the unsent bytes dropped.
 *
 * <p>Tomcat hands the application a {@link ResponseFacade} whose connector response is not public;
 * it is read the way Spring's own Tomcat adapter reads it. Any other container, or a facade this
 * code cannot open, answers false and the caller falls back to completing normally.
 */
final class TomcatConnectionAbort {

  private static final Field FACADE_RESPONSE = facadeResponseField();

  private TomcatConnectionAbort() {}

  /** True when the connection of this response was closed now, without a final flush. */
  static boolean closeNow(ServletResponse response, IOException cause) {
    ServletResponse current = response;
    while (current instanceof ServletResponseWrapper wrapper) {
      current = wrapper.getResponse();
    }
    if (FACADE_RESPONSE == null || !(current instanceof ResponseFacade facade)) {
      return false;
    }
    try {
      Response connectorResponse = (Response) FACADE_RESPONSE.get(facade);
      if (connectorResponse == null) {
        return false;
      }
      connectorResponse.getCoyoteResponse().action(ActionCode.CLOSE_NOW, cause);
      return true;
    } catch (IllegalAccessException | RuntimeException unavailable) {
      return false;
    }
  }

  private static Field facadeResponseField() {
    try {
      Field field = ResponseFacade.class.getDeclaredField("response");
      field.setAccessible(true);
      return field;
    } catch (NoSuchFieldException | RuntimeException unavailable) {
      return null;
    }
  }
}
