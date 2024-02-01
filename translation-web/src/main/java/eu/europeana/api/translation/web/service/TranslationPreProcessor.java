package eu.europeana.api.translation.web.service;

import eu.europeana.api.translation.definitions.model.TranslationObj;
import eu.europeana.api.translation.service.TranslationService;
import eu.europeana.api.translation.service.exception.TranslationException;
import eu.europeana.api.translation.web.utils.PreProcessorUtils;

import java.util.List;

public class TranslationPreProcessor implements TranslationService {

    @Override
    public String getServiceId() {
        return null;
    }

    @Override
    public void setServiceId(String serviceId) {
    // leave empty
    }

    @Override
    public boolean isSupported(String srcLang, String trgLang) {
        return false;
    }

    @Override
    public void translate(List<TranslationObj> translationStrings) throws TranslationException {
        PreProcessorUtils.processForEligibleValues(translationStrings);
    }

    @Override
    public void close() {

    }

    @Override
    public String getExternalServiceEndPoint() {
        return null;
    }
}
