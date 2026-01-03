package org.aznu.languageschoolrest;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.aznu.languageschoolrest.Response;
import org.aznu.languageschoolrest.SchoolApplication;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class LanguageSchoolGatewayService extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        // Konfiguracja REST (zgodna z AZNU7 str. 7)
        restConfiguration()
                .component("servlet")
                .bindingMode(RestBindingMode.json)
                .dataFormatProperty("prettyPrint", "true")
                .contextPath("/api") // Bazowa ścieżka API
                .apiContextPath("/api-doc") // Ścieżka do Swagger JSON
                .apiProperty("api.title", "School Gateway API")
                .apiProperty("api.version", "1.0.0");

        // Definicja Endpointu POST
        rest("/application")
                .post()
                .type(SchoolApplication.class)
                .outType(Response.class)
                .to("direct:processApplication");

        // Prosta trasa przetwarzania (Mock)
        from("direct:processApplication")
                .log("Otrzymano wniosek: ${body}")
                .process(exchange -> {
                    SchoolApplication req = exchange.getMessage().getBody(SchoolApplication.class);

                    Response resp = new Response();
                    resp.setApplicationId(UUID.randomUUID().toString());
                    resp.setStatus("RECEIVED");
                    resp.setMessage("Wniosek dla " + req.getFirstName() + " " + req.getLastName() + " został przyjęty.");

                    exchange.getMessage().setBody(resp);
                });
    }
}