package com.ojj.sujuro.common.file;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.Resource;

/**
 * 파일을 실제로 두는 곳. 설정 app.storage.type 으로 하나를 고른다 (FileStoreConfig).
 *   local (기본값) : 서버 폴더 app.upload-dir  → LocalFileStore  (팀원 로컬 PC)
 *   s3             : AWS S3 버킷               → S3FileStore     (배포 서버)
 *
 * key 는 "하위폴더/저장이름" (예: proposal/12/0a1b...pdf). DB 의 FILE_PATH_NM + "/" + STRG_FILE_NM 과 같다.
 * 경로 검사(.. 금지 등)는 FileStorage 가 먼저 한다. 업무 화면은 이 인터페이스를 직접 쓰지 않고 FileStorage 를 쓴다.
 */
public interface FileStore {

    /** key 에 저장한다. 같은 key 가 있으면 덮어쓴다. size 는 바이트 수 */
    void put(String key, InputStream in, long size) throws IOException;

    /** key 의 파일. 없으면 null */
    Resource get(String key) throws IOException;

    /** key 의 파일을 지운다. 없어도 오류가 아니다 */
    void delete(String key) throws IOException;
}
