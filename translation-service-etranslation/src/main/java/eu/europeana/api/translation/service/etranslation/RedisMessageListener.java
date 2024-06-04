package eu.europeana.api.translation.service.etranslation;

import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

/**
 * Class for receiving messages from the Redis publisher, following a publish/subscribe mechanism.
 */
public class RedisMessageListener implements MessageListener {

    private static final Logger LOGGER = LogManager.getLogger(RedisMessageListener.class);
    private String message;
    //if true, the message received will be a document (e.g. from the eTranslation), otherwise a text-snippet
    private boolean messageAsDocument; 
    
    public RedisMessageListener(boolean messageAsDocument) {
      super();
      this.messageAsDocument = messageAsDocument;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
      synchronized(this) {
        if(LOGGER.isDebugEnabled()) {
          LOGGER.debug("New message received from RedisMessageListener: {}", message);
        }
        String messageBody=new String(message.getBody(), StandardCharsets.UTF_8);
        if(messageBody.contains(ETranslationTranslationService.eTranslationErrorCallbackIndicator)) {
          //if we enter here, means the eTranslation error callback is called
          this.message=messageBody;
        }
        else {
          if(messageAsDocument) {
            this.message = messageBody;
          }
          else {
            /* 
             * the received message is treated as a json object and we need some adjustments for the escaped characters
             * (this only applies if we get the translated text from the translated-text field in the eTransl callback,
             * which happens if we send the text to be translated in the textToTranslate request param)
             */
            //remove double quotes at the beginning and at the end of the response, from some reason they are duplicated
            String messageRemDuplQuotes = messageBody.replaceAll("^\"|\"$", "");
            //replace a double backslash with a single backslash
            this.message = messageRemDuplQuotes.replace("\\n", "\n");
          }
        }
        
        //notify all threads waiting on this object
        notifyAll();
      }
    }

    public String getMessage() {
      return message;
    }
}
