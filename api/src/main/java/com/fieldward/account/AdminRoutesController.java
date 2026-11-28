package com.fieldward.account;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestController @RequestMapping("/api/admin/routes")
class AdminRoutesController {
    private final JdbcTemplate db;
    AdminRoutesController(JdbcTemplate db) { this.db = db; }
    @GetMapping List<Map<String,Object>> list() {
        var directions = db.queryForList("SELECT d.id,d.title,d.description,d.roles,(SELECT max(version) FROM career_route_versions v WHERE v.direction_id=d.id) AS version FROM career_directions d ORDER BY d.id");
        for (var direction : directions) direction.put("items", db.queryForList("SELECT capability_id AS \"capabilityId\", title, objective FROM career_route_items WHERE direction_id=? AND version=(SELECT max(version) FROM career_route_versions WHERE direction_id=?) ORDER BY position", direction.get("id"), direction.get("id")));
        return directions;
    }
    @GetMapping("/capabilities") List<Map<String,Object>> capabilities() { return db.queryForList("SELECT id,title FROM career_capabilities ORDER BY title,id"); }
    record CapabilityInput(@NotBlank @Size(max=100) @Pattern(regexp="[a-z][a-z0-9:-]*") String id, @NotBlank @Size(max=200) String title) {}
    @PostMapping("/capabilities") void createCapability(@RequestBody @Valid CapabilityInput input) {
        if (db.update("INSERT INTO career_capabilities(id,title) VALUES (?,?) ON CONFLICT DO NOTHING", input.id(), input.title()) == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "能力标识已存在");
    }
    record Item(@NotBlank @Size(max=200) String capabilityId, @NotBlank @Size(max=200) String title, @NotBlank @Size(max=1000) String objective) {}
    record Input(@NotNull @Min(1) Integer version, @NotBlank @Size(max=200) String title, @NotBlank @Size(max=1000) String description, @NotBlank @Size(max=1000) String roles, @NotEmpty @Size(max=100) List<@Valid Item> items) {}
    @PutMapping("/{id}") @Transactional void update(@PathVariable String id, @RequestBody @Valid Input input) {
        if (db.queryForList("SELECT id FROM career_directions WHERE id=? FOR UPDATE", id).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "方向不存在");
        var ids = new HashSet<String>();
        for (var item : input.items()) if (!ids.add(item.capabilityId()) || db.queryForObject("SELECT count(*) FROM career_capabilities WHERE id=?", Long.class, item.capabilityId()) == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "能力标识无效或重复");
        int current = db.queryForObject("SELECT COALESCE(max(version),0) FROM career_route_versions WHERE direction_id=?", Integer.class, id);
        if (current != input.version()) throw new ResponseStatusException(HttpStatus.CONFLICT, "路线已被修改，请刷新后重试");
        db.update("UPDATE career_directions SET title=?,description=?,roles=? WHERE id=?", input.title(), input.description(), input.roles(), id);
        int version = current + 1;
        db.update("INSERT INTO career_route_versions(direction_id,version) VALUES (?,?)", id, version);
        int position = 0;
        for (var item : input.items()) db.update("INSERT INTO career_route_items(direction_id,version,position,capability_id,legacy_skill_id,title,objective) VALUES (?,?,?,?,(SELECT legacy_skill_id FROM career_route_items WHERE direction_id=? AND version=? AND capability_id=?),?,?)", id, version, ++position, item.capabilityId(), id, version - 1, item.capabilityId(), item.title(), item.objective());
    }
}
