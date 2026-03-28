package com.fabricmanagement.flowboard.generator.api;

import com.fabricmanagement.flowboard.generator.domain.TaskTemplate;
import com.fabricmanagement.flowboard.generator.dto.TaskTemplateDto;
import com.fabricmanagement.flowboard.generator.dto.TemplateChecklistItemDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class TaskTemplateMapper {

  @Autowired protected ObjectMapper objectMapper;

  @Mapping(
      target = "checkList",
      expression = "java(parseCheckList(template.getChecklistTemplate()))")
  @Mapping(target = "labels", expression = "java(parseLabels(template.getAutoLabels()))")
  public abstract TaskTemplateDto toDto(TaskTemplate template);

  public abstract List<TaskTemplateDto> toDtoList(List<TaskTemplate> templates);

  // Parse methods
  protected List<TemplateChecklistItemDto> parseCheckList(String json) {
    if (json == null || json.isBlank()) return Collections.emptyList();
    try {
      return objectMapper.readValue(json, new TypeReference<List<TemplateChecklistItemDto>>() {});
    } catch (JsonProcessingException e) {
      return Collections.emptyList();
    }
  }

  protected List<String> parseLabels(String json) {
    if (json == null || json.isBlank()) return Collections.emptyList();
    try {
      return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    } catch (JsonProcessingException e) {
      return Collections.emptyList();
    }
  }

  public String toJson(Object obj) {
    if (obj == null) return null;
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      return null;
    }
  }
}
