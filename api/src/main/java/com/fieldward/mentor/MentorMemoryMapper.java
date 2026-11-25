package com.fieldward.mentor;

import com.fieldward.resume.ResumeUpload;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
interface MentorMemoryMapper {
    String NOTES = """
            note.id::text AS id, note.category, note.body, note.created_at,
            note.source_turn_id::text AS source_turn_id, turn.side_chat_id::text AS side_id, side.title AS side_title
            FROM mentor_memory_notes note
            LEFT JOIN mentor_turns turn ON turn.conversation_id = note.conversation_id AND turn.id = note.source_turn_id
            LEFT JOIN mentor_side_chats side ON side.conversation_id = turn.conversation_id AND side.id = turn.side_chat_id
            WHERE note.conversation_id = #{conversation}
            """;
    String FILE = """
            file.id::text AS id, file.file_name AS name, file.content_type, file.created_at,
            octet_length(file.file_bytes) AS size_bytes, (file.extracted_text <> '' OR ocr.body IS NOT NULL) AS readable,
            CASE WHEN file.content_type = 'application/pdf' AND file.extracted_text = ''
                THEN COALESCE(ocr.status, 'PENDING') END AS ocr_status
            """;
    String FILES = """
            FROM mentor_files file
            JOIN mentor_main_conversations conversation ON conversation.id = file.conversation_id
            LEFT JOIN mentor_document_ocr ocr ON ocr.account_id = conversation.account_id AND ocr.sha256 = file.sha256
            WHERE file.conversation_id = #{conversation}
            """;

    @Insert("""
            INSERT INTO mentor_memory_notes (conversation_id, source_turn_id, category, body)
            VALUES (#{conversation}, #{turn}, #{category}, #{body})
            ON CONFLICT (conversation_id, source_turn_id, body) DO NOTHING
            """)
    void saveNote(@Param("conversation") long conversation, @Param("turn") Long turn,
                  @Param("category") String category, @Param("body") String body);

    @Select("SELECT " + NOTES + """
              AND note.id < COALESCE(CAST(#{before} AS bigint), 9223372036854775807)
            ORDER BY note.id DESC LIMIT #{limit}
            """)
    List<Note> notes(@Param("conversation") long conversation, @Param("before") Long before, @Param("limit") int limit);

    @Select("SELECT " + NOTES + """
              AND (note.source_turn_id IS NULL OR note.source_turn_id < #{turn})
            ORDER BY ts_rank(mentor_search_vector(note.body), CAST(#{query} AS tsquery), 32) DESC, note.id DESC
            LIMIT 20
            """)
    List<Note> recallNotes(@Param("conversation") long conversation, @Param("turn") long turn,
                           @Param("query") String query);

    @Insert("""
            INSERT INTO mentor_files (conversation_id, request_id, file_name, content_type, sha256, extracted_text, file_bytes)
            VALUES (#{conversation}, CAST(#{requestId} AS uuid), #{file.name}, #{file.contentType}, #{file.sha256},
                    #{file.text}, #{file.bytes})
            ON CONFLICT (conversation_id, request_id) DO NOTHING
            """)
    void saveFile(@Param("conversation") long conversation, @Param("requestId") String requestId,
                  @Param("file") ResumeUpload file);

    @Select("""
            SELECT sha256 FROM mentor_files WHERE conversation_id = #{conversation}
                AND request_id = CAST(#{requestId} AS uuid)
            """)
    String fileHash(@Param("conversation") long conversation, @Param("requestId") String requestId);

    @Select("SELECT " + FILE + FILES + """
                AND file.request_id = CAST(#{requestId} AS uuid)
            """)
    FileInfo uploadedFile(@Param("conversation") long conversation, @Param("requestId") String requestId);

    @Select("SELECT " + FILE + FILES + """
                AND file.id < COALESCE(CAST(#{before} AS bigint), 9223372036854775807)
            ORDER BY file.id DESC LIMIT #{limit}
            """)
    List<FileInfo> files(@Param("conversation") long conversation, @Param("before") Long before, @Param("limit") int limit);

    @Select("""
            SELECT file.id::text AS id, file.file_name AS name,
                COALESCE(NULLIF(file.extracted_text, ''), ocr.body, '') AS body, ocr.body IS NOT NULL AS ocr
            """ + FILES + """
            ORDER BY GREATEST(
                ts_rank(mentor_search_vector(file.extracted_text || ' ' || file.file_name), CAST(#{query} AS tsquery), 32),
                ts_rank(mentor_search_vector(ocr.body), CAST(#{query} AS tsquery), 32)
            ) DESC, file.id DESC LIMIT 6
            """)
    List<FileExcerpt> recallFiles(@Param("conversation") long conversation, @Param("query") String query);

    @Select("""
            SELECT file_name AS name, content_type, file_bytes AS bytes FROM mentor_files
            WHERE conversation_id = #{conversation} AND id = #{id}
            """)
    Original original(@Param("conversation") long conversation, @Param("id") long id);

    record Note(String id, String category, String body, OffsetDateTime createdAt, String sourceTurnId,
                String sideId, String sideTitle) {}
    record FileInfo(String id, String name, String contentType, OffsetDateTime createdAt, int sizeBytes, boolean readable,
                    String ocrStatus) {}
    record FileExcerpt(String id, String name, String body, boolean ocr) {}
    record Original(String name, String contentType, byte[] bytes) {}
}
