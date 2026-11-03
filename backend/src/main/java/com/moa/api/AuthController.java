package com.moa.api;
import com.moa.service.AccountService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accounts;private final HttpSessionSecurityContextRepository contexts;
    public AuthController(AccountService a,HttpSessionSecurityContextRepository c) {accounts=a;contexts=c;}
    public record Register(@NotBlank @Email @Size(max=254) String email,@NotBlank @Size(min=8,max=64,message="비밀번호는 8~64자로 입력해 주세요.") String password,@NotBlank @Size(max=30) String nickname) {}
    public record Login(@NotBlank String email,@NotBlank String password) {}
    @GetMapping("/csrf") Map<String,String> csrf(CsrfToken token) {return Map.of("token",token.getToken(),"headerName",token.getHeaderName());}
    @GetMapping("/me") Map<String,Object> me(Authentication auth) {return accounts.user(accounts.requireId(auth));}
    @PostMapping("/register") ResponseEntity<?> register(@Valid @RequestBody Register r) {return ResponseEntity.status(201).body(accounts.register(r.email,r.password,r.nickname));}
    @PostMapping("/login") Map<String,Object> login(@Valid @RequestBody Login r,HttpServletRequest req,HttpServletResponse res) {
        long id=accounts.login(r.email,r.password);req.getSession();req.changeSessionId();
        var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(new UsernamePasswordAuthenticationToken(String.valueOf(id),null,List.of()));
        SecurityContextHolder.setContext(context);contexts.saveContext(context,req,res);return accounts.user(id);
    }
    @PostMapping("/logout") ResponseEntity<Void> logout(HttpServletRequest req) {var session=req.getSession(false);if(session!=null)session.invalidate();SecurityContextHolder.clearContext();return ResponseEntity.noContent().build();}
}
