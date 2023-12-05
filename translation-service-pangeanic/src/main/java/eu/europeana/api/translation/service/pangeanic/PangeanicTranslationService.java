package eu.europeana.api.translation.service.pangeanic;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import eu.europeana.api.translation.definitions.language.PangeanicLanguages;
import eu.europeana.api.translation.definitions.model.TranslationString;
import eu.europeana.api.translation.service.AbstractTranslationService;
import eu.europeana.api.translation.service.exception.LanguageDetectionException;
import eu.europeana.api.translation.service.exception.TranslationException;
import eu.europeana.api.translation.service.util.LoggingUtils;

/**
 * Service to send data to translate to Pangeanic Translate API V2
 * 
 * @author Srishti Singh
 */
// TODO get api key, for now passed empty
public class PangeanicTranslationService extends AbstractTranslationService {

  private PangeanicLangDetectService langDetectService;

  protected static final Logger LOG = LogManager.getLogger(PangeanicTranslationService.class);
  public final String externalServiceEndpoint;

  protected CloseableHttpClient translateClient;
  private String serviceId;

  public PangeanicTranslationService(String externalServiceEndpoint,
      PangeanicLangDetectService langDetectService) {
    this.externalServiceEndpoint = externalServiceEndpoint;
    this.langDetectService = langDetectService;
    init();
  }


  /**
   * Creates a new client that can send translation requests to Google Cloud Translate. Note that
   * the client needs to be closed when it's not used anymore
   * 
   * @throws IOException when there is a problem retrieving the first token
   * @throws JSONException when there is a problem decoding the received token
   */
  private void init() {
    if (StringUtils.isBlank(getExternalServiceEndPoint())) {
      return;
    }

    PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
    cm.setMaxTotal(PangeanicTranslationUtils.MAX_CONNECTIONS);
    cm.setDefaultMaxPerRoute(PangeanicTranslationUtils.MAX_CONNECTIONS_PER_ROUTE);
    cm.setDefaultSocketConfig(
        SocketConfig.custom().setSoKeepAlive(true).setSoTimeout(3600000).build());
    // SocketConfig socketConfig =
    // SocketConfig.custom().setSoKeepAlive(true).setSoTimeout(3600000).build(); //We need to set
    // socket keep alive
    translateClient = HttpClients.custom().setConnectionManager(cm).build();
    if (LOG.isInfoEnabled()) {
      LOG.info("Pangeanic translation service is initialized with translate Endpoint - {}",
          getExternalServiceEndPoint());
    }
  }

  /**
   * target language should be English for Pangeanic Translations and validate the source language
   * with list of supported languages
   *
   * @param srcLang source language of the data to be translated
   * @param targetLanguage target language in which data has to be translated
   * @return
   */
  @Override
  public boolean isSupported(String srcLang, String targetLanguage) {
    if (srcLang == null) {
      // automatic language detection
      return isTargetSupported(targetLanguage);
    }
    return PangeanicLanguages.isLanguagePairSupported(srcLang, targetLanguage);
  }

  private boolean isTargetSupported(String targetLanguage) {
    return PangeanicLanguages.isTargetLanguageSupported(targetLanguage);
  }

  @Override
  public void translate(List<TranslationString> translationStrings) throws TranslationException {
    try {
      if (translationStrings.isEmpty()) {
        return;
      }

      if (translationStrings.get(0).getSourceLang() == null) {
        // if the source language was not provided in the request, language detection needs to be called
        detectLanguages(translationStrings);
      }

      computeTranslations(translationStrings);

    } catch (JSONException e) {
      throw new TranslationException("Exception occured during Pangeanic translation!",
          HttpStatus.SC_BAD_GATEWAY, e);
    }
  }

  private void computeTranslations(List<TranslationString> translationStrings)
      throws JSONException, TranslationException {
    
    //collect source languages, they might be multiple 
    Set<String> sourceLanguages = new HashSet<>(translationStrings.stream().map(to -> to.getSourceLang()).toList());
    
    List<TranslationString> toTranslatePerLanguage;
    //the request has only one target language
    String targetLang = translationStrings.get(0).getTargetLang();
        
    for (String sourceLanguage : sourceLanguages) {
      if(sourceLanguages.size() == 1) {
        //not needed to iterate if all are in the same language, it will be only one translation request for all objects
        toTranslatePerLanguage = translationStrings;
      }else {
        toTranslatePerLanguage = getObjectsWithSourceLanguage(translationStrings, sourceLanguage);
      }
      //perform translation and fill results, the translations are filled directly in the original TranslationObj 
      translateAndAccumulateResults(toTranslatePerLanguage, sourceLanguage, targetLang);
    }
  }


  private void translateAndAccumulateResults(List<TranslationString> toTranslatePerLanguage,
      String sourceLanguage, String targetLang) throws JSONException, TranslationException {
    
    if(sourceLanguage == null) {
      //language not provided and not detected, skip translation request
      return;  
    }
    
    // send the translation request
    List<String> translTexts = toTranslatePerLanguage.stream().map(to -> to.getText()).toList();
    HttpPost translateRequest = PangeanicTranslationUtils.createTranslateRequest(
        getExternalServiceEndPoint(), translTexts, targetLang, sourceLanguage, "");
    
    //parse response and accumulate results 
    sendTranslateRequestAndFillTranslations(translateRequest, toTranslatePerLanguage, sourceLanguage);
  }

