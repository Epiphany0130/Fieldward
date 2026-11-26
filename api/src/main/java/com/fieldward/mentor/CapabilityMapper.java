package com.fieldward.mentor;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
interface CapabilityMapper {
    String EVIDENCE = "e.id, e.capability_id, e.source_type, e.source_id, e.outcome, e.created_at";

    @Select("SELECT max(version) FROM career_route_versions WHERE direction_id = #{directionId}")
    Integer currentVersion(String directionId);

    @Select("""
            SELECT capability_id, title, objective FROM career_route_items
            WHERE direction_id = #{directionId} AND version = #{version} ORDER BY position
            """)
    List<Item> items(String directionId, int version);

    @Select("SELECT " + EVIDENCE + """
             FROM capability_evidence e JOIN career_route_items r ON r.capability_id = e.capability_id
             WHERE r.direction_id = #{directionId} AND r.version = #{version} AND e.account_id = #{accountId}
             ORDER BY e.created_at, e.id
            """)
    List<CapabilityService.Evidence> routeEvidence(long accountId, String directionId, int version);

    @Select("SELECT " + EVIDENCE + """
             FROM capability_evidence e WHERE e.account_id = #{accountId} AND e.capability_id = #{capabilityId}
             ORDER BY e.created_at, e.id
            """)
    List<CapabilityService.Evidence> history(long accountId, String capabilityId);

    @Insert("""
            INSERT INTO capability_evidence (account_id, capability_id, source_type, source_id, outcome)
            VALUES (#{accountId}, #{capabilityId}, #{input.sourceType}, #{input.sourceId}, #{input.outcome})
            ON CONFLICT (account_id, capability_id, source_type, source_id) DO NOTHING
            """)
    void insert(long accountId, String capabilityId, CapabilityService.EvidenceInput input);

    @Select("SELECT " + EVIDENCE + """
             FROM capability_evidence e WHERE e.account_id = #{accountId} AND e.capability_id = #{capabilityId}
                 AND e.source_type = #{input.sourceType} AND e.source_id = #{input.sourceId}
            """)
    CapabilityService.Evidence find(long accountId, String capabilityId, CapabilityService.EvidenceInput input);

    record Item(String capabilityId, String title, String objective) {}
}
