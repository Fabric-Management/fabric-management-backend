package com.fabricmanagement.sales.quote.mapper;

import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalToken;
import com.fabricmanagement.sales.quote.dto.QuoteApprovalTokenDto;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface QuoteMapper {

  QuoteApprovalTokenDto toDto(QuoteApprovalToken entity);
}
