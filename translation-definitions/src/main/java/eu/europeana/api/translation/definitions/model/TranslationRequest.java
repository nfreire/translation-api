package eu.europeana.api.translation.definitions.model;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import eu.europeana.api.translation.definitions.vocabulary.TranslationAppConstants;

@JsonInclude(value = JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
/**
 * The representation of translation request body 
 * @author GordeaS
 *
 */
public class TranslationRequest {

  private String source;
  private String target;
  private String service;
  private String fallback;
  private List<String> text;
  //caching enabled by default
  private Boolean caching = Boolean.TRUE;

  public TranslationRequest() {
    super();
  }

  @JsonGetter(TranslationAppConstants.SOURCE_LANG)
  public String getSource() {
    return source;
  }

  @JsonSetter(TranslationAppConstants.SOURCE_LANG)
  public void setSource(String source) {
    this.source = source;
  }

  @JsonGetter(TranslationAppConstants.TARGET_LANG)
  public String getTarget() {
    return target;
  }

  @JsonSetter(TranslationAppConstants.TARGET_LANG)
  public void setTarget(String target) {
    this.target = target;
  }

  @JsonGetter(TranslationAppConstants.SERVICE)
  public String getService() {
    return service;
  }

  @JsonSetter(TranslationAppConstants.SERVICE)
  public void setService(String service) {
    this.service = service;
  }

  @JsonGetter(TranslationAppConstants.FALLBACK)
  public String getFallback() {
    return fallback;
  }

  @JsonSetter(TranslationAppConstants.FALLBACK)
  public void setFallback(String fallback) {
    this.fallback = fallback;
  }

  @JsonGetter(TranslationAppConstants.TEXT)
  public List<String> getText() {
    return text;
  }

  @JsonSetter(TranslationAppConstants.TEXT)
  public void setText(List<String> text) {
    this.text = text;
  }

  /**
   * Utility method indicating if the caching service should be used for this request 
   * @return true if request should be served with the caching service, caching is true by default
   */
  public boolean useCaching() {
    return caching;
  }
  
  @JsonGetter(TranslationAppConstants.CACHING)
  public Boolean getCaching() {
    return caching;
  }

  @JsonSetter(TranslationAppConstants.CACHING)
  public void setCaching(boolean caching) {
    this.caching = caching;
  }

}
