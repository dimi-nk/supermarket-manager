package com.supermarketmanager.pantry;

import com.supermarketmanager.core.NotFoundException;
import com.supermarketmanager.core.text.Text;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Service
public class ProfileService {
    private final ProfileRepository profiles;
    private final CatalogRepository catalog;
    private final StockRepository stock;
    private final Clock clock;

    public ProfileService(ProfileRepository profiles, CatalogRepository catalog, StockRepository stock, Optional<Clock> clock) {
        this.profiles = profiles;
        this.catalog = catalog;
        this.stock = stock;
        this.clock = clock.orElse(Clock.systemDefaultZone());
    }

    public List<Profile> profiles() {
        return profiles.profiles();
    }

    public Profile profile(String id) {
        return profiles.profile(id).orElseThrow(() -> new NotFoundException("No pantry profile '" + id + "'"));
    }

    /** The active profile, or the only one if there is exactly one. */
    public Profile activeProfile() {
        var all = profiles.profiles();
        return all.stream().filter(Profile::active).findFirst()
                .or(() -> all.size() == 1 ? Optional.of(all.getFirst()) : Optional.empty())
                .orElseThrow(() -> new NotFoundException("No active pantry profile - create one or activate one"));
    }

    @Transactional
    public Profile saveProfile(Profile input) {
        if (input.name() == null || input.name().isBlank()) throw new IllegalArgumentException("Profile name is required");
        String id = input.id() == null || input.id().isBlank() ? Text.slugify(input.name()) : input.id();
        boolean isNew = profiles.profile(id).isEmpty();
        var p = new Profile(id, input.name().trim(), CatalogService.blankToNull(input.description()),
                isNew && profiles.profiles().isEmpty(), Instant.now(clock).toString());
        profiles.upsertProfile(p);
        if (input.active()) profiles.activate(id);
        return profile(id);
    }

    @Transactional
    public void activate(String id) {
        profile(id);
        profiles.activate(id);
    }

    @Transactional
    public void deleteProfile(String id) {
        if (profiles.deleteProfile(id) == 0) throw new NotFoundException("No pantry profile '" + id + "'");
    }

    public List<TargetVersion> versions(String profileId) {
        profile(profileId);
        return profiles.versions(profileId);
    }

    /** Version in force at {@code date}: the latest one whose effective date is not after it. */
    public Optional<TargetVersion> versionAt(String profileId, LocalDate date) {
        return versions(profileId).stream()
                .filter(v -> !LocalDate.parse(v.effectiveFrom()).isAfter(date))
                .max(Comparator.comparing(TargetVersion::effectiveFrom));
    }

    /**
     * Creates a version. Without explicit targets it copies the version in force on {@code effectiveFrom},
     * so "change my diet from next month" starts from the current targets.
     */
    @Transactional
    public TargetVersion createVersion(String profileId, String effectiveFrom, String note, List<Target> targets) {
        profile(profileId);
        LocalDate from = effectiveFrom == null || effectiveFrom.isBlank() ? LocalDate.now(clock) : LocalDate.parse(effectiveFrom);
        if (profiles.versions(profileId).stream().anyMatch(v -> v.effectiveFrom().equals(from.toString()))) {
            throw new IllegalArgumentException("Profile '" + profileId + "' already has a version effective " + from);
        }
        List<Target> initial = targets != null ? targets
                : versionAt(profileId, from).map(TargetVersion::targets).orElse(List.of());
        long id = profiles.insertVersion(profileId, from.toString(), CatalogService.blankToNull(note), Instant.now(clock).toString());
        profiles.replaceTargets(id, validate(initial));
        return profiles.version(id).orElseThrow();
    }

    public TargetVersion version(long id) {
        return profiles.version(id).orElseThrow(() -> new NotFoundException("No target version " + id));
    }

    @Transactional
    public TargetVersion updateVersion(long id, String effectiveFrom, String note, List<Target> targets) {
        TargetVersion v = version(id);
        String from = effectiveFrom == null || effectiveFrom.isBlank() ? v.effectiveFrom() : LocalDate.parse(effectiveFrom).toString();
        profiles.updateVersion(id, from, note == null ? v.note() : CatalogService.blankToNull(note));
        if (targets != null) profiles.replaceTargets(id, validate(targets));
        return version(id);
    }

    /** Adds or replaces a single target in a version (amount 0 removes it). */
    @Transactional
    public TargetVersion setTarget(long versionId, Target target) {
        TargetVersion v = version(versionId);
        var list = new ArrayList<>(v.targets().stream().filter(t -> !t.subject().equals(target.subject())).toList());
        Target t = target.normalised();
        if (t.amount() > 0) list.add(t);
        profiles.replaceTargets(versionId, validate(list));
        return version(versionId);
    }

    @Transactional
    public void deleteVersion(long id) {
        if (profiles.deleteVersion(id) == 0) throw new NotFoundException("No target version " + id);
    }

    public DeltaReport delta(String profileId, LocalDate at) {
        Profile p = profileId == null || profileId.isBlank() ? activeProfile() : profile(profileId);
        LocalDate date = at == null ? LocalDate.now(clock) : at;
        TargetVersion v = versionAt(p.id(), date).orElseThrow(() ->
                new NotFoundException("Profile '" + p.id() + "' has no targets in force on " + date));
        var calc = new DeltaCalculator(catalog.categories(), catalog.products());
        var result = calc.compute(v.targets(), stock.amounts(), "Missing for " + p.name() + " on " + date);
        return new DeltaReport(p.id(), v.id(), v.effectiveFrom(), date.toString(), result.lines(), result.basket());
    }

    private List<Target> validate(List<Target> targets) {
        var calc = new DeltaCalculator(catalog.categories(), catalog.products());
        var seen = new HashSet<Subject>();
        var out = new ArrayList<Target>();
        for (Target raw : targets) {
            Target t = raw.normalised();
            if (t.subject() == null) throw new IllegalArgumentException("Every target needs a subject");
            if (!calc.exists(t.subject())) throw new NotFoundException("Unknown target subject '" + t.subject() + "'");
            if (t.amount() < 0) throw new IllegalArgumentException("Target amount must not be negative");
            if (!seen.add(t.subject())) throw new IllegalArgumentException("Duplicate target for '" + t.subject() + "'");
            out.add(t);
        }
        return out;
    }
}
