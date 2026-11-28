package com.fieldward.mentor;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class CareerDirections {
    private final JdbcTemplate db;
    CareerDirections(JdbcTemplate db) { this.db = db; }

    record Selection(CareerCatalog.Direction direction, RoadmapCatalog.Route route, int version) {}
    record Module(String id, String legacyId, String title, String objective) {}

    Selection require(String id) {
        var found = db.query("""
                SELECT d.title,d.description,d.roles,v.version,i.capability_id,i.legacy_skill_id,i.title AS item_title,i.objective
                FROM career_directions d
                JOIN career_route_versions v ON v.direction_id=d.id AND v.version=(SELECT max(version) FROM career_route_versions WHERE direction_id=d.id)
                JOIN career_route_items i ON i.direction_id=v.direction_id AND i.version=v.version
                WHERE d.id=? ORDER BY i.position
                """, (rs, n) -> new Object[]{rs.getString(1),rs.getString(2),rs.getString(3),rs.getInt(4),new Module(rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8))}, id);
        if (found.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择一个有效的职业方向");
        var row = found.getFirst();
        int version = (int) row[3];
        var modules = found.stream().map(item -> (Module) item[4]).toList();
        var legacy = CareerCatalog.DIRECTIONS.stream().filter(d -> d.id().equals(id)).findFirst().orElse(null);
        if (legacy != null && version == 1) return new Selection(legacy, RoadmapCatalog.forDirection(id), version);
        List<CareerCatalog.Skill> skills = modules.stream().map(module -> {
            var old = legacy == null ? null : legacy.skills().stream().filter(s -> s.id().equals(module.legacyId())).findFirst().orElse(null);
            return new CareerCatalog.Skill(module.id(), module.title(), old == null ? 1 : old.weight(), old == null || old.core(), module.objective(), old == null ? module.objective() : old.task(), old == null ? "请介绍你在这项能力上的经验。" : old.question());
        }).toList();
        var direction = new CareerCatalog.Direction(id, (String) row[0], (String) row[1], (String) row[2], skills);
        return new Selection(direction, new RoadmapCatalog.Route(null, List.of(new RoadmapCatalog.Phase("基础路线", modules.stream().map(Module::id).toList())), modules.stream().collect(java.util.stream.Collectors.toMap(Module::id, Module::title))), version);
    }
}
