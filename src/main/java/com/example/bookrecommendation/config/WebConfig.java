package com.example.bookrecommendation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.dialect.AbstractDialect;
import org.thymeleaf.dialect.IExpressionObjectDialect;
import org.thymeleaf.expression.IExpressionObjectFactory;

import java.util.Collections;
import java.util.Set;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Bean
    public UriEncoderDialect uriEncoderDialect() {
        return new UriEncoderDialect();
    }

    // Custom Thymeleaf dialect to provide the UriEncoder
    public static class UriEncoderDialect extends AbstractDialect implements IExpressionObjectDialect {

        public UriEncoderDialect() {
            super("uriEncoderDialect");
        }

        @Override
        public IExpressionObjectFactory getExpressionObjectFactory() {
            return new IExpressionObjectFactory() {

                @Override
                public Set<String> getAllExpressionObjectNames() {
                    return Collections.singleton("uriEncoder");
                }

                @Override
                public Object buildObject(IExpressionContext context, String expressionObjectName) {
                    if ("uriEncoder".equals(expressionObjectName)) {
                        return new UriEncoder();
                    }
                    return null;
                }


                @Override
                public boolean isCacheable(String expressionObjectName) {
                    return true;
                }
            };
        }
    }

    // URI Encoder utility class
    public static class UriEncoder {
        public String encode(String uri) {
            if (uri == null) {
                return "";
            }
            return UriComponentsBuilder.fromUriString(uri).build().encode().toUriString();
        }
    }
}
