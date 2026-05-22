package com.fabricmanagement.common.infrastructure.mapping;

import com.fabricmanagement.common.dto.MoneyDto;
import com.fabricmanagement.common.util.Money;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface MoneyMapper {

  default MoneyDto toDto(Money money) {
    if (money == null) {
      return null;
    }
    return new MoneyDto(money.getAmount(), money.getCurrency().getCurrencyCode());
  }

  default Money toEntity(MoneyDto dto) {
    if (dto == null) {
      return null;
    }
    return Money.of(dto.amount(), dto.currency());
  }
}