  private List<TranslationString> getObjectsWithSourceLanguage(List<TranslationString> translationStrings,
                                                               String sourceLanguage) {
    return translationStrings.stream()
        .filter(to -> sourceLanguage.equals(to.getSourceLang())).toList();
  }


  private void detectLanguages(List<TranslationString> translationStrings) throws TranslationException {
    if (langDetectService == null) {
      throw new TranslationException("No langDetectService configured!",
          HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }

    // detect languages
    List<String> texts =
        translationStrings.stream().map(to -> to.getText()).toList();
    List<String> detectedLanguages = null;
    try {
      detectedLanguages = langDetectService.detectLang(texts, null);
    } catch (LanguageDetectionException e) {
      throw new TranslationException("Error when tryng to detect the language of the text input!",
          e.getRemoteStatusCode(), e);
    }

    // verify language detection response
    if (detectedLanguages == null || detectedLanguages.contains(null) || detectedLanguages.size() != translationStrings.size()) {
      throw new TranslationException(
          "The translation cannot be performed. A list of detected languages is null or contains nulls.");
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug(
          "Pangeanic detect lang request with hint null is executed. Detected languages are {} ",
          LoggingUtils.sanitizeUserInput(detectedLanguages.toString()));
    }

    // update source language
    for (int i = 0; i < detectedLanguages.size(); i++) {
      translationStrings.get(i).setSourceLang(detectedLanguages.get(i));
    }
  }

  private void sendTranslateRequestAndFillTranslations(HttpPost post, List<TranslationString> translationStrings, String sourceLanguage) throws TranslationException {
    // initialize with unknown
    int remoteStatusCode = -1;
    try (CloseableHttpResponse response = translateClient.execute(post)) {
      if (response == null || response.getStatusLine() == null) {
        throw new TranslationException(
            "Invalid reponse received from Pangeanic service, no response or status line available!");
      }

      remoteStatusCode = response.getStatusLine().getStatusCode();
      boolean failedRequest = remoteStatusCode != HttpStatus.SC_OK;
      String responseBody = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
      if (failedRequest) {
        throw new TranslationException(
            "Error from Pangeanic Translation API: " + responseBody, remoteStatusCode);
      } else {
        JSONObject obj = new JSONObject(responseBody);
        // there are cases where we get an empty response
        if (!obj.has(PangeanicTranslationUtils.TRANSLATIONS)) {
          throw new TranslationException("Pangeanic Translation API returned empty response",
              remoteStatusCode);
        }
        extractTranslations(obj, translationStrings, sourceLanguage, remoteStatusCode);
      }
    } catch (ClientProtocolException e) {
      throw new TranslationException("Remote service invocation error.", remoteStatusCode, e);
    } catch (JSONException | IOException e) {
      throw new TranslationException("Cannot read pangeanic service response.", remoteStatusCode,
          e);
    }
  }

  private void extractTranslations(JSONObject obj, List<TranslationString> translationStrings,
      String sourceLanguage, int remoteStatusCode)
      throws JSONException, TranslationException {
    JSONArray translations = obj.getJSONArray(PangeanicTranslationUtils.TRANSLATIONS);
    if (translations.length() == 0) {
      throw new TranslationException("Translation failed (empty list) for source language - "
          + obj.get(PangeanicTranslationUtils.SOURCE_LANG), remoteStatusCode);
    }
    
    //ensure the size of the response is correct 
    if(translations.length() != translationStrings.size()){
      throw new TranslationException(
          "The translation is incomplete for text with language: " + sourceLanguage
          + ".  Expected " + translationStrings.size() + " but received: " + translations.length());
        
    }
    
    for (int i = 0; i < translations.length(); i++) {
      JSONObject object = (JSONObject) translations.get(i);
      if (hasTranslations(object)) {
        double score = object.getDouble(PangeanicTranslationUtils.TRANSLATE_SCORE);
        // only if score returned by the translation service is greater the threshold value, we
        // will accept the translations
        if (score > PangeanicLanguages.getThresholdForLanguage(sourceLanguage)) {
          translationStrings.get(i)
              .setTranslation(object.getString(PangeanicTranslationUtils.TRANSLATE_TARGET));
        }
      }
    }
  }

  private boolean hasTranslations(JSONObject object) {
    return object.has(PangeanicTranslationUtils.TRANSLATE_SOURCE)
        && object.has(PangeanicTranslationUtils.TRANSLATE_TARGET);
  }


  @Override
  public void close() {
    if (translateClient != null) {
      try {
        this.translateClient.close();
      } catch (RuntimeException | IOException e) {
        LOG.error("Error closing connection to Pangeanic Translation API", e);
      }
    }
  }


  @Override
  public String getExternalServiceEndPoint() {
    return externalServiceEndpoint;
  }


  @Override
  public String getServiceId() {
    return serviceId;
  }

  @Override
  public void setServiceId(String serviceId) {
    this.serviceId = serviceId;
  }

}
