package com.moa.config;
import com.moa.service.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.List;
@Component @ConditionalOnProperty(name="moa.demo-data",havingValue="true")
public class DemoData implements CommandLineRunner {
    private final AccountService accounts;private final PostService posts;private final JdbcTemplate db;
    public DemoData(AccountService accounts,PostService posts,JdbcTemplate db){this.accounts=accounts;this.posts=posts;this.db=db;}
    @Override @org.springframework.transaction.annotation.Transactional public void run(String... args){
        if(db.queryForObject("SELECT COUNT(*) FROM users",Integer.class)>0)return;
        long a=((Number)accounts.register("hello@moa.test","MoaDemo123!","모아 스튜디오").get("id")).longValue();
        long b=((Number)accounts.register("jiwoo@moa.test","MoaDemo123!","지우의 기록").get("id")).longValue();
        long c=((Number)accounts.register("green@moa.test","MoaDemo123!","그린룸").get("id")).longValue();
        long d=((Number)accounts.register("admin@moa.test","MoaAdmin123!","모아 운영팀").get("id")).longValue();db.update("UPDATE users SET role='ADMIN' WHERE user_id=?",d);
        accounts.update(a,"모아 스튜디오","일상에 작은 영감을 더하는 디자인 스튜디오");accounts.update(b,"지우의 기록","좋아하는 공간과 순간을 모읍니다.");accounts.update(c,"그린룸","식물과 함께 천천히 자라는 일상");
        var p1=posts.create(b,"주말에는 조금 느리게. 동네 카페에서 발견한 작은 여유를 기록해요. 여러분이 좋아하는 공간도 알려주세요 ☕","NORMAL",null,List.of("일상기록","카페","취향공유"));
        var p2=posts.create(c,"작은 초록 하나로 달라지는 공간. 처음 식물을 키우는 분들을 위한 그린룸의 첫 번째 가이드를 소개합니다.","PROMOTION","https://example.com/greenroom",List.of("식물생활","공간","브랜드소식"));
        var p3=posts.create(a,"좋아하는 것들을 모으는 곳, 모아. 새로운 생각을 나누고 나와 비슷한 취향을 발견해 보세요. 오늘 여러분의 첫 기록을 기다릴게요.","NORMAL",null,List.of("모아","취향공유","일상기록"));
        var p4=posts.create(b,"책상 위를 가볍게 정리하고 나니 생각도 조금 선명해졌어요. 이번 주에는 매일 한 문장씩 써보려고요.","NORMAL",null,List.of("데스크테리어","일상기록"));
        var p5=posts.create(a,"하루를 채우는 작은 문구들. 모아 스튜디오의 가을 기록 노트가 준비됐어요. 자세한 소식은 링크에서 만나요.","PROMOTION","https://example.com/moa-studio",List.of("문구","브랜드소식","기록"));
        for(var p:List.of(p1,p2,p3,p4,p5))posts.like(d,((Number)p.get("id")).longValue(),true);
        posts.like(b,((Number)p5.get("id")).longValue(),true);posts.addComment(b,((Number)p5.get("id")).longValue(),"색감이 따뜻해서 좋아요. 새 노트에 어떤 기록을 할지 고민되네요!");
        accounts.follow(b,a,true);accounts.follow(a,c,true);
    }
}
