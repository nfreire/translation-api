package eu.europeana.api.translation.service.pangeanic;

import java.util.List;
import eu.europeana.api.translation.definitions.model.TranslationString;
import eu.europeana.api.translation.service.exception.TranslationException;

/**
 * Dummy implementation preventing invocation of remote pangeanic service
 * @author GordeaS
 *
 */
public class DummyPangTranslationService extends PangeanicTranslationService{

  /**
   * Constructor using null as endpoint
   */
  public DummyPangTranslationService() {
    super(null, null);
  }
  
  @Override
  public void translate(List<TranslationString> translationStrings) throws TranslationException {
    for(TranslationString obj : translationStrings) {
      obj.setTranslation(obj.getText());
    }
  }

}
