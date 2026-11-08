package com.ojj.sujuro.common.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** S3 저장소: 요청에 버킷·키·크기·형식이 들어가는지, 없는 파일은 null, 그 밖의 S3 오류는 그대로 올린다 (실제 AWS 에는 접속하지 않는다) */
class S3FileStoreTest {

    private static final String BUCKET = "sujuro-files-s3";

    private S3Client s3;
    private S3FileStore store;

    @BeforeEach
    void setUp() {
        s3 = mock(S3Client.class);
        store = new S3FileStore(s3, BUCKET);
    }

    @Test
    void 버킷_이름이_비면_시작하지_않는다() {
        assertThrows(IllegalStateException.class, () -> new S3FileStore(s3, " "));
        assertThrows(IllegalStateException.class, () -> new S3FileStore(s3, null));
    }

    @Test
    void 올리기는_버킷_키_크기_형식을_넣는다() {
        store.put("proposal/12/a.pdf", new ByteArrayInputStream(new byte[5]), 5);

        ArgumentCaptor<PutObjectRequest> req = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(req.capture(), any(RequestBody.class));
        assertEquals(BUCKET, req.getValue().bucket());
        assertEquals("proposal/12/a.pdf", req.getValue().key());
        assertEquals(5L, req.getValue().contentLength());
        assertEquals("application/pdf", req.getValue().contentType());
    }

    @Test
    void 없는_파일은_null() {
        when(s3.headObject(any(HeadObjectRequest.class))).thenThrow(NoSuchKeyException.builder().message("no").build());
        assertNull(store.get("a/b.pdf"));
    }

    @Test
    void 상태코드_404도_없는_파일로_본다() {
        when(s3.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(404).message("nf").build());
        assertNull(store.get("a/b.pdf"));
    }

    @Test
    void 권한_오류는_그대로_올린다() {
        S3Exception denied = (S3Exception) S3Exception.builder().statusCode(403).message("denied").build();
        when(s3.headObject(any(HeadObjectRequest.class))).thenThrow(denied);
        assertSame(denied, assertThrows(S3Exception.class, () -> store.get("a/b.pdf")));
    }

    @Test
    void 읽기는_크기와_이름을_알려_주고_내용은_S3에서_가져온다() throws Exception {
        when(s3.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(5L).lastModified(Instant.ofEpochMilli(1000)).build());
        when(s3.getObject(any(GetObjectRequest.class))).thenAnswer(inv -> new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)))));

        Resource r = store.get("evaluation/7/x.xlsx");

        assertTrue(r.exists());
        assertEquals(5, r.contentLength());
        assertEquals(1000, r.lastModified());
        assertEquals("x.xlsx", r.getFilename());
        assertEquals("hello", StreamUtils.copyToString(r.getInputStream(), StandardCharsets.UTF_8));

        ArgumentCaptor<GetObjectRequest> req = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3).getObject(req.capture());
        assertEquals(BUCKET, req.getValue().bucket());
        assertEquals("evaluation/7/x.xlsx", req.getValue().key());
    }

    @Test
    void 삭제는_버킷과_키로_요청한다() {
        store.delete("ref/3/a.png");

        ArgumentCaptor<DeleteObjectRequest> req = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3).deleteObject(req.capture());
        assertEquals(BUCKET, req.getValue().bucket());
        assertEquals("ref/3/a.png", req.getValue().key());
    }
}
