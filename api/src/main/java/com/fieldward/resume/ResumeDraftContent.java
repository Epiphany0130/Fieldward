package com.fieldward.resume;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

record ResumeDraftContent(
        @Valid Profile profile,
        @NotNull @Valid Education education,
        @NotNull @Size(max = 30) List<@NotNull @Valid Experience> experiences,
        @NotNull @Size(max = 30) List<@NotNull @Valid Skill> skills
) {
    ResumeDraftContent {
        // Drafts saved before profile fields were introduced have no profile key.
        if (profile == null) {
            profile = new Profile("", "", "");
        }
    }

    static ResumeDraftContent empty() {
        return new ResumeDraftContent(null, new Education("", "", "", ""), List.of(), List.of());
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
            @NotNull @Size(max = 1000) String context,
            @NotNull @Size(max = 2000) String contribution
    ) {}

    record Skill(@NotNull @Size(max = 80) String name, @NotNull Proficiency proficiency) {}

    enum ExperienceType {
        COURSEWORK, PROJECT, INTERNSHIP, ACTIVITY
    }

    enum Proficiency {
        EXPOSURE, GUIDED, INDEPENDENT
    }
}
