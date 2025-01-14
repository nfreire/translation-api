package eu.europeana.api.translation.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import eu.europeana.api.commons.config.i18n.I18nService;
import eu.europeana.api.commons.config.i18n.I18nServiceImpl;
import eu.europeana.api.commons.oauth2.service.impl.EuropeanaClientDetailsService;
import eu.europeana.api.translation.service.etranslation.ETranslationTranslationService;
import eu.europeana.api.translation.service.exception.LangDetectionServiceConfigurationException;
import eu.europeana.api.translation.service.exception.TranslationServiceConfigurationException;
import eu.europeana.api.translation.service.google.DummyGLangDetectService;
import eu.europeana.api.translation.service.google.DummyGTranslateService;
import eu.europeana.api.translation.service.google.GoogleLangDetectService;
import eu.europeana.api.translation.service.google.GoogleTranslationService;
import eu.europeana.api.translation.service.google.GoogleTranslationServiceClientWrapper;
import eu.europeana.api.translation.service.pangeanic.DummyPangLangDetectService;
import eu.europeana.api.translation.service.pangeanic.DummyPangTranslationService;
import eu.europeana.api.translation.service.pangeanic.PangeanicLangDetectService;
import eu.europeana.api.translation.service.pangeanic.PangeanicTranslationService;
import eu.europeana.api.translation.service.tika.ApacheTikaLangDetectService;
import eu.europeana.api.translation.service.tika.DummyApacheTikaLangDetectService;
import eu.europeana.api.translation.web.exception.AppConfigurationException;
import eu.europeana.api.translation.web.model.CachedTranslation;
import eu.europeana.api.translation.web.service.LangDetectionPreProcessor;
import eu.europeana.api.translation.web.service.RedisCacheService;
import eu.europeana.api.translation.web.service.TranslationPreProcessor;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SslOptions;

@Configuration()
@PropertySource(value = "translation.user.properties", ignoreResourceNotFound = true)
public class TranslationApiAutoconfig implements ApplicationListener<ApplicationStartedEvent> {

  final String FILE_PANGEANIC_LANGUAGE_THRESHOLDS = "pangeanic_language_thresholds.properties";
  private final Logger logger = LogManager.getLogger(TranslationApiAutoconfig.class);

  /**
   * Any value that has at least 2 unicode consecutive letters. The condition considered the
   * fact that there can be words with only 2 letters that retain sufficient meaning and are therefore reasonable to be translated,
   * especially when looking at languages other than English (see article - https://www.grammarly.com/blog/the-shortest-words-in-the-english-language/).
   */
  private static final String PATTERN = "\\p{IsAlphabetic}{2,}";
  private static final Pattern IsAlphabetic = Pattern.compile(PATTERN);

  private final TranslationConfig translationConfig;
  TranslationServiceProvider translationServiceConfigProvider;
  
  @Value("${translation.service.config.file:}")
  private String serviceConfigFile;
  
  public TranslationApiAutoconfig(@Autowired TranslationConfig translationConfig) {
    this.translationConfig = translationConfig;
  }

  @Bean(BeanNames.BEAN_CLIENT_DETAILS_SERVICE)
  public EuropeanaClientDetailsService getClientDetailsService() {
    EuropeanaClientDetailsService clientDetailsService = new EuropeanaClientDetailsService();
    clientDetailsService.setApiKeyServiceUrl(translationConfig.getApiKeyUrl());
    return clientDetailsService;
  }

  @Bean(BeanNames.BEAN_I18N_SERVICE)
  public I18nService getI18nService() {
    return new I18nServiceImpl();
  }

  @Bean("messageSource")
  public MessageSource getMessageSource() {
    ReloadableResourceBundleMessageSource messageSource =
        new ReloadableResourceBundleMessageSource();
    messageSource.setBasename("classpath:messages");
    messageSource.setDefaultEncoding("utf-8");
    messageSource.setDefaultLocale(Locale.ENGLISH);
    return messageSource;
  }

  /**
   * Creates a new client wrapper that can send translation requests to Google Cloud Translate. Note
   * that the client needs to be closed when it's not used anymore
   * 
   * @throws IOException
   */
  @Bean(BeanNames.BEAN_GOOGLE_TRANSLATION_CLIENT_WRAPPER)
  public GoogleTranslationServiceClientWrapper getGoogleTranslationServiceClientWrapper()
      throws IOException {
    return new GoogleTranslationServiceClientWrapper(
        translationConfig.getGoogleTranslateProjectId(), translationConfig.useGoogleHttpClient());
  }

