package eu.europeana.api.translation.config;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

/**
 * Container for all settings that we load from the translation.properties file and optionally
 * override from translation.user.properties file
 */
@Configuration(BeanNames.BEAN_TRANSLATION_CONFIG)
@PropertySources({@PropertySource("classpath:translation.properties"),
@PropertySource(value = "translation.user.properties", ignoreResourceNotFound = true)})
public class TranslationConfig{

  private static final Logger LOG = LogManager.getLogger(TranslationConfig.class);
  /** Matches spring.profiles.active property in test/resource application.properties file */
  public static final String ACTIVE_TEST_PROFILE = "test";

  @Value("${europeana.apikey.jwttoken.signaturekey:}")
//  @Value("${europeana.signaturekey:}")
  private String apiKeyPublicKey;

  @Value("${authorization.api.name: translations}")
  private String authorizationApiName;

  @Value("${auth.read.enabled: true}")
  private boolean authReadEnabled;

  @Value("${auth.write.enabled: true}")
  private boolean authWriteEnabled;

  @Value("${spring.profiles.active:}")
  private String activeProfileString;

  @Value("${europeana.apikey.serviceurl:}")
  private String apiKeyUrl;

  @Value("${translation.pangeanic.endpoint.detect}")
  private String pangeanicDetectEndpoint;

  @Value("${translation.pangeanic.endpoint.translate}")
  private String pangeanicTranslateEndpoint;
  
  @Value("${translation.google.projectId:}")
  private String googleTranslateProjectId;
  
  @Value("${translation.google.usehttpclient: false}")
  private boolean useGoogleHttpClient;
  
  
  public TranslationConfig() {
    LOG.info("Initializing TranslConfigProperties bean.");
  }

  public String getApiKeyPublicKey() {
    return apiKeyPublicKey;
  }

  public String getAuthorizationApiName() {
    return authorizationApiName;
  }

  public boolean isAuthReadEnabled() {
    return authReadEnabled;
  }

  public boolean isAuthWriteEnabled() {
    return authWriteEnabled;
  }

  public String getApiKeyUrl() {
    return apiKeyUrl;
  }

  public String getPangeanicDetectEndpoint() {
    return pangeanicDetectEndpoint;
  }

  public String getPangeanicTranslateEndpoint() {
    return pangeanicTranslateEndpoint;
  }

  public String getGoogleTranslateProjectId() {
    return googleTranslateProjectId;
  }

  public void setTranslationGoogleProjectId(String googleTranslateProjectId) {
    this.googleTranslateProjectId = googleTranslateProjectId;
  }

//  public static boolean testProfileNotActive(String activeProfileString) {
//    return Arrays.stream(activeProfileString.split(",")).noneMatch(ACTIVE_TEST_PROFILE::equals);
//  }

  /** verify properties */
  public void verifyRequiredProperties() {
    List<String> missingProps = new ArrayList<>();

    if (isAuthReadEnabled() && StringUtils.isBlank(getApiKeyUrl())) {
      missingProps.add("europeana.apikey.jwttoken.signaturekey");
    }

    if (isAuthWriteEnabled() && StringUtils.isBlank(getApiKeyPublicKey())) {
      missingProps.add("europeana.apikey.serviceurl");
    }


    if (!missingProps.isEmpty()) {
      throw new IllegalStateException(String.format(
          "The following config properties are not set: %s", String.join("\n", missingProps)));
    }
  }

  public boolean useGoogleHttpClient() {
    return useGoogleHttpClient;
  }
  
}
