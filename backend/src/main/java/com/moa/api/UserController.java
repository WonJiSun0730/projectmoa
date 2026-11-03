package com.moa.api;
import com.moa.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api")
public class UserController {
    private final AccountService accounts;private final PostService posts;
    public UserController(AccountService accounts,PostService posts){this.accounts=accounts;this.posts=posts;}
    public record Profile(@NotBlank @Size(max=30) String nickname,@NotNull @Size(max=200) String bio){}
    @GetMapping("/users/{id}") public Map<String,Object> profile(@PathVariable long id,Authentication auth){return accounts.profile(id,accounts.optionalId(auth));}
    @PatchMapping("/users/me") public Map<String,Object> update(@Valid @RequestBody Profile body,Authentication auth){return accounts.update(accounts.requireId(auth),body.nickname,body.bio);}
    @PutMapping("/users/{id}/follow") public ResponseEntity<Void> follow(@PathVariable long id,Authentication auth){accounts.follow(accounts.requireId(auth),id,true);return ResponseEntity.noContent().build();}
    @DeleteMapping("/users/{id}/follow") public ResponseEntity<Void> unfollow(@PathVariable long id,Authentication auth){accounts.follow(accounts.requireId(auth),id,false);return ResponseEntity.noContent().build();}
    @GetMapping("/discover") public Map<String,Object> discover(Authentication auth){return Map.of("tags",posts.trending(),"users",accounts.suggested(accounts.optionalId(auth)));}
    @GetMapping("/insights") public Map<String,Object> insights(Authentication auth){return posts.insights(accounts.requireId(auth));}
}