  @Bean(BeanNames.BEAN_APACHE_TIKA_LANG_DETECT_SERVICE)
  public ApacheTikaLangDetectService getApacheTikaLangDetectService() {
    if (translationConfig.isUseDummyServices()) {
      return new DummyApacheTikaLangDetectService();
    } else {
      return new ApacheTikaLangDetectService();
    }
  }

  @Bean(BeanNames.BEAN_PANGEANIC_LANG_DETECT_SERVICE)
  public PangeanicLangDetectService getPangeanicLangDetectService() {
    if (translationConfig.isUseDummyServices()) {
      return new DummyPangLangDetectService();
    } else {
      return new PangeanicLangDetectService(translationConfig.getPangeanicDetectEndpoint());
    }
  }

  @Bean(BeanNames.BEAN_PANGEANIC_TRANSLATION_SERVICE)
  public PangeanicTranslationService getPangeanicTranslationService(
      @Qualifier(BeanNames.BEAN_PANGEANIC_LANG_DETECT_SERVICE) PangeanicLangDetectService pangeanicLangDetectService)
      throws TranslationServiceConfigurationException {
    if (translationConfig.isUseDummyServices()) {
      return new DummyPangTranslationService();
    } else {
      return new PangeanicTranslationService(translationConfig.getPangeanicTranslateEndpoint(),
          pangeanicLangDetectService, loadPangeanicThresholds());
    }
  }

  private Properties loadPangeanicThresholds() throws TranslationServiceConfigurationException {

    Properties thresholds = new Properties();

    File languageThresholdsFile = getConfigFile(FILE_PANGEANIC_LANGUAGE_THRESHOLDS);
    if (languageThresholdsFile.exists()) {
      // load thresholds from config file if available
      try (Reader input = Files.newBufferedReader(languageThresholdsFile.toPath())) {
        thresholds.load(input);
        if(logger.isInfoEnabled()) {
          logger.info("Successfully loaded pangeanic thresholds from config file, Values: {}", thresholds);
        }
      } catch (IOException e) {
        throw new TranslationServiceConfigurationException(
            "Cannot load pangeanic language thresholds from config file: " + languageThresholdsFile,
            e);
      }
    } else {
      // load thresholds from resources if available, need to search in the root folder of resources
      try (InputStream input = TranslationApiAutoconfig.class
          .getResourceAsStream("/" + FILE_PANGEANIC_LANGUAGE_THRESHOLDS)) {
        if (input != null) {
          thresholds.load(input);
          if(logger.isInfoEnabled()) {
            logger.info("Successfully loaded pangeanic thresholds from resources, Values: {}", thresholds);
          }
        }
      } catch (IOException e) {
        throw new TranslationServiceConfigurationException(
            "Cannot load pangeanic languae thresholds from file: " + languageThresholdsFile, e);
      }
    }

    // load properties
    if (thresholds.isEmpty()) {
      if (logger.isInfoEnabled()) {
        logger.info("No configurations found for pangeanic language thresholds available.");
      }
    }

    return thresholds;
  }


  @Bean(BeanNames.BEAN_GOOGLE_LANG_DETECT_SERVICE)
  public GoogleLangDetectService getGoogleLangDetectService(
      @Qualifier(BeanNames.BEAN_GOOGLE_TRANSLATION_CLIENT_WRAPPER) GoogleTranslationServiceClientWrapper googleTranslationServiceClientWrapper) {
    if (translationConfig.isUseDummyServices()) {
      return new DummyGLangDetectService(googleTranslationServiceClientWrapper);
    } else {
      return new GoogleLangDetectService(translationConfig.getGoogleTranslateProjectId(),
          googleTranslationServiceClientWrapper);
    }
  }

  @Bean(BeanNames.BEAN_GOOGLE_TRANSLATION_SERVICE)
  public GoogleTranslationService getGoogleTranslationService(
      @Qualifier(BeanNames.BEAN_GOOGLE_TRANSLATION_CLIENT_WRAPPER) GoogleTranslationServiceClientWrapper googleTranslationServiceClientWrapper) {
    if (translationConfig.isUseDummyServices()) {
      return new DummyGTranslateService(googleTranslationServiceClientWrapper);
    } else {
      return new GoogleTranslationService(translationConfig.getGoogleTranslateProjectId(),
          googleTranslationServiceClientWrapper);
    }
  }

