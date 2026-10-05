package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.sales.common.exception.OrderDomainException;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A party that is not registered in the system, as entered on the order: organisation name and how
 * to reach it. It stays on the order as entered; registering the party later does not rewrite it.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartySnapshot {

  public static final int MAX_NAME = 200;
  public static final int MAX_CONTACT_NAME = 120;
  public static final int MAX_EMAIL = 254;
  public static final int MAX_PHONE = 30;

  private String name;
  private String contactName;
  private String email;
  private String phone;

  public static PartySnapshot of(String name, String contactName, String email, String phone) {
    String party = Text.trimmed(name);
    if (party == null) {
      throw new OrderDomainException("Name the party");
    }
    PartySnapshot value = new PartySnapshot();
    value.name = Text.limited(party, MAX_NAME, "The party name is too long");
    value.contactName =
        Text.limited(Text.trimmed(contactName), MAX_CONTACT_NAME, "The contact name is too long");
    String mail = Text.limited(Text.trimmed(email), MAX_EMAIL, "The e-mail address is too long");
    if (mail != null && (mail.indexOf('@') < 1 || mail.indexOf('@') == mail.length() - 1)) {
      throw new OrderDomainException("The e-mail address is not valid");
    }
    value.email = mail;
    value.phone = Text.limited(Text.trimmed(phone), MAX_PHONE, "The phone number is too long");
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof PartySnapshot that
        && Objects.equals(name, that.name)
        && Objects.equals(contactName, that.contactName)
        && Objects.equals(email, that.email)
        && Objects.equals(phone, that.phone);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, contactName, email, phone);
  }
}
