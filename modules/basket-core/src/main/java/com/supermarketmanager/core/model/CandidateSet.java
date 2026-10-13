package com.supermarketmanager.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Output of a candidate source and input/output of item rules; input of a resolver.
 * The unit of chaining between modules.
 */
public record CandidateSet(String source, List<LineCandidates> lines, List<String> warnings) {

    public CandidateSet {
        lines = lines == null ? List.of() : List.copyOf(lines);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public CandidateSet withLines(List<LineCandidates> newLines, List<String> extraWarnings) {
        var w = new ArrayList<>(warnings);
        if (extraWarnings != null) w.addAll(extraWarnings);
        return new CandidateSet(source, newLines, w);
    }
}
