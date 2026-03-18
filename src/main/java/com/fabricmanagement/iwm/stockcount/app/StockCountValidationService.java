package com.fabricmanagement.iwm.stockcount.app;

import com.fabricmanagement.iwm.common.exception.IwmDomainException;
import com.fabricmanagement.iwm.stockcount.domain.StockCountStatus;
import com.fabricmanagement.iwm.stockcount.infra.repository.StockCountRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockCountValidationService {

  private final StockCountRepository stockCountRepository;

  /**
   * Belirtilen lokasyonda aktif bir sayım varsa hata fırlatır. Mevcut envanter akışlarında mal
   * giriş, çıkış veya transfer işlemi yapılmadan önce lokasyonun kilitli olup olmadığını doğrulamak
   * için kullanılır.
   */
  @Transactional(readOnly = true)
  public void validateLocationNotLockedForCounting(UUID locationId) {
    boolean isLocked =
        !stockCountRepository
            .findByLocationIdAndStatusAndDeletedAtIsNull(locationId, StockCountStatus.IN_PROGRESS)
            .isEmpty();

    if (isLocked) {
      log.warn("Attempt to perform inventory action on locked location: {}", locationId);
      throw new IwmDomainException(
          "Lokasyonda aktif bir fiziksel sayım (StockCount) yürütülmektedir. "
              + "Sayım bitinceye kadar işlemler kilitlidir.");
    }
  }
}
