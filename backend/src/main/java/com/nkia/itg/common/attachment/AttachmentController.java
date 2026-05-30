package com.nkia.itg.common.attachment;

import com.nkia.itg.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 첨부 파일 업로드 — stub 구현.
 * <p>본 단계에서는 저장소에 파일을 저장하지 않고 업로드 메타(파일명·MIME·크기)만 응답한다.
 * 실 저장(스토리지 도입)은 별도 ADR 로 다룬다(프런트 FileUpload 위젯의 백엔드 계약 stub).
 */
@Tag(name = "Attachment — 첨부 파일(stub)",
        description = "파일 업로드 메타만 응답하는 stub. 실 저장소 연동은 별도 ADR.")
@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    /** 업로드 메타 응답(저장 식별자·파일명·MIME·바이트 크기). */
    public record AttachmentMeta(
            @Schema(description = "임시 식별자(stub — 저장하지 않음)", example = "att-SAMPLE-1")
            String id,
            @Schema(description = "원본 파일명", example = "SAMPLE-스크린샷.png")
            String fileName,
            @Schema(description = "MIME 타입", example = "image/png")
            String mime,
            @Schema(description = "바이트 크기", example = "20480")
            long size
    ) {}

    @Operation(
            summary = "파일 업로드(stub)",
            description = "multipart 파일 한 건을 받아 메타만 반환한다. 파일은 저장하지 않는다. 인증 필수."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "업로드 메타 반환"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<AttachmentMeta>> upload(
            @RequestParam("file") MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        String mime = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        AttachmentMeta meta = new AttachmentMeta(
                "att-" + Math.abs((name + file.getSize()).hashCode()),
                name,
                mime,
                file.getSize()
        );
        return ResponseEntity.ok(ApiResponse.ok(meta));
    }
}
