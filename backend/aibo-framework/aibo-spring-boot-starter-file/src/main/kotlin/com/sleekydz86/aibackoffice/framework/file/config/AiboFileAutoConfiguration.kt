package com.sleekydz86.aibackoffice.framework.file.config

import com.sleekydz86.aibackoffice.framework.file.core.FileRules
import com.sleekydz86.aibackoffice.framework.file.core.FileStorage
import com.sleekydz86.aibackoffice.framework.file.core.S3FileStorage
import java.net.URI
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner

@AutoConfiguration
@EnableConfigurationProperties(AiboFileProperties::class)
class AiboFileAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun fileRules(properties: AiboFileProperties): FileRules = FileRules(properties)

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnExpression("'\${aibo.file.bucket:}' != ''")
    fun s3Client(properties: AiboFileProperties): S3Client {
        val builder = S3Client.builder()
            .region(Region.of(properties.region))
            .credentialsProvider(credentials(properties))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyle).build())
        properties.endpoint.takeIf { it.isNotBlank() }?.let { builder.endpointOverride(URI.create(it)) }
        return builder.build()
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnExpression("'\${aibo.file.bucket:}' != ''")
    fun s3Presigner(properties: AiboFileProperties): S3Presigner {
        val builder = S3Presigner.builder()
            .region(Region.of(properties.region))
            .credentialsProvider(credentials(properties))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyle).build())
        properties.endpoint.takeIf { it.isNotBlank() }?.let { builder.endpointOverride(URI.create(it)) }
        return builder.build()
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnExpression("'\${aibo.file.bucket:}' != ''")
    fun fileStorage(s3: S3Client, presigner: S3Presigner, properties: AiboFileProperties): FileStorage =
        S3FileStorage(s3, presigner, properties)

    private fun credentials(properties: AiboFileProperties): AwsCredentialsProvider =
        if (properties.accessKey.isNotBlank() && properties.secretKey.isNotBlank()) {
            StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey, properties.secretKey))
        } else {
            DefaultCredentialsProvider.builder().build()
        }
}
