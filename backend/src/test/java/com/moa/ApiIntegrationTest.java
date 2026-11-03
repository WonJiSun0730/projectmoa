package com.moa;
import com.moa.service.AccountService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.datasource.url=jdbc:h2:mem:moa-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","moa.demo-data=false","moa.upload-dir=./target/test-uploads"
})
class ApiIntegrationTest {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate db;
    @Autowired AccountService accounts;
    static final ObjectMapper JSON=JsonMapper.builder().build();
    class Client {
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();String token;
        HttpResponse<String> request(String method,String path,Object body)throws Exception{
            if(!method.equals("GET")&&token==null)token=json(request("GET","/api/auth/csrf",null)).get("token").asString();
            var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));
            if(!method.equals("GET"))b.header("X-CSRF-TOKEN",token);
            if(body!=null)b.header("Content-Type","application/json");
            return http.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
        long register()throws Exception {String key=UUID.randomUUID().toString();var r=request("POST","/api/auth/register",Map.of("email",key+"@test.local","password","TestPass123!","nickname","사용자"+key.substring(0,12)));assertEquals(201,r.statusCode(),r.body());var id=json(r).get("id").asLong();var l=request("POST","/api/auth/login",Map.of("email",key+"@test.local","password","TestPass123!"));assertEquals(200,l.statusCode(),l.body());return id;}
        long post(String text,String type,List<String> tags)throws Exception {var r=request("POST","/api/posts",Map.of("content",text,"postType",type,"tags",tags));assertEquals(201,r.statusCode(),r.body());assertTrue(r.headers().firstValue("Location").orElse("").startsWith("/api/posts/"));return json(r).get("id").asLong();}
        HttpResponse<String> upload(long id,byte[] bytes)throws Exception {
            String boundary="MoaTestBoundary";
            var out=new java.io.ByteArrayOutputStream();
            out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"files\"; filename=\"sample.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));out.write(bytes);out.write(("\r\n--"+boundary+"--\r\n").getBytes());
            var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/posts/"+id+"/media")).header("X-CSRF-TOKEN",token).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build();return http.send(req,HttpResponse.BodyHandlers.ofString());
        }
    }
    JsonNode json(HttpResponse<String> r){return JSON.readTree(r.body());}
    @Test void publicFeedIsJsonAndPrivateWritesRequireLogin()throws Exception{
        var c=new Client();var r=c.request("GET","/api/posts",null);assertEquals(200,r.statusCode());assertTrue(json(r).has("items"));assertEquals(401,c.request("POST","/api/posts",Map.of("content","x","postType","NORMAL","tags",List.of())).statusCode());
    }
    @Test void csrfIsRequiredEvenWithValidSession()throws Exception{
        var c=new Client();c.register();var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/posts")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"content\":\"no csrf\",\"postType\":\"NORMAL\",\"tags\":[]}")).build();assertEquals(403,c.http.send(req,HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    @Test void likesAreIdempotentAndCanBeRemoved()throws Exception{
        var c=new Client();c.register();long id=c.post("좋아요 검증","NORMAL",List.of());assertEquals(200,c.request("PUT","/api/posts/"+id+"/like",null).statusCode());var r=c.request("PUT","/api/posts/"+id+"/like",null);assertEquals(1,json(r).get("likeCount").asInt());assertTrue(json(r).get("liked").asBoolean());c.request("DELETE","/api/posts/"+id+"/like",null);r=c.request("DELETE","/api/posts/"+id+"/like",null);assertEquals(0,json(r).get("likeCount").asInt());
    }
    @Test void anotherUserCannotModifyOrDeletePosts()throws Exception{
        var a=new Client();a.register();long id=a.post("작성자 권한","NORMAL",List.of());var b=new Client();b.register();assertEquals(403,b.request("PUT","/api/posts/"+id,Map.of("content","변경","postType","NORMAL","tags",List.of())).statusCode());assertEquals(403,b.request("DELETE","/api/posts/"+id,null).statusCode());assertEquals(200,a.request("GET","/api/posts/"+id,null).statusCode());
    }
    @Test void promotionalLinksRejectUnsafeSchemesAndNormalPostLinks()throws Exception{
        var c=new Client();c.register();for(String type:List.of("NORMAL","PROMOTION"))assertEquals(400,c.request("POST","/api/posts",Map.of("content","홍보","postType",type,"externalUrl",type.equals("NORMAL")?"https://example.com":"javascript:alert(1)","tags",List.of())).statusCode());assertEquals(201,c.request("POST","/api/posts",Map.of("content","홍보","postType","PROMOTION","externalUrl","https://example.com/event","tags",List.of())).statusCode());
    }
    @Test void tagsAreNormalizedAndSearchable()throws Exception{
        var c=new Client();c.register();String tag="TAG"+UUID.randomUUID().toString().replace("-","").substring(0,8);long id=c.post("검색 테스트","NORMAL",List.of("#"+tag,tag.toLowerCase(Locale.ROOT)));var r=c.request("GET","/api/posts?q="+tag.toLowerCase(Locale.ROOT),null);assertEquals(id,json(r).get("items").get(0).get("id").asLong());assertEquals(1,json(c.request("GET","/api/posts/"+id,null)).get("tags").size());
    }
    @Test void followsAreIdempotentAndControlFollowingFeed()throws Exception{
        var a=new Client();long aid=a.register();var b=new Client();long bid=b.register();long id=b.post("팔로우 소식","NORMAL",List.of());assertEquals(400,a.request("PUT","/api/users/"+aid+"/follow",null).statusCode());a.request("PUT","/api/users/"+bid+"/follow",null);a.request("PUT","/api/users/"+bid+"/follow",null);assertEquals(1,json(a.request("GET","/api/users/"+bid,null)).get("followers").asInt());assertEquals(id,json(a.request("GET","/api/posts?tab=following",null)).get("items").get(0).get("id").asLong());a.request("DELETE","/api/users/"+bid+"/follow",null);assertEquals(0,json(a.request("GET","/api/posts?tab=following",null)).get("items").size());
    }
    @Test void commentsValidateContentAndOwnership()throws Exception{
        var a=new Client();a.register();var b=new Client();b.register();long id=a.post("댓글","NORMAL",List.of());assertEquals(400,a.request("POST","/api/posts/"+id+"/comments",Map.of("content"," ")).statusCode());var r=a.request("POST","/api/posts/"+id+"/comments",Map.of("content","첫 댓글"));assertEquals(201,r.statusCode());long cid=json(r).get(0).get("id").asLong();assertEquals(403,b.request("DELETE","/api/posts/"+id+"/comments/"+cid,null).statusCode());assertEquals(204,a.request("PUT","/api/posts/"+id+"/comments/"+cid,Map.of("content","고친 댓글")).statusCode());assertEquals(204,a.request("DELETE","/api/posts/"+id+"/comments/"+cid,null).statusCode());assertEquals(0,json(a.request("GET","/api/posts/"+id,null)).get("commentCount").asInt());
    }
    @Test void moderationHidesPostAndRequiresAdmin()throws Exception{
        var author=new Client();author.register();long id=author.post("검토할 게시물","NORMAL",List.of());var reporter=new Client();long rid=reporter.register();assertEquals(201,reporter.request("POST","/api/posts/"+id+"/reports",Map.of("reason","반복 게시물")).statusCode());assertEquals(409,reporter.request("POST","/api/posts/"+id+"/reports",Map.of("reason","중복 신고")).statusCode());assertEquals(403,reporter.request("GET","/api/admin/reports",null).statusCode());db.update("UPDATE users SET role='ADMIN' WHERE user_id=?",rid);long report=db.queryForObject("SELECT report_id FROM reports WHERE reporter_id=?",Long.class,rid);assertEquals(204,reporter.request("PATCH","/api/admin/reports/"+report,Map.of("decision","RESOLVED","note","내용 확인 후 숨김")).statusCode());assertEquals(404,author.request("GET","/api/posts/"+id,null).statusCode());assertEquals(404,author.request("GET","/api/posts/"+id+"/comments",null).statusCode());assertEquals(409,reporter.request("PATCH","/api/admin/reports/"+report,Map.of("decision","REJECTED","note","다시 처리")).statusCode());
    }
    @Test void deletedPostDisappearsFromDetailAndSearch()throws Exception{
        var c=new Client();c.register();String marker=UUID.randomUUID().toString();long id=c.post(marker,"NORMAL",List.of());assertEquals(204,c.request("DELETE","/api/posts/"+id,null).statusCode());assertEquals(404,c.request("GET","/api/posts/"+id,null).statusCode());assertEquals(0,json(c.request("GET","/api/posts?q="+marker,null)).get("items").size());assertEquals("DELETED",db.queryForObject("SELECT status FROM posts WHERE post_id=?",String.class,id));
    }
    @Test void paginationHasNoDuplicates()throws Exception{
        var c=new Client();long uid=c.register();for(int i=0;i<22;i++)c.post("페이지 "+i,"NORMAL",List.of());var first=json(c.request("GET","/api/posts?userId="+uid,null));assertEquals(20,first.get("items").size());var second=json(c.request("GET","/api/posts?userId="+uid+"&before="+first.get("nextCursor").asLong(),null));assertEquals(2,second.get("items").size());assertTrue(first.get("items").get(19).get("id").asLong()>second.get("items").get(0).get("id").asLong());
    }
    @Test void insightsArePrivateAndSuspendedUsersCannotWrite()throws Exception{
        var c=new Client();long uid=c.register();long id=c.post("집계","PROMOTION",List.of());c.request("PUT","/api/posts/"+id+"/like",null);assertEquals(1,json(c.request("GET","/api/insights",null)).get("likeCount").asInt());assertEquals(401,new Client().request("GET","/api/insights",null).statusCode());db.update("UPDATE users SET status='SUSPENDED' WHERE user_id=?",uid);assertEquals(403,c.request("POST","/api/posts",Map.of("content","차단","postType","NORMAL","tags",List.of())).statusCode());
    }
    @Test void uploadsValidateRealBytesAndHiddenImagesCannotBeFetched()throws Exception {
        var c=new Client();c.register();long id=c.post("첨부 이미지","NORMAL",List.of());
        assertEquals(400,c.upload(id,"not an actual png".getBytes()).statusCode());
        var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",out);
        var result=c.upload(id,out.toByteArray());assertEquals(201,result.statusCode(),result.body());String url=json(result).get("media").get(0).get("url").asString();
        assertEquals(200,c.request("GET",url,null).statusCode());var other=new Client();other.register();assertEquals(403,other.upload(id,out.toByteArray()).statusCode());
        db.update("UPDATE posts SET status='HIDDEN' WHERE post_id=?",id);assertEquals(404,c.request("GET",url,null).statusCode());
    }
    @Test void logoutInvalidatesSessionAndProfilesDoNotExposeEmails()throws Exception {
        var c=new Client();long uid=c.register();var p=json(c.request("GET","/api/users/"+uid,null));assertFalse(p.has("email"));assertFalse(p.has("role"));assertEquals(204,c.request("POST","/api/auth/logout",null).statusCode());assertEquals(401,c.request("GET","/api/auth/me",null).statusCode());
    }
}
