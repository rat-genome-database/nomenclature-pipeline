# nomenclature-pipeline

Proposes updated nomenclature for rat genes based on ortholog gene symbols.

## Overview

Reviews active rat genes that are in a "reviewable" state and checks whether their nomenclature
should be updated based on human or mouse ortholog symbols. Genes matching exclusion patterns
(e.g. LOC*, RGD*, ribosomal genes) are marked as untouchable and skipped.

## Logic

1. **Load reviewable genes** — retrieves active rat genes with a review date in the reviewable range
2. **Prescreening** — marks genes matching configurable exclusion patterns as untouchable
3. **Ortholog lookup** — for each remaining gene, finds active orthologs (human/mouse)
4. **Propose nomenclature** — selects the best ortholog and proposes a new symbol/name
   following rat nomenclature conventions (lowercase italicized symbol)
5. **Flag for review** — genes with new proposed nomenclature have their review date set to today,
   making them available for curator review

## Logging

- `status` — pipeline progress and summary counters

## Build and run

Requires Java 17. Built with Gradle:
```
./gradlew clean assembleDist
```