package org.aznu.languageschoolrest;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LanguageSchoolGatewayService extends RouteBuilder {

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
            })
            .marshal().json(JsonLibrary.Jackson)
            .to("kafka:SchoolReqTopic?brokers={{kafka.server}}")
            .process(exchange -> {
                String appId = exchange.getMessage().getHeader("applicationId", String.class);
                Response resp = new Response();
                resp.setApplicationId(appId);
                resp.setStatus("W TRAKCIE WERYFIKACJI");
                resp.setMessage("Wniosek został przyjęty do weryfikacji.");
                exchange.getMessage().setBody(resp);
            });

        from("kafka:SchoolResultTopic?brokers={{kafka.server}}")
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
                    log.info("Saga zakończona. Wynik zapisany w DB: " + status);
                });

        from("kafka:SchoolFailTopic?brokers={{kafka.server}}")
            .process(exchange -> {
                String appId = exchange.getMessage().getHeader("applicationId", String.class);
                stateService.sendEvent(appId, ProcessingEvent.CANCEL);
                log.info("Saga anulowana dla wniosku o ID: " + appId);
            });

        from("kafka:SchoolReqTopic?brokers={{kafka.server}}&groupId=verifierGroup")
            .routeId("verify-language-route")
            .log("Pobrano z Kafki wniosek: ${body}")
            .unmarshal().json(JsonLibrary.Jackson, SchoolApplication.class)
            .process(exchange -> {
                SchoolApplication app = exchange.getMessage().getBody(SchoolApplication.class);
                String appId = exchange.getMessage().getHeader("applicationId", String.class);

                ProcessingState previousState = stateService.sendEvent(appId, ProcessingEvent.START);
                exchange.getMessage().setHeader("previousState", previousState);

                org.aznu.languages.wsdl.LanguageRequest soapRequest = new org.aznu.languages.wsdl.LanguageRequest();
                soapRequest.setName(app.getLanguage());
                exchange.getMessage().setBody(soapRequest);
            })
            .choice()
            .when(header("previousState").isNotEqualTo(ProcessingState.CANCELLED))
            .to("cxf://http://{{soap.host}}:8080/soap-api/languages?serviceClass=org.aznu.languages.wsdl.LanguagePort&wsdlURL=http://{{soap.host}}:8080/soap-api/languages?wsdl")
            .process(exchange -> {
                org.apache.cxf.message.MessageContentsList responseList =
                        exchange.getMessage().getBody(org.apache.cxf.message.MessageContentsList.class);
                org.aznu.languages.wsdl.LanguageResponse soapResponse =
                        (org.aznu.languages.wsdl.LanguageResponse) responseList.get(0);

                String appId = exchange.getMessage().getHeader("applicationId", String.class);
                VerificationResult result = new VerificationResult();
                result.setApplicationId(appId);
                result.setVerified(soapResponse.isIsAvailable());
                result.setReason(soapResponse.getReason());

                exchange.getMessage().setBody(result);
                ProcessingState oldState = stateService.sendEvent(appId, ProcessingEvent.FINISH);
                exchange.getMessage().setHeader("previousState", oldState);
            })
            .marshal().json(JsonLibrary.Jackson)
            .to("kafka:SchoolResultTopic?brokers={{kafka.server}}")
            .log("Weryfikacja zakończona. Wynik wysłany na SchoolResultTopic.")
            .otherwise()
            .log("Proces przerwany - wykryto stan CANCELLED dla ID: ${header.applicationId}")
            .end();
    }
}