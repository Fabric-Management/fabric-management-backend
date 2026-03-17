package com.fabricmanagement.common.platform.user.app;

import com.fabricmanagement.common.platform.communication.domain.ContactType;
import com.fabricmanagement.common.platform.user.infra.repository.UserContactRepository;
import com.fabricmanagement.notification.hub.app.NotificationUserQueryService;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NotificationUserQueryService implementasyonu.
 *
 * <p>User modülü içinde tanımlanır — notification modülü user'a doğrudan bağımlı olmaz.
 * Anti-Corruption Layer: notification.hub.app.NotificationUserQueryService interface'ini implemente
 * eder.
 *
 * <p><b>Email çözünürlük stratejisi:</b>
 *
 * <ol>
 *   <li>Preferred contact EMAIL tipinde mi? → onu kullan
 *   <li>Değilse → tüm user contact'larını tara, ilk EMAIL'i al
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationUserQueryServiceImpl implements NotificationUserQueryService {

  private final UserContactRepository userContactRepository;

  @Transactional(readOnly = true)
  @Override
  public Optional<String> findEmailByUserId(UUID userId) {
    // 1. Preferred contact EMAIL mi?
    var preferred = userContactRepository.findPreferredContactByUserId(userId);
    if (preferred.isPresent()
        && preferred.get().getContact() != null
        && ContactType.EMAIL.equals(preferred.get().getContact().getContactType())) {
      return Optional.of(preferred.get().getContact().getContactValue());
    }

    // 2. Preferred EMAIL değilse → tüm contact'lardan ilk EMAIL'i bul
    return userContactRepository.findAllByUserId(userId).stream()
        .filter(uc -> uc.getContact() != null)
        .filter(uc -> ContactType.EMAIL.equals(uc.getContact().getContactType()))
        .findFirst()
        .map(uc -> uc.getContact().getContactValue());
  }

  @Transactional(readOnly = true)
  @Override
  public Optional<String> findPushTokenByUserId(UUID userId) {
    // Push token altyapısı henüz yok (FCM entegrasyonu Faz 7 devamında)
    log.debug("Push token lookup not yet implemented for userId={}", userId);
    return Optional.empty();
  }
}
