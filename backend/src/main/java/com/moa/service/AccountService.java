package com.moa.service;
import com.moa.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class AccountService {
    private final JdbcTemplate db; private final PasswordEncoder passwords;
    public AccountService(JdbcTemplate db,PasswordEncoder passwords) {this.db=db;this.passwords=passwords;}
    public Long optionalId(Authentication auth) { return auth==null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())?null:Long.valueOf(auth.getName()); }
    public long requireId(Authentication auth) {
        Long id=optionalId(auth); if(id==null)throw new ApiException(HttpStatus.UNAUTHORIZED,"로그인이 필요합니다.");
        if(!"ACTIVE".equals(user(id).get("status")))throw ApiException.forbidden(); return id;
    }
    public void admin(long id) { if(!"ADMIN".equals(user(id).get("role")))throw ApiException.forbidden(); }
    public Map<String,Object> user(long id) {
        return db.query("SELECT user_id AS id,email,nickname,bio,role,status,created_at FROM users WHERE user_id=?",(rs,n)->{
            Map<String,Object> m=new LinkedHashMap<>();m.put("id",rs.getLong("id"));m.put("email",rs.getString("email"));m.put("nickname",rs.getString("nickname"));m.put("bio",rs.getString("bio"));m.put("role",rs.getString("role"));m.put("status",rs.getString("status"));m.put("createdAt",rs.getTimestamp("created_at").toInstant().toString());return m;
        },id).stream().findFirst().orElseThrow(ApiException::missing);
    }
    @Transactional public Map<String,Object> register(String email,String password,String nickname) {
        if(password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw ApiException.bad("비밀번호는 UTF-8 기준 72바이트 이하로 입력해 주세요.");
        email=email.strip().toLowerCase(Locale.ROOT); nickname=nickname.strip();
        if(nickname.isEmpty())throw ApiException.bad("닉네임을 입력해 주세요.");
        db.update("INSERT INTO users(email,password_hash,nickname,bio) VALUES(?,?,?,?)",email,passwords.encode(password),nickname,"");
        return user(db.queryForObject("SELECT user_id FROM users WHERE email=?",Long.class,email));
    }
    public long login(String email,String password) {
        var rows=db.queryForList("SELECT user_id,password_hash,status FROM users WHERE email=?",email.strip().toLowerCase(Locale.ROOT));
        if(rows.isEmpty() || !passwords.matches(password,(String)rows.getFirst().get("password_hash")))throw new ApiException(HttpStatus.UNAUTHORIZED,"이메일 또는 비밀번호를 확인해 주세요.");
        if(!"ACTIVE".equals(rows.getFirst().get("status")))throw ApiException.forbidden();
        return ((Number)rows.getFirst().get("user_id")).longValue();
    }
    public Map<String,Object> profile(long id,Long viewer) {
        var u=user(id);u.remove("email");u.remove("role");u.remove("status");
        u.put("followers",db.queryForObject("SELECT COUNT(*) FROM follows WHERE following_id=?",Long.class,id));
        u.put("following",db.queryForObject("SELECT COUNT(*) FROM follows WHERE follower_id=?",Long.class,id));
        u.put("postCount",db.queryForObject("SELECT COUNT(*) FROM posts WHERE user_id=? AND status='ACTIVE'",Long.class,id));
        u.put("isFollowing",viewer!=null && db.queryForObject("SELECT COUNT(*) FROM follows WHERE follower_id=? AND following_id=?",Integer.class,viewer,id)>0);return u;
    }
    public Map<String,Object> update(long id,String nickname,String bio) {
        if(nickname.isBlank())throw ApiException.bad("닉네임을 입력해 주세요.");
        db.update("UPDATE users SET nickname=?,bio=? WHERE user_id=?",nickname.strip(),bio,id);return user(id);
    }
    @Transactional public void follow(long actor,long target,boolean follow) {
        if(actor==target)throw ApiException.bad("자신을 팔로우할 수 없습니다.");user(target);
        db.queryForObject("SELECT user_id FROM users WHERE user_id=? FOR UPDATE",Long.class,actor);
        if(follow) { if(db.queryForObject("SELECT COUNT(*) FROM follows WHERE follower_id=? AND following_id=?",Integer.class,actor,target)==0)db.update("INSERT INTO follows(follower_id,following_id) VALUES(?,?)",actor,target); }
        else db.update("DELETE FROM follows WHERE follower_id=? AND following_id=?",actor,target);
    }
    public List<Map<String,Object>> suggested(Long viewer) {
        return db.queryForList("SELECT user_id AS id,nickname,bio FROM users WHERE status='ACTIVE' AND role='USER' AND user_id<>? ORDER BY user_id LIMIT 4",viewer==null?-1:viewer);
    }
}
