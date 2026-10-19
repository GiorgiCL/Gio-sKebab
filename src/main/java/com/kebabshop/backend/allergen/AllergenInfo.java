package com.kebabshop.backend.allergen;

import java.util.Set;

/** Explicit verification: absence of a selection never implies absence of allergens. */
public record AllergenInfo(AllergenStatus status, Set<Allergen> allergens) {
    public AllergenInfo {
        if (status == null || allergens == null || allergens.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Allergen status and selection are required");
        }
        allergens = Set.copyOf(allergens);
        if ((status == AllergenStatus.VERIFIED_WITH_ALLERGENS) == allergens.isEmpty()) {
            throw new IllegalArgumentException("Allergen selection must match its verification state");
        }
    }
}
