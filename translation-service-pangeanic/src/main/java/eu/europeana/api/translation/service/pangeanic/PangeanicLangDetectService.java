package eu.europeana.api.translation.service.pangeanic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
import eu.europeana.api.translation.definitions.service.LanguageDetectionService;
import eu.europeana.api.translation.definitions.service.exception.LanguageDetectionException;

public class PangeanicLangDetectService implements LanguageDetectionService {
  
  protected static final Logger LOG = LogManager.getLogger(PangeanicLangDetectService.class);
  private static final double THRESHOLD = 0.5;
  private final String externalServiceEndpoint;
  private String serviceId;

  private Set<String> supportedLanguages = Set.of(
      "sk", "ro", "bg", "pl", "hr", "sv", "fr", "it", "es", "cs",
      "de", "lv", "nl", "el", "fi", "da", "sl", "hu", "pt", "et",
      "lt", "ga", "en");
  
  protected CloseableHttpClient detectClient;

    public PangeanicLangDetectService(String endPoint) {
      this.externalServiceEndpoint = endPoint;
      init();
      
    }
    
    /**
     * Creates a new client that can send translation requests to Google Cloud Translate. Note that the client needs
     * to be closed when it's not used anymore
     * @throws IOException when there is a problem retrieving the first token
     * @throws JSONException when there is a problem decoding the received token
     */
    private void init() {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(PangeanicTranslationUtils.MAX_CONNECTIONS);
        cm.setDefaultMaxPerRoute(PangeanicTranslationUtils.MAX_CONNECTIONS_PER_ROUTE);
        cm.setDefaultSocketConfig(SocketConfig.custom().setSoKeepAlive(true).setSoTimeout(3600000).build());
//        SocketConfig socketConfig = SocketConfig.custom().setSoKeepAlive(true).setSoTimeout(3600000).build(); //We need to set socket keep alive
        detectClient = HttpClients.custom().setConnectionManager(cm).build();
        LOG.info("Pangeanic Language Detection service is initialized with detect language Endpoint - {}", getExternalServiceEndPoint());
    }

    @Override
    public boolean isSupported(String srcLang) {
       return supportedLanguages.contains(srcLang);
    }

    @Override
    public List<String> detectLang(List<String> texts, String langHint) throws LanguageDetectionException {
          if(texts.isEmpty()) {
            return new ArrayList<>();
          }
          
          HttpPost post = PangeanicTranslationUtils.createDetectlanguageRequest(getExternalServiceEndPoint(), texts, langHint, "");
          return sendDetectRequestAndParse(post);
    }

    /**
     * Send the request to Pangeanic and parses the response in the list of strings
     * NOTE : We do not accept results if the threshold is lower than 0.5
     * For anything not recognised or present or not acceptable , we add null values in the list
     *
     * @param post http post request with body
     * @return list of languages detected in the same sequence
     * @throws IOException
     * @throws JSONException
     * @throws LanguageDetectionException
     */
    private List<String> sendDetectRequestAndParse(HttpPost post) throws LanguageDetectionException {
        try (CloseableHttpResponse response = detectClient.execute(post)) {
            // Pageanic BUG - sometimes language detect sends 400 Bad request with proper response and error message
            if(response == null || response.getStatusLine() == null) {
              throw new LanguageDetectionException("Invalid reponse received from Pangeanic service, no response or status line available!");
            } else if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK && response.getStatusLine().getStatusCode() != HttpStatus.SC_BAD_REQUEST) {
                throw new LanguageDetectionException("Error from Pangeanic Language Detect API: " +
                        response.getStatusLine().getStatusCode() + " - " + response.getStatusLine().getReasonPhrase());
            } else {
                String json = EntityUtils.toString(response.getEntity());
                // sometimes language detect sends 200 ok status with empty response data
                if (json.isEmpty()) {
                    throw new LanguageDetectionException("Language detect returned an empty response");
                }
                JSONObject obj = new JSONObject(json);

                // if json doesn't have detected lanaguge throw a error
                if (!obj.has(PangeanicTranslationUtils.DETECTED_LANGUAGE)) {
                    throw new LanguageDetectionException("Language detect response doesn't have detected_langs tags");
                }

                return extractDetectedLanguages(obj);
            }
        }catch (ClientProtocolException e) {
          throw new LanguageDetectionException("Remote service invocation error.", e);
        }catch (JSONException | IOException e) {
          throw new LanguageDetectionException("Cannot read pangeanic service response.", e);
        }
    }

    private List<String> extractDetectedLanguages(JSONObject obj) throws JSONException {
      List<String> result = new ArrayList<>();
      JSONArray detectedLangs = obj.getJSONArray(PangeanicTranslationUtils.DETECTED_LANGUAGE);
      for (int i = 0; i < detectedLangs.length(); i++) {
          JSONObject object = (JSONObject) detectedLangs.get(i);
          if (hasLanguageAndScoreDetected(object)) {
              double langScore = object.getDouble(PangeanicTranslationUtils.SOURCE_LANG_SCORE);
              // if lang detected is lower than 0.5 score then don't accept the results
              if (langScore >= THRESHOLD) {
                  result.add(object.getString(PangeanicTranslationUtils.SOURCE_DETECTED));
              } else {
                  result.add(null);
              }
          } else {
              // when no detected lang is returned. Ideally, this should not happen
              // But there are time Pangeanic returns no src_detected value
              // These values as well will remain non-translated
              result.add(null);
          }
      }
      return result;
    }

    private boolean hasLanguageAndScoreDetected(JSONObject object) {
        return object.has(PangeanicTranslationUtils.SOURCE_DETECTED) && object.has(PangeanicTranslationUtils.SOURCE_LANG_SCORE);
    }

    @Override
    public void close() {
        if (detectClient != null) {
            try {
                this.detectClient.close();
            } catch (IOException e) {
                LOG.error("Error closing connection to Pangeanic Translation API", e);
            }
        }
    }

    public String getExternalServiceEndPoint() {
      return externalServiceEndpoint;
    }
    
    @Override
    public String getServiceId() {
      return serviceId;
    }

    @Override
    public void setServiceId(String serviceId) {
      this.serviceId=serviceId;
    }    

}
