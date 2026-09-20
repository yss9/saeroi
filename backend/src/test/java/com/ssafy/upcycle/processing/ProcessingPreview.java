package com.ssafy.upcycle.processing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.UpcycleApplication;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.material.entity.*;
import com.ssafy.upcycle.material.repository.*;
import com.ssafy.upcycle.user.entity.*;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

/** Standalone local test runtime. Test sources/H2 never enter the production artifact. */
public class ProcessingPreview {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Pass frontend workspace path");
        Path frontend = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(frontend.resolve("package.json"))) throw new IllegalArgumentException("Not a frontend workspace");
        if (!Files.readString(frontend.resolve(".gitignore")).lines().map(String::trim)
                .anyMatch(line -> line.equals("/processing-preview.html") || line.equals("processing-preview.html"))) {
            throw new IllegalArgumentException("Ignore processing-preview.html before generating the local test session");
        }
        var context = new SpringApplicationBuilder(UpcycleApplication.class).run(
                "--server.address=127.0.0.1", "--server.port=8080",
                "--spring.datasource.url=jdbc:h2:mem:processing-preview;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "--spring.datasource.username=sa", "--spring.datasource.password=",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.jpa.hibernate.ddl-auto=create-drop", "--spring.sql.init.mode=never",
                "--app.security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "--app.security.jwt.issuer=processing-local-preview", "--app.security.jwt.access-token-ttl=PT8H");
        var users = context.getBean(UserRepository.class);
        var materials = context.getBean(MaterialRepository.class);
        var balances = context.getBean(MaterialBalanceRepository.class);
        var locks = context.getBean(MaterialLockRepository.class);
        var jdbc = context.getBean(JdbcTemplate.class);
        User brand = users.save(User.register("brand@preview.test","{noop}unused","새로이 스튜디오","010-0000-0000",UserRole.BRAND));
        String wallet = "0x" + "a".repeat(40);
        Material denim = materials.save(Material.register("MAT-DEMO-001",MaterialType.DENIM,MaterialRootOrigin.WASTE,
                "폐데님 · 워싱 원단",null,"A",30000,null,null,brand.getId()));
        balances.save(MaterialBalance.of(denim.getId(),brand.getId(),wallet,30000));
        locks.save(MaterialLock.create(denim.getId(),brand.getId(),5000,MaterialLockPurpose.MARKETPLACE,
                MaterialLockReferenceType.LISTING,100L,null));
        Material cotton = materials.save(Material.register("MAT-DEMO-002",MaterialType.COTTON,MaterialRootOrigin.WASTE,
                "재사용 면 원단",null,"B",15000,null,null,brand.getId()));
        balances.save(MaterialBalance.of(cotton.getId(),brand.getId(),wallet,15000));
        seedProcessor(users,jdbc,"recycle@preview.test","리뉴 텍스타일","서울 성동구",10000,1200,3);
        seedProcessor(users,jdbc,"green@preview.test","그린 루프 팩토리","경기 성남시",5000,900,5);
        seedProcessor(users,jdbc,"atelier@preview.test","다시 공방","부산 사상구",2000,1500,7);
        String token = context.getBean(JwtTokenService.class).issueAccessToken(brand.getId().toString(),List.of("BRAND")).value();
        String state = context.getBean(ObjectMapper.class).writeValueAsString(Map.of("state",Map.of(
                "user",Map.of("id",brand.getId(),"name",brand.getName(),"email",brand.getEmail(),"role","BRAND","walletAddress",wallet),
                "accessToken",token),"version",0));
        // Serialize twice: the script receives a string value, never interprets fixture content as JS.
        String literal = context.getBean(ObjectMapper.class).writeValueAsString(state);
        Files.writeString(frontend.resolve("processing-preview.html"), """
                <!doctype html><html lang="ko"><meta charset="utf-8"><title>가공 요청 로컬 테스트</title>
                <style>body{font:18px system-ui;max-width:640px;margin:80px auto;padding:24px;color:#163b36}a{color:#00685f}</style>
                <h1>가공 요청 로컬 테스트</h1><p>독립된 H2 테스트 데이터와 BRAND 계정으로 접속합니다.</p>
                <p>실제 지갑·운영 DB·온체인 거래는 사용하지 않습니다.</p>
                <a href="/brand/processing">Brand 가공 요청 시작</a>
                <p><a href="?mobile">390px 모바일 폭 확인</a></p>
                <script>localStorage.setItem('saeroi.auth', %s);
                if(location.search === '?mobile') {
                  const frame=document.createElement('iframe'); frame.src='/brand/processing';
                  frame.title='모바일 가공 요청'; frame.style='width:390px;height:844px;border:1px solid #bcc9c6';
                  document.body.appendChild(frame);
                }
                </script></html>
                """.formatted(literal));
        System.out.println("Processing preview ready. Open frontend /processing-preview.html. In-memory data resets on restart.");
    }

    /**
     * 가공사를 한 곳 심는다.
     *
     * <p>예전에는 수량 구간과 소요일 표를 따로 심었지만, 지금은 하루 처리량과
     * kg 당 단가 둘이면 된다 — 소요일은 ⌈수량 ÷ 하루 처리량⌉ 으로 나온다.
     */
    private static void seedProcessor(UserRepository users,JdbcTemplate jdbc,String email,String name,String region,
            long dailyCapacity,long pricePerKg,int unused) {
        Long id=users.save(User.register(email,"{noop}unused",name,"010-0000-0000",UserRole.PROCESSOR)).getId();
        jdbc.update("insert into processor_profile(user_id,region) values (?,?)",id,region);
        for (String material:List.of("DENIM","COTTON")) for (String purpose:List.of("CLEANING","DISASSEMBLY","CUTTING")) {
            jdbc.update("insert into processor_capability(processor_profile_id,material_type,process_type,daily_capacity,price_per_kg) values (?,?,?,?,?)",id,material,purpose,dailyCapacity,pricePerKg);
        }
    }
}
