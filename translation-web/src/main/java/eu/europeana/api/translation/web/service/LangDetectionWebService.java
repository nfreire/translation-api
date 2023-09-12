package eu.europeana.api.translation.web.service;

import java.util.List;
import java.util.Locale;
import javax.annotation.PreDestroy;
import javax.validation.constraints.NotNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import eu.europeana.api.commons.web.exception.ParamValidationException;
import eu.europeana.api.translation.config.I18nConstants;
import eu.europeana.api.translation.config.TranslationServiceProvider;
import eu.europeana.api.translation.definitions.vocabulary.TranslationAppConstants;
import eu.europeana.api.translation.model.LangDetectRequest;
import eu.europeana.api.translation.model.LangDetectResponse;
import eu.europeana.api.translation.service.LanguageDetectionService;
import eu.europeana.api.translation.service.exception.LanguageDetectionException;

@Service
public class LangDetectionWebService {

  @Autowired
  private TranslationServiceProvider translationServiceProvider;
  
  private final Logger logger = LogManager.getLogger(getClass());
  
  public LangDetectResponse detectLang(LangDetectRequest langDetectRequest) throws ParamValidationException, LanguageDetectionException {
    LanguageDetectionService langDetectService = getLangDetectService(langDetectRequest);
    LanguageDetectionService fallback = getFallbackService(langDetectRequest); 
    List<String> langs = null;
    try {
      langs = langDetectService.detectLang(langDetectRequest.getText(), langDetectRequest.getLang()); 
    }
    catch (LanguageDetectionException originalError) {
      //check if fallback is available
      if(fallback == null) {
        throw originalError;
      } 
      try {
        langs = fallback.detectLang(langDetectRequest.getText(), langDetectRequest.getLang());  
      } catch (LanguageDetectionException e) {
        if(logger.isDebugEnabled()) {
          logger.debug("Error when calling default service. ", e);
        }
        throw originalError;
      }
    }
    
    return new LangDetectResponse(langs, langDetectRequest.getLang());
  }

  private LanguageDetectionService getFallbackService(LangDetectRequest langDetectRequest)
      throws ParamValidationException {
    //only if indicated in request
    if(langDetectRequest.getFallback() == null) {
      return null;
    }
    //call the fallback service in case of failed lang detection (non 200 response by remote service)
    return getServiceInstance(langDetectRequest.getFallback(), langDetectRequest.getLang(), true);
  }  

  private LanguageDetectionService getLangDetectService(LangDetectRequest langDetectRequest) throws ParamValidationException {
    final String requestedServiceId = langDetectRequest.getService();
    final String languageHint = langDetectRequest.getLang();
    
    if(requestedServiceId != null) {
      return getServiceInstance(requestedServiceId, languageHint);
    }else {
      final String defaultServiceId = translationServiceProvider.getTranslationServicesConfig().getLangDetectConfig().getDefaultServiceId();
      return getServiceInstance(defaultServiceId, languageHint); 
    }
  }

  private LanguageDetectionService getServiceInstance(final String requestedServiceId,
      final String languageHint) throws ParamValidationException {
    return getServiceInstance(requestedServiceId, languageHint, false);
  }

  private LanguageDetectionService getServiceInstance(final String requestedServiceId,
      final String languageHint, boolean isFallbackService) throws ParamValidationException {
    LanguageDetectionService detectService = translationServiceProvider.getLangDetectServices().get(requestedServiceId);
    if(detectService==null) {
      final String paramName = isFallbackService? TranslationAppConstants.FALLBACK: TranslationAppConstants.SERVICE;
      throw new ParamValidationException(null, I18nConstants.INVALID_SERVICE_PARAM, new String[] {paramName, requestedServiceId});
    }
    //check if the "lang" is supported
    if(languageHint!=null && !detectService.isSupported(languageHint)) {
      throw new ParamValidationException(null, I18nConstants.UNSUPORTED_LANGUAGE_BY_DETECT_SERVICE,
          new String[] {TranslationAppConstants.LANG, requestedServiceId});
    }
    return detectService;
  }

  public boolean isLangDetectionSupported(@NotNull String lang) {
    return translationServiceProvider.getTranslationServicesConfig().getLangDetectConfig()
        .getSupported().contains(lang.toLowerCase(Locale.ENGLISH));
  }
  
  @PreDestroy
  public void close() {
    //call close method of all detection services
    for (LanguageDetectionService service : translationServiceProvider.getLangDetectServices().values()) {
      service.close(); 
    }
  }
  
}