package org.aznu.languageschoolrest;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LanguageSchoolRouteBuilder extends RouteBuilder {

    private static final java.util.Map<String, Response> db = new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired
    private StateService stateService;

    @Override
    public void configure() throws Exception {

        restConfiguration()
            .component("servlet")
            .bindingMode(RestBindingMode.json)
            .dataFormatProperty("prettyPrint", "true")
            .contextPath("/api")
            .apiContextPath("/api-doc")
            .apiProperty("api.title", "REST API")
            .apiProperty("api.version", "1.0.0");

        rest("/application")
            .post()
            .type(SchoolApplication.class)
            .outType(Response.class)
            .to("direct:processApplication");

        rest("/test")
            .get()
            .to("direct:test");

        rest("/application/{id}")
            .get()
            .to("direct:getStatus");

        from("direct:getStatus")
            .process(exchange -> {
                String id = exchange.getMessage().getHeader("id", String.class);
                Response response = db.getOrDefault(id, null);
                if(response == null){
                    response = new Response();
                    response.setApplicationId(id);
                    response.setStatus("NIEZNANE ID");
                    response.setMessage("Nie posiadamy w systemie wniosku o takim ID.");
                }

                Response r = new Response();
                r.setApplicationId(id);
                r.setStatus(response.getStatus());
                r.setMessage(response.getMessage());
                exchange.getMessage().setBody(r);
            });

        from("direct:test").transform().constant("Test!");

        from("direct:processApplication")
            .log("Otrzymano wniosek: ${body}")
            .process(exchange -> {
                String appId = UUID.randomUUID().toString();
                exchange.getMessage().setHeader("applicationId", appId);
                stateService.sendEvent(appId, ProcessingEvent.START);
                log.info("START: " + appId);
            })
            .marshal().json(JsonLibrary.Jackson)
            .to("kafka:ReqTopic?brokers={{kafka.server}}")
            .process(exchange -> {
                String appId = exchange.getMessage().getHeader("applicationId", String.class);
                Response resp = new Response();
                resp.setApplicationId(appId);
                resp.setStatus("W TRAKCIE WERYFIKACJI");
                resp.setMessage("Wniosek został przyjęty do weryfikacji.");
                stateService.sendEvent(appId, ProcessingEvent.FINISH);
                log.info("FINISH(w trakcie weryfikacji): " + appId);
                exchange.getMessage().setBody(resp);
            });

        from("kafka:ResultTopic?brokers={{kafka.server}}")
                .unmarshal().json(JsonLibrary.Jackson, VerificationResult.class)
                .process(exchange -> {
                    VerificationResult result = exchange.getMessage().getBody(VerificationResult.class);
                    String appId = result.getApplicationId();
                    String status = result.isVerified() ? "ZAAKCEPTOWANO" : "ODRZUCONO";
                    Response response = new Response();
                    response.setApplicationId(appId);
                    response.setStatus(status);
                    response.setMessage(result.getReason());
                    db.put(appId, response);
                    stateService.sendEvent(appId, ProcessingEvent.COMPLETE);
                    log.info("COMPLETE: Status dla " + appId + ": " + status);
                });

        from("kafka:FailTopic?brokers={{kafka.server}}")
            .process(exchange -> {
                String appId = exchange.getMessage().getHeader("applicationId", String.class);
                String status = "NIE ROZSTRZYGNIĘTO";
                String reason = "Wystąpił błąd w trakcie weryfikacji, proszę wyślij wniosek ponownie w późniejszym terminie.";
                Response response = new Response();
                response.setApplicationId(appId);
                response.setStatus(status);
                response.setMessage(reason);
                db.put(appId, response);
                stateService.sendEvent(appId, ProcessingEvent.CANCEL);
                log.info("CANCEL: " + appId);
            });
    }
}