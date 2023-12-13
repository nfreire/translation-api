package eu.europeana.api.translation.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import eu.europeana.api.commons.error.EuropeanaApiException;
import eu.europeana.api.translation.client.config.TranslationClientConfiguration;
import eu.europeana.api.translation.client.exception.TranslationException;
import eu.europeana.api.translation.definitions.model.LangDetectRequest;
import eu.europeana.api.translation.definitions.model.LangDetectResponse;
import eu.europeana.api.translation.definitions.model.TranslationRequest;
import eu.europeana.api.translation.definitions.model.TranslationResponse;

public class TranslationApiClient extends BaseTranslationApiClient {


    public TranslationApiClient(TranslationClientConfiguration configuration) {
        super(configuration);
    }

    public TranslationResponse translate(TranslationRequest request) throws EuropeanaApiException {
        return getTranslationApiRestClient().getTranslations(getJsonString(request));
    }


    public LangDetectResponse detectLang(LangDetectRequest langDetectRequest) throws EuropeanaApiException {
        return getTranslationApiRestClient().getDetectedLanguages(getJsonString(langDetectRequest));

    }

    private <T> String getJsonString(T request) throws TranslationException {
        try {
            return getObjectWriter().writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new TranslationException("Error parsing the request for Translation API");
        }

    }
}
