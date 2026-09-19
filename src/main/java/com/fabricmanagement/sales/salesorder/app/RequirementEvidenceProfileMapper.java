package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverEvidencePort.CertificateRequirement;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverEvidencePort.Facet;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverEvidencePort.FacetKind;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverEvidencePort.FacetRule;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverEvidencePort.FacetState;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverEvidencePort.Profile;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementFacet;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementFacetValue;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileSnapshot;
import com.fabricmanagement.sales.salesorder.domain.requirement.UnmodelledSpecConstraint;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Maps the rich sales profile into the narrow consumer-owned production evidence contract. */
public final class RequirementEvidenceProfileMapper {

  private RequirementEvidenceProfileMapper() {}

  public static Profile toPort(RequirementProfileSnapshot profile) {
    if (profile == null) {
      return null;
    }
    return new Profile(
        profile.complete(),
        profile.facets().stream().map(RequirementEvidenceProfileMapper::toPort).toList(),
        profile.unmodelledConstraints().stream()
            .filter(
                constraint ->
                    constraint.status() == UnmodelledSpecConstraint.Status.RESOLVED_UNSUPPORTED)
            .map(UnmodelledSpecConstraint::field)
            .toList());
  }

  private static Facet toPort(RequirementFacet facet) {
    UUID colourId =
        facet.value() instanceof RequirementFacetValue.ColourIdentity colour
            ? colour.colorId()
            : null;
    List<CertificateRequirement> certificates =
        facet.value() instanceof RequirementFacetValue.Certification certification
            ? certification.certificates().stream()
                .map(
                    reference ->
                        new CertificateRequirement(reference.scheme(), reference.certificateKind()))
                .toList()
            : List.of();
    Set<String> categories =
        facet.value() instanceof RequirementFacetValue.Categorical categorical
            ? categorical.values()
            : Set.of();
    return new Facet(
        facet.identity(),
        FacetKind.valueOf(facet.kind().name()),
        FacetState.valueOf(facet.state().name()),
        FacetRule.valueOf(facet.comparison().name()),
        colourId,
        certificates,
        categories);
  }
}
