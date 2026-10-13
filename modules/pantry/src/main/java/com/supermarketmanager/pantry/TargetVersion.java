package com.supermarketmanager.pantry;

import java.util.List;

/** The targets of a profile from {@code effectiveFrom} (ISO date) until the next version. */
public record TargetVersion(long id, String profileId, String effectiveFrom, String note, String createdAt, List<Target> targets) {
}
