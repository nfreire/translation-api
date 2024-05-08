package eu.europeana.api.translation.web;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import eu.europeana.api.commons.definitions.utils.LoggingUtils;
import eu.europeana.api.translation.web.model.CachedTranslation;
import io.swagger.v3.oas.annotations.tags.Tag;
@RestController
@Tag(name = "ETranslation callback controller", description = "Receives the eTranslation response")
public class ETranslationCallbackController {

  private static final Logger LOGGER = LogManager.getLogger(ETranslationCallbackController.class);
  
  RedisTemplate<String, CachedTranslation> redisTemplate;

  @Autowired
  public ETranslationCallbackController(RedisTemplate<String, CachedTranslation> redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Tag(description = "ETranslation callback endpoint", name = "eTranslationCallback")
  @PostMapping(value = "/eTranslation/callback")
  public void eTranslationCallback(
      @RequestParam(value = "target-language", required = false) String targetLanguage,
      @RequestParam(value = "translated-text", required = false) String translatedTextSnippet,
      @RequestParam(value = "request-id", required = false) String requestId,
      @RequestParam(value = "external-reference", required = true) String externalReference,
      @RequestBody(required = false) String body) {
    if(LOGGER.isDebugEnabled()) {
      LOGGER.debug("eTranslation callback on translation api has been received with the request-id: {}, and the"
          + " external-reference: {}", LoggingUtils.sanitizeUserInput(requestId), LoggingUtils.sanitizeUserInput(externalReference));
    }
    if(externalReference!=null && translatedTextSnippet!=null) {
      redisTemplate.convertAndSend(externalReference, translatedTextSnippet);
    }
  } 
  
}