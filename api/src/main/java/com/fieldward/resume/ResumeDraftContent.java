package com.fieldward.resume;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

record ResumeDraftContent(
        @NotNull @Valid Education education,
        @NotNull @Size(max = 30) List<@NotNull @Valid Experience> experiences,
        @NotNull @Size(max = 30) List<@NotNull @Valid Skill> skills
) {
    static ResumeDraftContent empty() {
        return new ResumeDraftContent(new Education("", "", "", ""), List.of(), List.of());
    }

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
