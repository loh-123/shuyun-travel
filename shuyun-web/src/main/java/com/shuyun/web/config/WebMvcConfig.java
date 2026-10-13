package com.shuyun.web.config;

import com.shuyun.web.interceptor.AdminInterceptor;
import com.shuyun.web.interceptor.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Web MVC 配置
 *
 * <p>注册 {@link JwtInterceptor}：拦截 /api/**，放行登录、注册、健康检查，
 * 以及景点 / 市州 / 文化 / 非遗 / 美食 / 传承人 / 聚合搜索 / 天气等公开浏览接口；
 * 并把上传目录映射到 /upload/**。</p>
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    private final AdminInterceptor adminInterceptor;

    /** 上传文件保存目录 */
    @Value("${shuyun.upload-dir:./upload}")
    private String uploadDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/auth/send-email-code",
                        "/api/auth/verify-email-code",
                        "/api/health",
                        "/api/scenic/**",
                        "/api/city/**",
                        "/api/culture/**",
                        "/api/heritage/**",
                        "/api/food/**",
                        "/api/person/**",
                        "/api/search",
                        "/api/weather",
                        "/api/screen/**");

        // 后台接口：在登录校验之后，进一步校验管理员角色
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/admin/**");
    }

    /**
     * 允许前端跨域访问（本地 Vite 开发服务器 / 生产 EdgeOne 域名），含 SSE 长连接
     *
     * <p>在开启 {@code allowCredentials(true)} 的前提下，来源必须用
     * {@code allowedOriginPatterns} 通配；若用 {@code allowedOrigins("*")}
     * Spring Boot 3 启动会直接报错。</p>
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // 允许任意来源（已开启 allowCredentials=true，只能用 patterns 通配）
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                // 暴露响应头：Authorization（登录令牌）与 X-Session-Id（AI 会话ID），供前端跨域读取
                .exposedHeaders("Authorization", "X-Session-Id")
                .allowCredentials(true)
                // 预检结果缓存 1 小时，减少 OPTIONS 请求
                .maxAge(3600);
    }

    /**
     * 把上传目录（头像等）映射为 /upload/** 静态访问
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/upload/**").addResourceLocations(location);
    }
}
