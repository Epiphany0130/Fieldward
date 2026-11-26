package com.fieldward.mentor;

import java.util.List;
import java.util.Objects;

/** Provider-independent question selection; persistence and transport are separate concerns. */
class DiagnosisService {
    private final Provider provider;

    DiagnosisService(Provider provider) { this.provider = Objects.requireNonNull(provider); }

    State begin(String direction, String resume, List<String> allowed) {
        if (direction == null || direction.isBlank() || resume == null || allowed == null || allowed.isEmpty())
            throw new IllegalArgumentException("invalid diagnosis context");
        var route = List.copyOf(allowed);
        var plan = Objects.requireNonNull(provider.plan(direction, resume, route));
        if (plan.modules() == null || plan.modules().isEmpty()
                || plan.modules().stream().anyMatch(id -> id == null || !route.contains(id))
                || plan.modules().stream().distinct().count() != plan.modules().size()
                || plan.question() == null || plan.question().isBlank())
            throw new IllegalArgumentException("invalid diagnosis plan");
        return new State(List.copyOf(plan.modules()), plan.question(), false);
    }

    State answer(State state, String answer) {
        if (state == null || state.complete() || answer == null || answer.isBlank())
            throw new IllegalArgumentException("invalid diagnosis answer");
        var next = Objects.requireNonNull(provider.followUp(state.modules(), answer));
        if (!next.complete() && (next.question() == null || next.question().isBlank()))
            throw new IllegalArgumentException("missing follow-up question");
        return new State(state.modules(), next.question(), next.complete());
    }

    interface Provider {
        Plan plan(String direction, String resume, List<String> allowed);
        Next followUp(List<String> modules, String answer);
    }
    record Plan(List<String> modules, String question) {}
    record Next(String question, boolean complete) {}
    record State(List<String> modules, String question, boolean complete) {}
}
