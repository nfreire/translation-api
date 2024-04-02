package eu.europeana.api.translation.service.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.commons.lang3.StringUtils;
import com.google.common.primitives.Ints;

public class TranslationUtils {
  
  private TranslationUtils() {
  }

  /**
   * generate redis keys
   * 
   * @param inputText the original text
   * @param sourceLang language of the original text
   * @param targetLang language of the translation
   * @param eTranslPrefix used for the eTranslation
   * @return generated redis key
   */
  public static String generateRedisKey(String inputText, String sourceLang, String targetLang, String eTranslPrefix) {
    StringBuilder builder = new StringBuilder();
    if(StringUtils.isNotBlank(eTranslPrefix)) {
      builder.append(eTranslPrefix);
    }
    builder.append(sourceLang).append(targetLang);
    byte[] hash =
        Base64.getEncoder().withoutPadding().encode(Ints.toByteArray(inputText.hashCode()));
    builder.append(new String(hash, StandardCharsets.UTF_8));
    return builder.toString();
  }
}
