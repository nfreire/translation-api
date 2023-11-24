package eu.europeana.api.translation.service.google;

import java.util.List;
import java.util.stream.Collectors;
import com.google.api.gax.rpc.ApiException;
import com.google.cloud.translate.v3.LocationName;
import com.google.cloud.translate.v3.TranslateTextRequest;
import com.google.cloud.translate.v3.TranslateTextRequest.Builder;
import com.google.cloud.translate.v3.TranslateTextResponse;
import com.google.cloud.translate.v3.Translation;
import eu.europeana.api.translation.definitions.model.TranslationObj;
import eu.europeana.api.translation.service.AbstractTranslationService;
import eu.europeana.api.translation.service.exception.TranslationException;

/**
 * Translation service implementing remote invocation of google language detection service
 *  Note that this requires the GOOGLE_APPLICATION_CREDENTIALS environment variable to be available
 * as well as a projectId (defined in the application properties).
 * @author GordeaS
 *
 */
public class GoogleTranslationService extends AbstractTranslationService {

  private static final String MIME_TYPE_TEXT = "text/plain";
  private final String googleProjectId;

  private GoogleTranslationServiceClientWrapper clientWrapper;
  private LocationName locationName;
  private String serviceId;

  public GoogleTranslationService(String googleProjectId, GoogleTranslationServiceClientWrapper clientWrapperBean) {
    this.googleProjectId = googleProjectId;
    this.locationName = LocationName.of(googleProjectId, "global");
    this.clientWrapper = clientWrapperBean;
  }
  
  /**
   * used mainly for testing purposes. 
   * @param client
   */
  public void init(GoogleTranslationServiceClientWrapper clientWrapper) {
    this.clientWrapper = clientWrapper;
    this.locationName = LocationName.of(getGoogleProjectId(), "global");
  }

  @Override
  public void translate(List<TranslationObj> translationObjs) throws TranslationException {
    try {
      if(translationObjs.isEmpty()) {
        return;
      }
      //build request
      TranslateTextRequest request = buildTranslationRequest(translationObjs);
      //extract response
      TranslateTextResponse response = this.clientWrapper.getClient().translateText(request);

      //check if the translation is complete / successful
      if(translationObjs.size() != response.getTranslationsCount()) {
        throw new TranslationException("The translation is not completed successfully. Expected " 
            + translationObjs.size() + " but received: " + response.getTranslationsCount());
      }

      //accumulate translation results
      for (int i = 0; i < response.getTranslationsCount(); i++) {
        updateFromTranslation(translationObjs.get(i), response.getTranslations(i));
      }
    }
    catch (ApiException ex) {
      final int remoteStatusCode = ex.getStatusCode().getCode().getHttpStatusCode();
      throw new TranslationException("Exception occured during Google translation!", remoteStatusCode, ex);
    } 
    
  }

  private void updateFromTranslation( TranslationObj translationObj, Translation translation) {
    if(translationObj.getSourceLang()==null) {
      translationObj.setSourceLang(translation.getDetectedLanguageCode());
    }
    translationObj.setTranslation(translation.getTranslatedText());
  }

  private TranslateTextRequest buildTranslationRequest(List<TranslationObj> translationObjs) {
    //get texts to translate
    List<String> texts = translationObjs.stream()
        .map(to -> to.getText())
        .collect(Collectors.toList());
  
    //NOTE: for the time being all texts are expected to be in the same language and translated in the same target language
    //If these conditions change  
    
    //build request
    String targetLang = translationObjs.get(0).getTargetLang();      
    Builder requestBuilder = TranslateTextRequest.newBuilder().setParent(locationName.toString())
        .setMimeType(MIME_TYPE_TEXT).setTargetLanguageCode(targetLang).addAllContents(texts);
    String sourceLanguage = translationObjs.get(0).getSourceLang();
    if(sourceLanguage != null) {
      requestBuilder.setSourceLanguageCode(sourceLanguage);
    }
    return requestBuilder.build();
  }

  @Override
  public boolean isSupported(String srcLang, String targetLanguage) {
    return true;
  }

  @Override
  public String getExternalServiceEndPoint() {
    return "/" + getGoogleProjectId();
  }

  public String getGoogleProjectId() {
    return googleProjectId;
  }

  @Override
  public String getServiceId() {
    return serviceId;
  }

  @Override
  public void setServiceId(String serviceId) {
    this.serviceId=serviceId;
  }

  @Override
  public void close() {
    this.clientWrapper.close();
  }

}
