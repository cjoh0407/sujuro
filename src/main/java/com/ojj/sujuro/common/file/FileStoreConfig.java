package com.ojj.sujuro.common.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 파일 저장소 고르기 (app.storage.type).
 *   설정 없음 또는 local : LocalFileStore (app.upload-dir 필요)  — 팀원 로컬 PC
 *   s3                   : S3FileStore    (app.storage.s3.bucket, app.storage.s3.region) — 배포 서버
 */
@Configuration
public class FileStoreConfig {

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
    public FileStore localFileStore(@Value("${app.upload-dir}") String uploadDir) {
        return new LocalFileStore(uploadDir);
    }

    /**
     * 권한은 EC2 IAM 역할에서 자동으로 받는다 (기본 자격 증명 순서). 키를 설정에 두지 않는다.
     * 통신은 자바 기본 HTTP(UrlConnectionHttpClient)를 쓴다. Apache HttpClient 를 넣으면 나라장터 호출 방식이 바뀐다 (pom.xml 참고)
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
    public S3Client s3Client(@Value("${app.storage.s3.region:ap-northeast-2}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
    public FileStore s3FileStore(S3Client s3Client, @Value("${app.storage.s3.bucket:}") String bucket) {
        return new S3FileStore(s3Client, bucket);
    }
}
