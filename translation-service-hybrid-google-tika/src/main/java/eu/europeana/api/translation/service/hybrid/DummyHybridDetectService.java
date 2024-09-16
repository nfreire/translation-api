package eu.europeana.api.translation.service.hybrid;

import java.util.List;

import eu.europeana.api.translation.definitions.model.LanguageDetectionObj;
import org.apache.commons.lang3.StringUtils;
import eu.europeana.api.translation.service.exception.LanguageDetectionException;
import eu.europeana.api.translation.service.google.GoogleTranslationServiceClientWrapper;

/**
 * Dummy implementation preventing invocation of remote google service
 * @author GordeaS
 *
 */
public class DummyHybridDetectService extends HybridLangDetectService {

  /**
   * Constructor using dummy project id
   * @param clientWrapperBean the client wrapper implemnetation
   */
  public DummyHybridDetectService(GoogleTranslationServiceClientWrapper clientWrapperBean) {
    super(GoogleTranslationServiceClientWrapper.MOCK_CLIENT_PROJ_ID, clientWrapperBean);
  }
  
  @Override
  public void detectLang(List<LanguageDetectionObj> languageDetectionObjs) throws LanguageDetectionException {
    String langHint = languageDetectionObjs.get(0).getHint();
    String value = StringUtils.isNotBlank(langHint) ?  langHint : "en";
    for (LanguageDetectionObj obj : languageDetectionObjs) {
      obj.setDetectedLang(value);
    }
  }
}
