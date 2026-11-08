package com.ojj.sujuro.common.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;

import com.ojj.sujuro.common.exception.BusinessException;

/**
 * 서버 폴더(app.upload-dir) 저장소. 팀원 로컬 PC의 기본값이다 (app.storage.type 이 없거나 local).
 * 파일 위치는 <app.upload-dir>/<key> 이다.
 */
public class LocalFileStore implements FileStore {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStore.class);

    private final Path root;

    public LocalFileStore(String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        log.info("파일 저장소: 서버 폴더 {}", root);
    }

    @Override
    public void put(String key, InputStream in, long size) throws IOException {
        Path p = path(key);
        Files.createDirectories(p.getParent());
        Files.copy(in, p, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public Resource get(String key) {
        Path p = path(key);
        return Files.isRegularFile(p) ? new PathResource(p) : null;
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(path(key));
    }

    /** root 밖으로 나가는 경로는 막는다 (FileStorage 가 먼저 막지만 한 번 더) */
    private Path path(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root) || p.equals(root)) {
            throw new BusinessException("잘못된 파일 경로입니다.");
        }
        return p;
    }
}
