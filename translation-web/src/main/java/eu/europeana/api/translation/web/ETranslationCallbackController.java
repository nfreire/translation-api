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
import eu.europeana.api.translation.service.etranslation.ETranslationTranslationService;
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
  @PostMapping(value = ETranslationTranslationService.eTranslationCallbackRelativeUrl)
  public void eTranslationCallback(
      @RequestParam(value = "target-language", required = false) String targetLanguage,
      @RequestParam(value = "translated-text", required = false) String translatedTextSnippet,
      @RequestParam(value = "request-id", required = false) String requestId,
      @RequestParam(value = "external-reference", required = true) String externalReference,
      @RequestBody(required = false) String body) {
    if(LOGGER.isDebugEnabled()) {
      LOGGER.debug("eTranslation callback has been received with the request-id: {}, and the"
          + " external-reference: {}", LoggingUtils.sanitizeUserInput(requestId), LoggingUtils.sanitizeUserInput(externalReference));
    }
    /*
     * in case we send a document for the translation, we get the output in the body, or otherwise,
     * if we send a text snippet in the text-to-translate field, we ge the output in the translated-text parameter 
     * (although also extracted from the body)
     */
    String translations = translatedTextSnippet!=null ? translatedTextSnippet : body;
    if(externalReference!=null && translations!=null) {
      redisTemplate.convertAndSend(externalReference, translations);
    }
  } 

  @Tag(description = "ETranslation error callback endpoint", name = "eTranslationErrorCallback")
  @PostMapping(value = ETranslationTranslationService.eTranslationErrorCallbackRelativeUrl)
  public void eTranslationErrorCallback(
      @RequestParam(value = "error-code", required = false) String errorCode,
      @RequestParam(value = "error-message", required = false) String errorMessage,
      @RequestParam(value = "target-languages", required = false) String targetLanguages,
      @RequestParam(value = "request-id", required = false) String requestId,
      @RequestParam(value = "external-reference", required = false) String externalReference,
      @RequestBody(required = false) String body) {
    if(LOGGER.isDebugEnabled()) {
      LOGGER.debug("eTranslation error callback has been received with the following parameters: error-code: {},"
          + "error-message: {}, request-id: {}, external-reference: {}", LoggingUtils.sanitizeUserInput(errorCode),
          LoggingUtils.sanitizeUserInput(errorMessage), LoggingUtils.sanitizeUserInput(requestId), LoggingUtils.sanitizeUserInput(externalReference));
    }
    if(externalReference!=null) {
      redisTemplate.convertAndSend(externalReference, String.format("%s: error-code=%s, error-message=%s", 
          ETranslationTranslationService.eTranslationErrorCallbackIndicator, errorCode, errorMessage));
    }
  } 

}
