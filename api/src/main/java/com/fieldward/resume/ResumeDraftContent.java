package com.fieldward.resume;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

record ResumeDraftContent(
        @Valid Profile profile,
        @NotNull @Valid Education education,
        @NotNull @Size(max = 30) List<@NotNull @Valid Experience> experiences,
        @NotNull @Size(max = 30) List<@NotNull @Valid Skill> skills,
        @Valid Layout layout
) {
    ResumeDraftContent {
        // Drafts saved before profile fields were introduced have no profile key.
        if (profile == null) {
            profile = new Profile("", "", "");
        }
        if (layout == null) {
            layout = new Layout(Theme.RULED, Density.STANDARD, List.of(Section.COURSEWORK, Section.SKILLS));
        }
    }

    static ResumeDraftContent empty() {
        return new ResumeDraftContent(null, new Education("", "", "", ""), List.of(), List.of(), null);
    }

    record Profile(
            @NotNull @Size(max = 80) String name,
            @NotNull @Size(max = 254) String email,
            @NotNull @Size(max = 40) String phone
    ) {}

    record Education(
            @NotNull @Size(max = 120) String school,
            @NotNull @Size(max = 120) String major,
            @NotNull @Size(max = 30) String degree,
            @NotNull @Size(max = 30) String graduation
    ) {}

    record Experience(
            @NotNull ExperienceType type,
            @NotNull @Size(max = 120) String title,
            @Size(max = 80) String role,
            @Size(max = 60) String period,
            @NotNull @Size(max = 1000) String context,
            @NotNull @Size(max = 2000) String contribution
    ) {
        Experience {
            role = role == null ? "" : role;
            period = period == null ? "" : period;
        }
    }

    record Skill(@NotNull @Size(max = 80) String name, @NotNull Proficiency proficiency) {}

    record Layout(
            @NotNull Theme theme,
            @NotNull Density density,
            @NotNull @Size(max = 5) List<@NotNull Section> sections
    ) {}

    enum Theme { RULED, EDITORIAL }

    enum Density { COMPACT, STANDARD, RELAXED }

    enum Section { COURSEWORK, PROJECT, INTERNSHIP, ACTIVITY, SKILLS }

    enum ExperienceType {
        COURSEWORK, PROJECT, INTERNSHIP, ACTIVITY
    }

    enum Proficiency {
        EXPOSURE, GUIDED, INDEPENDENT
    }
}
