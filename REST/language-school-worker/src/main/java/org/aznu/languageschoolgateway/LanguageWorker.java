package org.aznu.languageschoolgateway;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LanguageWorker extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        onException(Exception.class)
                .handled(true)
                .log("SOAP nieosiągalny.")
                .process(exchange -> {
                    String appId = exchange.getMessage().getHeader("applicationId", String.class);
                    VerificationResult errorResult = new VerificationResult();
                    errorResult.setApplicationId(appId);
                    errorResult.setVerified(false);
                    errorResult.setReason("Wystąpił błąd w trakcie weryfikacji, proszę wyślij wniosek ponownie w późniejszym terminie.");
                    exchange.getMessage().setBody(errorResult);
                })
                .marshal().json(JsonLibrary.Jackson)
                .to("kafka:FailTopic?brokers={{kafka.server}}")
                .log("Wysłano powiadomienie o awarii.");

        from("kafka:ReqTopic?brokers={{kafka.server}}&groupId=verifierGroup")
                .routeId("verify-language-route")
                .log("Pobrano wniosek: ${body}")
                .unmarshal().json(JsonLibrary.Jackson, SchoolApplication.class)
                .process(exchange -> {
                    SchoolApplication app = exchange.getMessage().getBody(SchoolApplication.class);
                    String appId = exchange.getMessage().getHeader("applicationId", String.class);

                    org.aznu.languages.wsdl.LanguageRequest soapRequest = new org.aznu.languages.wsdl.LanguageRequest();
                    soapRequest.setName(app.getLanguage());
                    exchange.getMessage().setBody(soapRequest);
                })
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
                })
                .marshal().json(JsonLibrary.Jackson)
                .to("kafka:ResultTopic?brokers={{kafka.server}}")
                .log("Zweryfikowano wniosek.")
                .end();
    }
}