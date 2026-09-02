package io.github.patelsa032766.gmopayments.gmo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers typed provider configuration without leaking it into domain code. */
@Configuration
@EnableConfigurationProperties(GmoProperties.class)
public class GmoAdapterConfiguration {}
