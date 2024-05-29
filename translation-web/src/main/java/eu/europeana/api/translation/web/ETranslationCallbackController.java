package eu.europeana.api.translation.web;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
  public void eTranslationCallbackPost(
      @RequestParam(value = "target-language", required = false) String targetLanguage,
      @RequestParam(value = "translated-text", required = true) String translatedTextSnippet,
      @RequestParam(value = "request-id", required = true) String requestId,
      @RequestParam(value = "external-reference", required = true) String externalReference) {

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "eTranslation callback has been received with the request-id: {}, and the"
              + " external-reference: {}",
          LoggingUtils.sanitizeUserInput(requestId),
          LoggingUtils.sanitizeUserInput(externalReference));
    }
    if (externalReference != null && translatedTextSnippet != null) {
      redisTemplate.convertAndSend(externalReference, translatedTextSnippet);
    }
  }

  @Tag(description = "ETranslation callback endpoint", name = "eTranslationCallback")
  @GetMapping(value = ETranslationTranslationService.eTranslationCallbackRelativeUrl)
  public ResponseEntity<String> eTranslationCallbackGet(
      @RequestParam(value = "target-language", required = false) String targetLanguage,
      @RequestParam(value = "translated-text", required = false) String translatedTextSnippet,
      @RequestParam(value = "request-id", required = false) String requestId,
      @RequestParam(value = "external-reference", required = false) String externalReference,
      @RequestParam(value = "timeout", required = false) int timeout,
      @RequestBody(required = false) String body) throws InterruptedException {
    if (timeout > 0) {
      // for simulation purposes, wait for $timeout seconds
      Thread.sleep(timeout * 1000);
      return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "eTranslation callback has been received with the request-id: {}, and the"
              + " external-reference: {}",
          LoggingUtils.sanitizeUserInput("" + requestId),
          LoggingUtils.sanitizeUserInput("" + externalReference));
    }
    if (externalReference != null && translatedTextSnippet != null) {
      redisTemplate.convertAndSend(externalReference, translatedTextSnippet);
    }

    return ResponseEntity.status(HttpStatus.ACCEPTED).build();


  }

  @Tag(description = "ETranslation error callback endpoint", name = "eTranslationErrorCallback")
  @PostMapping(value = ETranslationTranslationService.eTranslationErrorCallbackRelativeUrl)
  public void eTranslationErrorCallbackPost(
      @RequestParam(value = "error-code", required = false) String errorCode,
      @RequestParam(value = "error-message", required = false) String errorMessage,
      @RequestParam(value = "target-languages", required = false) String targetLanguages,
      @RequestParam(value = "request-id", required = false) String requestId,
      @RequestParam(value = "external-reference", required = false) String externalReference,
      @RequestBody(required = false) String body) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "eTranslation error callback has been received with the following parameters: error-code: {},"
              + "error-message: {}, request-id: {}, external-reference: {}",
          LoggingUtils.sanitizeUserInput(errorCode), LoggingUtils.sanitizeUserInput(errorMessage),
          LoggingUtils.sanitizeUserInput(requestId),
          LoggingUtils.sanitizeUserInput(externalReference));
    }
    if (externalReference != null) {
      redisTemplate.convertAndSend(externalReference,
          String.format("%s: error-code=%s, error-message=%s",
              ETranslationTranslationService.eTranslationErrorCallbackIndicator, errorCode,
              errorMessage));
    }
  }

  @Tag(description = "ETranslation error callback endpoint", name = "eTranslationErrorCallback")
  @GetMapping(value = ETranslationTranslationService.eTranslationErrorCallbackRelativeUrl)
  public void eTranslationErrorCallbackGet(
      @RequestParam(value = "error-code", required = false) String errorCode,
      @RequestParam(value = "error-message", required = false) String errorMessage,
      @RequestParam(value = "target-languages", required = false) String targetLanguages,
      @RequestParam(value = "request-id", required = false) String requestId,
      @RequestParam(value = "external-reference", required = false) String externalReference,
      @RequestBody(required = false) String body) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "eTranslation error callback has been received with the following parameters: error-code: {},"
              + "error-message: {}, request-id: {}, external-reference: {}",
          LoggingUtils.sanitizeUserInput(errorCode), LoggingUtils.sanitizeUserInput(errorMessage),
          LoggingUtils.sanitizeUserInput(requestId),
          LoggingUtils.sanitizeUserInput(externalReference));
    }
    if (externalReference != null) {
      redisTemplate.convertAndSend(externalReference,
          String.format("%s: error-code=%s, error-message=%s",
              ETranslationTranslationService.eTranslationErrorCallbackIndicator, errorCode,
              errorMessage));
    }
  }


}
