package shop.yesaladin.gateway.filter;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Gateway 전역에서 확인하기 위한 Logging Filter 입니다.
 *
 * @author : 송학현
 * @since : 1.0
 */
@Slf4j
@Component
public class GlobalLoggingFilter extends AbstractGatewayFilterFactory<GlobalLoggingFilter.Config> {

    public GlobalLoggingFilter() {
        super(Config.class);
    }

    /**
     * 설정 클래스 입니다.
     *
     * @author : 송학현
     * @since : 1.0
     */
    @Data
    public static class Config {
        private String baseMessage;
        private boolean preLogger;
        private boolean postLogger;
    }

    /**
     * Gateway로 들어오는 모든 요청에 대해 log를 남기기 위한 filter 로직입니다.
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
            ServerHttpResponse response = exchange.getResponse();

            log.info("Global Logging Filter baseMessage: {}", config.getBaseMessage());
            if (config.isPreLogger()) {
                log.info("Global Logging Filter Start: request id -> {}", request.getId());
                log.info("Global Logging Filter Start: request uri -> {}", request.getURI());
                log.info("Global Logging Filter Start: request path -> {}", request.getPath());
            }
            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                if (config.isPostLogger()) {
                    log.info("Global Logging Filter End: response code -> {}", response.getStatusCode());
                }
            }));
        };
    }
}
