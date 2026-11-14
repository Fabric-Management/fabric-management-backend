package com.fabricmanagement.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the setup page needs to know before it shows a form.
 *
 * @param valid the link is known, not used and not expired
 * @param passwordRequired the page must ask for a password; false when the address already has a
 *     login, in which case the existing password is kept and the person signs in normally
 */
@Schema(
    description = "Whether a setup link can be used, and whether the page must ask for a password")
public record SetupLinkStatus(
    @Schema(
            description = "The link is known, not used and not expired",
            requiredMode = Schema.RequiredMode.REQUIRED)
        boolean valid,
    @Schema(
            description =
                "Ask for a password. False when the address already has a login (and when the"
                    + " link is not valid)",
            requiredMode = Schema.RequiredMode.REQUIRED)
        boolean passwordRequired) {

  public static SetupLinkStatus invalid() {
    return new SetupLinkStatus(false, false);
  }
}
