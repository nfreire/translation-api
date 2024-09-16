package eu.europeana.api.translation.service.hybrid;

import java.util.List;

import eu.europeana.api.translation.definitions.model.LanguageDetectionObj;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.api.gax.rpc.ApiException;
import com.google.cloud.translate.v3.DetectLanguageRequest;
import com.google.cloud.translate.v3.DetectLanguageRequest.Builder;
import com.google.cloud.translate.v3.DetectLanguageResponse;
import com.google.cloud.translate.v3.LocationName;
import eu.europeana.api.translation.service.LanguageDetectionService;
import eu.europeana.api.translation.service.exception.LanguageDetectionException;
import eu.europeana.api.translation.service.google.GoogleLangDetectService;
import eu.europeana.api.translation.service.google.GoogleTranslationServiceClientWrapper;
import eu.europeana.api.translation.service.tika.ApacheTikaLangDetectService;

/**
 * Translation service implementing remote invocation of google language detection service
 * @author GordeaS
 *
 */
public class HybridLangDetectService implements LanguageDetectionService {
   
  protected static final Logger LOG = LogManager.getLogger(HybridLangDetectService.class);
  private static final int MIN_TEXT_LENGTH_FOR_TIKA=40;
  private GoogleLangDetectService googleService;
  private ApacheTikaLangDetectService tikaService;
  private String serviceId;
   
  public HybridLangDetectService(String googleProjectId, GoogleTranslationServiceClientWrapper clientWrapperBean) {
	  googleService=new GoogleLangDetectService(googleProjectId, clientWrapperBean);
	  tikaService=new ApacheTikaLangDetectService();
  }

  @Override
  public boolean isSupported(String srcLang) {
     return googleService.isSupported(srcLang) && tikaService.isSupported(srcLang);
  }

  @Override
  public void detectLang(List<LanguageDetectionObj> languageDetectionObjs) throws LanguageDetectionException {
    //docs: https://cloud.google.com/translate/docs/advanced/detecting-language-v3#translate_v3_detect_language-java
    try {
      if (languageDetectionObjs.isEmpty()) {
        return;
      }

      for(LanguageDetectionObj object : languageDetectionObjs) {
    	  if (object.getText().length()>=MIN_TEXT_LENGTH_FOR_TIKA)
    		  googleService.detectLang(languageDetectionObjs);
    	  else
    		  tikaService.detectLang(languageDetectionObjs);
      }
    } catch (ApiException ex) {
      final int remoteStatusCode = ex.getStatusCode().getCode().getHttpStatusCode();
      throw new LanguageDetectionException("Exception occured during Google language detection!", remoteStatusCode, ex);
    }
  }
  
  @Override
  public void close() {
	  googleService.close();
	  tikaService.close();
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
  public String getExternalServiceEndPoint() {
    return null;
  }    

}
