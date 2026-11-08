package com.ojj.sujuro.common.file;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * AWS S3 저장소. 배포 서버에서 app.storage.type=s3 로 켠다.
 * 객체 키는 FileStore 의 key 그대로다 (서버 폴더 구조와 같아서 aws s3 sync 로 옮길 수 있다).
 *
 * 권한은 EC2 에 붙인 IAM 역할(sujuro-ec2-role)에서 SDK 가 자동으로 받는다. 액세스 키를 설정에 두지 않는다.
 * 버킷은 퍼블릭 접근 차단 상태이고, 내려받기는 앱이 권한을 확인한 뒤 이 클래스로 읽어 보낸다.
 */
public class S3FileStore implements FileStore {

    private static final Logger log = LoggerFactory.getLogger(S3FileStore.class);

    private final S3Client s3;
    private final String bucket;

    public S3FileStore(S3Client s3, String bucket) {
        if (bucket == null || bucket.trim().isEmpty()) {
            throw new IllegalStateException("app.storage.s3.bucket 이 비어 있습니다.");
        }
        this.s3 = s3;
        this.bucket = bucket.trim();
        log.info("파일 저장소: S3 버킷 {}", this.bucket);
    }

    @Override
    public void put(String key, InputStream in, long size) {
        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentLength(size)
                .contentType(contentType(key))
                .build();
        s3.putObject(req, RequestBody.fromInputStream(in, size));
    }

    /** 있는지와 크기를 먼저 확인한다(HEAD). 내용은 화면이 읽을 때(getInputStream) 가져온다 */
    @Override
    public Resource get(String key) {
        HeadObjectResponse head;
        try {
            head = s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (NoSuchKeyException e) {
            return null;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return null;
            }
            throw e;
        }
        long length = head.contentLength() == null ? -1 : head.contentLength();
        Instant modified = head.lastModified();
        return new S3ObjectResource(s3, bucket, key, length, modified == null ? 0 : modified.toEpochMilli());
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    static String contentType(String key) {
        return MediaTypeFactory.getMediaType(key).map(MediaType::toString).orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    /**
     * S3 객체 하나. 크기·이름을 알려 줘서 내려받기 응답에 Content-Length 가 붙는다.
     * getInputStream 을 부를 때마다 S3 에서 새로 읽는다 (여러 번 읽어도 된다).
     */
    static class S3ObjectResource extends AbstractResource {

        private final S3Client s3;
        private final String bucket;
        private final String key;
        private final long length;
        private final long lastModified;

        S3ObjectResource(S3Client s3, String bucket, String key, long length, long lastModified) {
            this.s3 = s3;
            this.bucket = bucket;
            this.key = key;
            this.length = length;
            this.lastModified = lastModified;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public long contentLength() {
            return length;
        }

        @Override
        public long lastModified() {
            return lastModified;
        }

        @Override
        public String getFilename() {
            return key.substring(key.lastIndexOf('/') + 1);
        }

        @Override
        public String getDescription() {
            return "S3 객체 [s3://" + bucket + "/" + key + "]";
        }
    }
}
