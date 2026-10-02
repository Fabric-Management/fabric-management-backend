package com.fabricmanagement.sales.orderintake.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "IntakeAttachmentKind", enumAsRef = true)
public enum IntakeAttachmentKind {
  SAMPLE_PHOTO,
  SPECIFICATION,
  CUSTOMER_REPLY,
  OTHER
}
