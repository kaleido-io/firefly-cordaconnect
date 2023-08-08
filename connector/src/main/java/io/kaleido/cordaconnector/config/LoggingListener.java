package io.kaleido.cordaconnector.config;

import java.util.Arrays;
import java.util.List;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

public class LoggingListener implements ApplicationListener<ApplicationPreparedEvent> {

  private final List<String> SENSITIVE_PROPERTIES = List.of("password", "credential", "token");
  private final List<String> RELEVANT_PROPERTIES = List.of(
      "rpc",
      "db",
      "java.vm.vendor",
      "java.vendor.version");
  Logger logger = LoggerFactory.getLogger(LoggingListener.class);

  @Override
  public void onApplicationEvent(final ApplicationPreparedEvent event) {
    logProperties(event.getApplicationContext());
  }

  public void logProperties(ApplicationContext applicationContext) {
    final Environment env = applicationContext.getEnvironment();
    logger.info("====== Properties in effect ======");

    if (env instanceof AbstractEnvironment) {
      final MutablePropertySources sources = ((AbstractEnvironment) env).getPropertySources();
      StreamSupport.stream(sources.spliterator(), false)
          .filter(ps -> ps instanceof EnumerablePropertySource)
          .map(this::castToEnumerablePropertySource)
          .map(EnumerablePropertySource::getPropertyNames)
          .flatMap(Arrays::stream)
          .forEach(prop -> appendProperty(env, prop, logger));
    } else {
      logger.info("Unable to read properties");
    }
  }

  private EnumerablePropertySource<?> castToEnumerablePropertySource(final PropertySource<?> propertySource) {
    return (EnumerablePropertySource<?>) propertySource;
  }

  private void appendProperty(final Environment env, final String prop, final Logger logger) {
    boolean isASensitiveProperty = SENSITIVE_PROPERTIES.stream()
        .anyMatch(prop::contains);
    boolean isRelevantProperty = RELEVANT_PROPERTIES.stream()
        .anyMatch(prop::contains);
    try {
      final String value;
      if (isASensitiveProperty) {
        value = StringUtils.abbreviate(env.getProperty(prop), 4);
      } else {
        value = env.getProperty(prop);
      }
      if (isRelevantProperty) {
        System.out.printf("%s: %s\n", prop, value);
      }
    } catch (Exception ex) {
      logger.info(String.format("%s: %s", prop, ex.getMessage()));
    }

  }
}