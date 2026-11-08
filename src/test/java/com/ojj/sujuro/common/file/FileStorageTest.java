package com.ojj.sujuro.common.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.StreamUtils;

import com.ojj.sujuro.common.exception.BusinessException;

/** 파일 저장: key 만들기(경로 막기), 서버 폴더 저장소로 저장·읽기·삭제, 저장소 오류 안내 문구 */
class FileStorageTest {

    @TempDir
    Path tmp;

    private static MockMultipartFile pdf(String body) {
        return new MockMultipartFile("file", "제안서.pdf", "application/pdf", body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void key는_하위폴더와_저장이름을_슬래시로_잇는다() {
        assertEquals("proposal/12/a.pdf", FileStorage.key("proposal/12", "a.pdf"));
        assertEquals("seed/bid/x.pdf", FileStorage.key("seed\\bid/", "x.pdf"));
        assertEquals("a/b/c", FileStorage.key("a//b", "c"));
        assertEquals("c", FileStorage.key("", "c"));
    }

    @Test
    void key는_폴더_밖을_가리키는_경로를_막는다() {
        String[][] bad = { { "../x", "a" }, { "/etc", "a" }, { "a", "../b" }, { "a/./b", "c" }, { "a", "" }, { "a", null }, { "a", "b/c" } };
        for (String[] b : bad) {
            assertThrows(BusinessException.class, () -> FileStorage.key(b[0], b[1]), b[0] + " / " + b[1]);
        }
    }

    @Test
    void 서버_폴더에_저장하고_읽고_지운다() throws Exception {
        FileStorage storage = new FileStorage(new LocalFileStore(tmp.toString()));

        String name = storage.save(pdf("hello"), "proposal/12", "pdf");
        assertTrue(name.matches("[0-9a-f]{32}\\.pdf"), name);
        assertTrue(Files.isRegularFile(tmp.resolve("proposal/12/" + name)));

        Resource r = storage.load("proposal/12", name);
        assertEquals(5, r.contentLength());
        assertEquals("hello", StreamUtils.copyToString(r.getInputStream(), StandardCharsets.UTF_8));

        storage.deleteQuietly("proposal/12", name);
        assertFalse(Files.exists(tmp.resolve("proposal/12/" + name)));
        BusinessException e = assertThrows(BusinessException.class, () -> storage.load("proposal/12", name));
        assertEquals("서버에서 파일을 찾을 수 없습니다.", e.getMessage());
    }

    @Test
    void 이상한_확장자는_저장_이름에_붙이지_않는다() {
        FileStorage storage = new FileStorage(new LocalFileStore(tmp.toString()));
        assertTrue(storage.save(pdf("x"), "ref/1", "p/df").matches("[0-9a-f]{32}"));
        assertTrue(storage.save(pdf("x"), "ref/1", null).matches("[0-9a-f]{32}"));
    }

    @Test
    void 저장소_오류는_안내_문구로_바꾼다() throws Exception {
        FileStore broken = mock(FileStore.class);
        doThrow(new RuntimeException("network")).when(broken).put(anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
        when(broken.get(anyString())).thenThrow(new RuntimeException("network"));
        doThrow(new RuntimeException("network")).when(broken).delete(anyString());
        FileStorage storage = new FileStorage(broken);

        assertEquals("파일을 저장하지 못했습니다. 잠시 후 다시 올려 주세요.",
                assertThrows(BusinessException.class, () -> storage.save(pdf("x"), "a", "pdf")).getMessage());
        assertEquals("파일 저장소에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.",
                assertThrows(BusinessException.class, () -> storage.load("a", "b.pdf")).getMessage());
        storage.deleteQuietly("a", "b.pdf"); // 조용히 무시
    }
}
