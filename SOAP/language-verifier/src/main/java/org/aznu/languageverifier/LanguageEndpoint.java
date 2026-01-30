package org.aznu.languageverifier;

import org.springframework.stereotype.Service;
import org.aznu.languages.wsdl.LanguagePort;
import org.aznu.languages.wsdl.LanguageResponse;
import org.aznu.languages.wsdl.LanguageRequest;

import java.util.List;

@Service
public class LanguageEndpoint implements LanguagePort {

    @Override
    public LanguageResponse checkLanguage(LanguageRequest request) {
        String id = request.getId();
        String name = request.getName();

        LanguageResponse response = new LanguageResponse();
        response.setId(id);
        var availableLanguages = List.of("Angielski", "Niemiecki", "Francuski");
        boolean available = availableLanguages.stream()
                .anyMatch(lang -> lang.equalsIgnoreCase(name));
        response.setIsAvailable(available);
        if (available) {
            response.setReason("Język dostępny, zapraszamy na zajęcia!");
        } else {
            response.setReason("Takiego języka nie ma w ofercie.");
        }
        return response;
    }
}