  @Bean(BeanNames.BEAN_E_TRANSLATION_SERVICE)
  public ETranslationTranslationService getETranslationService(
      @Qualifier(BeanNames.BEAN_REDIS_MESSAGE_LISTENER_CONTAINER) RedisMessageListenerContainer redisMessageListenerContainer) throws Exception {
    return new ETranslationTranslationService(
        translationConfig.getEtranslationBaseUrl(), 
        translationConfig.getEtranslationDomain(), 
        translationConfig.getTranslationApiBaseUrl(),
        translationConfig.getEtranslationMaxWaitMillisec(), 
        translationConfig.getEtranslationUsername(),
        translationConfig.getEtranslationPassword(),
        redisMessageListenerContainer);
  }

  @Bean(BeanNames.BEAN_SERVICE_PROVIDER)
  @DependsOn(value = {BeanNames.BEAN_PANGEANIC_LANG_DETECT_SERVICE,
      BeanNames.BEAN_PANGEANIC_TRANSLATION_SERVICE, BeanNames.BEAN_GOOGLE_TRANSLATION_SERVICE})
  public TranslationServiceProvider getTranslationServiceProvider() {
    if(StringUtils.isNotEmpty(serviceConfigFile)) {
      translationServiceConfigProvider = new TranslationServiceProvider(getConfigFile(serviceConfigFile));
    } else {
      translationServiceConfigProvider = new TranslationServiceProvider();  
    }
    
    return translationServiceConfigProvider;
  }

  @Bean(BeanNames.BEAN_LANGDETECT_PRE_PROCESSOR_SERVICE)
  public LangDetectionPreProcessor langDetectionPreProcessor() {
   return new LangDetectionPreProcessor(IsAlphabetic);
  }

  @Bean(BeanNames.BEAN_TRANSLATION_PRE_PROCESSOR_SERVICE)
  public TranslationPreProcessor translationPreProcessor() {
    return new TranslationPreProcessor(IsAlphabetic);
  }


  /*
   * Help, see connect to a standalone redis server:
   * https://medium.com/turkcell/making-first-connection-to-redis-with-java-application-spring-boot-
   * 4fc58e6fa173 A separate connection factory bean is needed here because of the proper
   * initialization, where some methods (e.g. afterPropertiesSet()) are called by spring after the
   * bean creation. Otherwise all these methods would need to be called manually which is not the
   * best solution.
   */
  @Bean(BeanNames.BEAN_REDIS_CONNECTION_FACTORY)
  LettuceConnectionFactory getRedisConnectionFactory() throws AppConfigurationException {
    // in case of integration tests, we do not need the SSL certificate
    LettuceClientConfiguration.LettuceClientConfigurationBuilder lettuceClientConfigurationBuilder =
        LettuceClientConfiguration.builder();
    // if redis secure protocol is used (rediss vs. redis)
    boolean sslEnabled = translationConfig.getRedisConnectionUrl().startsWith("rediss");
    if (sslEnabled) {
      final File truststore = getTrustoreFile();
      SslOptions sslOptions = SslOptions.builder().jdkSslProvider()
          .truststore(truststore, translationConfig.getTruststorePass()).build();

      ClientOptions clientOptions = ClientOptions.builder().sslOptions(sslOptions).build();

      lettuceClientConfigurationBuilder.clientOptions(clientOptions).useSsl();
    }

    LettuceClientConfiguration lettuceClientConfiguration =
        lettuceClientConfigurationBuilder.build();

    RedisConfiguration redisConf = LettuceConnectionFactory
        .createRedisConfiguration(translationConfig.getRedisConnectionUrl());
    return new LettuceConnectionFactory(redisConf, lettuceClientConfiguration);
  }

  private File getTrustoreFile() throws AppConfigurationException {

    String truststorePathConfig = translationConfig.getTruststorePath();
    if (truststorePathConfig == null) {
      throw new AppConfigurationException(
          "A trustore must be provided in configurations when confinguring redis ssl connection");
    }
    // allow configurations to use the full path, for backward compatibility
    final File trustoreFile = getConfigFile(truststorePathConfig);
    if (!trustoreFile.exists()) {
      throw new AppConfigurationException(
          "Invalid config file location: " + trustoreFile.getAbsolutePath());
    }
    return trustoreFile;
  }

  /**
   * If the input is a full path, the filename will be extracted
   * 
   * @param configFile name of the config file
   * @return the File object for the configFile within the config folder
   */
  private File getConfigFile(String configFile) {
    return new File(translationConfig.getConfigFolder(), FilenameUtils.getName(configFile));
  }

