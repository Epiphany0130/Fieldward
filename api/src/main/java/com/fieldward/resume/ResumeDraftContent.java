package com.fieldward.resume;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

record ResumeDraftContent(
        @Valid Profile profile,
        @NotNull @Valid Education education,
        @Size(max = 9) List<@NotNull @Valid Education> additionalEducation,
        @NotNull @Size(max = 30) List<@NotNull @Valid Experience> experiences,
        @NotNull @Size(max = 30) List<@NotNull @Valid Skill> skills,
        @Valid Layout layout
) {
    ResumeDraftContent {
        // Older drafts and submitted versions omit the newer profile and layout fields.
        profile = profile == null ? new Profile("", "", "", null, null, "") : profile;
        additionalEducation = additionalEducation == null ? List.of() : additionalEducation;
        layout = layout == null ? new Layout(Theme.RULED, Density.STANDARD,
                List.of(Section.COURSEWORK, Section.SKILLS), null, null, null) : layout;
    }

    static ResumeDraftContent empty() {
        return new ResumeDraftContent(null, new Education("", "", "", "", null), List.of(), List.of(), List.of(), null);
    }

    boolean hasSchool() {
        return !education.school().isBlank()
                || additionalEducation.stream().anyMatch(item -> !item.school().isBlank());
    }

    record Profile(
            @NotNull @Size(max = 80) String name,
            @NotNull @Size(max = 254) String email,
            @NotNull @Size(max = 40) String phone,
            @Valid Career career,
            @Valid Links links,
            @Size(max = 150000) @Pattern(regexp = "(?:data:image/jpeg;base64,[A-Za-z0-9+/]+={0,2})?") String photo
    ) {
        Profile {
            career = career == null ? new Career("", "", "") : career;
            links = links == null ? new Links("", "", "", "") : links;
            photo = valueOrEmpty(photo);
        }
    }

    record Career(
            @NotNull @Size(max = 40) String status,
            @NotNull @Size(max = 80) String position,
            @NotNull @Size(max = 80) String preferredCity
    ) {}

    record Links(
            @NotNull @Size(max = 300) String website,
            @NotNull @Size(max = 300) String github,
            @NotNull @Size(max = 80) String wechat,
            @NotNull @Size(max = 80) String city
    ) {}

    record Education(
            @NotNull @Size(max = 120) String school,
            @NotNull @Size(max = 120) String major,
            @NotNull @Size(max = 30) String degree,
            @NotNull @Size(max = 30) String graduation,
            @Valid EducationDetails details
    ) {
        Education {
            details = details == null ? new EducationDetails("", "", "", "", "") : details;
        }
    }

    record EducationDetails(
            @NotNull @Size(max = 30) String start,
            @NotNull @Size(max = 40) String studyType,
            @NotNull @Size(max = 120) String department,
            @NotNull @Size(max = 80) String city,
            @NotNull @Size(max = 1000) String notes
    ) {}

    record Experience(
            @NotNull ExperienceType type,
            @NotNull @Size(max = 120) String title,
            @Size(max = 80) String role,
            @Size(max = 60) String period,
            @NotNull @Size(max = 1000) String context,
            @NotNull @Size(max = 2000) String contribution,
            @Valid ExperienceDetails details
    ) {
        Experience {
            role = valueOrEmpty(role);
            period = valueOrEmpty(period);
            details = details == null ? new ExperienceDetails("", "", "", TextStyle.PARAGRAPH) : details;
        }
    }

    record ExperienceDetails(
            @NotNull @Size(max = 120) String organization,
            @NotNull @Size(max = 80) String city,
            @NotNull @Size(max = 300) String link,
            @NotNull TextStyle textStyle
    ) {}

    record Skill(
            @NotNull @Size(max = 80) String name,
            @NotNull Proficiency proficiency,
            @Size(max = 1000) String description
    ) {
        Skill {
            description = valueOrEmpty(description);
        }
    }

    record Layout(
            @NotNull Theme theme,
            @NotNull Density density,
            @NotNull @Size(max = 11) List<@NotNull Section> sections,
            @Size(max = 11) Map<@NotNull Section, @NotNull @Size(max = 30) String> titles,
            @Size(max = 11) List<@NotNull Section> hiddenSections,
            @Valid Appearance appearance
    ) {
        Layout {
            titles = titles == null ? Map.of() : titles;
            hiddenSections = hiddenSections == null ? List.of() : hiddenSections;
            if (appearance == null) {
                int lineSpacing = density == Density.COMPACT ? 137 : density == Density.RELAXED ? 173 : 155;
                int sectionGap = density == Density.COMPACT ? 3 : density == Density.RELAXED ? 7 : 5;
                appearance = new Appearance(Font.SANS, Accent.FOREST, Alignment.LEFT, DetailsStyle.INLINE,
                        10, lineSpacing, 14, 15, 15, 14, sectionGap, 0, 2);
            }
        }
    }

    record Appearance(
            @NotNull Font font,
            @NotNull Accent accent,
            @NotNull Alignment alignment,
            @NotNull DetailsStyle detailsStyle,
            @Min(9) @Max(13) int fontSize,
            @Min(120) @Max(190) int lineSpacing,
            @Min(8) @Max(25) int topMargin,
            @Min(8) @Max(25) int leftMargin,
            @Min(8) @Max(25) int rightMargin,
            @Min(8) @Max(25) int bottomMargin,
            @Min(2) @Max(10) int sectionGap,
            @Min(0) @Max(8) int contentIndent,
            @Min(1) @Max(6) int headingGap
    ) {}

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    enum Theme { RULED, EDITORIAL, MODERN }
    enum Density { COMPACT, STANDARD, RELAXED }
    enum Font { SANS, SERIF }
    enum Accent { FOREST, GRAPHITE, NAVY, SIENNA }
    enum Alignment { LEFT, CENTER, RIGHT }
    enum DetailsStyle { INLINE, LABELED }
    enum TextStyle { PARAGRAPH, BULLETS, NUMBERED }
    enum Section { COURSEWORK, PROJECT, INTERNSHIP, ACTIVITY, SKILLS, STRENGTH, HONOR, RESEARCH, PORTFOLIO, OTHER, CUSTOM }
    enum ExperienceType { COURSEWORK, PROJECT, INTERNSHIP, ACTIVITY, STRENGTH, HONOR, RESEARCH, PORTFOLIO, OTHER, CUSTOM }
    enum Proficiency { EXPOSURE, GUIDED, INDEPENDENT }
}
