package com.moa.api;
import com.moa.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api")
public class ModerationController {
    private final JdbcTemplate db;private final AccountService accounts;private final PostService posts;
    public ModerationController(JdbcTemplate db,AccountService accounts,PostService posts){this.db=db;this.accounts=accounts;this.posts=posts;}
    public record Report(@NotBlank @Size(max=300) String reason){}
    public record Resolve(@NotBlank String decision,@NotBlank @Size(max=300) String note){}
    public record Status(@NotBlank String status){}
    @PostMapping("/posts/{id}/reports") public ResponseEntity<?> report(@PathVariable long id,@Valid @RequestBody Report body,Authentication auth){long actor=accounts.requireId(auth);posts.get(id,actor);db.update("INSERT INTO reports(reporter_id,post_id,reason) VALUES(?,?,?)",actor,id,body.reason.strip());return ResponseEntity.status(201).body(Map.of("message","신고를 접수했습니다."));}
    @GetMapping("/admin/reports") public List<Map<String,Object>> reports(Authentication auth){accounts.admin(accounts.requireId(auth));return db.queryForList("SELECT r.*,p.content,u.nickname FROM reports r JOIN posts p ON p.post_id=r.post_id JOIN users u ON u.user_id=r.reporter_id ORDER BY r.report_id DESC");}
    @Transactional @PatchMapping("/admin/reports/{id}") public ResponseEntity<Void> resolve(@PathVariable long id,@Valid @RequestBody Resolve body,Authentication auth){
        long actor=accounts.requireId(auth);accounts.admin(actor);if(!Set.of("RESOLVED","REJECTED").contains(body.decision))throw ApiException.bad("처리 결과를 확인해 주세요.");
        var report=db.queryForList("SELECT * FROM reports WHERE report_id=? FOR UPDATE",id).stream().findFirst().orElseThrow(ApiException::missing);
        if(!"OPEN".equals(report.get("status")))throw new ApiException(org.springframework.http.HttpStatus.CONFLICT,"이미 처리한 신고입니다.");
        if("RESOLVED".equals(body.decision))db.update("UPDATE posts SET status='HIDDEN',updated_at=CURRENT_TIMESTAMP WHERE post_id=? AND status='ACTIVE'",report.get("post_id"));
        db.update("UPDATE reports SET status=?,resolution_note=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP WHERE report_id=?",body.decision,body.note,actor,id);return ResponseEntity.noContent().build();
    }
    @PatchMapping("/admin/users/{id}") public ResponseEntity<Void> status(@PathVariable long id,@Valid @RequestBody Status body,Authentication auth){long actor=accounts.requireId(auth);accounts.admin(actor);if(id==actor)throw ApiException.bad("본인 계정은 제한할 수 없습니다.");if(!Set.of("ACTIVE","SUSPENDED").contains(body.status))throw ApiException.bad("이용 상태를 확인해 주세요.");accounts.user(id);db.update("UPDATE users SET status=? WHERE user_id=?",body.status,id);return ResponseEntity.noContent().build();}
}
