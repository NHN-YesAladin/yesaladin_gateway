package shop.yesaladin.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * JWT 토큰의 유효성을 검증 하여 인증 처리를 해주는 필터 입니다.
 *
 * @author : 송학현
 * @since : 1.0
 */
@Slf4j
@Component
public class AuthorizationHeaderFilter extends
        AbstractGatewayFilterFactory<AuthorizationHeaderFilter.Config> {

    @Value("${jwt.secret}")
    private String secretKey;

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * JWT를 생성하기 위해 HMAC-SHA 알고리즘으로 JWT에 서명할 키를 생성합니다.
     *
     * @param secretKey JWT를 생성하기 위해 사용하는 secretKey 입니다.
     * @return 인코딩 된 secretKey를 기반으로 HMAC-SHA 알고리즘으로 생성한 Key를 반환합니다.
     * @author : 송학현
     * @since : 1.0
     */
    private Key getSecretKey(String secretKey) {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }


    public AuthorizationHeaderFilter(RedisTemplate<String, Object> redisTemplate) {
        super(Config.class);
        this.redisTemplate = redisTemplate;
    }

    /**
     * 설정 클래스 입니다.
     *
     * @author : 송학현
     * @since : 1.0
     */
    public static class Config {

    }

    /**
     * Authorization Header에 들어있는 JWT 토큰의 유효성을 검증하기 위한 filter 로직입니다.
     *
     * @param config 필터의 설정 클래스 입니다.
     * @return Spring Cloud Gateway에서 작동 하는 filter 입니다.
     * @author : 송학현
     * @since : 1.0
     */
    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                return onError(exchange, "No authorization header", HttpStatus.UNAUTHORIZED);
            }

            String uuid = request.getHeaders().get("UUID").get(0);
            log.info("uuid={}", uuid);

            if (Objects.isNull(redisTemplate.opsForHash().get(uuid, "temp"))) {
                log.info("로그아웃된 사용자");

                return onError(exchange, "Already logged out", HttpStatus.UNAUTHORIZED);
            }

            String authorizationHeader = request.getHeaders().get(HttpHeaders.AUTHORIZATION).get(0);
            String jwt = authorizationHeader.replace("Bearer ", "");
            log.info("jwt={}", jwt);

            if (!isJwtValid(jwt)) {
                return onError(exchange, "JWT token is not valid", HttpStatus.UNAUTHORIZED);
            }

            log.info("loginId={}", extractLoginId(jwt));
            log.info("roles={}", extractRoles(jwt));

            exchange.getRequest()
                    .mutate()
                    .header("AUTH-ID", extractLoginId(jwt))
                    .header("AUTH-ROLES", extractRoles(jwt))
                    .build();

            return chain.filter(exchange);
        };
    }

    /**
     * 에러 발생 시 동작 하는 기능 입니다.
     *
     * @param exchange   Spring Reactive Web에서 제공하는 HTTP 요청/응답, Request Attributes, Session
     *                   Attributes를 모두 포함하고 있는 컨테이너 입니다.
     * @param err        Error 메시지 입니다.
     * @param httpStatus Http 상태 코드입니다.
     * @return error 발생 시 응답 결과 입니다.
     * @author : 송학현
     * @since : 1.0
     */
    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);

        log.error(err);
        return response.setComplete();
    }

    /**
     * JWT 토큰의 유효성을 검증하는 기능입니다.
     * 토큰을 생성할 때 사용 했던 secret key를 기반으로 유효한 토큰인지, 만료 기간이 지나지 않았는지를 판단 합니다.
     *
     * @param jwt Authorization Header에 들어있는 JWT 토큰 입니다.
     * @return 토큰의 유효성 판단 결과 입니다.
     * @author : 송학현
     * @since : 1.0
     */
    private boolean isJwtValid(String jwt) {
        boolean returnValue = true;

        String subject = null;

        try {
            subject = Jwts.parserBuilder()
                    .setSigningKey(getSecretKey(secretKey))
                    .build()
                    .parseClaimsJws(jwt)
                    .getBody()
                    .getSubject();

        } catch (Exception e) {
            returnValue = false;
        }

        if (Objects.isNull(subject) || subject.isEmpty()) {
            returnValue = false;
        }

        return returnValue;
    }

    private String extractLoginId(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSecretKey(secretKey))
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    private String extractRoles(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSecretKey(secretKey))
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("roles")
                .toString();
    }
}
