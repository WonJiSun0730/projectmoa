package com.moa.service;
import com.moa.api.ApiException;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.sql.Statement;
import java.util.*;
@Service
public class PostService {
    private final JdbcTemplate db;
    public PostService(JdbcTemplate db) {this.db=db;}
    private static final String SELECT="""
        SELECT p.*,u.nickname,
        (SELECT COUNT(*) FROM post_likes l WHERE l.post_id=p.post_id) AS like_count,
        (SELECT COUNT(*) FROM comments c WHERE c.post_id=p.post_id AND c.status='ACTIVE') AS comment_count
        FROM posts p JOIN users u ON u.user_id=p.user_id
        """;
    private Map<String,Object> map(java.sql.ResultSet rs,int row) throws java.sql.SQLException {
        Map<String,Object> p=new LinkedHashMap<>();p.put("id",rs.getLong("post_id"));p.put("userId",rs.getLong("user_id"));p.put("nickname",rs.getString("nickname"));p.put("content",rs.getString("content"));p.put("postType",rs.getString("post_type"));p.put("externalUrl",rs.getString("external_url"));p.put("status",rs.getString("status"));p.put("createdAt",rs.getTimestamp("created_at").toInstant().toString());p.put("likeCount",rs.getLong("like_count"));p.put("commentCount",rs.getLong("comment_count"));return p;
    }
    private Map<String,Object> enrich(Map<String,Object> p,Long viewer) {
        long id=((Number)p.get("id")).longValue();
        p.put("liked",viewer!=null && db.queryForObject("SELECT COUNT(*) FROM post_likes WHERE user_id=? AND post_id=?",Integer.class,viewer,id)>0);
        p.put("tags",db.queryForList("SELECT h.name FROM hashtags h JOIN post_hashtags ph ON ph.hashtag_id=h.hashtag_id WHERE ph.post_id=? ORDER BY h.name",String.class,id));
        p.put("media",db.query("SELECT media_id,storage_key,sort_order FROM post_media WHERE post_id=? ORDER BY sort_order",(rs,n)->Map.of("id",rs.getLong("media_id"),"url","/api/posts/"+id+"/media/"+rs.getLong("media_id"),"order",rs.getInt("sort_order")),id));return p;
    }
    public Map<String,Object> get(long id,Long viewer) {
        return enrich(db.query(SELECT+" WHERE p.post_id=? AND p.status='ACTIVE'",this::map,id).stream().findFirst().orElseThrow(ApiException::missing),viewer);
    }
    public Map<String,Object> feed(Long viewer,String tab,String q,Long userId,Long before) {
        if(!Set.of("all","following","promotion").contains(tab))throw ApiException.bad("피드 구분을 확인해 주세요.");
        if(q.length()>100)throw ApiException.bad("검색어는 100자 이하로 입력해 주세요.");
        var sql=new StringBuilder(SELECT+" WHERE p.status='ACTIVE'");List<Object> args=new ArrayList<>();
        if("following".equals(tab)){if(viewer==null)throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED,"팔로잉 피드는 로그인 후 확인할 수 있어요.");sql.append(" AND EXISTS (SELECT 1 FROM follows f WHERE f.following_id=p.user_id AND f.follower_id=?)");args.add(viewer);}
        if("promotion".equals(tab))sql.append(" AND p.post_type='PROMOTION'");
        if(userId!=null){sql.append(" AND p.user_id=?");args.add(userId);}
        if(before!=null){sql.append(" AND p.post_id<?");args.add(before);}
        if(!q.isBlank()){
            String term=q.strip().replaceFirst("^#","").toLowerCase(Locale.ROOT);
            // LOCATE treats '%' and '_' as literal search characters, not SQL wildcards.
            sql.append(" AND (LOCATE(?,LOWER(p.content))>0 OR LOCATE(?,LOWER(u.nickname))>0 OR EXISTS (SELECT 1 FROM post_hashtags ph JOIN hashtags h ON h.hashtag_id=ph.hashtag_id WHERE ph.post_id=p.post_id AND LOCATE(?,h.name)>0))");
            args.addAll(List.of(term,term,term));
        }
        sql.append(" ORDER BY p.post_id DESC LIMIT 21");var results=db.query(sql.toString(),this::map,args.toArray());boolean more=results.size()>20;if(more)results=results.subList(0,20);
        var items=results.stream().map(v->enrich(v,viewer)).toList();Map<String,Object> response=new LinkedHashMap<>();response.put("items",items);response.put("nextCursor",more?items.getLast().get("id"):null);return response;
    }
    private void validate(String content,String type,String url,List<String> tags) {
        if(content==null || content.isBlank() || content.length()>2000)throw ApiException.bad("본문은 1~2,000자로 입력해 주세요.");
        if(type==null || !Set.of("NORMAL","PROMOTION").contains(type))throw ApiException.bad("게시물 유형을 확인해 주세요.");
        if(url!=null && !url.isBlank()){
            if(!"PROMOTION".equals(type))throw ApiException.bad("외부 링크는 홍보 게시물에만 추가할 수 있습니다.");
            try{var u=URI.create(url);if(url.length()>2048 || !Set.of("http","https").contains(u.getScheme()) || u.getHost()==null || u.getUserInfo()!=null)throw new IllegalArgumentException();}catch(IllegalArgumentException e){throw ApiException.bad("올바른 http(s) 링크를 입력해 주세요.");}
        }
        if(tags==null || tags.size()>10)throw ApiException.bad("태그는 최대 10개까지 입력할 수 있습니다.");
        if(tags.stream().anyMatch(t->t==null || !t.replaceFirst("^#","").matches("[\\p{L}\\p{N}_]{1,50}")))throw ApiException.bad("태그는 공백 없이 문자·숫자·밑줄로 1~50자 입력해 주세요.");
    }
    private void tags(long id,List<String> tags) {
        db.update("DELETE FROM post_hashtags WHERE post_id=?",id);
        for(String tag:tags.stream().map(t->t.replaceFirst("^#","").toLowerCase(Locale.ROOT)).distinct().toList()){
            // Atomic upsert keeps concurrent posts using the same new hashtag valid.
            db.update("INSERT INTO hashtags(name) VALUES(?) ON DUPLICATE KEY UPDATE name=VALUES(name)",tag);
            Long tid=db.queryForObject("SELECT hashtag_id FROM hashtags WHERE name=?",Long.class,tag);
            db.update("INSERT INTO post_hashtags(post_id,hashtag_id) VALUES(?,?)",id,tid);
        }
    }
    @Transactional public Map<String,Object> create(long actor,String content,String type,String url,List<String> tags) {
        validate(content,type,url,tags);KeyHolder key=new GeneratedKeyHolder();
        db.update(c->{var ps=c.prepareStatement("INSERT INTO posts(user_id,content,post_type,external_url) VALUES(?,?,?,?)",new String[]{"post_id"});ps.setLong(1,actor);ps.setString(2,content.strip());ps.setString(3,type);ps.setString(4,url==null || url.isBlank()?null:url.strip());return ps;},key);
        long id=Objects.requireNonNull(key.getKey()).longValue();tags(id,tags);return get(id,actor);
    }
    public void owner(long actor,long id) {var post=get(id,actor);if(((Number)post.get("userId")).longValue()!=actor)throw ApiException.forbidden();}
    @Transactional public Map<String,Object> update(long actor,long id,String content,String type,String url,List<String> tags) {
        owner(actor,id);validate(content,type,url,tags);db.update("UPDATE posts SET content=?,post_type=?,external_url=?,updated_at=CURRENT_TIMESTAMP WHERE post_id=?",content.strip(),type,url==null||url.isBlank()?null:url.strip(),id);tags(id,tags);return get(id,actor);
    }
    @Transactional public void delete(long actor,long id) {owner(actor,id);db.update("UPDATE posts SET status='DELETED',updated_at=CURRENT_TIMESTAMP WHERE post_id=?",id);}
    @Transactional public Map<String,Object> like(long actor,long id,boolean liked) {
        // Serialize reactions per post. PUT/DELETE express the desired state and are idempotent.
        get(id,actor);db.queryForObject("SELECT post_id FROM posts WHERE post_id=? FOR UPDATE",Long.class,id);get(id,actor);
        if(liked){if(db.queryForObject("SELECT COUNT(*) FROM post_likes WHERE user_id=? AND post_id=?",Integer.class,actor,id)==0)db.update("INSERT INTO post_likes(user_id,post_id) VALUES(?,?)",actor,id);}
        else db.update("DELETE FROM post_likes WHERE user_id=? AND post_id=?",actor,id);
        return get(id,actor);
    }
    public List<Map<String,Object>> comments(long id,Long viewer) {
        get(id,viewer);return db.query("SELECT c.*,u.nickname FROM comments c JOIN users u ON u.user_id=c.user_id WHERE c.post_id=? AND c.status='ACTIVE' ORDER BY c.comment_id",(rs,n)->Map.of("id",rs.getLong("comment_id"),"userId",rs.getLong("user_id"),"nickname",rs.getString("nickname"),"content",rs.getString("content"),"createdAt",rs.getTimestamp("created_at").toInstant().toString()),id);
    }
    public void addComment(long actor,long id,String content) {get(id,actor);checkComment(content);db.update("INSERT INTO comments(post_id,user_id,content) VALUES(?,?,?)",id,actor,content.strip());}
    private void checkComment(String content){if(content==null||content.isBlank()||content.length()>500)throw ApiException.bad("댓글은 1~500자로 입력해 주세요.");}
    public void changeComment(long actor,long id,long comment,String content,boolean delete) {
        get(id,actor);if(!delete)checkComment(content);
        int changed=delete?db.update("UPDATE comments SET status='DELETED',updated_at=CURRENT_TIMESTAMP WHERE comment_id=? AND post_id=? AND user_id=? AND status='ACTIVE'",comment,id,actor):db.update("UPDATE comments SET content=?,updated_at=CURRENT_TIMESTAMP WHERE comment_id=? AND post_id=? AND user_id=? AND status='ACTIVE'",content.strip(),comment,id,actor);
        if(changed==0)throw ApiException.forbidden();
    }
    public List<Map<String,Object>> trending() {return db.queryForList("SELECT h.name,COUNT(*) AS postCount FROM hashtags h JOIN post_hashtags ph ON ph.hashtag_id=h.hashtag_id JOIN posts p ON p.post_id=ph.post_id WHERE p.status='ACTIVE' GROUP BY h.hashtag_id,h.name ORDER BY COUNT(*) DESC,h.name LIMIT 6");}
    public Map<String,Object> insights(long actor) {
        var items=db.query(SELECT+" WHERE p.user_id=? AND p.status='ACTIVE' ORDER BY p.post_id DESC",this::map,actor);
        return Map.of("posts",items,"postCount",items.size(),"likeCount",items.stream().mapToLong(v->((Number)v.get("likeCount")).longValue()).sum(),"commentCount",items.stream().mapToLong(v->((Number)v.get("commentCount")).longValue()).sum());
    }
}
