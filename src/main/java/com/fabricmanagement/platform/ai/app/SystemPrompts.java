package com.fabricmanagement.platform.ai.app;

import com.fabricmanagement.platform.user.dto.UserDto;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * System Prompts for FabricAI.
 *
 * <p>Centralized system prompt management. Contains all system prompts used by FabricAI.
 */
@Slf4j
public class SystemPrompts {

  /**
   * Main FabricAI system prompt.
   *
   * <p>Defines FabricAI's role, responsibilities, and behavior.
   *
   * @param context user context (optional, for personalization)
   * @return system prompt content
   */
  /**
   * ✅ OPTIMIZED: Compact system prompt (reduced from ~11080 to ~3000 chars).
   *
   * <p><b>Token Savings:</b> ~2800 → ~800 tokens (65% reduction)
   *
   * <p><b>Strategy:</b> Keep only essential rules, remove verbose examples
   */
  public static String fabricAIPrompt(UserContext context) {
    StringBuilder prompt = new StringBuilder();

    prompt.append("You are FabricAI, a fabric management assistant.\n");
    prompt.append("Help users with stock, materials, and production via natural language.\n\n");

    prompt.append("## Rules:\n");
    prompt.append("- Use functions for ALL data queries (never guess)\n");
    prompt.append("- Ask confirmation before write actions (create/update/delete)\n");
    prompt.append("- Respond in user's language (Turkish/English)\n");
    prompt.append("- Use metric units (kg, m) unless specified\n\n");

    prompt.append("## Functions:\n");
    prompt.append("- smart_search(query): Auto-detect FIBER/YARN/FABRIC (USE THIS FIRST)\n");
    prompt.append("- search_fibers(query): Search fibers only\n");
    prompt.append("- search_materials(query): Search materials only\n");
    prompt.append("- check_material_stock(materialName): Check inventory\n");
    prompt.append("- create_material/create_fiber: Create entities (ask confirmation)\n\n");

    prompt.append("## Entity Detection:\n");
    prompt.append("- Base name (pamuk, polyester) → FIBER\n");
    prompt.append("- Name + \"iplik\"/yarn → YARN\n");
    prompt.append("- Fabric names (gabardin, poplin) → FABRIC\n\n");

    prompt.append("## Translation:\n");
    prompt.append("- Turkish → English for names (pamuk→cotton, viskoz→viscose)\n");
    prompt.append("- Preserve technical specs AS-IS (30/1, GSM, 16x12)\n\n");

    prompt.append("## Creation:\n");
    prompt.append("- Material: type (FIBER/YARN/FABRIC) + unit (kg/m)\n");
    prompt.append("- Fiber: name + category (Material auto-created)\n");
    prompt.append("- Always ask confirmation before creating\n\n");

    prompt.append("## Confirmations:\n");
    prompt.append("- Accept: yes, confirm, proceed, ok, do it, go ahead\n");
    prompt.append("- Prefer responding in English unless user writes in another language.\n");

    // Add user context if available
    if (context != null && context.getUser() != null) {
      UserDto user = context.getUser();
      prompt.append("\n");
      prompt.append("## User Context:\n");
      prompt.append("\n");
      prompt.append("Current user: ").append(user.getDisplayName()).append("\n");
      if (user.getOrganizationId() != null) {
        prompt.append("Organization ID: ").append(user.getOrganizationId()).append("\n");
      }

      // Add learned preferences if available
      if (context.getPreferences() != null) {
        UserBehaviorLearner.UserPreferences prefs = context.getPreferences();
        if (prefs.getLanguageConfidence() > 0.5) {
          prompt
              .append("User's preferred language: ")
              .append(prefs.getPreferredLanguage())
              .append("\n");
          prompt
              .append("(Based on ")
              .append(prefs.getTrCount() + prefs.getEnCount())
              .append(" previous interactions)\n");
        }
      }
    }

    return prompt.toString();
  }

  /** User context for prompt personalization. */
  public static class UserContext {
    private UserDto user;

    @SuppressWarnings("unused") // Reserved for future use
    private String screen;

    @SuppressWarnings("unused") // Reserved for future use
    private Map<String, Object> filters;

    private UserBehaviorLearner.UserPreferences preferences;

    public static UserContext of(UserDto user) {
      UserContext context = new UserContext();
      context.user = user;
      return context;
    }

    public UserDto getUser() {
      return user;
    }

    public void setScreen(String screen) {
      this.screen = screen;
    }

    public void setFilters(Map<String, Object> filters) {
      this.filters = filters;
    }

    public void setPreferences(UserBehaviorLearner.UserPreferences preferences) {
      this.preferences = preferences;
    }

    public UserBehaviorLearner.UserPreferences getPreferences() {
      return preferences;
    }
  }
}
