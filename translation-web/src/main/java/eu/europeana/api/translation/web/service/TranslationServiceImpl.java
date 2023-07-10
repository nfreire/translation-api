package eu.europeana.api.translation.web.service;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import eu.europeana.api.translation.config.InitServicesGlobalJsonConfig;
import eu.europeana.api.translation.config.serialization.TranslationServicesConfiguration;
import eu.europeana.api.translation.model.LangDetectRequest;
import eu.europeana.api.translation.model.LangDetectResponse;
import eu.europeana.api.translation.model.TranslationRequest;
import eu.europeana.api.translation.model.TranslationResponse;
import eu.europeana.api.translation.web.exception.TranslationException;

@Service
public class TranslationServiceImpl {

  @Autowired
  private InitServicesGlobalJsonConfig initGlobalJsonConfig;
  
  public TranslationServicesConfiguration info() {
    return initGlobalJsonConfig.getAppGlobalJsonConfig();
  }
  
  public TranslationResponse translate(TranslationRequest translRequest) throws TranslationException {
    TranslationService defaultTranslService = getDefaultTranslService();
    List<String> translations = defaultTranslService.translate(translRequest.getText(), translRequest.getTarget(), translRequest.getSource(), translRequest.getDetect());
    TranslationResponse result = new TranslationResponse();
    result.setTranslations(translations);
    result.setLang(translRequest.getTarget());
    return result;
  }
  
  public LangDetectResponse detectLang(LangDetectRequest langDetectRequest) throws TranslationException {
    LanguageDetectionService defaultLangDetectService = getDefaultLangDetectService();
    List<String> langs = defaultLangDetectService.detectLang(langDetectRequest.getText(), langDetectRequest.getLang()); 
    LangDetectResponse result = new LangDetectResponse();
    result.setLangs(langs);
    result.setLang(langDetectRequest.getLang());
    return result;
  }  
  
  private TranslationService getDefaultTranslService() throws TranslationException {
    List<TranslationService> translServices = initGlobalJsonConfig.getTranslServices();
    String defaultTranslServiceClassname = initGlobalJsonConfig.getAppGlobalJsonConfig().getTranslConfig().getDefaultClassname();
    for(TranslationService translServ : translServices) {
      if(defaultTranslServiceClassname.equals(translServ.getClass().getName())) {
        return translServ;
      }
    }
    throw new TranslationException("There is no default TranslationService configured.");
  }
  
  private LanguageDetectionService getDefaultLangDetectService() throws TranslationException {
    List<LanguageDetectionService> langDetestServices = initGlobalJsonConfig.getLangDetectServices();
    String defaultLangDetectServiceClassname = initGlobalJsonConfig.getAppGlobalJsonConfig().getLangDetectConfig().getDefaultClassname();
    for(LanguageDetectionService langDetectServ : langDetestServices) {
      if(defaultLangDetectServiceClassname.equals(langDetectServ.getClass().getName())) {
        return langDetectServ;
      }
    }
    throw new TranslationException("There is no default LanguageDetectionService configured.");
  }

}
