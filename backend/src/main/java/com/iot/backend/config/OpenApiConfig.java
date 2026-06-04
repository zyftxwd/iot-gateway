package com.iot.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI iiotGatewayOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("IIoT Gateway API")
                        .description("工业网关后端接口文档，包含资产、点表、实时数据、历史数据、报警、工单、权限和协议模板接口。")
                        .version("1.0.0")
                        .contact(new Contact().name("IIoT Gateway"))
                        .license(new License().name("Internal Project")));
    }
}
