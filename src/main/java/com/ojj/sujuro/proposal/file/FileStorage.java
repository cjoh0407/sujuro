package com.ojj.sujuro.proposal.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.ojj.sujuro.common.exception.BusinessException;

/**
 * 업로드 파일을 서버 폴더(app.upload-dir, application-local.properties)에 저장하고 꺼낸다.
 * 저장 이름은 UUID라 원래 이름이 겹치거나 경로 문자가 들어 있어도 안전하다. 원래 이름은 DB에만 둔다.
 * 업로드 검사(FR-065: 파일당 50MB, 실행 파일 금지)도 여기서 한다. 제안서 파일·제출서류·조견표가 같이 쓴다.
 */
@Component
public class FileStorage {

    /** 파일당 최대 용량 50MB (FR-065) */
    public static final long MAX_SIZE = 50L * 1024 * 1024;

    /** 보안을 위해 막는 실행 파일 확장자 (FR-065) */
    private static final Set<String> BLOCKED_EXT = new HashSet<>(Arrays.asList(
            "exe", "bat", "cmd", "com", "msi", "scr", "ps1", "vbs", "js", "jar", "sh", "dll", "cpl"));

    /**
     * 올린 파일을 검사하고 원래 이름(경로를 뗀 것)을 돌려준다.
     * 비었거나, 50MB를 넘거나, 실행 파일이면 업무 예외 (FR-065)
     */
    public static String checkUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("올릴 파일을 골라 주세요.");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("파일당 최대 50MB까지 올릴 수 있습니다. ZIP으로 압축해서 다시 올려 주세요.");
        }
        String name = cleanName(file.getOriginalFilename());
        String ext = extension(name);
        if (BLOCKED_EXT.contains(ext)) {
            throw new BusinessException("실행 파일(." + ext + ")은 보안을 위해 올릴 수 없습니다.");
        }
        return name;
    }

    /** 경로를 떼고 이름만 남긴다 (일부 브라우저는 전체 경로를 보낸다). 300자 제한 */
    public static String cleanName(String name) {
        String n = name == null ? "" : name.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).trim();
        if (n.isEmpty()) {
            n = "file";
        }
        return n.length() > 300 ? n.substring(n.length() - 300) : n;
    }

    /** 소문자 확장자. 없으면 빈 문자열 */
    public static String extension(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 || i == name.length() - 1 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    /** DB 확장자 컬럼(5자)에 넣을 값. 길면 null */
    public static String extColumn(String ext) {
        return ext == null || ext.isEmpty() || ext.length() > 5 ? null : ext;
    }

    private final Path root;

    public FileStorage(@Value("${app.upload-dir}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /** subDir(예: proposal/12) 아래에 저장하고 저장 이름을 돌려준다. */
    public String save(MultipartFile file, String subDir, String ext) {
        String safeExt = ext == null || ext.isEmpty() || ext.length() > 10 || !ext.matches("[a-z0-9]+") ? "" : ext;
        String name = UUID.randomUUID().toString().replace("-", "") + (safeExt.isEmpty() ? "" : "." + safeExt);
        Path dir = resolve(subDir);
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(dir);
            Files.copy(in, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("파일을 저장하지 못했습니다. 잠시 후 다시 올려 주세요.");
        }
        return name;
    }

    /** 저장한 파일. 없으면 업무 예외 */
    public Resource load(String subDir, String storedName) {
        Path p = resolve(subDir).resolve(storedName).normalize();
        if (!p.startsWith(root) || !Files.isRegularFile(p)) {
            throw new BusinessException("서버에서 파일을 찾을 수 없습니다.");
        }
        return new PathResource(p);
    }

    /** DB 저장이 실패했을 때 방금 저장한 파일을 지운다. (조용히 무시) */
    public void deleteQuietly(String subDir, String storedName) {
        try {
            Files.deleteIfExists(resolve(subDir).resolve(storedName));
        } catch (IOException ignored) {
            // 남아도 화면에는 보이지 않는다
        }
    }

    private Path resolve(String subDir) {
        Path p = root.resolve(subDir).normalize();
        if (!p.startsWith(root)) {
            throw new BusinessException("잘못된 파일 경로입니다.");
        }
        return p;
    }
}
