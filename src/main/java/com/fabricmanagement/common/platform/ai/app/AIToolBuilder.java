package com.fabricmanagement.common.platform.ai.app;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI Tool Builder - Builds OpenAI function calling tools definition.
 *
 * <p>Defines available functions for FabricAI.</p>
 */
public class AIToolBuilder {

    /**
     * Get available tools for OpenAI function calling.
     */
    public static List<Map<String, Object>> getAvailableTools() {
        return List.of(
            buildTool("check_material_stock", 
                "Check stock quantity for a material by name. Use this when user asks about stock, inventory, or availability.",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "materialName", Map.of(
                            "type", "string",
                            "description", "Material name or UID to check stock for. " +
                                "Preserve technical textile specifications EXACTLY as user typed (e.g., '30/1 gabardin', 'gabardin 30/1', '40/2 keten', '190 GSM cotton'). " +
                                "DO NOT translate or reformat numeric/symbolic codes like 30/1, 40/2, Ne 20/1, 16x12, 190 GSM, %100."
                        )
                    ),
                    "required", List.of("materialName")
                )
            ),
            buildTool("search_materials",
                "Search for materials in the system. Use this when user asks about available materials or wants to find a material.",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "query", Map.of(
                            "type", "string",
                            "description", "Search query to find materials. " +
                                "Preserve technical textile specifications EXACTLY as user typed (e.g., '30/1 gabardin', '40/2 keten'). " +
                                "DO NOT translate numeric/symbolic codes."
                        )
                    ),
                    "required", List.of("query")
                )
            ),
            buildTool("get_production_status",
                "Get current production status and active orders. Use this when user asks about production status, orders, or manufacturing.",
                Map.of(
                    "type", "object",
                    "properties", Map.of(),
                    "required", List.of()
                )
            )
        );
    }

    private static Map<String, Object> buildTool(String name, String description, Map<String, Object> parameters) {
        Map<String, Object> tool = new HashMap<>();
        tool.put("type", "function");
        
        Map<String, Object> function = new HashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", parameters);
        
        tool.put("function", function);
        return tool;
    }
}