  @Bean(BeanNames.BEAN_REDIS_TEMPLATE)
  public RedisTemplate<String, CachedTranslation> getRedisTemplate(
      @Qualifier(BeanNames.BEAN_REDIS_CONNECTION_FACTORY) LettuceConnectionFactory redisConnectionFactory) throws AppConfigurationException {
    RedisTemplate<String, CachedTranslation> redisTemplate = new RedisTemplate<>();
    redisConnectionFactory.afterPropertiesSet();
    redisTemplate.setConnectionFactory(redisConnectionFactory);
    redisTemplate.setKeySerializer(new StringRedisSerializer());
    redisTemplate.setValueSerializer(
        new Jackson2JsonRedisSerializer<CachedTranslation>(CachedTranslation.class));
    redisTemplate.afterPropertiesSet();
    return redisTemplate;
  }
  
  @Bean(BeanNames.BEAN_REDIS_CACHE_SERVICE)
  @ConditionalOnProperty(name = "redis.connection.url")
  public RedisCacheService getRedisCacheService(
      @Qualifier(BeanNames.BEAN_REDIS_TEMPLATE) RedisTemplate<String, CachedTranslation> redisTemplate) throws AppConfigurationException {
    return new RedisCacheService(redisTemplate);
  }

  @Bean(BeanNames.BEAN_REDIS_MESSAGE_LISTENER_ADAPTER)
  MessageListenerAdapter listenerAdapter() {
      return new MessageListenerAdapter();
  }
  
  @Bean(BeanNames.BEAN_REDIS_MESSAGE_LISTENER_CONTAINER)
  RedisMessageListenerContainer getRedisMessageListenerContainer(
      @Qualifier(BeanNames.BEAN_REDIS_CONNECTION_FACTORY) LettuceConnectionFactory redisConnectionFactory,
      @Qualifier(BeanNames.BEAN_REDIS_MESSAGE_LISTENER_ADAPTER) MessageListenerAdapter messageListenerAdapter
      ) throws AppConfigurationException {
    RedisMessageListenerContainer container  = new RedisMessageListenerContainer(); 
    redisConnectionFactory.afterPropertiesSet();
    container.setConnectionFactory(redisConnectionFactory); 
    /*
     * This is needed to avoid some cases redis closes all channels and does not allow any subscriptions (please see here: https://github.com/spring-projects/spring-data-redis/issues/2425).
     * In this case we create one channel that is never un-subscribed from.
     */
    container.addMessageListener(messageListenerAdapter, ChannelTopic.of("default"));
//    container.addMessageListener(messageListener(), topic()); 
    return container; 
  }

  @Override
  public void onApplicationEvent(ApplicationStartedEvent event) {
    // log beans for debuging purposes
    if (logger.isDebugEnabled()) {
      printRegisteredBeans(event.getApplicationContext());
    }

    // load either normal or dummy services (used for stress testing)
    loadServices(event);
  }

  private void loadServices(ApplicationStartedEvent event) {
    try {
      // verify required configurations for initialization of translation services
      verifyMandatoryProperties(event.getApplicationContext());

      // init translation services
      initTranslationServices(event.getApplicationContext());
    } catch (Exception e) {
      // gracefully stop the application in case of configuration problems (code 1 means exception
      // occured at startup)
      logger.fatal(
          "Stopping application. Translation Service initialization failed due to configuration errors!",
          e);
      System.exit(SpringApplication.exit(event.getApplicationContext(), () -> 1));
    }
  }

  /**
   * Method for initialization of service provider using the service configurations
   * 
   * @param ctx the application context holding the initialized beans
   * @throws TranslationServiceConfigurationException if translations services cannot be correctly
   *         instantiated
   * @throws LangDetectionServiceConfigurationException if language detection services cannot be
   *         correctly instantiated
   */
  public void initTranslationServices(ApplicationContext ctx)
      throws TranslationServiceConfigurationException, LangDetectionServiceConfigurationException {
    TranslationServiceProvider translationServiceProvider =
        (TranslationServiceProvider) ctx.getBean(BeanNames.BEAN_SERVICE_PROVIDER);
    translationServiceProvider.initTranslationServicesConfiguration();
  }

  /**
   * Method to verify required properties in translation config
   * 
   * @param ctx the application context holding references to instantiated beans
   */
  public void verifyMandatoryProperties(ApplicationContext ctx) {
    translationConfig.verifyRequiredProperties();
  }

  private void printRegisteredBeans(ApplicationContext ctx) {
    String[] beanNames = ctx.getBeanDefinitionNames();
    Arrays.sort(beanNames);
    logger.debug("Instantiated beans:");
    logger.debug(StringUtils.join(beanNames, "\n"));
  }

}

