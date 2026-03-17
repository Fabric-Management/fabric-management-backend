package com.fabricmanagement.sales.api;

import com.fabricmanagement.sales.app.ProductCatalogService;
import com.fabricmanagement.sales.domain.catalog.ProductCatalog;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sales/product-catalog")
@RequiredArgsConstructor
public class ProductCatalogController {

  private final ProductCatalogService catalogService;

  @GetMapping
  public List<ProductCatalog> listCatalog(
      @RequestParam(value = "moduleType", required = false) String moduleType) {
    return catalogService.getActiveCatalogForModule(moduleType);
  }

  @GetMapping("/material/{materialId}")
  public ProductCatalog getByMaterial(@PathVariable UUID materialId) {
    return catalogService.getActiveByMaterialId(materialId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ProductCatalog createEntry(@RequestBody ProductCatalog entry) {
    return catalogService.createEntry(entry);
  }

  @DeleteMapping("/{id}")
  public void deactivateEntry(@PathVariable UUID id) {
    catalogService.deactivateEntry(id);
  }
}
