package com.cicd.myapi.security.filter;

import com.cicd.myapi.dto.MemberUserDetail;
import com.cicd.myapi.util.JWTUtil;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

@Slf4j
public class JWTCheckFilter extends OncePerRequestFilter {
    // 필터 생략할것 지정하는 메서드 추가 (OncePer... 에 있는 메서드 오버라이딩)
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {

        // Preflight 필터 체크 X (Ajax CORS 요청 전에 날리는것)
        if(request.getMethod().equals("OPTIONS")) {
            return true;
        }

        String requestURI = request.getRequestURI();
        log.info("************** JWTCheckFilter - shouldNotFilter : requestURI : {}", requestURI);

        // /api/member/.. 경로 요청은 필터 체크 X
        if(requestURI.startsWith("/api/member/")) {
            return true;
        }
        // 테스트용 경로
        if(requestURI.startsWith("/apitest")) {
            return true;
        }
        return false;
    }

    // 필터링 로직 작성 : 추상메서드로 구현 필수
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        log.info("************ JWTCheckFilter - doFilterInternal!");

        String authValue = request.getHeader("Authorization");
        log.info("********* doFilterInternal - authValue : {}", authValue);
        // Bearer XxxxxxxxaccessToken값
        try {
            String accessToken = authValue.substring(7);
            Map<String, Object> claims = JWTUtil.validateToken(accessToken);
            log.info("********* doFilterInternal - claims : {}", claims);

            // 인증 정보 claims로 MemberDTO 구성 -> 시큐리티에 반영 추가 (시큐리티용 권한)
            String email = (String) claims.get("email");
            String password = (String) claims.get("password");
            String nickname = (String) claims.get("nickname");
            Boolean social = (Boolean) claims.get("social");
            List<String> roleNames = (List<String>) claims.get("roleNames");

            MemberUserDetail memberUserDetail = new MemberUserDetail(email, password, nickname, social, roleNames);
            log.info("******** doFileterInternal - memberUserDetail : {}", memberUserDetail);

            // 시큐리티 인증 추가 : JWT와 SpringSecurity 로그인상태 호환되도록 처리
            UsernamePasswordAuthenticationToken authenticationToken
                    = new UsernamePasswordAuthenticationToken(memberUserDetail, password, memberUserDetail.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);

            filterChain.doFilter(request, response); // 다음필터 이동해라~
        }catch (Exception e) {
            // Access Token 검증 예외 처리 (검증하다 실패하면 우리가만든 예외 발생-> 그에 따른 처리하기)
            log.error("*********** JWTCheckFilter error!!!");
            log.error(e.getMessage());

            // 에러라고 응답해줄 메세지 생성 -> 전송
            Gson gson = new Gson();
            String msg = gson.toJson(Map.of("error", "ERROR_ACCESS_TOKEN"));

            response.setContentType("application/json");
            PrintWriter writer = response.getWriter();
            writer.println(msg);
            writer.close();
        }
    }
}
