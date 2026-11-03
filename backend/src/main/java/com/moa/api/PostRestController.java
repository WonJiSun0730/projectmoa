package com.moa.api;
import com.moa.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.net.URI;
import java.util.*;
@RestController
@RequestMapping("/api/posts")
public class PostRestController {
    private final PostService posts;private final AccountService accounts;private final MediaService media;
    public PostRestController(PostService posts,AccountService accounts,MediaService media){this.posts=posts;this.accounts=accounts;this.media=media;}
    public record WritePost(@NotBlank @Size(max=2000) String content,@NotBlank String postType,@Size(max=2048) String externalUrl,@NotNull @Size(max=10) List<String> tags) {}
    public record WriteComment(@NotBlank @Size(max=500) String content) {}
    @GetMapping public Map<String,Object> list(Authentication auth,@RequestParam(defaultValue="all") String tab,@RequestParam(defaultValue="") String q,@RequestParam(required=false) Long userId,@RequestParam(required=false) Long before) {return posts.feed(accounts.optionalId(auth),tab,q,userId,before);}
    @GetMapping("/{id}") public Map<String,Object> detail(@PathVariable long id,Authentication auth){return posts.get(id,accounts.optionalId(auth));}
    @PostMapping public ResponseEntity<?> create(@Valid @RequestBody WritePost body,Authentication auth){var created=posts.create(accounts.requireId(auth),body.content,body.postType,body.externalUrl,body.tags);return ResponseEntity.created(URI.create("/api/posts/"+created.get("id"))).body(created);}
    @PutMapping("/{id}") public Map<String,Object> update(@PathVariable long id,@Valid @RequestBody WritePost body,Authentication auth){return posts.update(accounts.requireId(auth),id,body.content,body.postType,body.externalUrl,body.tags);}
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable long id,Authentication auth){posts.delete(accounts.requireId(auth),id);return ResponseEntity.noContent().build();}
    @PutMapping("/{id}/like") public Map<String,Object> like(@PathVariable long id,Authentication auth){return posts.like(accounts.requireId(auth),id,true);}
    @DeleteMapping("/{id}/like") public Map<String,Object> unlike(@PathVariable long id,Authentication auth){return posts.like(accounts.requireId(auth),id,false);}
    @GetMapping("/{id}/comments") public List<Map<String,Object>> comments(@PathVariable long id,Authentication auth){return posts.comments(id,accounts.optionalId(auth));}
    @PostMapping("/{id}/comments") public ResponseEntity<?> comment(@PathVariable long id,@Valid @RequestBody WriteComment body,Authentication auth){long actor=accounts.requireId(auth);posts.addComment(actor,id,body.content);return ResponseEntity.status(201).body(posts.comments(id,actor));}
    @PutMapping("/{id}/comments/{commentId}") public ResponseEntity<Void> editComment(@PathVariable long id,@PathVariable long commentId,@Valid @RequestBody WriteComment body,Authentication auth){posts.changeComment(accounts.requireId(auth),id,commentId,body.content,false);return ResponseEntity.noContent().build();}
    @DeleteMapping("/{id}/comments/{commentId}") public ResponseEntity<Void> deleteComment(@PathVariable long id,@PathVariable long commentId,Authentication auth){posts.changeComment(accounts.requireId(auth),id,commentId,"",true);return ResponseEntity.noContent().build();}
    @PostMapping(value="/{id}/media",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ResponseEntity<?> upload(@PathVariable long id,@RequestParam List<MultipartFile> files,Authentication auth)throws java.io.IOException{long actor=accounts.requireId(auth);media.upload(actor,id,files);return ResponseEntity.status(201).body(posts.get(id,actor));}
    @GetMapping("/{id}/media/{mediaId}") public ResponseEntity<?> image(@PathVariable long id,@PathVariable long mediaId){var file=media.read(id,mediaId);return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.type())).cacheControl(CacheControl.noStore()).body(new FileSystemResource(file.path()));}
    @DeleteMapping("/{id}/media/{mediaId}") public ResponseEntity<Void> removeImage(@PathVariable long id,@PathVariable long mediaId,Authentication auth){media.delete(accounts.requireId(auth),id,mediaId);return ResponseEntity.noContent().build();}
}
